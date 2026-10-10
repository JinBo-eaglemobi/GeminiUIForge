package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.utils.geometry.RectTransformHelper
import org.gemini.ui.forge.utils.geometry.TransformHandle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图集原图高精度交互校对画布
 *
 * 遵循红线规则：
 * 1. 纯数学几何算法 100% 统一复用 [RectTransformHelper]；
 * 2. 控制手柄与图元拉伸严格基于光标逻辑绝对坐标直接锚定（Direct Cursor Pinning），误差 0 像素；
 * 3. 手势协程使用长效保活修饰符 `.pointerInput(Unit)`；
 * 4. 滚轮无损以光标为中心缩放。
 */
@Composable
fun AssetSliceCanvas(
    imageBitmap: ImageBitmap?,
    items: List<EditableSliceItem>,
    selectedItemId: String?,
    toolMode: VerifyToolMode,
    scale: Float,
    panOffset: Offset,
    onScaleChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onSelectItem: (String?) -> Unit,
    onUpdateItemBounds: (String, SerialRect) -> Unit,
    onAddNewItem: (SerialRect) -> Unit,
    onBeforeTransform: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // 长效保活引用的最新状态
    val currentItems by rememberUpdatedState(items)
    val currentSelectedId by rememberUpdatedState(selectedItemId)
    val currentToolMode by rememberUpdatedState(toolMode)
    val currentScale by rememberUpdatedState(scale)
    val currentPan by rememberUpdatedState(panOffset)

    // 拖拽操作私有状态（彻底解耦，防止状态异步时将 A 图元的 bounds 改写给 B 图元）
    var activeDragTargetId by remember { mutableStateOf<String?>(null) }
    var activeHandle by remember { mutableStateOf<TransformHandle?>(null) }
    var isDraggingBlock by remember { mutableStateOf(false) }
    var isPanningCanvas by remember { mutableStateOf(false) }
    var dragBlockStartLogicalOffset by remember { mutableStateOf<Offset?>(null) }
    var dragBlockInitialBounds by remember { mutableStateOf<SerialRect?>(null) }
    var hasActuallyChanged by remember { mutableStateOf(false) }

    // 新建画框拖拽状态
    var isDrawingRect by remember { mutableStateOf(false) }
    var drawStartLogicalPos by remember { mutableStateOf<Offset?>(null) }
    var drawCurrentLogicalPos by remember { mutableStateOf<Offset?>(null) }

    // 屏幕像素坐标转原图逻辑绝对坐标
    fun screenToLogical(screenOffset: Offset): Offset {
        return (screenOffset - currentPan) / currentScale
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0F172A)) // 深暗色网格底板
            // 1. 鼠标滚轮以光标为中心缩放
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            val scrollDeltaY = change.scrollDelta.y
                            if (scrollDeltaY != 0f) {
                                val cursorScreenPos = change.position
                                val oldScale = currentScale
                                val zoomFactor = if (scrollDeltaY < 0f) 1.15f else 1f / 1.15f
                                val newScale = (oldScale * zoomFactor).coerceIn(0.1f, 8.0f)

                                // 以光标为锚点无损缩放
                                val logicalCursor = (cursorScreenPos - currentPan) / oldScale
                                val newPan = cursorScreenPos - logicalCursor * newScale

                                onScaleChange(newScale)
                                onPanChange(newPan)
                                change.consume()
                            }
                        }
                    }
                }
            }
            // 2. 单击即时命中选择监听（消除“必须拖动才显示属性”的缺陷）
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { screenPos ->
                        if (currentToolMode == VerifyToolMode.SELECT) {
                            val logicalPos = screenToLogical(screenPos)
                            // 倒序优先命中上层
                            val hitItem = currentItems.reversed().find { item ->
                                val b = item.bounds
                                logicalPos.x >= b.left && logicalPos.x <= b.right &&
                                        logicalPos.y >= b.top && logicalPos.y <= b.bottom
                            }
                            onSelectItem(hitItem?.id)
                        }
                    }
                )
            }
            // 3. 长效拖拽手势保活监听（拉伸、移动、画框、画布平移）
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startScreenPos ->
                        val logicalPos = screenToLogical(startScreenPos)
                        hasActuallyChanged = false

                        // 模式 A：手动画框补录模式
                        if (currentToolMode == VerifyToolMode.DRAW_RECT) {
                            isDrawingRect = true
                            drawStartLogicalPos = logicalPos
                            drawCurrentLogicalPos = logicalPos
                            return@detectDragGestures
                        }

                        // 模式 B：选择与 8 向拉伸变换模式
                        val selected = currentItems.find { it.id == currentSelectedId }
                        val touchRadiusLogical = 12f / currentScale

                        // 优先检查是否命中了当前选中图元的 8 向控制手柄
                        val hitHandle = if (selected != null) {
                            RectTransformHelper.hitTestHandle(
                                rect = selected.bounds,
                                logicalX = logicalPos.x,
                                logicalY = logicalPos.y,
                                touchSlopLogical = touchRadiusLogical
                            )
                        } else null

                        if (hitHandle != null && selected != null) {
                            // 命中了选中图元的手柄
                            onBeforeTransform()
                            activeHandle = hitHandle
                            activeDragTargetId = selected.id
                            isDraggingBlock = false
                            isPanningCanvas = false
                        } else {
                            activeHandle = null
                            // 检测是否命中任意图元包围盒（倒序优先命中上层）
                            val hitItem = currentItems.reversed().find { item ->
                                val b = item.bounds
                                logicalPos.x >= b.left && logicalPos.x <= b.right &&
                                        logicalPos.y >= b.top && logicalPos.y <= b.bottom
                            }

                            if (hitItem != null) {
                                // 命中具体图元：锁死拖拽目标为此图元，并立即同步选中状态
                                onBeforeTransform()
                                activeDragTargetId = hitItem.id
                                isDraggingBlock = true
                                isPanningCanvas = false
                                dragBlockStartLogicalOffset = logicalPos
                                dragBlockInitialBounds = hitItem.bounds
                                onSelectItem(hitItem.id)
                            } else {
                                // 均未命中，取消选中，进入画布视口平移模式
                                activeDragTargetId = null
                                isDraggingBlock = false
                                isPanningCanvas = true
                                onSelectItem(null)
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentScreenPos = change.position
                        val logicalPos = screenToLogical(currentScreenPos)

                        // 画框模式中更新矩形对角线
                        if (isDrawingRect) {
                            drawCurrentLogicalPos = logicalPos
                            return@detectDragGestures
                        }

                        // 8 向手柄直接锚定拉伸（严格作用于 activeDragTargetId）
                        val handle = activeHandle
                        val targetId = activeDragTargetId
                        if (handle != null && targetId != null) {
                            val target = currentItems.find { it.id == targetId }
                            if (target != null) {
                                val newRect = RectTransformHelper.resizeRectDirectPin(
                                    current = target.bounds,
                                    handle = handle,
                                    cursorPos = logicalPos,
                                    minSize = 8f
                                )
                                onUpdateItemBounds(targetId, newRect)
                                hasActuallyChanged = true
                            }
                            return@detectDragGestures
                        }

                        // 移动选中图元整体位置（严格作用于 activeDragTargetId）
                        if (isDraggingBlock && targetId != null) {
                            val initial = dragBlockInitialBounds
                            val start = dragBlockStartLogicalOffset
                            if (initial != null && start != null) {
                                val delta = logicalPos - start
                                val newRect = SerialRect(
                                    left = initial.left + delta.x,
                                    top = initial.top + delta.y,
                                    right = initial.right + delta.x,
                                    bottom = initial.bottom + delta.y
                                )
                                onUpdateItemBounds(targetId, newRect)
                                hasActuallyChanged = true
                            }
                            return@detectDragGestures
                        }

                        // 平移整个视口画布
                        if (isPanningCanvas) {
                            onPanChange(currentPan + dragAmount)
                        }
                    },
                    onDragEnd = {
                        // 结束画框并新建图元
                        if (isDrawingRect) {
                            val start = drawStartLogicalPos
                            val end = drawCurrentLogicalPos
                            if (start != null && end != null) {
                                val l = min(start.x, end.x)
                                val r = max(start.x, end.x)
                                val t = min(start.y, end.y)
                                val b = max(start.y, end.y)
                                if ((r - l) >= 12f && (b - t) >= 12f) {
                                    onAddNewItem(SerialRect(l, t, r, b))
                                }
                            }
                        }

                        isDrawingRect = false
                        drawStartLogicalPos = null
                        drawCurrentLogicalPos = null
                        activeHandle = null
                        activeDragTargetId = null
                        isDraggingBlock = false
                        isPanningCanvas = false
                        dragBlockStartLogicalOffset = null
                        dragBlockInitialBounds = null
                        hasActuallyChanged = false
                    },
                    onDragCancel = {
                        isDrawingRect = false
                        drawStartLogicalPos = null
                        drawCurrentLogicalPos = null
                        activeHandle = null
                        activeDragTargetId = null
                        isDraggingBlock = false
                        isPanningCanvas = false
                        dragBlockStartLogicalOffset = null
                        dragBlockInitialBounds = null
                        hasActuallyChanged = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = scale
            val pan = panOffset

            // 1. 绘制底层原图
            if (imageBitmap != null) {
                drawImage(
                    image = imageBitmap,
                    topLeft = pan,
                    alpha = 1.0f
                )
            }

            // 逻辑坐标转屏幕坐标
            fun logicalToScreen(lX: Float, lY: Float): Offset {
                return Offset(lX * s + pan.x, lY * s + pan.y)
            }

            // 2. 绘制所有图元线框与胶囊标签
            items.forEach { item ->
                val isSelected = item.id == selectedItemId
                val b = item.bounds

                val screenTopLeft = logicalToScreen(b.left, b.top)
                val screenBottomRight = logicalToScreen(b.right, b.bottom)
                val screenW = screenBottomRight.x - screenTopLeft.x
                val screenH = screenBottomRight.y - screenTopLeft.y

                val strokeColor = if (isSelected) Color(0xFFFF9100) else Color(0xFF00E5FF)
                val fillColor = if (isSelected) Color(0xFFFF9100).copy(alpha = 0.15f) else Color(0xFF00E5FF).copy(alpha = 0.05f)
                val strokeWidth = if (isSelected) 2.5f else 1.2f

                // 填充与边框
                drawRect(
                    color = fillColor,
                    topLeft = screenTopLeft,
                    size = Size(screenW, screenH)
                )
                drawRect(
                    color = strokeColor,
                    topLeft = screenTopLeft,
                    size = Size(screenW, screenH),
                    style = Stroke(width = strokeWidth)
                )

                // 绘制名称与尺寸药丸小标签
                val tagText = "${item.name} [${b.width.roundToInt()}×${b.height.roundToInt()}]"
                val textLayout = textMeasurer.measure(
                    text = tagText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                )

                val tagHeight = textLayout.size.height + 6f
                val tagWidth = textLayout.size.width + 12f
                val tagTopLeft = Offset(screenTopLeft.x, max(0f, screenTopLeft.y - tagHeight - 2f))

                drawRoundRect(
                    color = if (isSelected) Color(0xFFE65100).copy(alpha = 0.9f) else Color(0xFF006064).copy(alpha = 0.85f),
                    topLeft = tagTopLeft,
                    size = Size(tagWidth, tagHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = tagText,
                    topLeft = Offset(tagTopLeft.x + 6f, tagTopLeft.y + 3f),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                )

                // 3. 为当前选中的图元绘制 8 向控制手柄
                if (isSelected) {
                    val handleRadius = 5.5f
                    val handleColor = Color.White
                    val handleBorder = Color(0xFFE65100)

                    val cx = (screenTopLeft.x + screenBottomRight.x) / 2f
                    val cy = (screenTopLeft.y + screenBottomRight.y) / 2f

                    val handles = listOf(
                        Offset(screenTopLeft.x, screenTopLeft.y),
                        Offset(cx, screenTopLeft.y),
                        Offset(screenBottomRight.x, screenTopLeft.y),
                        Offset(screenTopLeft.x, cy),
                        Offset(screenBottomRight.x, cy),
                        Offset(screenTopLeft.x, screenBottomRight.y),
                        Offset(cx, screenBottomRight.y),
                        Offset(screenBottomRight.x, screenBottomRight.y)
                    )

                    handles.forEach { pos ->
                        drawCircle(color = handleColor, radius = handleRadius, center = pos)
                        drawCircle(color = handleBorder, radius = handleRadius, center = pos, style = Stroke(width = 1.5f))
                    }
                }
            }

            // 4. 绘制新建画框时的实时虚线框
            if (isDrawingRect && drawStartLogicalPos != null && drawCurrentLogicalPos != null) {
                val start = drawStartLogicalPos!!
                val cur = drawCurrentLogicalPos!!
                val l = min(start.x, cur.x)
                val r = max(start.x, cur.x)
                val t = min(start.y, cur.y)
                val b = max(start.y, cur.y)

                val screenTL = logicalToScreen(l, t)
                val screenBR = logicalToScreen(r, b)

                drawRect(
                    color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                    topLeft = screenTL,
                    size = Size(screenBR.x - screenTL.x, screenBR.y - screenTL.y)
                )
                drawRect(
                    color = Color(0xFFB388FF),
                    topLeft = screenTL,
                    size = Size(screenBR.x - screenTL.x, screenBR.y - screenTL.y),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

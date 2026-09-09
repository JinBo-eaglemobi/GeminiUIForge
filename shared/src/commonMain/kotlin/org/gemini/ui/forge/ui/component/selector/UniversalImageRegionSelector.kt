package org.gemini.ui.forge.ui.component.selector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.ResizeVerticalIcon
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.utils.decodeToBitmap
import org.gemini.ui.forge.utils.toImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 图像数据源多态封装密封类
 */
sealed class RegionImageSource {
    data class FromBitmap(val bitmap: ImageBitmap) : RegionImageSource()
    data class FromFile(val file: TemplateFile) : RegionImageSource()
    data class FromPath(val absolutePath: String) : RegionImageSource()
    data class FromBytes(val bytes: ByteArray) : RegionImageSource() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as FromBytes
            return bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/**
 * 8 方向手柄枚举
 */
enum class RegionHandle {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * 全局通用高精度图片选区裁切组件 (UniversalImageRegionSelector)
 *
 * 工业级规范：
 * 1. 鼠标光标定点无级缩放 (Cursor-Centric Zooming)：以鼠标指针所在像素为绝对定点缩放；
 * 2. 键盘全能快捷键 (Ctrl+/-, Ctrl+0, Space+拖拽)；
 * 3. 浮动缩放控制岛 (Floating Zoom HUD)；
 * 4. 纯净 Px 物理坐标系，选区与底图 100% 严密贴合。
 */
@Composable
fun UniversalImageRegionSelector(
    imageSource: RegionImageSource?,
    pageWidth: Float = 1080f,
    pageHeight: Float = 1920f,
    initialRect: SerialRect? = null,
    initialZoom: Float = 1.0f,
    minSelectionSize: Float = 8f,
    selectionBorderColor: Color = Color(0xFF00E5FF),
    dimColor: Color = Color.Black,
    showThirdsGrid: Boolean = true,
    showDimensionBadge: Boolean = true,
    onSelectionChange: (SerialRect?) -> Unit = {},
    onSelectionConfirmed: (SerialRect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. 记录打开弹窗时的原始初始选区（用于永久常驻的重置按钮）
    val originalInitialBounds = remember { initialRect }

    // 2. 核心选区矩形状态与手势交互锁
    var selectedRect by remember { mutableStateOf(initialRect) }
    var isInteracting by remember { mutableStateOf(false) }

    // 3. 外部变更同步（仅在非手势交互时响应）
    LaunchedEffect(initialRect) {
        if (!isInteracting && initialRect != selectedRect) {
            selectedRect = initialRect
        }
    }

    // 4. 异步解码底图为 ImageBitmap
    val bitmapState = produceState<ImageBitmap?>(null, imageSource) {
        value = when (imageSource) {
            is RegionImageSource.FromBitmap -> imageSource.bitmap
            is RegionImageSource.FromFile -> imageSource.file.decodeToBitmap()
            is RegionImageSource.FromPath -> imageSource.absolutePath.decodeBase64ToBitmap()
            is RegionImageSource.FromBytes -> imageSource.bytes.toImageBitmap()
            null -> null
        }
    }
    val loadedBitmap = bitmapState.value

    // 5. 交互修饰键与视口缩放状态 (Px)
    var isSpacePressed by remember { mutableStateOf(false) }
    var isRightButtonDragging by remember { mutableStateOf(false) }
    var isAltPressed by remember { mutableStateOf(false) }
    var isShiftPressed by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(initialZoom.coerceIn(0.1f, 15f)) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    var currentCursorIcon by remember { mutableStateOf(PointerIcon.Default) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .clipToBounds()
            .background(Color(0xFF141414))
            .onPreviewKeyEvent { keyEvent ->
                // 空格键抓手状态与全套键盘快捷键（必须在 Preview 阶段拦截，防止弹窗内按钮抢先消费空格）
                if (keyEvent.key == Key.Spacebar) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> { isSpacePressed = true; true }
                        KeyEventType.KeyUp -> { isSpacePressed = false; true }
                        else -> false
                    }
                } else if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrl = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                    when {
                        (isCtrl && (keyEvent.key == Key.Equals || keyEvent.key == Key.Plus)) || keyEvent.key == Key.Plus -> {
                            zoom = (zoom * 1.2f).coerceIn(0.1f, 15f)
                            true
                        }
                        (isCtrl && keyEvent.key == Key.Minus) || keyEvent.key == Key.Minus -> {
                            zoom = (zoom / 1.2f).coerceIn(0.1f, 15f)
                            true
                        }
                        isCtrl && (keyEvent.key == Key.Zero || keyEvent.key == Key.NumPad0) -> {
                            zoom = 1.0f
                            pan = Offset.Zero
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerWPx = with(density) { maxWidth.toPx() }
            val containerHPx = with(density) { maxHeight.toPx() }

            // 6. 核心法定基准：统一以画布逻辑尺寸 (pageWidth, pageHeight) 计算适应缩放与居中偏移 (Px)
            val fitScale = min(containerWPx / pageWidth, containerHPx / pageHeight)
            val effectiveScale = fitScale * zoom

            val baseOffsetX = (containerWPx - pageWidth * effectiveScale) / 2f + pan.x
            val baseOffsetY = (containerHPx - pageHeight * effectiveScale) / 2f + pan.y

            var activeHandle by remember { mutableStateOf<RegionHandle?>(null) }
            var isMovingRect by remember { mutableStateOf(false) }
            var isCreatingNew by remember { mutableStateOf(false) }
            var createStartPos by remember { mutableStateOf<Offset?>(null) }

            val currentRectState by rememberUpdatedState(selectedRect)
            val spaceState by rememberUpdatedState(isSpacePressed)
            // 视口参数动态快照：空格拖动会持续修改 pan -> baseOffset 变化，
            // 若作为 pointerInput 的 key 会在拖拽中途取消手势协程导致平移立即中断，
            // 因此手势协程一律 pointerInput(Unit)，运行期通过 State 读取最新值
            val baseOffsetXState by rememberUpdatedState(baseOffsetX)
            val baseOffsetYState by rememberUpdatedState(baseOffsetY)
            val effectiveScaleState by rememberUpdatedState(effectiveScale)
            val altState by rememberUpdatedState(isAltPressed)
            val panState by rememberUpdatedState(pan)
            val zoomState by rememberUpdatedState(zoom)

            // 7. 屏幕物理像素 -> 画布逻辑绝对坐标 转换函数（读取 State 快照保证协程内始终拿到最新视口）
            fun screenToLogical(screenPos: Offset): Offset {
                val lx = ((screenPos.x - baseOffsetXState) / effectiveScaleState).coerceIn(0f, pageWidth)
                val ly = ((screenPos.y - baseOffsetYState) / effectiveScaleState).coerceIn(0f, pageHeight)
                return Offset(lx, ly)
            }

            // 8. 鼠标光标定点缩放函数 (严格不动点代数方程：鼠标指针下方图像像素绝对稳固不动)
            fun zoomAt(cursorPos: Offset, zoomChangeFactor: Float) {
                val oldZoom = zoom
                val newZoom = (oldZoom * zoomChangeFactor).coerceIn(0.1f, 15f)
                if (oldZoom == newZoom) return

                // 1. 获取光标当前所指向的底图逻辑绝对坐标 (Lx, Ly)
                val oldEffectiveScale = fitScale * oldZoom
                val oldBaseOffsetX = (containerWPx - pageWidth * oldEffectiveScale) / 2f + pan.x
                val oldBaseOffsetY = (containerHPx - pageHeight * oldEffectiveScale) / 2f + pan.y
                val logicalX = (cursorPos.x - oldBaseOffsetX) / oldEffectiveScale
                val logicalY = (cursorPos.y - oldBaseOffsetY) / oldEffectiveScale

                // 2. 计算在 newZoom 下，为了让 (Lx, Ly) 依然对齐在 cursorPos 下所需的新 pan
                val newEffectiveScale = fitScale * newZoom
                val newBaseCenteredOffsetX = (containerWPx - pageWidth * newEffectiveScale) / 2f
                val newBaseCenteredOffsetY = (containerHPx - pageHeight * newEffectiveScale) / 2f

                pan = Offset(
                    x = cursorPos.x - newBaseCenteredOffsetX - logicalX * newEffectiveScale,
                    y = cursorPos.y - newBaseCenteredOffsetY - logicalY * newEffectiveScale
                )
                zoom = newZoom
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(if (isSpacePressed || isRightButtonDragging) PointerIcon.Hand else currentCursorIcon)
                    // ★ 专属鼠标右键平移底图手势（与空格平移体验 100% 一致）
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.buttons.isSecondaryPressed) {
                                    isRightButtonDragging = true
                                    if (event.type == PointerEventType.Move) {
                                        val change = event.changes.firstOrNull()
                                        if (change != null) {
                                            val delta = change.position - change.previousPosition
                                            pan += delta
                                            change.consume()
                                        }
                                    }
                                } else {
                                    if (isRightButtonDragging) {
                                        isRightButtonDragging = false
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 监听鼠标滚轮：光标定点无级缩放
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.type == PointerEventType.Scroll) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val scrollDelta = change.scrollDelta.y
                                        if (scrollDelta != 0f) {
                                            val factor = if (scrollDelta < 0) 1.15f else 0.85f
                                            zoomAt(change.position, factor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 鼠标悬停光标检测（Unit key：避免视口变化重启协程，运行期读 State）
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                isAltPressed = event.keyboardModifiers.isAltPressed
                                isShiftPressed = event.keyboardModifiers.isShiftPressed

                                if (event.type == PointerEventType.Move) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        if (spaceState) {
                                            currentCursorIcon = PointerIcon.Hand
                                        } else {
                                            val logicalPos = screenToLogical(change.position)
                                            val handleSlopLogical = 18f / effectiveScaleState
                                            val rect = currentRectState

                                            if (rect != null) {
                                                val handle = hitTestHandle(rect, logicalPos.x, logicalPos.y, handleSlopLogical)
                                                currentCursorIcon = when (handle) {
                                                    RegionHandle.TOP_CENTER, RegionHandle.BOTTOM_CENTER -> ResizeVerticalIcon
                                                    RegionHandle.CENTER_LEFT, RegionHandle.CENTER_RIGHT -> ResizeHorizontalIcon
                                                    RegionHandle.TOP_LEFT, RegionHandle.BOTTOM_RIGHT -> ResizeHorizontalIcon
                                                    RegionHandle.TOP_RIGHT, RegionHandle.BOTTOM_LEFT -> ResizeVerticalIcon
                                                    null -> {
                                                        val l = min(rect.left, rect.right)
                                                        val r = max(rect.left, rect.right)
                                                        val t = min(rect.top, rect.bottom)
                                                        val b = max(rect.top, rect.bottom)
                                                        val isInside = logicalPos.x in l..r && logicalPos.y in t..b
                                                        if (isInside) PointerIcon.Hand else PointerIcon.Default
                                                    }
                                                }
                                            } else {
                                                currentCursorIcon = PointerIcon.Default
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 拖动手势：100% 绝对对称数学跟踪（Unit key：防止空格平移修改 pan 时 key 变化中断手势）
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (spaceState || isRightButtonDragging) {
                                    activeHandle = null
                                    isMovingRect = false
                                    isCreatingNew = false
                                    createStartPos = null
                                    return@detectDragGestures
                                }

                                isInteracting = true
                                val logicalPos = screenToLogical(offset)
                                val handleSlopLogical = 18f / effectiveScaleState
                                val rect = currentRectState

                                if (rect != null) {
                                    val hitHandle = hitTestHandle(rect, logicalPos.x, logicalPos.y, handleSlopLogical)
                                    if (hitHandle != null) {
                                        activeHandle = hitHandle
                                        isMovingRect = false
                                        isCreatingNew = false
                                        createStartPos = null
                                    } else {
                                        val l = min(rect.left, rect.right)
                                        val r = max(rect.left, rect.right)
                                        val t = min(rect.top, rect.bottom)
                                        val b = max(rect.top, rect.bottom)
                                        isMovingRect = logicalPos.x in l..r && logicalPos.y in t..b
                                        if (!isMovingRect) {
                                            // 点击在选区外部：拉出新选区
                                            isCreatingNew = true
                                            createStartPos = logicalPos
                                            activeHandle = null
                                            selectedRect = SerialRect(logicalPos.x, logicalPos.y, logicalPos.x, logicalPos.y)
                                            onSelectionChange(selectedRect)
                                        }
                                    }
                                } else {
                                    // 无选区时：在点击起点新建选区
                                    isCreatingNew = true
                                    createStartPos = logicalPos
                                    activeHandle = null
                                    isMovingRect = false
                                    selectedRect = SerialRect(logicalPos.x, logicalPos.y, logicalPos.x, logicalPos.y)
                                    onSelectionChange(selectedRect)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (spaceState) {
                                    // 空格抓手平移画布（无限自由平移）
                                    pan += dragAmount
                                } else {
                                    // 逻辑位移量 = 屏幕拖拽像素 ÷ effectiveScaleState (100% 绝对像素同步跟随)
                                    val deltaLogicalX = dragAmount.x / effectiveScaleState
                                    val deltaLogicalY = dragAmount.y / effectiveScaleState

                                    if (activeHandle != null) {
                                        // ★ 工业级光标绝对锚定铁律 (Direct Cursor Pinning)：手柄直接锚定在当前鼠标反投影的逻辑绝对坐标上，彻底杜绝增量累加导致的严重失步滞后！
                                        val cur = currentRectState ?: return@detectDragGestures
                                        val curLogical = screenToLogical(change.position)
                                        val resized = resizeRegionDirectPin(
                                            current = cur,
                                            handle = activeHandle!!,
                                            cursorPos = curLogical,
                                            isAltCenterResize = altState,
                                            maxW = pageWidth,
                                            maxH = pageHeight,
                                            minSize = minSelectionSize
                                        )
                                        selectedRect = resized
                                        onSelectionChange(resized)
                                    } else if (isMovingRect) {
                                        // 选区整体移动
                                        val cur = currentRectState ?: return@detectDragGestures
                                        val l = min(cur.left, cur.right)
                                        val r = max(cur.left, cur.right)
                                        val t = min(cur.top, cur.bottom)
                                        val b = max(cur.top, cur.bottom)
                                        val w = r - l
                                        val h = b - t
                                        val newLeft = (l + deltaLogicalX).coerceIn(0f, (pageWidth - w).coerceAtLeast(0f))
                                        val newTop = (t + deltaLogicalY).coerceIn(0f, (pageHeight - h).coerceAtLeast(0f))
                                        val moved = SerialRect(newLeft, newTop, newLeft + w, newTop + h)
                                        selectedRect = moved
                                        onSelectionChange(moved)
                                    } else if (isCreatingNew && createStartPos != null) {
                                        // 依据起始锚点双向拉出新矩形
                                        val curLogical = screenToLogical(change.position)
                                        val start = createStartPos!!
                                        val newRect = SerialRect(
                                            left = min(start.x, curLogical.x),
                                            top = min(start.y, curLogical.y),
                                            right = max(start.x, curLogical.x),
                                            bottom = max(start.y, curLogical.y)
                                        )
                                        selectedRect = newRect
                                        onSelectionChange(newRect)
                                    }
                                }
                            },
                            onDragEnd = {
                                activeHandle = null
                                isMovingRect = false
                                isCreatingNew = false
                                createStartPos = null
                                isInteracting = false
                                selectedRect?.let { r ->
                                    val w = abs(r.width)
                                    val h = abs(r.height)
                                    if (w >= 4f && h >= 4f) {
                                        val normalized = SerialRect(
                                            left = min(r.left, r.right),
                                            top = min(r.top, r.bottom),
                                            right = max(r.left, r.right),
                                            bottom = max(r.top, r.bottom)
                                        )
                                        selectedRect = normalized
                                        onSelectionConfirmed(normalized)
                                    } else {
                                        selectedRect = null
                                        onSelectionChange(null)
                                        onSelectionConfirmed(null)
                                    }
                                } ?: run {
                                    onSelectionChange(null)
                                    onSelectionConfirmed(null)
                                }
                            },
                            onDragCancel = {
                                activeHandle = null
                                isMovingRect = false
                                isCreatingNew = false
                                createStartPos = null
                                isInteracting = false
                            }
                        )
                    }
            ) {
                // 9. 渲染底图图像（物理像素绝对落位）
                if (loadedBitmap != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val imgW = loadedBitmap.width.toFloat()
                        val imgH = loadedBitmap.height.toFloat()
                        val targetW = pageWidth * effectiveScale
                        val targetH = pageHeight * effectiveScale

                        drawImage(
                            image = loadedBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(imgW.toInt(), imgH.toInt()),
                            dstOffset = IntOffset(baseOffsetX.toInt(), baseOffsetY.toInt()),
                            dstSize = IntSize(targetW.toInt(), targetH.toInt())
                        )
                    }
                }

                // 10. 渲染选区、暗角蒙版、三分辅助线与 8 手柄
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rect = currentRectState
                    if (rect != null) {
                        val normL = min(rect.left, rect.right)
                        val normR = max(rect.left, rect.right)
                        val normT = min(rect.top, rect.bottom)
                        val normB = max(rect.top, rect.bottom)

                        val leftPx = baseOffsetX + normL * effectiveScale
                        val topPx = baseOffsetY + normT * effectiveScale
                        val rightPx = baseOffsetX + normR * effectiveScale
                        val bottomPx = baseOffsetY + normB * effectiveScale
                        val rectW = rightPx - leftPx
                        val rectH = bottomPx - topPx

                        // 4 块暗角蒙版镂空
                        drawRect(color = dimColor.copy(alpha = 0.5f), topLeft = Offset.Zero, size = Size(size.width, topPx))
                        drawRect(color = dimColor.copy(alpha = 0.5f), topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
                        drawRect(color = dimColor.copy(alpha = 0.5f), topLeft = Offset(0f, topPx), size = Size(leftPx, rectH))
                        drawRect(color = dimColor.copy(alpha = 0.5f), topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, rectH))

                        // 三分法辅助线
                        if (showThirdsGrid && rectW > 40f && rectH > 40f) {
                            val thirdW = rectW / 3f
                            val thirdH = rectH / 3f
                            val gridColor = Color.White.copy(alpha = 0.35f)
                            drawLine(color = gridColor, start = Offset(leftPx + thirdW, topPx), end = Offset(leftPx + thirdW, bottomPx), strokeWidth = 1f)
                            drawLine(color = gridColor, start = Offset(leftPx + thirdW * 2, topPx), end = Offset(leftPx + thirdW * 2, bottomPx), strokeWidth = 1f)
                            drawLine(color = gridColor, start = Offset(leftPx, topPx + thirdH), end = Offset(rightPx, topPx + thirdH), strokeWidth = 1f)
                            drawLine(color = gridColor, start = Offset(leftPx, topPx + thirdH * 2), end = Offset(rightPx, topPx + thirdH * 2), strokeWidth = 1f)
                        }

                        // 选区边框
                        drawRect(
                            color = selectionBorderColor,
                            topLeft = Offset(leftPx, topPx),
                            size = Size(rectW, rectH),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 8 独立拉伸控制手柄
                        val handleRadius = 5.5.dp.toPx()
                        val cx = (leftPx + rightPx) / 2f
                        val cy = (topPx + bottomPx) / 2f

                        val points = listOf(
                            Offset(leftPx, topPx),
                            Offset(cx, topPx),
                            Offset(rightPx, topPx),
                            Offset(leftPx, cy),
                            Offset(rightPx, cy),
                            Offset(leftPx, bottomPx),
                            Offset(cx, bottomPx),
                            Offset(rightPx, bottomPx)
                        )

                        points.forEach { pt ->
                            drawCircle(color = Color.White, radius = handleRadius, center = pt)
                            drawCircle(
                                color = selectionBorderColor,
                                radius = handleRadius,
                                center = pt,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    } else {
                        drawRect(color = dimColor.copy(alpha = 0.35f), topLeft = Offset.Zero, size = size)
                    }
                }
            }

            // 11. 悬浮尺寸 HUD 胶囊标签
            val rect = selectedRect
            if (showDimensionBadge && rect != null) {
                val rw = abs(rect.width).toInt()
                val rh = abs(rect.height).toInt()
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = AppShapes.small,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选区尺寸: ${rw}px × ${rh}px  位置: (${rect.left.toInt()}, ${rect.top.toInt()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // 12. 视口左下角：浮动缩放控制岛 (Floating Zoom HUD)
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = AppShapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 缩小按钮（以视口中心定点缩小）
                    IconButton(
                        onClick = { zoomAt(Offset(containerWPx / 2f, containerHPx / 2f), 0.85f) },
                        modifier = Modifier.size(24.dp).tip("缩小视角 (Ctrl + -)")
                    ) {
                        Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }

                    // 缩放百分比显示（点击一键复位 100%）
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = AppShapes.extraSmall,
                        modifier = Modifier.clickable {
                            zoom = 1.0f
                            pan = Offset.Zero
                        }.tip("点击快速恢复 100% 居中 (Ctrl + 0)")
                    ) {
                        Text(
                            text = "${(zoom * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 放大按钮（以视口中心定点放大）
                    IconButton(
                        onClick = { zoomAt(Offset(containerWPx / 2f, containerHPx / 2f), 1.15f) },
                        modifier = Modifier.size(24.dp).tip("放大视角 (Ctrl + +)")
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }

                    VerticalDivider(modifier = Modifier.height(14.dp), color = Color.White.copy(alpha = 0.3f))

                    // 一键复位居中视角
                    IconButton(
                        onClick = {
                            zoom = 1.0f
                            pan = Offset.Zero
                        },
                        modifier = Modifier.size(24.dp).tip("一键复位居中视角 (Ctrl + 0)")
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // 13. 右上角悬浮快捷操作栏：【整图全选】、【一键清除】、【重置初始】
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = AppShapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 整图全选按钮
                    FilledTonalButton(
                        onClick = {
                            val fullRect = SerialRect(0f, 0f, pageWidth, pageHeight)
                            selectedRect = fullRect
                            onSelectionChange(fullRect)
                            onSelectionConfirmed(fullRect)
                        },
                        modifier = Modifier.height(28.dp).tip("将选区扩展覆盖至整张底图"),
                        shape = AppShapes.extraSmall,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("整图全选", style = MaterialTheme.typography.labelSmall)
                    }

                    // 清除选区按钮
                    TextButton(
                        onClick = {
                            selectedRect = null
                            onSelectionChange(null)
                            onSelectionConfirmed(null)
                        },
                        modifier = Modifier.height(28.dp).tip("清除当前选区"),
                        shape = AppShapes.extraSmall,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清除选区", style = MaterialTheme.typography.labelSmall)
                    }

                    // 重置初始按钮
                    if (originalInitialBounds != null) {
                        TextButton(
                            onClick = {
                                selectedRect = originalInitialBounds
                                onSelectionChange(originalInitialBounds)
                                onSelectionConfirmed(originalInitialBounds)
                            },
                            modifier = Modifier.height(28.dp).tip("恢复为弹窗打开时的初始选区"),
                            shape = AppShapes.extraSmall,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Restore, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重置", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8 方向手柄点击命中测试算法
 */
fun hitTestHandle(
    rect: SerialRect,
    logicalX: Float,
    logicalY: Float,
    touchSlopLogical: Float
): RegionHandle? {
    val l = min(rect.left, rect.right)
    val r = max(rect.left, rect.right)
    val t = min(rect.top, rect.bottom)
    val b = max(rect.top, rect.bottom)

    fun isNear(targetX: Float, targetY: Float): Boolean {
        val dx = logicalX - targetX
        val dy = logicalY - targetY
        return (dx * dx + dy * dy) <= (touchSlopLogical * touchSlopLogical)
    }

    val cx = (l + r) / 2f
    val cy = (t + b) / 2f

    return when {
        isNear(l, t) -> RegionHandle.TOP_LEFT
        isNear(r, t) -> RegionHandle.TOP_RIGHT
        isNear(l, b) -> RegionHandle.BOTTOM_LEFT
        isNear(r, b) -> RegionHandle.BOTTOM_RIGHT
        isNear(cx, t) -> RegionHandle.TOP_CENTER
        isNear(cx, b) -> RegionHandle.BOTTOM_CENTER
        isNear(l, cy) -> RegionHandle.CENTER_LEFT
        isNear(r, cy) -> RegionHandle.CENTER_RIGHT
        else -> null
    }
}

/**
 * 选区拉伸尺寸计算：光标绝对坐标直接锚定法 (Direct Cursor Pinning)
 *
 * 彻底废除增量累加，手柄物理位置直接绑定鼠标当前反投影的逻辑点，100% 绝对实时对齐，误差永远为 0。
 */
fun resizeRegionDirectPin(
    current: SerialRect,
    handle: RegionHandle,
    cursorPos: Offset,
    isAltCenterResize: Boolean,
    maxW: Float,
    maxH: Float,
    minSize: Float = 8f
): SerialRect {
    var l = min(current.left, current.right)
    var r = max(current.left, current.right)
    var t = min(current.top, current.bottom)
    var b = max(current.top, current.bottom)

    val cx = (l + r) / 2f
    val cy = (t + b) / 2f

    when (handle) {
        RegionHandle.TOP_LEFT -> {
            l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                val dx = cx - l
                val dy = cy - t
                r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.TOP_CENTER -> {
            t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                val dy = cy - t
                b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.TOP_RIGHT -> {
            r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                val dx = r - cx
                val dy = cy - t
                l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.CENTER_LEFT -> {
            l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                val dx = cx - l
                r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            }
        }
        RegionHandle.CENTER_RIGHT -> {
            r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            if (isAltCenterResize) {
                val dx = r - cx
                l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_LEFT -> {
            l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                val dx = cx - l
                val dy = b - cy
                r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_CENTER -> {
            b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                val dy = b - cy
                t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_RIGHT -> {
            r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                val dx = r - cx
                val dy = b - cy
                l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
    }
    return SerialRect(l, t, r, b)
}

/**
 * 选区拉伸尺寸计算核心方法（绝对安全防崩溃版）
 */
fun resizeRegion(
    current: SerialRect,
    handle: RegionHandle,
    deltaX: Float,
    deltaY: Float,
    isAltCenterResize: Boolean,
    maxW: Float,
    maxH: Float,
    minSize: Float = 8f
): SerialRect {
    var l = min(current.left, current.right)
    var r = max(current.left, current.right)
    var t = min(current.top, current.bottom)
    var b = max(current.top, current.bottom)

    when (handle) {
        RegionHandle.TOP_LEFT -> {
            l = (l + deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            t = (t + deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                r = (r - deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                b = (b - deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.TOP_CENTER -> {
            t = (t + deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                b = (b - deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.TOP_RIGHT -> {
            r = (r + deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            t = (t + deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                l = (l - deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                b = (b - deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            }
        }
        RegionHandle.CENTER_LEFT -> {
            l = (l + deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            if (isAltCenterResize) {
                r = (r - deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            }
        }
        RegionHandle.CENTER_RIGHT -> {
            r = (r + deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            if (isAltCenterResize) {
                l = (l - deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_LEFT -> {
            l = (l + deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
            b = (b + deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                r = (r - deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                t = (t - deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_CENTER -> {
            b = (b + deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                t = (t - deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
        RegionHandle.BOTTOM_RIGHT -> {
            r = (r + deltaX).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
            b = (b + deltaY).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
            if (isAltCenterResize) {
                l = (l - deltaX).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                t = (t - deltaY).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
            }
        }
    }

    return SerialRect(l, t, r, b)
}

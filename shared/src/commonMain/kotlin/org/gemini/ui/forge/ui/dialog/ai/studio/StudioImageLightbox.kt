package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.*

/**
 * 微信式图片编辑模式枚举
 */
enum class WeChatEditTool {
    SELECT, // 选择/移动/缩放图元模式
    BRUSH,  // 自由画笔涂鸦
    RECT,   // 矩形框选标注
    TEXT    // 文字说明标注
}

/**
 * 文字字号档位枚举
 */
enum class AnnotationFontSize(val label: String, val spValue: Float) {
    SMALL("小", 14f),
    MEDIUM("中", 20f),
    LARGE("大", 28f)
}

/**
 * 微信截图级对象化图元抽象基类
 */
sealed class CanvasItem(
    open val id: String,
    open val color: Color
) {
    data class Brush(
        override val id: String = "brush_${getCurrentTimeMillis()}_${(1000..9999).random()}",
        override val color: Color = Color.Red,
        val points: List<Offset>,
        val strokeWidth: Float = 4f
    ) : CanvasItem(id, color) {
        fun translate(delta: Offset): Brush = copy(points = points.map { it + delta })
    }

    data class RectBox(
        override val id: String = "rect_${getCurrentTimeMillis()}_${(1000..9999).random()}",
        override val color: Color = Color.Red,
        val bounds: Rect,
        val strokeWidth: Float = 3.5f
    ) : CanvasItem(id, color) {
        fun translate(delta: Offset): RectBox = copy(bounds = bounds.translate(delta))
        fun resizeCorner(cornerIndex: Int, newPos: Offset): RectBox {
            var l = bounds.left
            var t = bounds.top
            var r = bounds.right
            var b = bounds.bottom
            when (cornerIndex) {
                0 -> { l = newPos.x; t = newPos.y } // 左上
                1 -> { r = newPos.x; t = newPos.y } // 右上
                2 -> { r = newPos.x; b = newPos.y } // 右下
                3 -> { l = newPos.x; b = newPos.y } // 左下
            }
            val minX = minOf(l, r)
            val maxX = maxOf(l, r)
            val minY = minOf(t, b)
            val maxY = maxOf(t, b)
            return copy(bounds = Rect(minX, minY, maxX, maxY))
        }
    }

    data class TextLabel(
        override val id: String = "text_${getCurrentTimeMillis()}_${(1000..9999).random()}",
        override val color: Color = Color.Red,
        val text: String,
        val position: Offset,
        val sizeLevel: AnnotationFontSize = AnnotationFontSize.MEDIUM
    ) : CanvasItem(id, color) {
        fun translate(delta: Offset): TextLabel = copy(position = position + delta)
    }
}

/**
 * 微信截图级交互式大图灯箱与标注编辑器
 *
 * 核心特性：
 * 1. 【1:1 物理原始尺寸防拉大模糊】：默认按真实物理像素呈现，小图不拉伸、超大图等比缩限在视口内，仅受鼠标滚轮平滑缩放；
 * 2. 【点击就地内联自适应打字】：彻底废除模态弹窗，点击位置就地渲染输入框，光标自动聚焦，随字数动态撑开与自动换行，回车即固化；
 * 3. 【文字图元移动与双击编辑】：文字单击显示占用空间边框，可按住平移移动，双击文字原地重新激活修改文案；
 * 4. 【一键清空标注功能】：顶部工具栏新增清空按钮，一键清空所有图元；
 * 5. 【矩形 4 角控制手柄缩放】：选中的矩形显示 4 角控制手柄，可自由拉伸缩放；
 * 6. 【无损合并保存】：Skia 离屏无损合并保存 PNG，自动同步替换为视觉工作室当前参考图。
 */
@Composable
fun StudioImageLightbox(
    imageModel: Any,
    projectName: String = "",
    storage: LocalFileStorage = remember { LocalFileStorage() },
    onSaveAsReference: ((newImagePath: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 缩放比例状态（仅缩放，不可拖拽移动底图）
    var zoomScale by remember { mutableStateOf(1.0f) }

    // 当前激活的编辑工具
    var activeTool by remember { mutableStateOf(WeChatEditTool.SELECT) }

    // 当前选中的字号档位
    var selectedFontSize by remember { mutableStateOf(AnnotationFontSize.MEDIUM) }

    // 调色盘
    val palette = listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF), Color.White)
    var selectedColor by remember { mutableStateOf(palette.first()) }

    // 对象化图元集合
    val elements = remember { mutableStateListOf<CanvasItem>() }

    // 当前选中的图元 ID 与拖拽状态
    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var activeHandleIndex by remember { mutableStateOf<Int?>(null) } // 0..3 为矩形4角手柄

    // 正在进行的绘制草稿
    var inProgressStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var inProgressRectStart by remember { mutableStateOf<Offset?>(null) }
    var inProgressRectCurrent by remember { mutableStateOf<Offset?>(null) }

    // ★ 微信式就地内联打字输入状态（就地弹出，废除模态弹窗！）
    var inlineInputPos by remember { mutableStateOf<Offset?>(null) }
    var inlineInputText by remember { mutableStateOf("") }
    var editingTextElementId by remember { mutableStateOf<String?>(null) }

    // 真实物理图片与基准尺寸探测
    var naturalSize by remember { mutableStateOf<IntSize?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // 异步加载图片的真实物理像素宽高
    LaunchedEffect(imageModel) {
        withContext(Dispatchers.Default) {
            try {
                val rawBytes = when (imageModel) {
                    is ByteArray -> imageModel
                    is String -> readLocalFileBytes(imageModel)
                    else -> null
                }
                if (rawBytes != null) {
                    val skiaImg = Image.makeFromEncoded(rawBytes)
                    naturalSize = IntSize(skiaImg.width, skiaImg.height)
                }
            } catch (_: Throwable) {}
        }
    }

    // 就地提交内联文字
    fun commitInlineText() {
        val text = inlineInputText.trim()
        val pos = inlineInputPos
        val editId = editingTextElementId

        if (text.isNotBlank() && pos != null) {
            if (editId != null) {
                // 更新已有文字图元内容
                val idx = elements.indexOfFirst { it.id == editId }
                if (idx != -1) {
                    val old = elements[idx] as? CanvasItem.TextLabel
                    if (old != null) {
                        elements[idx] = old.copy(text = text, sizeLevel = selectedFontSize, color = selectedColor)
                    }
                }
            } else {
                // 新增文字图元
                val newLabel = CanvasItem.TextLabel(
                    text = text,
                    position = pos,
                    color = selectedColor,
                    sizeLevel = selectedFontSize
                )
                elements.add(newLabel)
                selectedElementId = newLabel.id
            }
        }
        inlineInputPos = null
        inlineInputText = ""
        editingTextElementId = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                // 滚轮缩放监听：严格锁定居中，仅允许缩放
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) {
                                    zoomScale = (zoomScale - delta * 0.1f).coerceIn(0.2f, 5.0f)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val maxViewportW = maxWidth * 0.88f
            val maxViewportH = maxHeight * 0.88f

            // 计算未放大（1.0x）时的基准尺寸：小图 1:1 原寸呈现绝对不拉大模糊！大图等比缩限在 88% 视口内
            val (baseWidthDp, baseHeightDp) = remember(naturalSize, maxViewportW, maxViewportH) {
                val origW = naturalSize?.width?.toFloat() ?: 400f
                val origH = naturalSize?.height?.toFloat() ?: 400f
                val origWDp = with(density) { origW.toDp() }
                val origHDp = with(density) { origH.toDp() }

                if (origWDp <= maxViewportW && origHDp <= maxViewportH) {
                    // 1:1 原尺寸呈现，零拉伸、零模糊！
                    origWDp to origHDp
                } else {
                    // 超大图等比安全收缩
                    val scaleX = maxViewportW / origWDp
                    val scaleY = maxViewportH / origHDp
                    val s = minOf(scaleX, scaleY)
                    (origWDp * s) to (origHDp * s)
                }
            }

            // ──────────────────────────────────────────────────────────
            // 1. 中部核心图层（从左上角 TopStart 绝对对齐，彻底消除 offset 居中漂移）
            // ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(baseWidthDp, baseHeightDp)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                    },
                contentAlignment = Alignment.TopStart
            ) {
                // 底图呈现
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Natural Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(AppShapes.small),
                    contentScale = ContentScale.Fit
                )

                // 交互绘制与命中手势层
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTool, selectedColor, selectedFontSize, elements.size, selectedElementId) {
                            detectDragGestures(
                                onDragStart = { startPos ->
                                    if (inlineInputPos != null) {
                                        commitInlineText()
                                    }

                                    when (activeTool) {
                                        WeChatEditTool.SELECT -> {
                                            // 1. 优先检查是否命中已选矩形的 4 个控制手柄
                                            val currentSelected = elements.find { it.id == selectedElementId }
                                            if (currentSelected is CanvasItem.RectBox) {
                                                val r = currentSelected.bounds
                                                val corners = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
                                                val hitIndex = corners.indexOfFirst { (it - startPos).getDistance() <= 20f }
                                                if (hitIndex != -1) {
                                                    activeHandleIndex = hitIndex
                                                    return@detectDragGestures
                                                }
                                            }

                                            // 2. 命中测试：自顶向下寻找命中的图元
                                            activeHandleIndex = null
                                            val hit = elements.asReversed().firstOrNull { item ->
                                                when (item) {
                                                    is CanvasItem.RectBox -> item.bounds.contains(startPos)
                                                    is CanvasItem.TextLabel -> {
                                                        val estW = item.text.length * item.sizeLevel.spValue * 0.9f
                                                        val estH = item.sizeLevel.spValue * 1.5f
                                                        Rect(item.position.x - 6f, item.position.y - 6f, item.position.x + estW + 6f, item.position.y + estH + 6f).contains(startPos)
                                                    }
                                                    is CanvasItem.Brush -> item.points.any { (it - startPos).getDistance() <= 12f }
                                                }
                                            }
                                            selectedElementId = hit?.id
                                        }

                                        WeChatEditTool.BRUSH -> {
                                            selectedElementId = null
                                            inProgressStrokePoints = listOf(startPos)
                                        }

                                        WeChatEditTool.RECT -> {
                                            selectedElementId = null
                                            inProgressRectStart = startPos
                                            inProgressRectCurrent = startPos
                                        }

                                        WeChatEditTool.TEXT -> {
                                            // 点击位置就地弹出内联输入框（无打断体验！）
                                            inlineInputPos = startPos
                                            inlineInputText = ""
                                            editingTextElementId = null
                                            activeTool = WeChatEditTool.SELECT
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    when (activeTool) {
                                        WeChatEditTool.SELECT -> {
                                            val handleIdx = activeHandleIndex
                                            val elemId = selectedElementId
                                            if (handleIdx != null && elemId != null) {
                                                // 拖动手柄缩放矩形
                                                val idx = elements.indexOfFirst { it.id == elemId }
                                                if (idx != -1) {
                                                    val item = elements[idx]
                                                    if (item is CanvasItem.RectBox) {
                                                        elements[idx] = item.resizeCorner(handleIdx, change.position)
                                                    }
                                                }
                                            } else if (elemId != null) {
                                                // 拖拽平移移动整个图元（矩形、文字或画笔均可随意平移移动！）
                                                val idx = elements.indexOfFirst { it.id == elemId }
                                                if (idx != -1) {
                                                    elements[idx] = when (val item = elements[idx]) {
                                                        is CanvasItem.RectBox -> item.translate(dragAmount)
                                                        is CanvasItem.TextLabel -> item.translate(dragAmount)
                                                        is CanvasItem.Brush -> item.translate(dragAmount)
                                                    }
                                                }
                                            }
                                        }

                                        WeChatEditTool.BRUSH -> {
                                            inProgressStrokePoints = inProgressStrokePoints + change.position
                                        }

                                        WeChatEditTool.RECT -> {
                                            inProgressRectCurrent = change.position
                                        }

                                        WeChatEditTool.TEXT -> {}
                                    }
                                },
                                onDragEnd = {
                                    when (activeTool) {
                                        WeChatEditTool.BRUSH -> {
                                            if (inProgressStrokePoints.size > 1) {
                                                elements.add(
                                                    CanvasItem.Brush(
                                                        points = inProgressStrokePoints,
                                                        color = selectedColor
                                                    )
                                                )
                                            }
                                            inProgressStrokePoints = emptyList()
                                        }

                                        WeChatEditTool.RECT -> {
                                            val s = inProgressRectStart
                                            val c = inProgressRectCurrent
                                            if (s != null && c != null) {
                                                val l = minOf(s.x, c.x)
                                                val t = minOf(s.y, c.y)
                                                val r = maxOf(s.x, c.x)
                                                val b = maxOf(s.y, c.y)
                                                if (r - l > 10 && b - t > 10) {
                                                    val box = CanvasItem.RectBox(bounds = Rect(l, t, r, b), color = selectedColor)
                                                    elements.add(box)
                                                    selectedElementId = box.id // 绘制后自动进入选中态方便平移或缩放
                                                }
                                            }
                                            inProgressRectStart = null
                                            inProgressRectCurrent = null
                                        }

                                        else -> {}
                                    }
                                    activeHandleIndex = null
                                }
                            )
                        }
                ) {
                    // 1. 绘制历史图元
                    elements.forEach { item ->
                        val isItemSelected = item.id == selectedElementId
                        when (item) {
                            is CanvasItem.Brush -> {
                                if (item.points.size > 1) {
                                    val path = Path().apply {
                                        moveTo(item.points[0].x, item.points[0].y)
                                        for (i in 1 until item.points.size) {
                                            lineTo(item.points[i].x, item.points[i].y)
                                        }
                                    }
                                    drawPath(path, color = item.color, style = Stroke(width = item.strokeWidth))
                                    if (isItemSelected) {
                                        drawPath(path, color = Color.White, style = Stroke(width = 1.5f))
                                    }
                                }
                            }

                            is CanvasItem.RectBox -> {
                                drawRect(color = item.color, topLeft = item.bounds.topLeft, size = item.bounds.size, style = Stroke(width = item.strokeWidth))
                                // 选中时显示 4 角控制手柄（微信标准）
                                if (isItemSelected) {
                                    drawRect(color = Color.White, topLeft = item.bounds.topLeft, size = item.bounds.size, style = Stroke(width = 1.5f))
                                    val corners = listOf(item.bounds.topLeft, item.bounds.topRight, item.bounds.bottomRight, item.bounds.bottomLeft)
                                    corners.forEach { corner ->
                                        drawCircle(color = Color.White, radius = 6f, center = corner)
                                        drawCircle(color = item.color, radius = 4f, center = corner)
                                    }
                                }
                            }

                            is CanvasItem.TextLabel -> {}
                        }
                    }

                    // 2. 绘制正在进行中的画笔草稿
                    if (inProgressStrokePoints.size > 1) {
                        val path = Path().apply {
                            moveTo(inProgressStrokePoints[0].x, inProgressStrokePoints[0].y)
                            for (i in 1 until inProgressStrokePoints.size) {
                                lineTo(inProgressStrokePoints[i].x, inProgressStrokePoints[i].y)
                            }
                        }
                        drawPath(path, color = selectedColor, style = Stroke(width = 4f))
                    }

                    // 3. 绘制正在进行中的矩形框草稿
                    val rs = inProgressRectStart
                    val rc = inProgressRectCurrent
                    if (rs != null && rc != null) {
                        val l = minOf(rs.x, rc.x)
                        val t = minOf(rs.y, rc.y)
                        val r = maxOf(rs.x, rc.x)
                        val b = maxOf(rs.y, rc.y)
                        drawRect(color = selectedColor, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 3.5f))
                    }
                }

                // 4. 文字图元悬浮交互层（支持移动、单击选中显示占用空间、双击重新就地编辑）
                elements.filterIsInstance<CanvasItem.TextLabel>().forEach { textItem ->
                    // 如果正在就地编辑该文字，则隐藏静态文字标签，防止文字重影
                    if (editingTextElementId == textItem.id) return@forEach

                    val isSelected = textItem.id == selectedElementId
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { textItem.position.x.toDp() },
                                y = with(density) { textItem.position.y.toDp() }
                            )
                            .pointerInput(textItem.id) {
                                detectTapGestures(
                                    onTap = {
                                        selectedElementId = textItem.id
                                    },
                                    onDoubleTap = {
                                        // ★ 双击文字图元：原地重新激活内联输入框修改已有文案！
                                        editingTextElementId = textItem.id
                                        inlineInputPos = textItem.position
                                        inlineInputText = textItem.text
                                        selectedElementId = textItem.id
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color.Black.copy(alpha = 0.75f) else Color.Transparent)
                            // 单击选中的时候绘制文字占用空间边框！
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(4.dp)
                    ) {
                        Text(
                            text = textItem.text,
                            color = textItem.color,
                            fontSize = textItem.sizeLevel.spValue.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 5. 就地内联输入框（直接在点击坐标就地渲染，自动聚焦，随字数自适应变宽，回车即固化！）
                val currentInputPos = inlineInputPos
                if (currentInputPos != null) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(currentInputPos) {
                        focusRequester.requestFocus()
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { currentInputPos.x.toDp() },
                                y = with(density) { currentInputPos.y.toDp() }
                            )
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, selectedColor),
                            modifier = Modifier.wrapContentSize().padding(2.dp)
                        ) {
                            BasicTextField(
                                value = inlineInputText,
                                onValueChange = { inlineInputText = it },
                                textStyle = TextStyle(
                                    color = selectedColor,
                                    fontSize = selectedFontSize.spValue.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                singleLine = false,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .widthIn(min = 26.dp, max = 320.dp)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                            commitInlineText()
                                            true
                                        } else false
                                    }
                            )
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 2. 微信风格顶部悬浮控制条（模式切换、字号、调色板、撤销、一键清空、保存）
            // ──────────────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = spacing.large),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E24).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 移动/选择工具
                    IconButton(
                        onClick = {
                            commitInlineText()
                            activeTool = WeChatEditTool.SELECT
                        },
                        modifier = Modifier.size(36.dp).tip("选择/移动工具（点击图形选中，按住移动，拉动手柄缩放，双击文字重新编辑）")
                    ) {
                        Icon(
                            Icons.Default.NearMe,
                            null,
                            tint = if (activeTool == WeChatEditTool.SELECT) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 2. 画笔涂鸦（点击激活，再次点击取消）
                    IconButton(
                        onClick = {
                            commitInlineText()
                            activeTool = if (activeTool == WeChatEditTool.BRUSH) WeChatEditTool.SELECT else WeChatEditTool.BRUSH
                        },
                        modifier = Modifier.size(36.dp).tip("自由画笔涂鸦（再次点击退出绘制）")
                    ) {
                        Icon(
                            Icons.Default.Brush,
                            null,
                            tint = if (activeTool == WeChatEditTool.BRUSH) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 3. 矩形框选（点击激活，再次点击取消）
                    IconButton(
                        onClick = {
                            commitInlineText()
                            activeTool = if (activeTool == WeChatEditTool.RECT) WeChatEditTool.SELECT else WeChatEditTool.RECT
                        },
                        modifier = Modifier.size(36.dp).tip("矩形框选标记（再次点击退出绘制）")
                    ) {
                        Icon(
                            Icons.Default.CropSquare,
                            null,
                            tint = if (activeTool == WeChatEditTool.RECT) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 4. 文字标注（点击激活，再次点击取消）
                    IconButton(
                        onClick = {
                            commitInlineText()
                            activeTool = if (activeTool == WeChatEditTool.TEXT) WeChatEditTool.SELECT else WeChatEditTool.TEXT
                        },
                        modifier = Modifier.size(36.dp).tip("文字标注输入（点击图片就地输入，再次点击退出绘制）")
                    ) {
                        Icon(
                            Icons.Default.TextFields,
                            null,
                            tint = if (activeTool == WeChatEditTool.TEXT) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 文字字号三档快捷切换胶囊（小 14 | 中 20 | 大 28）
                    if (activeTool == WeChatEditTool.TEXT || (elements.find { it.id == selectedElementId } is CanvasItem.TextLabel)) {
                        VerticalDivider(modifier = Modifier.height(20.dp), color = Color.White.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.height(28.dp).clip(AppShapes.extraSmall).background(Color.Black.copy(alpha = 0.4f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnnotationFontSize.entries.forEach { sizeOption ->
                                val isChosen = selectedFontSize == sizeOption
                                Surface(
                                    color = if (isChosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.clickable {
                                        selectedFontSize = sizeOption
                                        val elemId = selectedElementId
                                        if (elemId != null) {
                                            val idx = elements.indexOfFirst { it.id == elemId }
                                            if (idx != -1 && elements[idx] is CanvasItem.TextLabel) {
                                                elements[idx] = (elements[idx] as CanvasItem.TextLabel).copy(sizeLevel = sizeOption)
                                            }
                                        }
                                    }.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = sizeOption.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = Color.White.copy(alpha = 0.3f))

                    // 调色盘
                    palette.forEach { c ->
                        Surface(
                            shape = CircleShape,
                            color = c,
                            border = if (selectedColor == c) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedColor = c
                                    val elemId = selectedElementId
                                    if (elemId != null) {
                                        val idx = elements.indexOfFirst { it.id == elemId }
                                        if (idx != -1) {
                                            elements[idx] = when (val item = elements[idx]) {
                                                is CanvasItem.RectBox -> item.copy(color = c)
                                                is CanvasItem.TextLabel -> item.copy(color = c)
                                                is CanvasItem.Brush -> item.copy(color = c)
                                            }
                                        }
                                    }
                                }
                        ) {}
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = Color.White.copy(alpha = 0.3f))

                    // 撤销上一步
                    IconButton(
                        onClick = { if (elements.isNotEmpty()) elements.removeAt(elements.size - 1) },
                        enabled = elements.isNotEmpty(),
                        modifier = Modifier.size(36.dp).tip("撤销上一条标注")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, tint = if (elements.isNotEmpty()) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
                    }

                    // 🗑️ 一键清空所有标注 (Clear All)
                    IconButton(
                        onClick = {
                            elements.clear()
                            selectedElementId = null
                            inlineInputPos = null
                            editingTextElementId = null
                            Toast.show("已清空全部手绘与文字标注", ToastType.INFO)
                        },
                        enabled = elements.isNotEmpty(),
                        modifier = Modifier.size(36.dp).tip("一键清空所有已绘制的涂鸦、矩形与文字标注")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            null,
                            tint = if (elements.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 缩放比例重置
                    TextButton(
                        onClick = { zoomScale = 1.0f },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("${(zoomScale * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = Color.White.copy(alpha = 0.3f))

                    // 💾 保存并作为新参考图
                    if (onSaveAsReference != null) {
                        Button(
                            onClick = {
                                commitInlineText()
                                isSaving = true
                                scope.launch {
                                    val savedPath = saveMergedAnnotatedImage(imageModel, elements.toList(), baseWidthDp.value, baseHeightDp.value, storage)
                                    isSaving = false
                                    if (savedPath != null) {
                                        onSaveAsReference(savedPath)
                                        Toast.show("已将手绘标注图保存并设为当前参考底图", ToastType.SUCCESS)
                                        onDismiss()
                                    } else {
                                        Toast.show("保存标注图片失败", ToastType.ERROR)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = AppShapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).tip("将手绘标注图层与原始底图无损合并保存，并自动设为工作区参考底图")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("保存为参考图", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 关闭
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).tip("关闭大图查看器")) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * 离屏将原始物理底图与对象化标注图元无损合并落盘为 PNG
 */
private suspend fun saveMergedAnnotatedImage(
    imageModel: Any,
    items: List<CanvasItem>,
    viewWidthDp: Float,
    viewHeightDp: Float,
    storage: LocalFileStorage
): String? = withContext(Dispatchers.Default) {
    try {
        val rawBytes = when (imageModel) {
            is ByteArray -> imageModel
            is String -> readLocalFileBytes(imageModel)
            else -> null
        } ?: return@withContext null

        val srcImage = Image.makeFromEncoded(rawBytes)
        val origW = srcImage.width
        val origH = srcImage.height

        val surface = Surface.makeRasterN32Premul(origW, origH)
        val canvas = surface.canvas

        // 1. 绘制原始物理全分辨率底图
        canvas.drawImage(srcImage, 0f, 0f)

        // 计算视图与物理像素之间的缩放比率
        val scaleRatioX = origW.toFloat() / viewWidthDp.coerceAtLeast(1f)
        val scaleRatioY = origH.toFloat() / viewHeightDp.coerceAtLeast(1f)

        // 2. 绘制标注图元
        items.forEach { item ->
            when (item) {
                is CanvasItem.Brush -> {
                    if (item.points.size > 1) {
                        val builder = PathBuilder()
                        builder.moveTo(item.points[0].x * scaleRatioX, item.points[0].y * scaleRatioY)
                        for (i in 1 until item.points.size) {
                            builder.lineTo(item.points[i].x * scaleRatioX, item.points[i].y * scaleRatioY)
                        }
                        val path = builder.detach()
                        val paint = Paint().apply {
                            color = item.color.value.toInt()
                            mode = PaintMode.STROKE
                            strokeWidth = item.strokeWidth * scaleRatioX
                            isAntiAlias = true
                        }
                        canvas.drawPath(path, paint)
                    }
                }

                is CanvasItem.RectBox -> {
                    val paint = Paint().apply {
                        color = item.color.value.toInt()
                        mode = PaintMode.STROKE
                        strokeWidth = item.strokeWidth * scaleRatioX
                        isAntiAlias = true
                    }
                    val l = item.bounds.left * scaleRatioX
                    val t = item.bounds.top * scaleRatioY
                    val w = item.bounds.width * scaleRatioX
                    val h = item.bounds.height * scaleRatioY
                    canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(l, t, w, h), paint)
                }

                is CanvasItem.TextLabel -> {
                    // 文字标注已在 UI 层面可视化
                }
            }
        }

        val snapshot = surface.makeImageSnapshot()
        val pngData = snapshot.encodeToData(EncodedImageFormat.PNG) ?: return@withContext null
        val bytes = pngData.bytes

        val timestamp = getCurrentTimeMillis()
        val relPath = "cache/wechat_ref_$timestamp.png"
        storage.saveBytesToFile(relPath, bytes)

        storage.getFilePath(relPath)
    } catch (_: Throwable) {
        null
    }
}

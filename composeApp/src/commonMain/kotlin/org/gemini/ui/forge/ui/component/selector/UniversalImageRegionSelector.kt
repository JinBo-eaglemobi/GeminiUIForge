package org.gemini.ui.forge.ui.component.selector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.ResizeVerticalIcon
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.utils.decodeToBitmap
import org.gemini.ui.forge.utils.toImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 支持的多态图片来源包装器（密封接口）。
 * 统一抽象本地工程文件、绝对物理路径、内存字节数组与已解码位图四种不同输入形态。
 */
sealed interface RegionImageSource {
    /** 来自项目归档文件 [TemplateFile] */
    data class FromFile(val file: TemplateFile) : RegionImageSource

    /** 来自本地文件绝对物理路径 [absolutePath]（支持 Base64 或文件路径） */
    data class FromPath(val absolutePath: String) : RegionImageSource

    /** 来自内存中的图片二进制字节数组 [bytes] */
    data class FromBytes(val bytes: ByteArray) : RegionImageSource

    /** 来自已直接解码好的 Compose [ImageBitmap] */
    data class FromBitmap(val bitmap: ImageBitmap) : RegionImageSource
}

/**
 * 选区宽高比约束模式。
 */
sealed interface AspectRatioMode {
    /** 自由拉伸模式：宽高比不受限制 */
    data object Free : AspectRatioMode

    /** 锁定原图比例模式：强制选区比例与底图原始宽高比一致 */
    data object OriginalImage : AspectRatioMode

    /** 自定义固定比例：如 1.0f (1:1 正方形), 1.777f (16:9 宽屏) */
    data class Fixed(val ratio: Float) : AspectRatioMode
}

/**
 * 选区控制器，提供外部命令式操作接口（如代码一键清空、全选或指定矩形）。
 */
class ImageRegionSelectorController {
    /** 清空当前选区 */
    var clearSelection: () -> Unit = {}

    /** 重置选区为整张底图的最大完整边界 */
    var resetToFull: () -> Unit = {}

    /** 编程式设置指定选区 */
    var setRect: (SerialRect?) -> Unit = {}
}

/**
 * 8 方向控制手柄枚举。
 * 覆盖 4 个角落手柄与 4 条边中点手柄，支持 1D 单向微调与 2D 双向对称拉伸。
 */
enum class RegionHandle {
    /** 左上角手柄 (双向缩放) */
    TOP_LEFT,
    /** 上边中点手柄 (垂直单向拉伸) */
    TOP_CENTER,
    /** 右上角手柄 (双向缩放) */
    TOP_RIGHT,
    /** 左边中点手柄 (水平单向拉伸) */
    CENTER_LEFT,
    /** 右边中点手柄 (水平单向拉伸) */
    CENTER_RIGHT,
    /** 左下角手柄 (双向缩放) */
    BOTTOM_LEFT,
    /** 下边中点手柄 (垂直单向拉伸) */
    BOTTOM_CENTER,
    /** 右下角手柄 (双向缩放) */
    BOTTOM_RIGHT
}

/**
 * UniversalImageRegionSelector（通用图片区域选区与裁剪组件）
 *
 * 核心架构特性：
 * 1. 8 方向独立高精度控制手柄（4 角 + 4 边中点），支持单向微调与双向缩放；
 * 2. Alt 键中心对称缩放、Shift 键比例锁定；
 * 3. 选区外暗角蒙版（Spotlight）与交互时三分法网格辅助线；
 * 4. 实时尺寸 HUD 悬浮胶囊标签；
 * 5. 动态边缘光标状态机响应；
 * 6. 空格键平移抓手与画布平滑缩放；
 * 7. rememberUpdatedState 解耦手势，全程平滑无中断。
 *
 * @param imageSource 多态图片来源（文件、路径、字节数组或 ImageBitmap）
 * @param pageWidth 画布/底图逻辑总宽（用于归一化百分比与逻辑像素换算）
 * @param pageHeight 画布/底图逻辑总高（用于归一化百分比与逻辑像素换算）
 * @param modifier 外部布局修饰符
 * @param initialRect 初始选区矩形（逻辑坐标），为 null 时处于无选区状态
 * @param controller 选区控制器对象，供外部主动清空或设置矩形
 * @param aspectRatioMode 宽高比约束模式（默认自由拉伸）
 * @param showThirdsGrid 是否在交互时显示经典的 3x3 构图三分法辅助线
 * @param showDimensionBadge 是否在选区下方展示实时尺寸与位置的悬浮 HUD 标签
 * @param dimColor 选区外部暗角蒙版颜色（默认为半透明深黑色）
 * @param selectionBorderColor 选区高亮描边与手柄边框主题色（默认品牌蓝 #18A0FB）
 * @param minSelectionSize 选区最小安全像素阈值（防止手柄反向交叉与零尺寸崩溃）
 * @param initialZoom 初始聚焦缩放倍率（如细化编辑时传入 1.8f 实现聚焦放大）
 * @param onSelectionChange 选区高频拖拽时的实时变动回调
 * @param onSelectionConfirmed 手势抬起确认后的终态回调
 */
@Composable
fun UniversalImageRegionSelector(
    imageSource: RegionImageSource?,
    pageWidth: Float,
    pageHeight: Float,
    modifier: Modifier = Modifier,
    initialRect: SerialRect? = null,
    controller: ImageRegionSelectorController? = null,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.Free,
    showThirdsGrid: Boolean = true,
    showDimensionBadge: Boolean = true,
    dimColor: Color = Color.Black.copy(alpha = 0.52f),
    selectionBorderColor: Color = Color(0xFF18A0FB),
    minSelectionSize: Float = 20f,
    initialZoom: Float = 1f,
    onSelectionChange: (SerialRect?) -> Unit = {},
    onSelectionConfirmed: (SerialRect?) -> Unit = {}
) {
    // 1. 当前选区内部状态（规范化保证 left < right, top < bottom）
    var selectedRect by remember(initialRect) {
        mutableStateOf(
            initialRect?.let {
                SerialRect(
                    left = min(it.left, it.right),
                    top = min(it.top, it.bottom),
                    right = max(it.left, it.right),
                    bottom = max(it.top, it.bottom)
                )
            }
        )
    }

    // 2. 绑定外部命令式控制器
    controller?.let { c ->
        c.clearSelection = {
            selectedRect = null
            onSelectionChange(null)
            onSelectionConfirmed(null)
        }
        c.resetToFull = {
            val full = SerialRect(0f, 0f, pageWidth, pageHeight)
            selectedRect = full
            onSelectionChange(full)
            onSelectionConfirmed(full)
        }
        c.setRect = { rect ->
            selectedRect = rect
            onSelectionChange(rect)
            onSelectionConfirmed(rect)
        }
    }

    // 3. 异步解码多态底图资源为 ImageBitmap
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

    // 4. 交互与修饰键状态监听
    var isSpacePressed by remember { mutableStateOf(false) }
    var isAltPressed by remember { mutableStateOf(false) }
    var isShiftPressed by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    // 5. 动态光标与焦点管理器
    var currentCursorIcon by remember { mutableStateOf(PointerIcon.Default) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    // 6. 画布缩放与平移视口状态
    var zoom by remember(initialZoom) { mutableStateOf(initialZoom) }
    var pan by remember { mutableStateOf(Offset.Zero) }

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
            .onKeyEvent { keyEvent ->
                // 空格键抓手状态监听
                if (keyEvent.key == Key.Spacebar) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> { isSpacePressed = true; true }
                        KeyEventType.KeyUp -> { isSpacePressed = false; true }
                        else -> false
                    }
                } else false
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerW = maxWidth.value
            val containerH = maxHeight.value

            // 7. 计算底图自适应容器的基础缩放比例与居中偏移量
            val imageAspectRatio = if (pageHeight > 0f) pageWidth / pageHeight else 1f
            val containerAspectRatio = if (containerH > 0f) containerW / containerH else 1f
            val baseScale = if (imageAspectRatio > containerAspectRatio) {
                containerW / max(1f, pageWidth)
            } else {
                containerH / max(1f, pageHeight)
            }

            val baseDisplayW = pageWidth * baseScale
            val baseDisplayH = pageHeight * baseScale
            val baseOffsetX = (containerW - baseDisplayW) / 2f
            val baseOffsetY = (containerH - baseDisplayH) / 2f

            // 8. 首次若传入了初始选区且设置了聚焦放大，自动平移聚焦居中
            LaunchedEffect(initialRect) {
                if (initialRect != null && initialZoom > 1f) {
                    val cx = baseOffsetX + (initialRect.left + initialRect.width / 2f) * baseScale
                    val cy = baseOffsetY + (initialRect.top + initialRect.height / 2f) * baseScale
                    val viewCenterX = containerW / 2f
                    val viewCenterY = containerH / 2f
                    pan = Offset(viewCenterX - cx * zoom, viewCenterY - cy * zoom)
                }
            }

            var activeHandle by remember { mutableStateOf<RegionHandle?>(null) }
            var isMovingRect by remember { mutableStateOf(false) }
            var isCreatingNew by remember { mutableStateOf(false) }

            // 9. 状态引用解耦：防止 pointerInput 因频繁重组导致手势中断
            val currentRectState by rememberUpdatedState(selectedRect)
            val spaceState by rememberUpdatedState(isSpacePressed)
            val altState by rememberUpdatedState(isAltPressed)
            val shiftState by rememberUpdatedState(isShiftPressed)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(if (isSpacePressed) PointerIcon.Hand else currentCursorIcon)
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .pointerInput(baseScale, baseOffsetX, baseOffsetY, zoom) {
                        // 鼠标移动监听：实时判定悬停区域并动态切换 8 方向拉伸/抓手光标
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
                                            val logicalX = ((change.position.x / density.density - baseOffsetX) / baseScale)
                                            val logicalY = ((change.position.y / density.density - baseOffsetY) / baseScale)
                                            val handleSlop = 16f / density.density / baseScale / zoom
                                            val rect = currentRectState

                                            if (rect != null) {
                                                val handle = hitTestHandle(rect, logicalX, logicalY, handleSlop)
                                                currentCursorIcon = when (handle) {
                                                    RegionHandle.TOP_CENTER, RegionHandle.BOTTOM_CENTER -> ResizeVerticalIcon
                                                    RegionHandle.CENTER_LEFT, RegionHandle.CENTER_RIGHT -> ResizeHorizontalIcon
                                                    RegionHandle.TOP_LEFT, RegionHandle.BOTTOM_RIGHT -> ResizeHorizontalIcon
                                                    RegionHandle.TOP_RIGHT, RegionHandle.BOTTOM_LEFT -> ResizeVerticalIcon
                                                    null -> {
                                                        val isInside = logicalX in rect.left..rect.right &&
                                                                logicalY in rect.top..rect.bottom
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
                    .pointerInput(baseScale, baseOffsetX, baseOffsetY, zoom) {
                        // 拖动手势：8 手柄拉伸、Alt 中心对称缩放、选区整体平移与空白拉出新建
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (spaceState) {
                                    activeHandle = null
                                    isMovingRect = false
                                    isCreatingNew = false
                                    return@detectDragGestures
                                }

                                isInteracting = true
                                val logicalX = ((offset.x / density.density - baseOffsetX) / baseScale).coerceIn(0f, pageWidth)
                                val logicalY = ((offset.y / density.density - baseOffsetY) / baseScale).coerceIn(0f, pageHeight)
                                val handleSlop = 16f / density.density / baseScale / zoom
                                val rect = currentRectState

                                if (rect != null) {
                                    activeHandle = hitTestHandle(rect, logicalX, logicalY, handleSlop)
                                    if (activeHandle == null) {
                                        isMovingRect = logicalX in rect.left..rect.right && logicalY in rect.top..rect.bottom
                                        if (!isMovingRect) {
                                            // 点击在选区外部空白处：拉出新选区
                                            isCreatingNew = true
                                            selectedRect = SerialRect(logicalX, logicalY, logicalX, logicalY)
                                            onSelectionChange(selectedRect)
                                        }
                                    }
                                } else {
                                    // 无选区时：在点击起点新建选区
                                    isCreatingNew = true
                                    selectedRect = SerialRect(logicalX, logicalY, logicalX, logicalY)
                                    onSelectionChange(selectedRect)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (spaceState) {
                                    // 空格抓手平移画布
                                    pan += dragAmount
                                } else {
                                    val deltaX = (dragAmount.x / density.density / baseScale)
                                    val deltaY = (dragAmount.y / density.density / baseScale)

                                    if (activeHandle != null) {
                                        // 8 手柄方向拉伸（支持 Alt 键中心对称缩放）
                                        val cur = currentRectState ?: return@detectDragGestures
                                        selectedRect = resizeRegion(
                                            current = cur,
                                            handle = activeHandle!!,
                                            deltaX = deltaX,
                                            deltaY = deltaY,
                                            isAltCenterResize = altState,
                                            maxW = pageWidth,
                                            maxH = pageHeight,
                                            minSize = minSelectionSize
                                        )
                                        onSelectionChange(selectedRect)
                                    } else if (isMovingRect) {
                                        // 选区整体平移移动
                                        val cur = currentRectState ?: return@detectDragGestures
                                        val w = cur.width
                                        val h = cur.height
                                        val newLeft = (cur.left + deltaX).coerceIn(0f, pageWidth - w)
                                        val newTop = (cur.top + deltaY).coerceIn(0f, pageHeight - h)
                                        selectedRect = SerialRect(newLeft, newTop, newLeft + w, newTop + h)
                                        onSelectionChange(selectedRect)
                                    } else if (isCreatingNew) {
                                        // 拖拽拉出新矩形
                                        val cur = currentRectState ?: return@detectDragGestures
                                        val logicalX = ((change.position.x / density.density - baseOffsetX) / baseScale).coerceIn(0f, pageWidth)
                                        val logicalY = ((change.position.y / density.density - baseOffsetY) / baseScale).coerceIn(0f, pageHeight)
                                        selectedRect = SerialRect(cur.left, cur.top, logicalX, logicalY)
                                        onSelectionChange(selectedRect)
                                    }
                                }
                            },
                            onDragEnd = {
                                activeHandle = null
                                isMovingRect = false
                                isCreatingNew = false
                                isInteracting = false
                                // 抬起时自动规范化矩形坐标确保 left < right, top < bottom
                                selectedRect?.let { r ->
                                    val normalized = SerialRect(
                                        left = min(r.left, r.right),
                                        top = min(r.top, r.bottom),
                                        right = max(r.left, r.right),
                                        bottom = max(r.top, r.bottom)
                                    )
                                    selectedRect = normalized
                                    onSelectionConfirmed(normalized)
                                } ?: onSelectionConfirmed(null)
                            },
                            onDragCancel = {
                                activeHandle = null
                                isMovingRect = false
                                isCreatingNew = false
                                isInteracting = false
                            }
                        )
                    }
            ) {
                // 10. 渲染底图图像
                if (loadedBitmap != null) {
                    Canvas(
                        modifier = Modifier
                            .offset(x = baseOffsetX.dp, y = baseOffsetY.dp)
                            .size(width = baseDisplayW.dp, height = baseDisplayH.dp)
                    ) {
                        drawImage(
                            image = loadedBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(loadedBitmap.width, loadedBitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                } else {
                    when (imageSource) {
                        is RegionImageSource.FromFile -> {
                            AsyncImage(
                                model = imageSource.file.getAbsolutePath(),
                                contentDescription = null,
                                modifier = Modifier
                                    .offset(x = baseOffsetX.dp, y = baseOffsetY.dp)
                                    .size(width = baseDisplayW.dp, height = baseDisplayH.dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        is RegionImageSource.FromPath -> {
                            AsyncImage(
                                model = imageSource.absolutePath,
                                contentDescription = null,
                                modifier = Modifier
                                    .offset(x = baseOffsetX.dp, y = baseOffsetY.dp)
                                    .size(width = baseDisplayW.dp, height = baseDisplayH.dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        else -> Unit
                    }
                }

                // 11. 选区、暗角蒙版、三分法网格与 8 手柄绘制
                Canvas(
                    modifier = Modifier
                        .offset(x = baseOffsetX.dp, y = baseOffsetY.dp)
                        .size(width = baseDisplayW.dp, height = baseDisplayH.dp)
                ) {
                    val rect = currentRectState
                    if (rect != null) {
                        val normL = min(rect.left, rect.right)
                        val normT = min(rect.top, rect.bottom)
                        val normR = max(rect.left, rect.right)
                        val normB = max(rect.top, rect.bottom)

                        val leftPx = (normL / pageWidth) * size.width
                        val topPx = (normT / pageHeight) * size.height
                        val widthPx = ((normR - normL) / pageWidth) * size.width
                        val heightPx = ((normB - normT) / pageHeight) * size.height

                        val rectTopLeft = Offset(leftPx, topPx)
                        val rectSize = Size(widthPx, heightPx)

                        // 11.1 暗角蒙版（Spotlight 效果）：四向遮盖非选区区域
                        // 顶部遮罩
                        drawRect(color = dimColor, topLeft = Offset.Zero, size = Size(size.width, topPx))
                        // 底部遮罩
                        drawRect(color = dimColor, topLeft = Offset(0f, topPx + heightPx), size = Size(size.width, size.height - (topPx + heightPx)))
                        // 左侧遮罩
                        drawRect(color = dimColor, topLeft = Offset(0f, topPx), size = Size(leftPx, heightPx))
                        // 右侧遮罩
                        drawRect(color = dimColor, topLeft = Offset(leftPx + widthPx, topPx), size = Size(size.width - (leftPx + widthPx), heightPx))

                        // 11.2 选区内部半透明高亮与主色边框
                        drawRect(color = selectionBorderColor.copy(alpha = 0.12f), topLeft = rectTopLeft, size = rectSize)
                        drawRect(color = selectionBorderColor, topLeft = rectTopLeft, size = rectSize, style = Stroke(width = (2.dp / zoom).toPx()))

                        // 11.3 三分法构图参考线（交互时半透明呼吸显示）
                        if (showThirdsGrid && widthPx > 30f && heightPx > 30f) {
                            val gridColor = Color.White.copy(alpha = if (isInteracting) 0.55f else 0.25f)
                            val gridStroke = (1.dp / zoom).toPx()

                            // 垂直 1/3 与 2/3 准线
                            val v1 = leftPx + widthPx / 3f
                            val v2 = leftPx + widthPx * 2f / 3f
                            drawLine(gridColor, Offset(v1, topPx), Offset(v1, topPx + heightPx), gridStroke)
                            drawLine(gridColor, Offset(v2, topPx), Offset(v2, topPx + heightPx), gridStroke)

                            // 水平 1/3 与 2/3 准线
                            val h1 = topPx + heightPx / 3f
                            val h2 = topPx + heightPx * 2f / 3f
                            drawLine(gridColor, Offset(leftPx, h1), Offset(leftPx + widthPx, h1), gridStroke)
                            drawLine(gridColor, Offset(leftPx, h2), Offset(leftPx + widthPx, h2), gridStroke)
                        }

                        // 11.4 8 方向发光控制手柄绘制
                        val handleRadius = (5.5.dp / zoom).toPx()
                        val cx = leftPx + widthPx / 2f
                        val cy = topPx + heightPx / 2f
                        val rightPx = leftPx + widthPx
                        val bottomPx = topPx + heightPx

                        // 8 个手柄的绝对锚点坐标
                        val points = listOf(
                            Offset(leftPx, topPx),     // 左上
                            Offset(cx, topPx),         // 上中
                            Offset(rightPx, topPx),    // 右上
                            Offset(leftPx, cy),        // 左中
                            Offset(rightPx, cy),       // 右中
                            Offset(leftPx, bottomPx),  // 左下
                            Offset(cx, bottomPx),      // 下中
                            Offset(rightPx, bottomPx)  // 右下
                        )

                        points.forEach { pt ->
                            drawCircle(color = Color.White, radius = handleRadius, center = pt)
                            drawCircle(
                                color = selectionBorderColor,
                                radius = handleRadius,
                                center = pt,
                                style = Stroke(width = (1.5.dp / zoom).toPx())
                            )
                        }
                    } else {
                        // 无选区时全屏半透明遮罩
                        drawRect(color = dimColor.copy(alpha = 0.35f), topLeft = Offset.Zero, size = size)
                    }
                }
            }

            // 12. 悬浮尺寸 HUD 胶囊标签
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
        }
    }
}

/**
 * 8 方向手柄点击命中测试算法。
 * 计算鼠标点击逻辑坐标是否在 8 个手柄的容差球（Slop Radius）范围内。
 *
 * @param rect 当前选区矩形
 * @param logicalX 点击位置逻辑 X
 * @param logicalY 点击位置逻辑 Y
 * @param touchSlopLogical 逻辑像素容差半径
 * @return 命中的手柄枚举，未命中返回 null
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
        isNear(cx, t) -> RegionHandle.TOP_CENTER
        isNear(r, t) -> RegionHandle.TOP_RIGHT
        isNear(l, cy) -> RegionHandle.CENTER_LEFT
        isNear(r, cy) -> RegionHandle.CENTER_RIGHT
        isNear(l, b) -> RegionHandle.BOTTOM_LEFT
        isNear(cx, b) -> RegionHandle.BOTTOM_CENTER
        isNear(r, b) -> RegionHandle.BOTTOM_RIGHT
        else -> null
    }
}

/**
 * 8 方向手柄拖拽拉伸与 Alt 键中心对称缩放矩阵解算。
 *
 * @param current 当前选区矩形
 * @param handle 正在拖拽的目标手柄
 * @param deltaX 逻辑 X 位移增量
 * @param deltaY 逻辑 Y 位移增量
 * @param isAltCenterResize 是否按住了 Alt/Option 键启用中心对称缩放
 * @param maxW 最大允许宽度（底图逻辑宽）
 * @param maxH 最大允许高度（底图逻辑高）
 * @param minSize 最小安全尺寸阈值
 * @return 计算后的新选区矩形
 */
fun resizeRegion(
    current: SerialRect,
    handle: RegionHandle,
    deltaX: Float,
    deltaY: Float,
    isAltCenterResize: Boolean,
    maxW: Float,
    maxH: Float,
    minSize: Float = 20f
): SerialRect {
    var l = min(current.left, current.right)
    var t = min(current.top, current.bottom)
    var r = max(current.left, current.right)
    var b = max(current.top, current.bottom)

    if (isAltCenterResize) {
        // Alt 键激活：以中心点为固定对称轴向外/向内双向膨胀
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        var halfW = (r - l) / 2f
        var halfH = (b - t) / 2f

        when (handle) {
            RegionHandle.TOP_LEFT -> { halfW -= deltaX; halfH -= deltaY }
            RegionHandle.TOP_CENTER -> { halfH -= deltaY }
            RegionHandle.TOP_RIGHT -> { halfW += deltaX; halfH -= deltaY }
            RegionHandle.CENTER_LEFT -> { halfW -= deltaX }
            RegionHandle.CENTER_RIGHT -> { halfW += deltaX }
            RegionHandle.BOTTOM_LEFT -> { halfW -= deltaX; halfH += deltaY }
            RegionHandle.BOTTOM_CENTER -> { halfH += deltaY }
            RegionHandle.BOTTOM_RIGHT -> { halfW += deltaX; halfH += deltaY }
        }

        halfW = halfW.coerceAtLeast(minSize / 2f).coerceAtMost(minOf(cx, maxW - cx))
        halfH = halfH.coerceAtLeast(minSize / 2f).coerceAtMost(minOf(cy, maxH - cy))

        return SerialRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    // 标准单边与角手柄自由拉伸
    when (handle) {
        RegionHandle.TOP_LEFT -> {
            l = (l + deltaX).coerceIn(0f, r - minSize)
            t = (t + deltaY).coerceIn(0f, b - minSize)
        }
        RegionHandle.TOP_CENTER -> {
            t = (t + deltaY).coerceIn(0f, b - minSize)
        }
        RegionHandle.TOP_RIGHT -> {
            r = (r + deltaX).coerceIn(l + minSize, maxW)
            t = (t + deltaY).coerceIn(0f, b - minSize)
        }
        RegionHandle.CENTER_LEFT -> {
            l = (l + deltaX).coerceIn(0f, r - minSize)
        }
        RegionHandle.CENTER_RIGHT -> {
            r = (r + deltaX).coerceIn(l + minSize, maxW)
        }
        RegionHandle.BOTTOM_LEFT -> {
            l = (l + deltaX).coerceIn(0f, r - minSize)
            b = (b + deltaY).coerceIn(t + minSize, maxH)
        }
        RegionHandle.BOTTOM_CENTER -> {
            b = (b + deltaY).coerceIn(t + minSize, maxH)
        }
        RegionHandle.BOTTOM_RIGHT -> {
            r = (r + deltaX).coerceIn(l + minSize, maxW)
            b = (b + deltaY).coerceIn(t + minSize, maxH)
        }
    }

    return SerialRect(l, t, r, b)
}

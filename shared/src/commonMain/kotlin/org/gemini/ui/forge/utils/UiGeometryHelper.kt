package org.gemini.ui.forge.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 画布与窗口几何坐标计算中枢 (UiGeometryHelper)
 *
 * 统一管理画布视口状态（CanvasArea 在窗口中的位置、baseScale、offset、zoom、pan、density），
 * 并提供图元从局部相对矩形、全局绝对矩形到画布视口渲染矩形、窗口绝对逻辑矩形以及物理截图像素的无损闭环转换。
 *
 * 杜绝全项目各处散落手写坐标推导，从根本上解决漏算 DPI、漏算视口缩放平移、混淆窗口/屏幕坐标导致的切图错乱。
 */
object UiGeometryHelper {

    /**
     * 画布视口状态快照
     */
    data class ViewportState(
        val canvasBoundsInWindow: Rect = Rect.Zero,
        val pageWidth: Float = 1080f,
        val pageHeight: Float = 1920f,
        val baseScale: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val zoom: Float = 1f,
        val pan: Offset = Offset.Zero,
        val density: Float = 1f
    )

    @Volatile
    private var currentViewport: ViewportState = ViewportState()

    /**
     * 获取当前视口状态快照
     */
    val viewport: ViewportState
        get() = currentViewport

    /**
     * 更新画布视口状态（由 CanvasArea 实时更新）
     */
    fun updateViewport(
        canvasBoundsInWindow: Rect = currentViewport.canvasBoundsInWindow,
        pageWidth: Float = currentViewport.pageWidth,
        pageHeight: Float = currentViewport.pageHeight,
        baseScale: Float = currentViewport.baseScale,
        offsetX: Float = currentViewport.offsetX,
        offsetY: Float = currentViewport.offsetY,
        zoom: Float = currentViewport.zoom,
        pan: Offset = currentViewport.pan,
        density: Float = currentViewport.density
    ) {
        currentViewport = ViewportState(
            canvasBoundsInWindow = canvasBoundsInWindow,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            baseScale = baseScale,
            offsetX = offsetX,
            offsetY = offsetY,
            zoom = zoom,
            pan = pan,
            density = density
        )
    }

    /**
     * 将图元的全景全局绝对矩形 (SerialRect) 转换为在 CanvasArea 未缩放排版时的逻辑像素矩形 (Rect)
     *
     * 注意：CanvasArea 中的 offsetX, offsetY, baseScale 是以 dp 为基准计算的，
     * 此处统一乘以 density 换算为纯像素 (Px)，彻底杜绝 dp 与 px 混算导致的比例畸变。
     *
     * @param absBounds 全局绝对矩形（以页面左上角 0,0 为基准）
     */
    fun calculateBlockBoundsInCanvas(absBounds: SerialRect): Rect {
        val vp = currentViewport
        val d = if (vp.density > 0f) vp.density else 1.0f

        val leftPx = (vp.offsetX + absBounds.left * vp.baseScale) * d
        val topPx = (vp.offsetY + absBounds.top * vp.baseScale) * d
        val widthPx = (absBounds.width * vp.baseScale) * d
        val heightPx = (absBounds.height * vp.baseScale) * d

        return Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx)
    }

    /**
     * 将图元的全景全局绝对矩形 (SerialRect) 转换为经过 graphicsLayer 视口缩放 (zoom) 与平移 (pan) 变换后的实际渲染像素矩形 (Rect)
     *
     * graphicsLayer 变换原点锁定在左上角：
     * transformOrigin = TransformOrigin(0f, 0f)
     * pan 是以像素 (px) 为单位的位移量
     *
     * @param absBounds 全局绝对矩形
     */
    fun calculateBlockBoundsInViewport(absBounds: SerialRect): Rect {
        val vp = currentViewport
        val canvasRect = calculateBlockBoundsInCanvas(absBounds)

        // 与 CanvasArea 中 transformOrigin = TransformOrigin(0f, 0f) 100% 精确对齐
        val transformedLeft = canvasRect.left * vp.zoom + vp.pan.x
        val transformedTop = canvasRect.top * vp.zoom + vp.pan.y
        val transformedWidth = canvasRect.width * vp.zoom
        val transformedHeight = canvasRect.height * vp.zoom

        return Rect(
            transformedLeft,
            transformedTop,
            transformedLeft + transformedWidth,
            transformedTop + transformedHeight
        )
    }

    /**
     * 计算图元在整个应用窗口（Compose Window 客户区坐标系，以窗口左上角为原点）内的精确绝对像素矩形
     *
     * @param absBounds 全局绝对矩形
     */
    fun calculateBlockBoundsInWindow(absBounds: SerialRect): Rect {
        val vp = currentViewport
        val viewportRect = calculateBlockBoundsInViewport(absBounds)

        // 加上 CanvasArea 在窗口内的像素偏移 (canvasBoundsInWindow 是 LayoutCoordinates 上报的真实 Px)
        val windowLeft = vp.canvasBoundsInWindow.left + viewportRect.left
        val windowTop = vp.canvasBoundsInWindow.top + viewportRect.top

        return Rect(
            windowLeft,
            windowTop,
            windowLeft + viewportRect.width,
            windowTop + viewportRect.height
        )
    }

    /**
     * 便捷方法：直接计算图元在窗口中的逻辑矩形
     */
    fun getBlockBoundsInWindow(block: UIBlock): Rect {
        return calculateBlockBoundsInWindow(block.toAbsoluteBounds())
    }

    /**
     * 获取指定模块在窗口截图时的最佳逻辑裁剪矩形 (IntRect)
     *
     * 自动包含指定的外扩 Padding，并保证宽度与高度恒大于 0，杜绝任何黑条或空图缺陷。
     *
     * @param block 目标图元
     * @param paddingPx 外扩边距（像素，默认 32）
     * @return 适合直接传入 AppWindowHolder.captureWindowBytes 的裁剪矩形
     */
    fun getBlockCropRegionInWindow(block: UIBlock, paddingPx: Int = 32): IntRect {
        val blockRect = getBlockBoundsInWindow(block)

        val left = max(0, (blockRect.left - paddingPx).roundToInt())
        val top = max(0, (blockRect.top - paddingPx).roundToInt())
        val width = max(16, (blockRect.width + paddingPx * 2).roundToInt())
        val height = max(16, (blockRect.height + paddingPx * 2).roundToInt())

        return IntRect(left, top, left + width, top + height)
    }

    /**
     * 将 Compose 窗口逻辑矩形按真实的物理像素与逻辑像素比例转换为物理 Bitmap 裁剪矩形
     *
     * @param logicalRect 窗口逻辑矩形
     * @param scaleRatioX 物理宽度 / 逻辑宽度
     * @param scaleRatioY 物理高度 / 逻辑高度
     * @param maxPhysicalWidth 物理 Bitmap 宽度上限
     * @param maxPhysicalHeight 物理 Bitmap 高度上限
     */
    fun toPhysicalCropRect(
        logicalRect: IntRect,
        scaleRatioX: Double,
        scaleRatioY: Double,
        maxPhysicalWidth: Int,
        maxPhysicalHeight: Int
    ): IntRect {
        val physLeft = (logicalRect.left * scaleRatioX).roundToInt().coerceIn(0, maxPhysicalWidth)
        val physTop = (logicalRect.top * scaleRatioY).roundToInt().coerceIn(0, maxPhysicalHeight)
        val physRight = (logicalRect.right * scaleRatioX).roundToInt().coerceIn(physLeft, maxPhysicalWidth)
        val physBottom = (logicalRect.bottom * scaleRatioY).roundToInt().coerceIn(physTop, maxPhysicalHeight)

        val safeRight = if (physRight <= physLeft) min(maxPhysicalWidth, physLeft + 1) else physRight
        val safeBottom = if (physBottom <= physTop) min(maxPhysicalHeight, physTop + 1) else physBottom

        return IntRect(physLeft, physTop, safeRight, safeBottom)
    }
}

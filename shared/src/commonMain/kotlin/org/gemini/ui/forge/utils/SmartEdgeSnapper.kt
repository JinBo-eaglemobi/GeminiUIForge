package org.gemini.ui.forge.utils

import org.gemini.ui.forge.model.ui.SerialRect
import org.jetbrains.skia.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 智能边缘吸附纠偏分析结果
 */
data class SnappingResult(
    /** 物理图片像素坐标系下的精准矩形 */
    val physicalRect: SerialRect,
    /** 映射回画布逻辑坐标系下的精准矩形 (用于修正 template.json 中的 bounds) */
    val logicalRect: SerialRect,
    /** 物理像素 X 轴纠偏偏移量 */
    val deltaX: Float,
    /** 物理像素 Y 轴纠偏偏移量 */
    val deltaY: Float,
    /** 水平尺度比 (ImageWidth / CanvasWidth) */
    val scaleX: Float,
    /** 垂直尺度比 (ImageHeight / CanvasHeight) */
    val scaleY: Float
)

/**
 * 纯离线智能边缘吸附与轮廓对齐引擎
 * 基于 Skia 内存位图与线段投影梯度积分算子 (Line Projection Profile Integral)
 * 解决大模型预测坐标漂移与尺度失调问题，将粗定位自动磁力吸附至真实物理边缘
 */
object SmartEdgeSnapper {

    /**
     * 对大模型预测的逻辑 bounds 执行物理边缘吸附
     *
     * @param imageBytes 原始参考图二进制字节
     * @param logicalBounds 大模型在画布坐标系下的粗略矩形
     * @param canvasWidth 画布逻辑宽度 (如 600f)
     * @param canvasHeight 画布逻辑高度 (如 750f)
     * @param searchRadius 边缘搜索半宽 (像素)，默认 45px
     */
    fun snapBounds(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        canvasWidth: Float,
        canvasHeight: Float,
        searchRadius: Int = 45
    ): SnappingResult? {
        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (_: Exception) {
            return null
        }

        val imgW = skiaImage.width
        val imgH = skiaImage.height

        val sx = if (canvasWidth > 0f) imgW.toFloat() / canvasWidth else 1.0f
        val sy = if (canvasHeight > 0f) imgH.toFloat() / canvasHeight else 1.0f

        // 1. 第一阶：空间尺度反投影至物理位图坐标空间
        val roughPhysLeft = (logicalBounds.left * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysTop = (logicalBounds.top * sy).coerceIn(0f, imgH.toFloat())
        val roughPhysRight = (logicalBounds.right * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysBottom = (logicalBounds.bottom * sy).coerceIn(0f, imgH.toFloat())

        if (roughPhysRight <= roughPhysLeft || roughPhysBottom <= roughPhysTop) {
            return null
        }

        val bitmap = Bitmap().apply { allocN32Pixels(imgW, imgH) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(skiaImage, 0f, 0f)

        // 2. 第二阶：线段投影梯度积分物理吸附 (四边独立磁力收敛)
        val snappedPhysLeft = snapVerticalEdge(
            bitmap = bitmap,
            roughX = roughPhysLeft.roundToInt(),
            yStart = roughPhysTop.roundToInt(),
            yEnd = roughPhysBottom.roundToInt(),
            radius = searchRadius,
            isLeftEdge = true
        ).toFloat()

        val snappedPhysRight = snapVerticalEdge(
            bitmap = bitmap,
            roughX = roughPhysRight.roundToInt(),
            yStart = roughPhysTop.roundToInt(),
            yEnd = roughPhysBottom.roundToInt(),
            radius = searchRadius,
            isLeftEdge = false
        ).toFloat()

        val snappedPhysTop = snapHorizontalEdge(
            bitmap = bitmap,
            roughY = roughPhysTop.roundToInt(),
            xStart = snappedPhysLeft.roundToInt(),
            xEnd = snappedPhysRight.roundToInt(),
            radius = searchRadius,
            isTopEdge = true
        ).toFloat()

        val snappedPhysBottom = snapHorizontalEdge(
            bitmap = bitmap,
            roughY = roughPhysBottom.roundToInt(),
            xStart = snappedPhysLeft.roundToInt(),
            xEnd = snappedPhysRight.roundToInt(),
            radius = searchRadius,
            isTopEdge = false
        ).toFloat()

        // 形状合理性保护：如果吸附后导致异常反转或塌缩，平滑退火保留反投影粗略值
        val finalLeft = if (snappedPhysRight > snappedPhysLeft + 10f) snappedPhysLeft else roughPhysLeft
        val finalRight = if (snappedPhysRight > snappedPhysLeft + 10f) snappedPhysRight else roughPhysRight
        val finalTop = if (snappedPhysBottom > snappedPhysTop + 10f) snappedPhysTop else roughPhysTop
        val finalBottom = if (snappedPhysBottom > snappedPhysTop + 10f) snappedPhysBottom else roughPhysBottom

        val snappedPhysicalRect = SerialRect(finalLeft, finalTop, finalRight, finalBottom)

        // 3. 第三阶：逆向映射回画布逻辑坐标空间
        val snappedLogicalRect = SerialRect(
            left = finalLeft / sx,
            top = finalTop / sy,
            right = finalRight / sx,
            bottom = finalBottom / sy
        )

        return SnappingResult(
            physicalRect = snappedPhysicalRect,
            logicalRect = snappedLogicalRect,
            deltaX = finalLeft - roughPhysLeft,
            deltaY = finalTop - roughPhysTop,
            scaleX = sx,
            scaleY = sy
        )
    }

    /**
     * 沿垂直直边段搜索水平色差梯度积分峰值
     */
    private fun snapVerticalEdge(
        bitmap: Bitmap,
        roughX: Int,
        yStart: Int,
        yEnd: Int,
        radius: Int,
        isLeftEdge: Boolean
    ): Int {
        val h = yEnd - yStart
        if (h <= 4) return roughX

        // 直边段排除 15% 上下圆角过渡，仅在纯直边上积分
        val margin = (h * 0.15f).roundToInt().coerceAtLeast(1)
        val validYStart = (yStart + margin).coerceIn(0, bitmap.height - 1)
        val validYEnd = (yEnd - margin).coerceIn(0, bitmap.height - 1)
        if (validYEnd <= validYStart) return roughX

        val minX = (roughX - radius).coerceIn(1, bitmap.width - 2)
        val maxX = (roughX + radius).coerceIn(1, bitmap.width - 2)

        var bestX = roughX
        var maxEnergy = -1.0

        for (x in minX..maxX) {
            var energy = 0.0
            for (y in validYStart..validYEnd) {
                val c1 = bitmap.getColor(x - 1, y)
                val c2 = bitmap.getColor(x + 1, y)
                val dr = abs(Color.getR(c1) - Color.getR(c2))
                val dg = abs(Color.getG(c1) - Color.getG(c2))
                val db = abs(Color.getB(c1) - Color.getB(c2))
                val diff = dr + dg + db
                energy += diff
            }
            if (energy > maxEnergy) {
                maxEnergy = energy
                bestX = x
            }
        }
        return bestX
    }

    /**
     * 沿水平直边段搜索垂直色差梯度积分峰值
     */
    private fun snapHorizontalEdge(
        bitmap: Bitmap,
        roughY: Int,
        xStart: Int,
        xEnd: Int,
        radius: Int,
        isTopEdge: Boolean
    ): Int {
        val w = xEnd - xStart
        if (w <= 4) return roughY

        // 直边段排除 15% 左右圆角过渡
        val margin = (w * 0.15f).roundToInt().coerceAtLeast(1)
        val validXStart = (xStart + margin).coerceIn(0, bitmap.width - 1)
        val validXEnd = (xEnd - margin).coerceIn(0, bitmap.width - 1)
        if (validXEnd <= validXStart) return roughY

        val minY = (roughY - radius).coerceIn(1, bitmap.height - 2)
        val maxY = (roughY + radius).coerceIn(1, bitmap.height - 2)

        var bestY = roughY
        var maxEnergy = -1.0

        for (y in minY..maxY) {
            var energy = 0.0
            for (x in validXStart..validXEnd) {
                val c1 = bitmap.getColor(x, y - 1)
                val c2 = bitmap.getColor(x, y + 1)
                val dr = abs(Color.getR(c1) - Color.getR(c2))
                val dg = abs(Color.getG(c1) - Color.getG(c2))
                val db = abs(Color.getB(c1) - Color.getB(c2))
                val diff = dr + dg + db
                energy += diff
            }
            if (energy > maxEnergy) {
                maxEnergy = energy
                bestY = y
            }
        }
        return bestY
    }

    /**
     * 结合智能吸附与 2px 辉光保护槽，从原始大图物理裁切出严丝合缝的透明 PNG 组件图片
     */
    fun cropSnappedComponent(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Int = 2
    ): ByteArray? {
        val snapped = snapBounds(imageBytes, logicalBounds, canvasWidth, canvasHeight)
            ?: return null

        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (_: Exception) {
            return null
        }

        val pr = snapped.physicalRect
        val cl = (pr.left - paddingPx).roundToInt().coerceIn(0, skiaImage.width - 1)
        val ct = (pr.top - paddingPx).roundToInt().coerceIn(0, skiaImage.height - 1)
        val cr = (pr.right + paddingPx).roundToInt().coerceIn(cl + 1, skiaImage.width)
        val cb = (pr.bottom + paddingPx).roundToInt().coerceIn(ct + 1, skiaImage.height)

        val cw = cr - cl
        val ch = cb - ct
        if (cw <= 0 || ch <= 0) return null

        val outBmp = Bitmap().apply { allocN32Pixels(cw, ch) }
        val canvas = Canvas(outBmp)
        val srcRect = Rect.makeLTRB(cl.toFloat(), ct.toFloat(), cr.toFloat(), cb.toFloat())
        val dstRect = Rect.makeWH(cw.toFloat(), ch.toFloat())
        canvas.drawImageRect(skiaImage, srcRect, dstRect)

        val outImage = Image.makeFromBitmap(outBmp)
        return outImage.encodeToData(EncodedImageFormat.PNG)?.bytes
    }
}

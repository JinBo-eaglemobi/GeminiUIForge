package org.gemini.ui.forge.service.detection.cv

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.IComponentAligner
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.SmartEdgeSnapper
import org.gemini.ui.forge.utils.SnappingResult
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 传统 CV 几何轮廓对齐引擎 (方案 B - Classic CV Contour Aligner)
 *
 * 零外部模型依赖。基于局部多尺度自适应二值化、几何外轮廓边界拟合与不动点山脊收敛。
 * 针对规则按钮、胶囊药丸、圆角卡片具有极佳的几何拟合能力。
 */
class CvContourAligner : IComponentAligner {
    override val mode: DetectionEngineMode = DetectionEngineMode.CLASSIC_CV
    override val displayName: String = "传统 CV 几何轮廓"

    override fun align(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        blockType: UIBlockType,
        canvasWidth: Float,
        canvasHeight: Float
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

        val roughPhysLeft = (logicalBounds.left * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysTop = (logicalBounds.top * sy).coerceIn(0f, imgH.toFloat())
        val roughPhysRight = (logicalBounds.right * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysBottom = (logicalBounds.bottom * sy).coerceIn(0f, imgH.toFloat())

        if (roughPhysRight <= roughPhysLeft || roughPhysBottom <= roughPhysTop) return null

        val bitmap = Bitmap().apply { allocN32Pixels(imgW, imgH) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(skiaImage, 0f, 0f)

        // 优先调用 SmartEdgeSnapper 内置的不动点与高精度连通轮廓
        val snapRes = SmartEdgeSnapper.snapBounds(
            imageBytes = imageBytes,
            logicalBounds = logicalBounds,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

        AppLogger.d("CvContourAligner", "CV 几何轮廓校准完成: [${blockType.name}] 偏移: ΔX=${snapRes?.deltaX?.toInt()}px, ΔY=${snapRes?.deltaY?.toInt()}px")
        return snapRes
    }
}

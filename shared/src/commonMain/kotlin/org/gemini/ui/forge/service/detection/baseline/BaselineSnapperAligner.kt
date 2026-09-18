package org.gemini.ui.forge.service.detection.baseline

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.IComponentAligner
import org.gemini.ui.forge.utils.SmartEdgeSnapper
import org.gemini.ui.forge.utils.SnappingResult

/**
 * 经典微观吸附对齐引擎 (Baseline Micro-Snapper)
 *
 * 基于 Skia 像素矩阵差分、周界环境背景采样、不动点收敛锁定与死区过滤。
 * 专精 0 误差微观像素级严丝合缝对齐。
 */
class BaselineSnapperAligner : IComponentAligner {
    override val mode: DetectionEngineMode = DetectionEngineMode.BASELINE_SNAPPER
    override val displayName: String = "经典微观吸附"

    override fun align(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        blockType: UIBlockType,
        canvasWidth: Float,
        canvasHeight: Float
    ): SnappingResult? {
        return SmartEdgeSnapper.snapBounds(
            imageBytes = imageBytes,
            logicalBounds = logicalBounds,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
    }
}

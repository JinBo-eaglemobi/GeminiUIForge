package org.gemini.ui.forge.service.detection

import org.gemini.ui.forge.service.detection.baseline.BaselineSnapperAligner
import org.gemini.ui.forge.service.detection.cv.CvContourAligner

/**
 * 离线检测与对齐引擎统一注册中心与工厂门面
 */
object DetectionEngineRegistry {

    private val aligners: Map<DetectionEngineMode, IComponentAligner> = mapOf(
        DetectionEngineMode.BASELINE_SNAPPER to BaselineSnapperAligner(),
        DetectionEngineMode.CLASSIC_CV to CvContourAligner()
    )

    /**
     * 根据模式获取对应的模块几何校准对齐器
     */
    fun getAligner(mode: DetectionEngineMode): IComponentAligner {
        return aligners[mode] ?: aligners.getValue(DetectionEngineMode.BASELINE_SNAPPER)
    }

    /**
     * 获取所有支持的对齐模式列表
     */
    fun getAvailableAlignerModes(): List<DetectionEngineMode> {
        return listOf(
            DetectionEngineMode.BASELINE_SNAPPER,
            DetectionEngineMode.CLASSIC_CV
        )
    }
}

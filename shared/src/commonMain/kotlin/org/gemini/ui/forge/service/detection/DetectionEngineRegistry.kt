package org.gemini.ui.forge.service.detection

import org.gemini.ui.forge.service.detection.baseline.BaselineSnapperAligner
import org.gemini.ui.forge.service.detection.cv.CvContourAligner
import org.gemini.ui.forge.service.detection.cv.CvContourTemplateDetector
import org.gemini.ui.forge.service.detection.onnx.OnnxYoloAligner
import org.gemini.ui.forge.service.detection.onnx.OnnxYoloTemplateDetector

/**
 * 离线检测与对齐引擎统一注册中心与工厂门面
 */
object DetectionEngineRegistry {

    private val aligners: Map<DetectionEngineMode, IComponentAligner> = mapOf(
        DetectionEngineMode.BASELINE_SNAPPER to BaselineSnapperAligner(),
        DetectionEngineMode.CLASSIC_CV to CvContourAligner(),
        DetectionEngineMode.ONNX_AI to OnnxYoloAligner()
    )

    private val detectors: Map<DetectionEngineMode, ITemplateDetector> = mapOf(
        DetectionEngineMode.CLASSIC_CV to CvContourTemplateDetector(),
        DetectionEngineMode.ONNX_AI to OnnxYoloTemplateDetector()
    )

    /**
     * 根据模式获取对应的模块几何校准对齐器
     */
    fun getAligner(mode: DetectionEngineMode): IComponentAligner {
        return aligners[mode] ?: aligners.getValue(DetectionEngineMode.BASELINE_SNAPPER)
    }

    /**
     * 根据模式获取对应的离线识图建模板引擎
     */
    fun getTemplateDetector(mode: DetectionEngineMode): ITemplateDetector {
        return detectors[mode] ?: detectors.getValue(DetectionEngineMode.CLASSIC_CV)
    }

    /**
     * 获取所有支持的对齐模式列表
     */
    fun getAvailableAlignerModes(): List<DetectionEngineMode> {
        return listOf(
            DetectionEngineMode.BASELINE_SNAPPER,
            DetectionEngineMode.CLASSIC_CV,
            DetectionEngineMode.ONNX_AI
        )
    }

    /**
     * 获取所有支持的离线建模板模式列表
     */
    fun getAvailableOfflineDetectorModes(): List<DetectionEngineMode> {
        return listOf(
            DetectionEngineMode.CLASSIC_CV,
            DetectionEngineMode.ONNX_AI
        )
    }
}

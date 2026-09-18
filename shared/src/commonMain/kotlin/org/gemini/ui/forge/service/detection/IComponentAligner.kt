package org.gemini.ui.forge.service.detection

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.SnappingResult

/**
 * 模块物理尺寸与坐标对齐校准策略接口
 */
interface IComponentAligner {
    val mode: DetectionEngineMode
    val displayName: String

    /**
     * 对已有图元模块进行几何尺寸与坐标校验对齐
     *
     * @param imageBytes 参考原图的二进制字节
     * @param logicalBounds 当前模块的全局绝对逻辑矩形 (absoluteBounds)
     * @param blockType 组件类型 (BUTTON, VIEW, HEADER 等)
     * @param canvasWidth 画布逻辑宽度 (如 1080f)
     * @param canvasHeight 画布逻辑高度 (如 1920f)
     * @return 校准结果，若无需变动或已收敛则保持 0 偏移
     */
    fun align(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        blockType: UIBlockType,
        canvasWidth: Float,
        canvasHeight: Float
    ): SnappingResult?
}

package org.gemini.ui.forge.model.assetsheet

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType

/**
 * 外部图集经规范检测与同步引擎处理后的标准化图元数据契约。
 *
 * @property id 图元唯一标识符（如 "T01", "S01", "btn_spin"）
 * @property name 图元语义化名称（如 "Back_Button", "WILD_Symbol"）
 * @property category 业务所属分组/类别（如 "Top Navigation", "Special Symbols"）
 * @property type 映射自系统标准的组件类型 [UIBlockType]
 * @property bounds 图元在原图中的绝对外包矩形坐标与尺寸 (left, top, width, height)
 * @property description 辅助语义说明或 AI 识别文本
 * @property isPureContainer 是否标记为纯容器（默认 false，图集资产通常为独立可视化切片实体）
 */
@Serializable
data class NormalizedAssetItem(
    val id: String,
    val name: String,
    val category: String = "Default",
    val type: UIBlockType = UIBlockType.IMAGE,
    val bounds: SerialRect,
    val description: String? = null,
    val isPureContainer: Boolean = false
) {
    /**
     * 将标准化图元实体无缝转为项目原生的 [UIBlock] 核心领域模型。
     *
     * @param referenceImagePath 可选的原始图集或切片物理路径
     * @return 符合项目规范的 [UIBlock] 对象
     */
    fun toUIBlock(referenceImagePath: String? = null): UIBlock {
        val templateFile = referenceImagePath?.let {
            try {
                TemplateFile(it)
            } catch (e: Exception) {
                null
            }
        }
        return UIBlock(
            id = id,
            type = type,
            bounds = bounds,
            cropRect = bounds,
            isPureContainer = isPureContainer,
            referenceImage = templateFile,
            currentImageUri = templateFile,
            userPromptZh = description ?: name,
            userPromptEn = name
        )
    }
}

/**
 * 图集或外部 JSON 解析与自我同步的综合结果报告。
 *
 * @property canvasWidth 检测或计算得出的图集/画布总宽度（像素）
 * @property canvasHeight 检测或计算得出的图集/画布总高度（像素）
 * @property items 成功提取并标准化的图元列表
 * @property detectedFormat 识别出的输入 JSON 数据格式体系（如 "GOD_CUP_SPEC", "FLAT_ARRAY", "AI_GENERATED"）
 * @property warnings 解析过程中触发的自动校正、容错修复或字段缺失警告
 */
@Serializable
data class AssetSheetParseResult(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val items: List<NormalizedAssetItem>,
    val detectedFormat: String,
    val warnings: List<String> = emptyList()
)

/**
 * UI Studio 图集导入与校对过程中的本地持久化草稿。
 *
 * 用于在切片预审校对阶段实时落盘暂存，防止意外退出或重新进入时校对心血丢失。
 *
 * @property sourceImagePath 原始图集图片文件路径
 * @property sourceJsonPath 原始坐标 JSON 文件路径（可选）
 * @property canvasWidth 图集逻辑宽度
 * @property canvasHeight 图集逻辑高度
 * @property items 当前校对中的图元列表
 * @property savedAtMillis 保存时间戳（毫秒）
 */
@Serializable
data class AssetImportDraft(
    val sourceImagePath: String = "",
    val sourceJsonPath: String = "",
    val canvasWidth: Float = 1920f,
    val canvasHeight: Float = 1080f,
    val items: List<NormalizedAssetItem> = emptyList(),
    val savedAtMillis: Long = 0L
)

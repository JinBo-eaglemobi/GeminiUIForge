package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * UI Studio 专用页面模型 (UIStudioPage)
 *
 * 包含多个散落 UIStudioBlock 的单张视图环境（例如主游戏界面或 Bonus 奖励界面）。
 * 与传统 Slots 的 [UIPage] 完全独立：本模型不持有 sourceImageUri 整景参考图字段
 * （整景参考图是 Slots 工程的专属概念，UI Studio 工程无整景参考图）。
 *
 * @property id 页面的唯一标识符
 * @property nameStr 页面的标题名称
 * @property width 页面逻辑宽度
 * @property height 页面逻辑高度
 * @property canvasBackgroundColor 页面画布背景色（可空，null 时使用主题默认）
 * @property blocks 页面包含的所有子功能块列表
 */
@Serializable
data class UIStudioPage(
    val id: String,
    val nameStr: String = "Page",
    val width: Float = 1080f,
    val height: Float = 1920f,
    val canvasBackgroundColor: String? = null,
    val blocks: List<UIStudioBlock> = emptyList()
)

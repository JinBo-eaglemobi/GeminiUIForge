package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 页面模型：UI 页面 (UIPage)
 * 包含多个散落 UIBlock 的单张视图环境。例如主游戏界面或 Bonus 奖励界面。
 * @property id 页面的唯一标识符
 * @property nameStr 页面的标题名称
 * @property width 页面的逻辑宽度（与分析时的图片比例对应）
 * @property height 页面的逻辑高度
 * @property sourceImageUri 该页面所关联的原始参考图的本地归档路径
 * @property blocks 页面包含的所有子功能块列表
 */
@Serializable
data class UIPage(
    val id: String,
    val nameStr: String = "Page",
    val width: Float = 1080f,
    val height: Float = 1920f,
    val sourceImageUri: String? = null,
    val blocks: List<UIBlock> = emptyList()
)

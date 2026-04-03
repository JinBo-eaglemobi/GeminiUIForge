package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 页面模型：UI 页面 (UIPage)
 * 包含多个散落 UIBlock 的单张视图环境。例如主游戏界面或 Bonus 奖励界面。
 * @property id 页面的唯一标识符
 * @property nameStr 页面的标题名称
 * @property blocks 页面包含的所有子功能块列表
 */
@Serializable
data class UIPage(
    val id: String,
    val nameStr: String = "Page",
    val width: Float = 1080f,
    val height: Float = 1920f,
    val blocks: List<UIBlock> = emptyList()
)

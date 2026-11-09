package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * 项目级公用配置文件中引用的单页面轻量索引项
 *
 * 仅记录该页面的唯一标识、友好显示名称以及相对文件存储路径，
 * 绝不包含任何页面私有的特定宽高、画布背景色或图元树数据。
 *
 * @property id 页面唯一标识符（例如 "MainPage", "page_slots_window"）
 * @property nameStr 页面对外展示的友好标题（例如 "主界面", "老虎机滚轴组件"）
 * @property fileRelPath 相对项目目录的物理存储路径（例如 "pages/MainPage.json"）
 */
@Serializable
data class UIStudioPageIndexItem(
    val id: String,
    val nameStr: String = "Page",
    val fileRelPath: String = "pages/$id.json"
)

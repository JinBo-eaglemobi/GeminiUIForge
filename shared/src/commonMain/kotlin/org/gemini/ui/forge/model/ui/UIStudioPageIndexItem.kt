package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * 项目级公用配置文件中引用的单页面轻量索引项
 *
 * 仅记录该页面的唯一标识（对应物理存储路径恒为 pages/{id}.json），
 * 绝不包含任何页面私有的特定宽高、画布背景色或图元树数据。
 *
 * @property id 页面唯一标识符（例如 "MainPage", "slots_window"）
 */
@Serializable
data class UIStudioPageIndexItem(
    val id: String
)

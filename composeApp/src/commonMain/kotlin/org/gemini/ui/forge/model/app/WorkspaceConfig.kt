package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

/**
 * 工作区配置持久化模型。
 * 用于记录用户在特定项目下的 UI 偏好设置。
 */
@Serializable
data class WorkspaceConfig(
    /** 已折叠的属性板块标题集合，按模块 ID 区分。使用 "global" 作为未选中模块时的全局页面属性标识 */
    val collapsedSections: Map<String, Set<String>> = emptyMap(),
    /** 是否处于视觉模式 */
    val isVisualMode: Boolean = false,
    /** 是否隐藏描边 */
    val isHideOutlines: Boolean = false,
    /** 参考图显示模式 */
    val referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    /** 参考图透明度 */
    val referenceOpacity: Float = 0.4f
)

package org.gemini.ui.forge.state.app

import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 全局应用状态模型
 * 仅存放跨模块共享的核心数据：当前项目信息及全局配置快照
 */
data class AppState(
    /** 当前项目的基础数据 */
    val projectName: String = "",
    /** 当前激活的项目模板工程树状状态 */
    val project: ProjectState = ProjectState(),

    /** 应用全局配置状态 (主题、语言、导航等) */
    val globalState: AppGlobalState = AppGlobalState(),

    /** 是否有未保存的修改 */
    val isDirty: Boolean = false,
)
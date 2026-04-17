package org.gemini.ui.forge.state

import org.gemini.ui.forge.model.app.AppGlobalState
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.ProjectState

/**
 * 全局应用状态模型
 * 仅存放跨模块共享的核心数据：当前项目信息及全局配置快照
 */
data class AppState(
    /** 当前项目的基础数据 */
    val projectName: String = "",
    val project: ProjectState = ProjectState(),
    
    /** 应用全局配置状态 (主题、语言、导航等) */
    val globalState: AppGlobalState = AppGlobalState(),
    
    /** 选中的页面 ID (用于跨页跳转同步) */
    val selectedPageId: String? = null,
    
    /** 当前编辑/操作的提示词语言偏好 */
    val currentEditingPromptLang: PromptLanguage = PromptLanguage.ZH
)

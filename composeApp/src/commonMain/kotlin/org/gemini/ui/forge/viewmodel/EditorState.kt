package org.gemini.ui.forge.viewmodel

import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.domain.UIPage
import org.gemini.ui.forge.domain.UIBlock

/**
 * 编辑器页面的 UI 状态模型
 * @property globalState 应用全局状态
 * @property project 当前正在编辑的项目数据状态
 * @property projectName 项目名称
 * @property selectedPageId 当前选中的页面 ID
 * @property selectedBlockId 当前选中的 UI 组件 ID
 * @property currentEditingPromptLang 当前 UI 上正在编辑的提示词语言 (用于切换显示)
 * @property isGenerating 是否正在通过 AI 生成资源
 * @property generatedCandidates AI 生成的候选图片数据列表 (Base64)
 */
data class EditorState(
    val globalState: AppGlobalState = AppGlobalState(),
    val project: ProjectState = ProjectState(),
    val projectName: String = "Untitled",
    val selectedPageId: String? = null,
    val selectedBlockId: String? = null,
    val currentEditingPromptLang: PromptLanguage = PromptLanguage.ZH,
    val isGenerating: Boolean = false,
    val generatedCandidates: List<String> = emptyList()
) {
    /** 获取当前选中的页面对象 */
    val currentPage: UIPage?
        get() = project.pages.find { it.id == selectedPageId }
        
    /** 获取当前选中的 UI 组件对象 */
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.find { it.id == selectedBlockId }

    /** 根据设置或当前 UI 状态获取应显示的提示词文本 */
    val currentPromptText: String
        get() = selectedBlock?.let { block ->
            when (currentEditingPromptLang) {
                PromptLanguage.EN -> block.userPromptEn
                PromptLanguage.ZH -> block.userPromptZh
                else -> block.userPromptZh
            }
        } ?: ""
}

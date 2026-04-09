package org.gemini.ui.forge.viewmodel

import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.domain.UIPage
import org.gemini.ui.forge.domain.UIBlock

/**
 * 参考图显示模式
 */
enum class ReferenceDisplayMode {
    HIDDEN,   // 隐藏参考图
    SPLIT,    // 上下分屏对照
    OVERLAY   // 重叠半透明对齐
}

/**
 * 编辑器页面的 UI 状态模型
 */
data class EditorState(
    val globalState: AppGlobalState = AppGlobalState(),
    val project: ProjectState = ProjectState(),
    val projectName: String = "Untitled",
    val selectedPageId: String? = null,
    val selectedBlockId: String? = null,
    val currentEditingPromptLang: PromptLanguage = PromptLanguage.ZH,
    val isGenerating: Boolean = false,
    val generatedCandidates: List<String> = emptyList(),
    
    // 视觉对照相关状态
    val referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    val referenceOpacity: Float = 0.4f
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

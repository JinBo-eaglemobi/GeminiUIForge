package org.gemini.ui.forge.state
import org.gemini.ui.forge.model.app.AppGlobalState
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage

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
    val referenceOpacity: Float = 0.4f,

    // 层级与分组状态
    val editingGroupId: String? = null
) {
    /** 获取当前选中的页面对象 */
    val currentPage: UIPage?
        get() = project.pages.find { it.id == selectedPageId }
        
    /** 获取当前选中的 UI 组件对象 (支持深度搜索) */
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.let { findBlockById(it, selectedBlockId) }

    private fun findBlockById(blocks: List<UIBlock>, id: String?): UIBlock? {
        if (id == null) return null
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }

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

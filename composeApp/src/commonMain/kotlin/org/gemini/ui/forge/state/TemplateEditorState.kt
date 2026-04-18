package org.gemini.ui.forge.state

import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock

/**
 * 布局编辑器专用的运行时状态
 */
data class TemplateEditorState(
    /** 当前正在编辑的项目副本 */
    val project: ProjectState = ProjectState(),
    /** 项目名称 */
    val projectName: String = "",
    /** 当前选中的页面 ID */
    val selectedPageId: String? = null,
    /** 当前选中的块 ID */
    val selectedBlockId: String? = null,
    /** 正在编辑的组 ID (Isolated Mode) */
    val editingGroupId: String? = null,
    /** 是否正在执行 AI 任务 */
    val isGenerating: Boolean = false,
    /** AI 任务日志 */
    val generationLogs: List<String> = emptyList(),
    /** 是否显示 AI 任务进度对话框 */
    val showAITaskDialog: Boolean = false,
    /** AI 优化指令模板 */
    val defaultRefineInstructionUpdate: String = "",
    val defaultRefineInstructionNew: String = "",
    /** 临时保存的舞台背景颜色 (不持久化到模板中) */
    val stageBackgroundColor: String = "#2D2D2D"
) {
    val currentPage get() = project.pages.find { it.id == selectedPageId }
    val selectedBlock: UIBlock? get() = currentPage?.let { page -> findBlockById(page.blocks, selectedBlockId ?: "") }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }
}

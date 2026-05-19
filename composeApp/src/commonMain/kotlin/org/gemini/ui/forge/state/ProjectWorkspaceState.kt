package org.gemini.ui.forge.state

import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock

/**
 * 统一工作区运行时状态
 */
data class ProjectWorkspaceState(
    /** 当前编辑的项目副本 */
    val project: ProjectState = ProjectState(),
    /** 项目名称 */
    val projectName: String = "",
    /** 当前选中的页面 ID */
    val selectedPageId: String? = project.pages.firstOrNull()?.id,
    /** 当前选中的块 ID */
    val selectedBlockId: String? = null,
    /** 正在编辑的组 ID (Isolated Mode) */
    val editingGroupId: String? = null,

    /** AI 执行通用状态 */
    val isGenerating: Boolean = false,
    val generationLogs: List<String> = emptyList(),
    val currentTaskStatus: String = "",
    val showAITaskDialog: Boolean = false,
    val isGenerationLogVisible: Boolean = true,

    /** 布局编辑专用 */
    val defaultRefineInstructionUpdate: String = "",
    val defaultRefineInstructionNew: String = "",
    val showDeleteBlockConfirmation: Boolean = false,
    val pendingDeleteBlockId: String? = null,
    val chatHistories: Map<String, List<org.gemini.ui.forge.model.api.ChatMessage>> = emptyMap(),

    /** 资产生成专用 */
    val isLocalProcessing: Boolean = false,
    val isGenerateTransparent: Boolean = true,
    val isPrioritizeCloudRemoval: Boolean = false,
    val generatedCandidates: List<TemplateFile> = emptyList(),
    val isVisualMode: Boolean = false,
    val isHideOutlines: Boolean = false,
    val referenceMode: org.gemini.ui.forge.model.app.ReferenceDisplayMode = org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
    val referenceOpacity: Float = 0.4f,
    val selectedModel: GeminiModel = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW,
    val globalStyle: String = project.globalStyle,
    val referenceImageUri: TemplateFile? = project.styleReferenceUri,

    /** 批量生成相关 */
    val showBatchGenDialog: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null,
    val batchPendingConfirmBlock: UIBlock? = null,
    val activeWorkers: List<WorkerStatus> = emptyList(),

    /** 按钮多态生成相关 */
    val showButtonGenDialog: Boolean = false,
    val buttonPressedPrompt: String = "",
    val buttonDisabledPrompt: String = "",
    val buttonPressedCandidate: TemplateFile? = null,
    val buttonDisabledCandidate: TemplateFile? = null,
    val isButtonGenInProgress: Boolean = false,

    /** 转轴组件生成相关 */
    val selectedReelItemId: String? = null,

    /** 临时状态 */
    val stageBackgroundColor: String = "#2D2D2D",
    val currentLang: PromptLanguage = PromptLanguage.ZH,
    val showHistoryPanel: Boolean = false,
    val statusMessage: String = "就绪",
    val showLogViewer: Boolean = false,

    /** 历史记录快照 (Undo/Redo) */
    val undoStack: List<org.gemini.ui.forge.model.history.HistoryEntry> = emptyList(),
    val redoStack: List<org.gemini.ui.forge.model.history.HistoryEntry> = emptyList()
) {
    val currentPage get() = project.pages.find { it.id == selectedPageId }
    val selectedBlock: UIBlock?
        get() = currentPage?.let { page ->
            findBlockById(page.blocks, selectedBlockId ?: editingGroupId ?: "")
        }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }
}

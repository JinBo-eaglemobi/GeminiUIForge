package org.gemini.ui.forge.state

import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock

/**
 * 资产生成模块专用的运行时状态
 */
data class TemplateAssetGenState(
    /** 当前编辑的项目副本 */
    val project: ProjectState = ProjectState(),
    /** 项目名称 */
    val projectName: String = "",
    /** 选中的页面及块 */
    val selectedPageId: String? = project.pages.firstOrNull()?.id,
    val selectedBlockId: String? = null,
    /** 隔离编辑组 */
    val editingGroupId: String? = null,

    /** AI 生成相关 */
    val isGenerating: Boolean = false,
    val generationLogs: List<String> = emptyList(),
    val showAITaskDialog: Boolean = false,
    val isGenerationLogVisible: Boolean = false,

    /** 本地任务处理状态（如批量抠图） */
    val isLocalProcessing: Boolean = false,

    /** 生成偏好 */
    val isGenerateTransparent: Boolean = true,
    val isPrioritizeCloudRemoval: Boolean = false,

    /** 候选资产 */
    val generatedCandidates: List<TemplateFile> = emptyList(),

    /** 视觉辅助 */
    val isVisualMode: Boolean = false,

    /** 当前选中的生图模型类型 */
    val selectedModel: GeminiModel = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW,

    /** 全局生成风格定义 */
    val globalStyle: String = project.globalStyle,
    /** 参考图本地 URI (用于图生图) */
    val referenceImageUri: TemplateFile? = project.styleReferenceUri,

    /** 临时保存的舞台背景颜色 (不持久化到模板中) */
    val stageBackgroundColor: String = "#2D2D2D",

    /** 当前场景内使用的提示词语言状态 (独立维护，避免跨页面泄漏) */
    val currentLang: PromptLanguage = PromptLanguage.ZH,

    /** 批量生成相关 */
    val showBatchGenDialog: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (已完成, 总数)
    val batchPendingConfirmBlock: UIBlock? = null,
    val currentTaskStatus: String = "", // 实时单行任务状态
    val activeWorkers: List<WorkerStatus> = emptyList() // 任务 4 新增：并行工作槽位状态
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

/**
 * 并行工作线程状态模型
 */
data class WorkerStatus(
    val id: Int,             // 槽位索引
    val blockId: String = "", // 当前正在处理的模块ID
    val action: String = "",  // 当前动作 (如：生图中、抠图中)
    val info: String = "",    // 进度或数据大小信息
    val isBusy: Boolean = false,
    val isCompleted: Boolean = false
)

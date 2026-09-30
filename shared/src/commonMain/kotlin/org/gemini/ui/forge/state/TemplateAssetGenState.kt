package org.gemini.ui.forge.state

import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.findBlockById

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
    val isGenerationLogVisible: Boolean = true,

    /** 本地任务处理状态（如批量抠图） */
    val isLocalProcessing: Boolean = false,

    /** 生成偏好 */
    val isGenerateTransparent: Boolean = true,

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
    /** 批量生图总体进度 (已完成数量 to 总计划任务数) */
    val batchProgress: Pair<Int, Int>? = null,
    /** 当前待人工确认或审核的图元实体 */
    val batchPendingConfirmBlock: UIBlock? = null,
    /** 实时单行任务执行状态简述 */
    val currentTaskStatus: String = "",
    /** 并行并发工作槽位的实时状态列表 */
    val activeWorkers: List<WorkerStatus> = emptyList(),

    /** 按钮多态生成对话框相关 */
    val showButtonGenDialog: Boolean = false,
    /** 按钮按下态 (Pressed) 的自定义生图提示词 */
    val buttonPressedPrompt: String = "",
    /** 按钮禁用态 (Disabled) 的自定义生图提示词 */
    val buttonDisabledPrompt: String = "",
    /** 生成出的按钮按下态候选图片句柄 */
    val buttonPressedCandidate: TemplateFile? = null,
    /** 生成出的按钮禁用态候选图片句柄 */
    val buttonDisabledCandidate: TemplateFile? = null,
    /** 按钮多态批量生图是否正在执行中 */
    val isButtonGenInProgress: Boolean = false
) {
    /** 当前选中的页面实体，若未选中则返回 null */
    val currentPage get() = project.pages.find { it.id == selectedPageId }
    /** 当前选中的图元实体 */
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.findBlockById(selectedBlockId ?: editingGroupId ?: "")
}

/**
 * 并行工作线程/槽位状态模型 (WorkerStatus)
 *
 * @property id 槽位数字索引标识
 * @property blockId 当前槽位正在处理的图元唯一标识
 * @property action 当前正在执行的微操作简述 (如：调用大模型生图、执行本地抠图等)
 * @property info 补充诊断或传输大小信息
 * @property isBusy 该工作槽位当前是否处于繁忙计算中
 * @property isCompleted 当前分配的子任务是否已经圆满完成
 */
data class WorkerStatus(
    val id: Int,
    val blockId: String = "",
    val action: String = "",
    val info: String = "",
    val isBusy: Boolean = false,
    val isCompleted: Boolean = false
)

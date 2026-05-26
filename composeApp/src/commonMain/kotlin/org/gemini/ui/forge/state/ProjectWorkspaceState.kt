package org.gemini.ui.forge.state

import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.findBlockById

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
    /** 当前选中的块 ID 集合 */
    val selectedBlockIds: Set<String> = if (selectedBlockId != null) setOf(selectedBlockId) else emptySet(),
    /** 正在编辑的组 ID (Isolated Mode) */
    val editingGroupId: String? = null,

    /** AI 资产或布局是否正在生成中 */
    val isGenerating: Boolean = false,
    /** AI 生成过程中的实时控制台日志列表 */
    val generationLogs: List<String> = emptyList(),
    /** 当前 AI 任务的简短进度或状态描述文本 */
    val currentTaskStatus: String = "",
    /** 是否展示 AI 生成任务进度对话弹窗 */
    val showAITaskDialog: Boolean = false,
    /** 任务进度弹窗中的实时日志详情区域是否展开可见 */
    val isGenerationLogVisible: Boolean = true,

    /** 布局编辑：默认的修改/优化（Refine）Prompt 预设 */
    val defaultRefineInstructionUpdate: String = "",
    /** 布局编辑：默认的新增（New）Prompt 预设 */
    val defaultRefineInstructionNew: String = "",
    /** 是否显示删除组件块的二次确认对话框 */
    val showDeleteBlockConfirmation: Boolean = false,
    /** 待确认删除的组件块 ID */
    val pendingDeleteBlockId: String? = null,
    /** 是否展示视觉微调/重绘（Visual Refine）弹窗 */
    val showVisualRefine: Boolean = false,
    /** 正在进行视觉重绘的目标组件块 ID */
    val refineTargetId: String? = null,
    /** 是否在右侧属性栏展示参考底图配置区域 */
    val showReferenceArea: Boolean = false,
    /** 正在配置参考底图的目标组件块 ID */
    val referenceAreaTargetId: String? = null,
    /** 是否展示历史资产/多态资源绑定弹窗 */
    val showHistoricalDialog: Boolean = false,
    /** 历史弹窗中可供勾选绑定的历史生成候选图列表 */
    val historicalImages: List<TemplateFile> = emptyList(),
    /** 历史弹窗所针对的目标组件块/子属性的标识符（例如："blockId_pressed", "blockId_spin"） */
    val historicalTargetBlockId: String? = null,
    /** 针对每个组件块与 Gemini 交互的历史对话上下文缓存（Key 为 blockId） */
    val chatHistories: Map<String, List<org.gemini.ui.forge.model.api.ChatMessage>> = emptyMap(),

    /** 界面持久化配置：属性栏中各个大分类区域（如通用、位置、资产）的折叠状态缓存 */
    val collapsedSections: Map<String, Set<String>> = emptyMap(),

    /** 资产生成：是否在本地处理多模态图像编辑（如本地抠图、透明度裁剪） */
    val isLocalProcessing: Boolean = false,
    /** 资产生成：生成新资产时是否默认启用 AI 自动去背景（保留透明通道） */
    val isGenerateTransparent: Boolean = true,
    /** 资产生成：是否优先调用云端高精度抠图算法处理透明去背 */
    val isPrioritizeCloudRemoval: Boolean = false,
    /** 资产生成：最近一次生成任务产生的所有候选资产文件列表 */
    val generatedCandidates: List<TemplateFile> = emptyList(),
    /** 视觉呈现：画布舞台是否开启纯视觉展示模式（隐藏辅助线和选择手柄） */
    val isVisualMode: Boolean = false,
    /** 视觉呈现：是否完全隐藏未选中组件块的灰色/蓝色细虚线定位外轮廓 */
    val isHideOutlines: Boolean = false,
    /** 视觉呈现：参考底图的设计对比显示模式（如隐藏、分栏对比、覆盖等） */
    val referenceMode: org.gemini.ui.forge.model.app.ReferenceDisplayMode = org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
    /** 视觉呈现：覆盖显示参考底图时的不透明度 (0.0f - 1.0f) */
    val referenceOpacity: Float = 0.4f,
    /** 视觉呈现：当前工作区所选用的 Gemini API 模型版本 */
    val selectedModel: GeminiModel = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW,
    /** 视觉呈现：整个页面的全局画面提示词风格约束（Style Preset） */
    val globalStyle: String = project.globalStyle,
    /** 视觉呈现：全局风格参考底图的 Uri 资源 */
    val referenceImageUri: TemplateFile? = project.styleReferenceUri,

    /** 批量一键生成：是否展示批量资产生成和确认进度弹窗 */
    val showBatchGenDialog: Boolean = false,
    /** 批量一键生成：当前的整体任务生成进度（Pair.first 为当前已生成数，Pair.second 为总数） */
    val batchProgress: Pair<Int, Int>? = null,
    /** 批量一键生成：生成完毕等待用户点击“应用”确认保存的临时块快照 */
    val batchPendingConfirmBlock: UIBlock? = null,
    /** 批量一键生成：执行中的后台批量工作器/任务流的状态列表 */
    val activeWorkers: List<WorkerStatus> = emptyList(),

    /** 按钮多态生成：是否显示按钮专属的多态资源生成/绑定弹窗 */
    val showButtonGenDialog: Boolean = false,
    /** 按钮多态生成：多态按钮“点击态 (Pressed)”的 AI 生成提示词 */
    val buttonPressedPrompt: String = "",
    /** 按钮多态生成：多态按钮“禁用态 (Disabled)”的 AI 生成提示词 */
    val buttonDisabledPrompt: String = "",
    /** 按钮多态生成：生成出的“点击态 (Pressed)”临时资产候选图 */
    val buttonPressedCandidate: TemplateFile? = null,
    /** 按钮多态生成：生成出的“禁用态 (Disabled)”临时资产候选图 */
    val buttonDisabledCandidate: TemplateFile? = null,
    /** 按钮多态生成：按钮多态异步生成过程是否正在执行中 */
    val isButtonGenInProgress: Boolean = false,

    /** 转轴组件生成：转轴网格（Reel Grid）中当前选中的单个具体符号项 ID */
    val selectedReelItemId: String? = null,

    /** 临时状态：舞台画布编辑区域的主背景颜色（支持 Hex 输入） */
    val stageBackgroundColor: String = "#2D2D2D",
    /** 临时状态：提示词自动生成的语言偏好 */
    val currentLang: PromptLanguage = PromptLanguage.ZH,
    /** 临时状态：是否在右侧展示详细的编辑操作历史记录树面板 */
    val showHistoryPanel: Boolean = false,
    /** 临时状态：左下角/底部状态栏显示的即时就绪提示信息 */
    val statusMessage: String = "就绪",
    /** 临时状态：是否在画布底部显示高精度的底层日志控制台面板 */
    val showLogViewer: Boolean = false,
    /** 临时状态：项目多语言、资源、XML 绑定的本地配置清单绝对路径 */
    val resourceConfigPath: String? = null,
    /** 临时状态：用来触发监听并重载本地文件资源的随机刷新时间戳 */
    val resourceConfigRefreshTrigger: Long = 0L,

    /** 历史记录快照 (Undo)：撤销操作栈 */
    val undoStack: List<org.gemini.ui.forge.model.history.HistoryEntry> = emptyList(),
    /** 历史记录快照 (Redo)：重做操作栈 */
    val redoStack: List<org.gemini.ui.forge.model.history.HistoryEntry> = emptyList()
) {
    val currentPage get() = project.pages.find { it.id == selectedPageId }
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.findBlockById(selectedBlockId ?: editingGroupId ?: "")
}

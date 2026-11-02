package org.gemini.ui.forge.ui.uistudio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.assetsheet.AssetImportDraft
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.tool.ai.AiToolExecutionDispatcher
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.service.canvas.CanvasScreenshotService
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.delegate.*
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast

/**
 * UI Studio 独立工作室的视图模型。
 * 负责画布交互、视口变换矩阵、工程生命周期、素材库联动、草稿暂存自愈与切片入库。
 */
class UIStudioViewModel {

    /** 专属后台协程作用域 */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val jsonHelper = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val storage = LocalFileStorage()
    private val mattingService = LocalMattingService(storage)
    private val configManager = ConfigManager()

    /** 当前项目私有的会话与通信统一根目录 (templates/{proj}/sessions) */
    val projectSessionsDir: String? get() {
        val proj = state.project ?: return null
        val sanitized = proj.projectId.trim().replace(" ", "_")
        return "templates/$sanitized/sessions"
    }

    /** 绑定至当前项目私有路径的通信归档管理器 */
    val trafficStore = SessionTrafficStore(
        storage = storage,
        customRootDirProvider = { projectSessionsDir }
    )
    val trafficScopeId: String get() = "uistudio_${state.project?.projectId?.ifBlank { "default" } ?: "default"}"
    val chatEngine = GeminiChatEngine()
    val toolDispatcher = AiToolExecutionDispatcher()

    /** 画布多模态快照核心服务 */
    val canvasScreenshotService = CanvasScreenshotService()

    /** 界面响应式状态快照 */
    var state by mutableStateOf(UIStudioState())
        private set

    /**
     * 初始化或重新加载 UI Studio 工程。
     *
     * @param project 传入的工程对象，若为 null 则自动创建默认空白工程
     */
    fun loadProject(project: ProjectState?) {
        val effectiveProject = project ?: createDefaultBlankProject("NewUIStudioProject", 1920f, 1080f)
        val firstPage = effectiveProject.studioPages.firstOrNull()
        val initialWidth = firstPage?.width?.takeIf { it > 0 } ?: 1920f
        val initialHeight = firstPage?.height?.takeIf { it > 0 } ?: 1080f

        state = state.copy(
            project = effectiveProject,
            currentViewId = null,
            openViewIds = emptyList(),
            activeContainerId = null,
            canvasWidth = initialWidth,
            canvasHeight = initialHeight,
            scale = 1.0f,
            panOffset = Offset.Zero,
            isFitToViewport = false,
            isViewportInitialized = false,
            isDirty = false,
            selectedBlockId = null
        )

        // 异步加载各视图视口记忆缓存 (viewport_cache.json)
        viewport.loadViewportCache(effectiveProject.projectId)

        // 异步加载工作台边栏布局记忆配置 (workspace_layout.json)
        viewport.loadWorkspaceLayout(effectiveProject.projectId)

        // 异步加载素材库 (asset_library.json)
        assetLibrary.loadAssetLibrary(effectiveProject.projectId)

        // 异步检查并恢复未完成的切片校对草稿
        checkDraft(effectiveProject.projectId)

        // 异步加载外部参考文档配置 (reference_docs.json)
        aiConfig.loadExternalDocs(effectiveProject.projectId)
    }

    /**
     * 重新加载或热更新当前 UI Studio 工程模型。
     *
     * 仅更新底层的 project 状态树，保持当前打开的标签、当前视口缩放与平移偏移量不变，
     * 用于 MCP / AI 工具修改工程后无感刷新 UI 界面与画布图元渲染。
     *
     * @param updatedProject 最新的工程状态对象
     */
    fun reload(updatedProject: ProjectState) {
        val currentProj = state.project
        val newProj = if (currentProj != null && currentProj.projectId == updatedProject.projectId) {
            updatedProject
        } else {
            updatedProject
        }
        state = state.copy(
            project = newProj,
            isDirty = false
        )
    }

    /**
     * 标记当前工程或视口布局发生了变更，设置 isDirty 为 true。
     */
    fun markDirty() {
        if (!state.isDirty) {
            state = state.copy(isDirty = true)
        }
    }

    /**
     * 请求退出 UI Studio 工作台（返回大厅）。
     * 退出前强制暂存当前视口，若当前有未保存的修改或视口调整，拦截并唤起三选确认弹窗；否则直接放行。
     */
    fun requestExit(onDirectExit: () -> Unit) {
        viewport.cacheCurrentViewport()
        if (state.isDirty) {
            state = state.copy(showExitConfirmDialog = true)
        } else {
            onDirectExit()
        }
    }

    /**
     * 关闭未保存退出确认弹窗。
     */
    fun dismissExitDialog() {
        state = state.copy(showExitConfirmDialog = false)
    }

    /**
     * 检查当前项目目录下是否存在暂存的图元切片校对草稿 (import_draft.json)
     */
    fun checkDraft(projectId: String? = null) {
        val effectiveId = (projectId ?: state.project?.projectId ?: "").trim()
        if (effectiveId.isBlank()) return

        scope.launch {
            try {
                val sanitizedName = effectiveId.replace(" ", "_")
                val draftPath = "templates/$sanitizedName/import_draft.json"
                val file = TemplateFile(draftPath)
                val bytes = file.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val draft = jsonHelper.decodeFromString<AssetImportDraft>(bytes.decodeToString())
                    if (draft.items.isNotEmpty()) {
                        state = state.copy(
                            importDraft = draft,
                            isDraftPromptVisible = true
                        )
                        AppLogger.i("UIStudioViewModel", "📋 发现未完成的切片草稿: ${draft.items.size} 个图元, 图片=${draft.sourceImagePath}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "解析草稿文件失败: ${e.message}")
            }
        }
    }

    /**
     * 恢复并继续未完成的切片草稿。
     */
    fun resumeDraft() {
        val draft = state.importDraft ?: return
        state = state.copy(
            sourceImagePath = draft.sourceImagePath,
            sourceJsonPath = draft.sourceJsonPath,
            verifySliceItems = draft.items,
            isVerifySliceDialogVisible = true,
            isDraftPromptVisible = false
        )
        Toast.show("已成功恢复上次的校对草稿（共 ${draft.items.size} 个图元）", ToastType.SUCCESS)
    }

    /**
     * 丢弃并物理清除草稿文件。
     */
    fun discardDraft() {
        val proj = state.project ?: return
        scope.launch {
            try {
                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val draftPath = "templates/$sanitizedName/import_draft.json"
                TemplateFile(draftPath).delete() // 物理删除草稿文件
            } catch (_: Exception) {}
            state = state.copy(
                importDraft = null,
                isDraftPromptVisible = false
            )
            Toast.show("已清除校对草稿", ToastType.INFO)
        }
    }

    /**
     * 将当前校对图元状态持久化落盘至草稿文件 (import_draft.json)，确保中途退出心血不丢。
     */
    fun saveDraft(items: List<NormalizedAssetItem>) {
        val proj = state.project ?: return
        val currentImage = state.sourceImagePath
        if (currentImage.isBlank() || items.isEmpty()) return

        scope.launch {
            try {
                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val draft = AssetImportDraft(
                    sourceImagePath = currentImage,
                    sourceJsonPath = state.sourceJsonPath,
                    canvasWidth = state.canvasWidth,
                    canvasHeight = state.canvasHeight,
                    items = items,
                    savedAtMillis = org.gemini.ui.forge.getCurrentTimeMillis()
                )
                val jsonStr = jsonHelper.encodeToString(AssetImportDraft.serializer(), draft)
                val draftPath = "templates/$sanitizedName/import_draft.json"
                TemplateFile(draftPath).writeBytes(jsonStr.encodeToByteArray())
                AppLogger.i("UIStudioViewModel", "💾 校对草稿已自动落盘暂存 (${items.size} 个图元)")
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存草稿失败: ${e.message}")
            }
        }
    }

    /**
     * 异步保存当前 UI Studio 工程至本地磁盘。
     *
     * @param repo 模板仓储服务 [TemplateRepository]
     */
    fun saveProject(repo: TemplateRepository) {
        val proj = state.project ?: return
        if (state.isSaving) return

        state = state.copy(isSaving = true)
        scope.launch {
            try {
                repo.saveTemplate(proj.projectId, proj)
                viewport.saveViewportCache(proj.projectId)
                viewport.saveWorkspaceLayout(proj.projectId)
                state = state.copy(isDirty = false)
                Toast.show("项目保存成功", ToastType.SUCCESS)
            } catch (e: Exception) {
                Toast.show("保存失败: ${e.message}", ToastType.ERROR)
            } finally {
                state = state.copy(isSaving = false)
            }
        }
    }

    /**
     * 创建一个带指定名称和分辨率的默认空白模板工程。
     */
    private fun createDefaultBlankProject(name: String, width: Float, height: Float): ProjectState {
        return ProjectState(
            projectId = name,
            studioPages = listOf(
                UIStudioPage(
                    id = "page_main",
                    nameStr = "MainPage",
                    width = width,
                    height = height,
                    blocks = emptyList()
                )
            )
        )
    }

    // ====== Domain Delegates（领域委托组合，按域逐步拆分迁移） ======

    /**
     * 画布编辑域委托：承载图元选择与几何变换、层级重排（置顶/置底/上移/下移）、
     * 成组与解组、拖拽挂载、锁定与可见性切换、撤销重做历史快照、
     * 剪贴板操作（复制/剪切/粘贴/克隆/微调）与树结构递归工具等画布编辑核心操作。
     */
    val canvasEditor: CanvasEditorDelegate = CanvasEditorDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope
    )

    /**
     * 视口域委托：承载画布视口缩放平移手势、视口历史记忆缓存（viewport_cache.json）、
     * 工作台布局持久化（workspace_layout.json）、边栏宽度与面板高度比例、
     * 图元几何属性（缩放因数 / 不透明度）与画布窗口包围盒等视口相关操作。
     */
    val viewport: ViewportDelegate = ViewportDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        markDirty = ::markDirty,
        jsonHelper = jsonHelper,
        updateBlockInTree = canvasEditor::updateBlockInTreeRecursively
    )

    /**
     * 素材库域委托：承载素材库加载与物理磁盘自愈扫描、一键去背、素材库持久化、
     * 文件夹与素材 CRUD、哈希检索、外部统一导入管道、素材树交互（展开折叠/重命名/
     * 资源管理器定位）、素材画布挂载以及 AI 生成图片入库等素材资产管理操作。
     */
    val assetLibrary: AssetLibraryDelegate = AssetLibraryDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        jsonHelper = jsonHelper,
        storage = storage,
        mattingService = mattingService,
        canvasEditor = canvasEditor
    )

    /**
     * 视图页面域委托：承载视图界面 (Page) 全生命周期管理，包括视图标签页的
     * 打开/切换/关闭、视图新建/克隆/删除、画布尺寸调整、选中状态清空，
     * 以及 AI 聊天全景弹窗的打开/停靠侧栏/关闭等交互编排。
     */
    val pageManager: PageManagerDelegate = PageManagerDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        viewport = viewport,
        loadProjectAgentConfig = { aiConfig.loadProjectAgentConfig() },
        loadChatSessions = { chatSession.loadChatSessions() },
        refreshSkills = { aiConfig.refreshSkills(it) },
        startSkillsDirectoryWatcher = { aiConfig.startSkillsDirectoryWatcher() },
        stopSkillsDirectoryWatcher = { aiConfig.stopSkillsDirectoryWatcher() }
    )

    /**
     * 切片导入域委托：承载外部图集大图 + 坐标 JSON 导入配置弹窗、零预设自适应
     * 离线解析、Gemini 视觉大模型逆向 AI 识别、可视化预审校对弹窗流转，
     * 以及确认后的多核物理切片、批量 AI 去背与素材登记入库等全流水线操作。
     */
    val sliceImport: SliceImportDelegate = SliceImportDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        mattingService = mattingService,
        assetLibrary = assetLibrary,
        saveDraft = ::saveDraft
    )

    /**
     * 会话域委托：承载项目级 AI Chat 会话全生命周期管理，包括会话 CRUD 与
     * 私有目录持久化、加载自愈（遗留清洗/报文回填/外溢档案迁移）、删除与清空
     * 确认弹窗编排、聊天附件增删、会话搜索，以及长对话上下文压缩与设计记忆结晶。
     */
    val chatSession: ChatSessionDelegate = ChatSessionDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        jsonHelper = jsonHelper,
        trafficStore = trafficStore,
        trafficScopeIdProvider = { trafficScopeId },
        configManager = configManager,
        chatEngine = chatEngine
    )

    /**
     * 布局应用域委托：承载大模型 apply_view_layout 工具结果的三通道鲁棒提取
     * （官方 Function Call / Markdown 代码块 / 裸文本）、AI 排版方案的确认应用
     * （替换当前视图或新建独立视图）与无损撤销还原等布局决策操作。
     */
    val aiLayout: AiLayoutDelegate = AiLayoutDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        jsonHelper = jsonHelper,
        storage = storage,
        saveProject = ::saveProject
    )

    /**
     * 智能体配置域委托：承载 Agent 人设与偏好配置（模型/思考等级/通信协议切换、
     * agent_pref.json 双落盘）、技能体系（注册探测、启停持久化、目录指纹监视）、
     * 外部参考文档与 @ Mention 引用体系（历史引用登记、拖拽分流、多源候选检索）。
     */
    val aiConfig: AiConfigDelegate = AiConfigDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        jsonHelper = jsonHelper,
        storage = storage,
        regenerateChatMessage = { aiAgent.regenerateChatMessage(it) }
    )

    /**
     * AI 智能体域委托：承载 AI 对话消息发送、流式思考生成、多轮工具调用循环、
     * 用户续轮确认通道、画布多模态快照挂载、生成中止、消息版本切换/删除/重新生成
     * 等 UI Studio 核心 AI 交互能力。
     */
    val aiAgent: AiAgentDelegate = AiAgentDelegate(
        getState = { state },
        setState = { state = it },
        scope = scope,
        jsonHelper = jsonHelper,
        configManager = configManager,
        chatEngine = chatEngine,
        toolDispatcher = toolDispatcher,
        trafficStore = trafficStore,
        trafficScopeIdProvider = { trafficScopeId },
        canvasScreenshotService = canvasScreenshotService,
        assetLibrary = assetLibrary,
        chatSession = chatSession,
        aiConfig = aiConfig,
        aiLayout = aiLayout
    )
}

package org.gemini.ui.forge.ui.uistudio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import org.gemini.ui.forge.utils.looseJson
import org.gemini.ui.forge.utils.MentionFuzzyMatcher
import org.gemini.ui.forge.service.ai.ImageGenIntentDetector
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.ui.uistudio.model.AgentPreference
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.assetsheet.AssetImportDraft
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.state.MentionState
import kotlinx.coroutines.Job
import org.gemini.ui.forge.model.chat.ChatToolCallRecord
import org.gemini.ui.forge.service.ai.AiToolExecutionDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.gemini.ui.forge.getPlatform
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import org.gemini.ui.forge.manager.ExternalDocManager
import org.gemini.ui.forge.manager.ExternalDocRecord
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.service.MattingTaskItem
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.utils.calculateFileHash
import org.gemini.ui.forge.utils.calculateShortFileHash
import org.gemini.ui.forge.utils.toShortHash
import org.gemini.ui.forge.utils.copyLocalFile
import org.gemini.ui.forge.utils.listFilesRecursively
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.isSupportedImageFile
import org.gemini.ui.forge.utils.TrafficSummaryExtractor
import kotlin.random.Random
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.ai.ChatCompressionManager
import org.gemini.ui.forge.service.ai.ChatPayloadAssembler
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.service.ai.GeminiChatRequest
import org.gemini.ui.forge.service.ai.GeminiChatAttachment
import org.gemini.ui.forge.service.ai.GeminiChatHistoryItem
import org.gemini.ui.forge.service.ai.GeminiFunctionCallRecord
import org.gemini.ui.forge.service.ai.GeminiFunctionResponse
import org.gemini.ui.forge.service.ai.GeminiGenerationConfig
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.ui.uistudio.delegate.AssetLibraryDelegate
import org.gemini.ui.forge.ui.uistudio.delegate.CanvasEditorDelegate
import org.gemini.ui.forge.ui.uistudio.delegate.PageManagerDelegate
import org.gemini.ui.forge.ui.uistudio.delegate.ViewportDelegate
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.ThinkingLevel
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.service.canvas.CanvasScreenshotService
import org.gemini.ui.forge.service.canvas.CanvasSnapshotMode
import org.gemini.ui.forge.ui.component.chat.CommonChatAttachment
import org.gemini.ui.forge.ui.uistudio.model.ProjectAgentConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.calculateBlockAbsoluteBounds
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.findParentBlockId
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.updateBlockInList

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

    /** AI 输入栏「@」上下文引用状态管理中心 */
    val mentionState = MentionState()

    /** 画布多模态快照核心服务 */
    val canvasScreenshotService = CanvasScreenshotService()

    /** 当前是否正在捕获画布快照 */
    var isCapturingCanvasSnapshot by mutableStateOf(false)
        private set

    /** 达到最大轮询次数时的用户确认续轮通道 */
    private var toolLoopContinueDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    /** 用户授权继续添加轮询 */
    fun continueToolLoop() {
        toolLoopContinueDeferred?.complete(true)
    }

    /** 用户选择结束或取消轮询 */
    fun cancelToolLoop() {
        toolLoopContinueDeferred?.complete(false)
    }

    /**
     * 捕获画布快照并挂载到聊天附件
     */
    fun captureAndAttachCanvas(
        mode: CanvasSnapshotMode = CanvasSnapshotMode.OFFSCREEN_COMPOSITE,
        onSuccess: (CommonChatAttachment) -> Unit
    ) {
        val project = state.project ?: return
        val currentView = state.currentView ?: return

        scope.launch(Dispatchers.Default) {
            isCapturingCanvasSnapshot = true
            try {
                val attachment = canvasScreenshotService.captureSnapshot(
                    projectName = project.projectId,
                    currentPage = currentView,
                    windowBounds = state.canvasWindowBounds,
                    mode = mode,
                    assetPathResolver = { hash -> assetLibrary.findAssetByHash(hash)?.relativePath }
                )
                if (attachment != null) {
                    onSuccess(attachment)
                    Toast.show("📸 画布快照已挂载", ToastType.SUCCESS)
                } else {
                    Toast.show("❌ 快照生成失败", ToastType.ERROR)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "捕获画布快照失败", e)
                Toast.show("快照失败: ${e.message}", ToastType.ERROR)
            } finally {
                isCapturingCanvasSnapshot = false
            }
        }
    }

    private val disabledSkillsFile = "skills/disabled_skills.json"

    /**
     * 从本地磁盘读取被用户显式禁用的技能 ID 集合
     */
    private suspend fun loadDisabledSkillIds(): Set<String> {
        val content = storage.readFromFile(disabledSkillsFile) ?: return emptySet()
        return runCatching {
            looseJson.decodeFromString<Set<String>>(content)
        }.getOrDefault(emptySet())
    }

    /**
     * 将被用户显式禁用的技能 ID 集合持久化落盘
     */
    private suspend fun saveDisabledSkillIds(disabledIds: Set<String>) {
        try {
            val jsonStr = looseJson.encodeToString(disabledIds)
            storage.saveToFile(disabledSkillsFile, jsonStr)
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "持久化禁用技能配置失败: ${e.message}")
        }
    }

    init {
        scope.launch(Dispatchers.Default) {
            val skills = UiStudioSkillManager.getRegisteredSkills(bypassCache = true)
            allSkills = skills
            val disabledIds = loadDisabledSkillIds()
            enabledSkillIds = skills
                .filter { it.id !in disabledIds && it.isSkillEnabled }
                .map { it.id }
                .toSet()
        }
    }

    /** 当前工程引用的外部参考规范文档清单 */
    var registeredExternalDocs by mutableStateOf<List<ExternalDocRecord>>(emptyList())
        private set

    /** 所有已探测并注册的 UI Studio 技能清单 */
    var allSkills by mutableStateOf<List<UiStudioSkill>>(emptyList())
        private set

    /** 当前活跃的 AI 流式生成后台作业，用于支持实时中止 */
    private var activeChatJob: Job? = null

    /** 当前处于启用/加载状态的技能 ID 集合（默认全启用） */
    var enabledSkillIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 切换单个技能的加载状态 */
    fun toggleSkill(skillId: String, isEnabled: Boolean) {
        enabledSkillIds = if (isEnabled) {
            enabledSkillIds + skillId
        } else {
            enabledSkillIds - skillId
        }
        val disabledIds = allSkills.map { it.id }.filterNot { it in enabledSkillIds }.toSet()
        scope.launch(Dispatchers.Default) {
            saveDisabledSkillIds(disabledIds)
        }
        val targetSkill = allSkills.find { it.id == skillId }
        val skillName = targetSkill?.name ?: skillId
        if (isEnabled) {
            Toast.show("已加载技能: $skillName", ToastType.INFO)
        } else {
            Toast.show("已停用技能: $skillName", ToastType.INFO)
        }
    }

    /** 当前激活的技能模式 */
    var skillMode by mutableStateOf(SkillActivationMode.AUTO)

    /** 当前智能判定的激活技能列表 */
    val activeSkills: List<UiStudioSkill>
        get() = UiStudioSkillManager.resolveActiveSkillsSync(
            prompt = mentionState.searchQuery,
            attachedMentions = mentionState.attachedMentions,
            mode = skillMode,
            enabledSkillIds = enabledSkillIds
        )

    /**
     * 异步加载当前工程关联的外部参考文档
     */
    fun loadExternalDocs(projectId: String) {
        scope.launch(Dispatchers.Default) {
            registeredExternalDocs = ExternalDocManager.getDocsForProject(projectId)
        }
    }

    /**
     * 正式登记外部参考文档到当前工程历史引用库
     */
    fun registerExternalDoc(filePath: String) {
        val projId = state.project?.projectId ?: "default"
        scope.launch(Dispatchers.Default) {
            val fileName = filePath.substringAfterLast('\\').substringAfterLast('/')
            ExternalDocManager.registerDoc(
                projectName = projId,
                fileName = fileName,
                absolutePath = filePath
            )
            registeredExternalDocs = ExternalDocManager.getDocsForProject(projId)
        }
    }

    /**
     * 从当前工程历史引用库中移除指定的外部参考文档
     */
    fun deleteExternalDoc(filePath: String) {
        val projId = state.project?.projectId ?: "default"
        scope.launch(Dispatchers.Default) {
            val fileName = filePath.substringAfterLast('\\').substringAfterLast('/')
            ExternalDocManager.removeDocByPath(
                projectName = projId,
                path = filePath
            )
            registeredExternalDocs = ExternalDocManager.getDocsForProject(projId)
            Toast.show("已清除历史引用: $fileName", ToastType.INFO)
        }
    }

    /**
     * 处理拖入文件的分流与自动挂载：
     * 1. 图片格式切片（png/jpg/webp 等）已由输入栏内部自动挂载为多模态附件；
     * 2. 规范与文档格式（md/txt/json/yaml 等）在此作为待发送的参考文档在输入栏挂载为 @ 药丸（暂不写入历史库，消息发送时才正式沉淀）。
     */
    fun handleFileDropped(filePath: String) {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        val isImage = ext in setOf("png", "jpg", "jpeg", "webp")
        if (!isImage) {
            val fileName = filePath.substringAfterLast('\\').substringAfterLast('/')
            mentionState.attachMention(
                MentionItem(
                    id = "doc_$filePath",
                    title = fileName,
                    subtitle = filePath,
                    category = MentionCategory.DOC,
                    icon = Icons.Default.Description,
                    payload = MentionPayload.DocPayload(
                        fileName = fileName,
                        absolutePath = filePath
                    )
                )
            )
            Toast.show("已挂载参考文档: $fileName", ToastType.INFO)
        }
    }

    /**
     * 获取当前工程中所有可供「@」引用的候选实体列表，支持根据关键词即时过滤
     */
    fun getMentionCandidates(query: String = ""): List<MentionItem> {
        val candidates = mutableListOf<MentionItem>()
        val trimmed = query.trim()
        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val projId = state.project?.projectId ?: "default"

        if (registeredExternalDocs.isEmpty()) {
            loadExternalDocs(projId)
        }

        // 1. 历史引用 (Recent References) - 提升为最高第 1 优先级置顶展示，默认最多显示最近引用的 5 项
        if (registeredExternalDocs.isEmpty()) {
            loadExternalDocs(projId)
        }
        val allDocs = registeredExternalDocs.sortedByDescending { it.lastReferencedAt }

        val displayDocs = if (tokens.isEmpty()) {
            allDocs.take(5)
        } else {
            allDocs.filter { doc ->
                MentionFuzzyMatcher.matchTokens(tokens, doc.fileName, doc.absolutePath) != null
            }
        }

        displayDocs.forEach { doc ->
            candidates.add(
                MentionItem(
                    id = "doc_${doc.absolutePath}",
                    title = doc.fileName,
                    subtitle = doc.absolutePath,
                    category = MentionCategory.DOC,
                    icon = Icons.Default.Description,
                    payload = MentionPayload.DocPayload(
                        fileName = doc.fileName,
                        absolutePath = doc.absolutePath
                    )
                )
            )
        }

        // 2. 素材库切片资产 (Assets) - 拼装首要原材料
        if (state.assetLibrary.items.isNotEmpty()) {
            candidates.add(
                MentionItem(
                    id = "all_assets_manifest",
                    title = "📦 全部素材库清单 (All Assets - 共 ${state.assetLibrary.items.size} 项)",
                    subtitle = "轻量结构化索引 · 包含全部切片名、物理宽高与智能角色标注",
                    category = MentionCategory.ASSET,
                    icon = Icons.Default.Inventory2,
                    payload = MentionPayload.AssetPayload(
                        assetId = "all_assets",
                        fileName = "ALL_ASSETS_MANIFEST",
                        filePath = "ALL",
                        width = state.assetLibrary.items.size,
                        height = 0
                    )
                )
            )
        }

        state.assetLibrary.items.forEach { asset ->
            val role = AssetManifestHelper.inferAssetRole(asset.name, asset.relativePath)
            candidates.add(
                MentionItem(
                    id = "asset_${asset.id}",
                    title = asset.name,
                    subtitle = "尺寸: ${asset.width}x${asset.height} · 角色: $role",
                    category = MentionCategory.ASSET,
                    icon = Icons.Default.Image,
                    previewImagePath = asset.relativePath,
                    payload = MentionPayload.AssetPayload(
                        assetId = asset.id,
                        fileName = asset.name,
                        filePath = asset.relativePath,
                        width = asset.width,
                        height = asset.height
                    )
                )
            )
        }

        // 3. 视图界面 (Views)
        state.project?.studioPages?.forEach { page ->
            candidates.add(
                MentionItem(
                    id = "view_${page.id}",
                    title = page.nameStr,
                    subtitle = "${page.width.toInt()}x${page.height.toInt()} · ${page.blocks.size} 个图元",
                    category = MentionCategory.VIEW,
                    icon = Icons.Default.AspectRatio,
                    payload = MentionPayload.ViewPayload(
                        viewId = page.id,
                        viewName = page.nameStr,
                        width = page.width.toInt(),
                        height = page.height.toInt(),
                        blockCount = page.blocks.size
                    )
                )
            )
        }

        // 4. 全工程所有页面图层图元 (Blocks - 优先排列当前活跃视图，随后遍历所有视图)
        val currentViewId = state.currentView?.id
        val allPages = state.project?.studioPages ?: listOfNotNull(state.currentView)
        val sortedPages = allPages.sortedByDescending { it.id == currentViewId }

        fun collectBlocks(pageId: String, pageName: String, blocks: List<UIStudioBlock>) {
            val isCurrent = pageId == currentViewId
            val viewPrefix = if (isCurrent) "视图: $pageName (当前)" else "视图: $pageName"
            blocks.forEach { block ->
                val w = block.bounds.width.toInt()
                val h = block.bounds.height.toInt()
                candidates.add(
                    MentionItem(
                        id = "block_${pageId}_${block.id}",
                        title = block.id,
                        subtitle = "$viewPrefix · 类型: ${block.type.name} · 尺寸: ${w}x${h}",
                        category = MentionCategory.BLOCK,
                        icon = Icons.Default.Layers,
                        payload = MentionPayload.BlockPayload(
                            blockId = block.id,
                            blockName = block.id,
                            type = block.type.name,
                            bounds = "",
                            width = w,
                            height = h,
                            isContainer = block.isPureContainer,
                            viewId = pageId,
                            viewName = pageName
                        )
                    )
                )
                collectBlocks(pageId, pageName, block.children)
            }
        }
        sortedPages.forEach { page ->
            collectBlocks(page.id, page.nameStr, page.blocks)
        }

        if (tokens.isEmpty()) return candidates
        return candidates.mapNotNull { item ->
            MentionFuzzyMatcher.matchTokens(tokens, item.title, item.subtitle)?.let { (score, _) ->
                item to score
            }
        }
            .sortedWith(compareByDescending<Pair<MentionItem, Int>> { it.second })
            .map { it.first }
    }

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
        loadExternalDocs(effectiveProject.projectId)
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

    private var skillsWatcherJob: Job? = null

    /**
     * 重新扫描技能目录，并自动执行失活技能 ID 清除与状态对齐
     */
    fun refreshSkills(userInitiated: Boolean = false) {
        scope.launch(Dispatchers.Default) {
            val newSkills = UiStudioSkillManager.getRegisteredSkills(bypassCache = true)
            allSkills = newSkills
            val validIds = newSkills.map { it.id }.toSet()
            val disabledIds = loadDisabledSkillIds()
            enabledSkillIds = newSkills
                .filter { it.id in validIds && it.id !in disabledIds && it.isSkillEnabled }
                .map { it.id }
                .toSet()
            if (userInitiated) {
                Toast.show("已重新扫描技能目录 (共 ${newSkills.size} 个技能)", ToastType.INFO)
            }
        }
    }

    /**
     * 在本地系统文件管理器中打开外部技能配置目录
     */
    fun openSkillsDirectory() {
        scope.launch(Dispatchers.Default) {
            val path = storage.getFilePath("skills")
            getPlatform().openInFileExplorer(path)
        }
    }

    private suspend fun computeSkillsDirectoryFingerprint(): String {
        return try {
            val fileNames = storage.listFiles("skills").sorted()
            if (fileNames.isEmpty()) return "empty"
            val sb = StringBuilder()
            for (name in fileNames) {
                val hash = storage.readFromFile("skills/$name")?.hashCode() ?: 0
                sb.append(name).append(':').append(hash).append(';')
            }
            sb.toString()
        } catch (_: Exception) {
            "error"
        }
    }

    /**
     * 启动针对外部技能目录的动态变动感知监视循环
     */
    private fun startSkillsDirectoryWatcher() {
        skillsWatcherJob?.cancel()
        skillsWatcherJob = scope.launch(Dispatchers.Default) {
            var lastFingerprint = computeSkillsDirectoryFingerprint()
            while (isActive) {
                delay(2500L) // 2.5 秒轻量检测
                val currentFingerprint = computeSkillsDirectoryFingerprint()
                if (currentFingerprint != lastFingerprint) {
                    lastFingerprint = currentFingerprint
                    val newSkills = UiStudioSkillManager.getRegisteredSkills(bypassCache = true)
                    allSkills = newSkills
                    val validIds = newSkills.map { it.id }.toSet()
                    val disabledIds = loadDisabledSkillIds()
                    enabledSkillIds = newSkills
                        .filter { it.id in validIds && it.id !in disabledIds && it.isSkillEnabled }
                        .map { it.id }
                        .toSet()
                    Toast.show("检测到外部技能文件变动，已自动同步", ToastType.INFO)
                }
            }
        }
    }

    private fun stopSkillsDirectoryWatcher() {
        skillsWatcherJob?.cancel()
        skillsWatcherJob = null
    }

    fun openSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = true)
    }

    fun closeSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = false)
    }

    fun requestDeleteChatSession(session: VisualChatSession) {
        state = state.copy(deletingSession = session)
    }

    fun cancelDeleteChatSession() {
        state = state.copy(deletingSession = null)
    }

    fun confirmDeleteChatSession() {
        val target = state.deletingSession ?: return
        state = state.copy(deletingSession = null)
        deleteChatSession(target.id)
    }

    fun requestClearChatMessages() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
        clearCurrentChatSessionMessages()
    }

    fun addChatAttachment(path: String) {
        if (path.isNotBlank() && !state.aiChatAttachments.contains(path)) {
            state = state.copy(aiChatAttachments = state.aiChatAttachments + path)
        }
    }

    fun removeChatAttachment(path: String) {
        state = state.copy(aiChatAttachments = state.aiChatAttachments - path)
    }

    fun clearChatHistory() {
        requestClearChatMessages()
    }

    fun loadProjectAgentConfig() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val legacyPath = "templates/$sanitized/agent_config.json"
        scope.launch(Dispatchers.Default) {
            try {
                // 1. 系统人设统一从全局 Markdown 文件 prompts/ui_studio_agent_prompt.md 加载
                val globalPrompt = PromptManager.getUiStudioAgentPrompt()
                if (globalPrompt.isNotBlank()) {
                    state = state.copy(
                        projectAgentConfig = state.projectAgentConfig.copy(systemPrompt = globalPrompt)
                    )
                }

                // 2. 加载用户偏好配置 (agent_pref.json)，项目私有优先，缺省回退全局默认偏好
                var pref: AgentPreference? = null
                val projPrefFile = TemplateFile("templates/$sanitized/agent_pref.json")
                if (projPrefFile.exists()) {
                    val bytes = projPrefFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        pref = jsonHelper.decodeFromString(AgentPreference.serializer(), bytes.decodeToString())
                    }
                }
                if (pref == null) {
                    val globalPrefFile = TemplateFile("preferences/agent_pref.json")
                    if (globalPrefFile.exists()) {
                        val bytes = globalPrefFile.readBytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            pref = jsonHelper.decodeFromString(AgentPreference.serializer(), bytes.decodeToString())
                        }
                    }
                }
                if (pref != null) {
                    state = state.copy(
                        projectAgentConfig = state.projectAgentConfig.copy(
                            modelName = pref.modelName,
                            thinkingLevel = pref.thinkingLevel,
                            apiFlavor = pref.apiFlavor,
                            temperature = pref.temperature
                        )
                    )
                    AppLogger.i("UIStudioViewModel", "✨ 已成功恢复智能设计助理偏好: model=${pref.modelName}, thinking=${pref.thinkingLevel}, flavor=${pref.apiFlavor}")
                }

                // 3. 无感平滑清理：检测并安全删除单项目目录下遗留的旧版 agent_config.json
                val legacyFile = TemplateFile(legacyPath)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    AppLogger.i("UIStudioViewModel", "🧹 已清理项目私有遗留配置文件: $legacyPath")
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载全局 Agent 人设配置或用户偏好失败", e)
            }
        }
    }

    private fun saveAgentPreferenceAsync() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val pref = AgentPreference(
            modelName = state.projectAgentConfig.modelName,
            thinkingLevel = state.projectAgentConfig.thinkingLevel,
            apiFlavor = state.projectAgentConfig.apiFlavor,
            temperature = state.projectAgentConfig.temperature
        )
        scope.launch(Dispatchers.Default) {
            try {
                val jsonStr = jsonHelper.encodeToString(AgentPreference.serializer(), pref)
                val bytes = jsonStr.encodeToByteArray()
                TemplateFile("templates/$sanitized/agent_pref.json").writeBytes(bytes)
                TemplateFile("preferences/agent_pref.json").writeBytes(bytes)
                AppLogger.d("UIStudioViewModel", "💾 已持久化智能设计助理用户偏好: model=${pref.modelName}, thinking=${pref.thinkingLevel}, flavor=${pref.apiFlavor}")
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "持久化智能设计助理偏好失败", e)
            }
        }
    }

    fun updateProjectAgentConfig(config: ProjectAgentConfig) {
        saveProjectAgentConfig(config)
    }

    fun updateProjectModel(modelName: String) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(modelName = modelName))
        saveAgentPreferenceAsync()
        Toast.show("已切换模型: $modelName", ToastType.SUCCESS)
    }

    fun updateProjectModel(model: GeminiModel) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(modelName = model.modelName))
        saveAgentPreferenceAsync()
        Toast.show("已切换模型: ${model.displayName}", ToastType.SUCCESS)
    }

    fun updateProjectSystemPrompt(systemPrompt: String) {
        val updated = state.projectAgentConfig.copy(systemPrompt = systemPrompt)
        saveProjectAgentConfig(updated, notifyUser = true)
    }

    fun updateProjectApiFlavor(apiFlavor: ApiFlavor) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(apiFlavor = apiFlavor))
        saveAgentPreferenceAsync()
        Toast.show("已切换通信协议: ${apiFlavor.displayNameZh}", ToastType.SUCCESS)
    }

    fun updateProjectThinkingLevel(level: ThinkingLevel) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(thinkingLevel = level))
        saveAgentPreferenceAsync()
        Toast.show("思考等级: ${level.displayName}", ToastType.SUCCESS)
    }

    /**
     * 智能自愈：平滑降级至 Gemini 3.8 Flash 模型并重试生成
     */
    fun fallbackToFlashAndRetry(message: VisualChatMessage) {
        updateProjectModel(GeminiModel.GEMINI_3_8_FLASH)
        regenerateChatMessage(message.id)
    }

    /**
     * 智能自愈：平滑降级至标准 GenerateContent 协议并重试生成
     */
    fun fallbackProtocolAndRetry(message: VisualChatMessage) {
        updateProjectApiFlavor(ApiFlavor.GENERATE_CONTENT)
        regenerateChatMessage(message.id)
    }

    fun saveProjectAgentConfig(config: ProjectAgentConfig, notifyUser: Boolean = true) {
        state = state.copy(projectAgentConfig = config)
        scope.launch(Dispatchers.Default) {
            try {
                // 系统人设统一持久化到全局 Markdown 文件 (ui_studio_agent_prompt.md)
                val isSuccess = PromptManager.saveUiStudioAgentPrompt(config.systemPrompt)
                if (notifyUser) {
                    if (isSuccess) {
                        Toast.show("智能设计助理全局人设已更新", ToastType.SUCCESS)
                    } else {
                        Toast.show("保存人设配置失败", ToastType.ERROR)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存全局 Agent 人设失败", e)
            }
        }
    }

    // ====== 项目专属会话存储与持久化 (templates/{proj}/sessions/{sessionId}.json) ======

    fun loadChatSessions() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val sessionsDirRel = "templates/$sanitized/sessions"
        scope.launch(Dispatchers.Default) {
            try {
                // 1. 自动自愈迁移历史遗留在全局根目录 sessions/uistudio_xxx 的外溢通信档案至项目私有目录
                migrateLegacyGlobalTraffic(sanitized)

                val root = org.gemini.ui.forge.utils.GlobalAppEnv.currentRootPath.trimEnd('/', '\\')
                val fullDirPath = "$root/$sessionsDirRel"
                val files = org.gemini.ui.forge.utils.listFilesInLocalDirectory(fullDirPath)
                val loadedSessions = files.filter { it.endsWith(".json", ignoreCase = true) }.mapNotNull { absPath ->
                    try {
                        val fileName = absPath.substringAfterLast("/").substringAfterLast("\\")
                        val sessionId = fileName.removeSuffix(".json")
                        val sessionFile = TemplateFile("$sessionsDirRel/$sessionId.json")
                        val text = sessionFile.readBytes()?.decodeToString()
                        if (!text.isNullOrBlank()) {
                            jsonHelper.decodeFromString(VisualChatSession.serializer(), text)
                        } else null
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "加载单个会话文件失败: $absPath", e)
                        null
                    }
                }.sortedByDescending { it.updatedAt }

                if (loadedSessions.isNotEmpty()) {
                    val healedSessions = healSessionErrors(loadedSessions)
                    val activeId = state.currentChatSessionId ?: healedSessions.first().id
                    state = state.copy(
                        chatSessions = healedSessions,
                        currentChatSessionId = activeId
                    )
                    return@launch
                }

                // 首次进入无会话，生成默认初始会话并持久化
                val now = getCurrentTimeMillis()
                val initialSession = VisualChatSession(
                    id = "session_${now}",
                    scopeId = sanitized,
                    title = "主界面架构与排版规划",
                    messages = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )
                val newSessions = listOf(initialSession)
                state = state.copy(
                    chatSessions = newSessions,
                    currentChatSessionId = initialSession.id
                )
                saveSession(initialSession)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载项目 Chat 会话失败", e)
            }
        }
    }

    fun saveSession(session: VisualChatSession) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/${session.id}.json"
        scope.launch(Dispatchers.Default) {
            try {
                val text = jsonHelper.encodeToString(VisualChatSession.serializer(), session)
                TemplateFile(path).writeBytes(text.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存会话文件失败: ${session.id}", e)
            }
        }
    }

    private suspend fun healSessionErrors(sessions: List<VisualChatSession>): List<VisualChatSession> {
        return sessions.map { session ->
            // 若该会话仅有 1 条消息且为历史生成的欢迎消息，将其过滤清洗为纯净空会话
            val isSingleLegacyWelcome = session.messages.size == 1 &&
                    session.messages.first().let { it.id.contains("welcome") || it.prompt.contains("已开启新的排版讨论会话") || it.prompt.contains("UI Studio 智能设计助理") || it.prompt.contains("UI Studio 智能排版设计助理") }
            if (isSingleLegacyWelcome) {
                val cleaned = session.copy(messages = emptyList())
                saveSession(cleaned)
                return@map cleaned
            }

            val hasUnresolvedError = session.messages.any { msg ->
                msg.role == "model" && (msg.effectiveText.isBlank() || msg.isError) && msg.errorMessage.isNullOrBlank()
            }
            if (!hasUnresolvedError) return@map session

            val records = trafficStore.listRecords(trafficScopeId, session.id)
            val respErrors = records.filter { it.direction == TrafficDirection.RESP }.mapNotNull { r ->
                try {
                    val root = jsonHelper.parseToJsonElement(r.body).jsonObject
                    val errObj = root["error"]?.jsonObject
                    if (errObj != null) {
                        val msg = errObj["message"]?.jsonPrimitive?.contentOrNull ?: "未知服务端错误"
                        val code = errObj["code"]?.jsonPrimitive?.contentOrNull ?: errObj["status"]?.jsonPrimitive?.contentOrNull
                        val formatted = if (!code.isNullOrBlank()) "[$code] $msg" else msg
                        r.timestamp to formatted
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            if (respErrors.isEmpty()) return@map session

            var sessionHealed = false
            val updatedMessages = session.messages.map { msg ->
                if (msg.role == "model" && (msg.effectiveText.isBlank() || msg.isError) && msg.errorMessage.isNullOrBlank()) {
                    val matchedErr = respErrors.minByOrNull { kotlin.math.abs(it.first - msg.timestamp) }?.second
                    if (matchedErr != null) {
                        sessionHealed = true
                        msg.copy(isError = true, errorMessage = matchedErr)
                    } else msg
                } else msg
            }

            val healedSession = if (sessionHealed) session.copy(messages = updatedMessages) else session
            if (sessionHealed) {
                saveSession(healedSession)
            }
            healedSession
        }
    }

    /**
     * 自愈迁移历史遗留在全局根目录 sessions/uistudio_{projectId} 下的外溢通信档案至项目私有目录。
     */
    private suspend fun migrateLegacyGlobalTraffic(sanitizedProjId: String) {
        val legacyScopeId = "uistudio_$sanitizedProjId"
        val targetRootDir = "templates/$sanitizedProjId/sessions"
        try {
            trafficStore.migrateLegacyTraffic(legacyScopeId, targetRootDir)
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "自愈迁移全局遗留通信报文失败: ${e.message}")
        }
    }

    fun createNewChatSession(title: String = "新布局讨论") {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val now = getCurrentTimeMillis()
        val newSession = VisualChatSession(
            id = "session_${now}",
            scopeId = sanitized,
            title = title,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val updatedSessions = listOf(newSession) + state.chatSessions
        state = state.copy(
            chatSessions = updatedSessions,
            currentChatSessionId = newSession.id
        )
        saveSession(newSession)
        Toast.show("已创建新会话: $title", ToastType.SUCCESS)
    }

    fun selectChatSession(sessionId: String) {
        state = state.copy(currentChatSessionId = sessionId)
    }

    fun deleteChatSession(sessionId: String) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/$sessionId.json"
        scope.launch(Dispatchers.Default) {
            try {
                TemplateFile(path).delete()
                // 同步彻底清理该会话在项目目录内的通信报文归档 (traffic/)
                trafficStore.deleteAll(trafficScopeId, sessionId)
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "删除会话文件失败: $sessionId", e)
            }
        }

        val remaining = state.chatSessions.filter { it.id != sessionId }
        val newSessions = if (remaining.isEmpty()) {
            val now = getCurrentTimeMillis()
            val initial = VisualChatSession(
                id = "session_${now}",
                scopeId = sanitized,
                title = "主界面排版讨论",
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
            saveSession(initial)
            listOf(initial)
        } else remaining
        val nextActiveId = if (state.currentChatSessionId == sessionId) newSessions.first().id else state.currentChatSessionId
        state = state.copy(
            chatSessions = newSessions,
            currentChatSessionId = nextActiveId
        )
        Toast.show("会话已删除", ToastType.INFO)
    }

    fun clearCurrentChatSessionMessages() {
        val activeSession = state.currentChatSession ?: return
        val updated = activeSession.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        val updatedSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        state = state.copy(chatSessions = updatedSessions)
        saveSession(updated)
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }

    fun updateChatSearchQuery(query: String) {
        state = state.copy(chatSearchQuery = query)
    }

    fun sendChatMessage(
        userText: String,
        attachmentsList: List<org.gemini.ui.forge.ui.component.chat.CommonChatAttachment> = emptyList(),
        apiKey: String = ""
    ) {
        if (userText.isBlank() && attachmentsList.isEmpty()) return
        val activeSession = state.currentChatSession ?: run {
            createNewChatSession()
            state.currentChatSession!!
        }

        val rawAttachments = if (attachmentsList.isNotEmpty()) {
            attachmentsList.mapNotNull { it.localUri }
        } else {
            state.aiChatAttachments
        }
        val now = getCurrentTimeMillis()
        val modelDisplayName = GeminiModel.fromModelName(state.projectAgentConfig.modelName)?.displayName
            ?: state.projectAgentConfig.modelName.ifBlank { GeminiModel.GEMINI_3_8_FLASH.displayName }

        val attachedMentionsList = mentionState.attachedMentions.toList()

        val mentionTags = attachedMentionsList.map {
            val resolvedPath = when (val p = it.payload) {
                is MentionPayload.DocPayload -> p.absolutePath
                is MentionPayload.AssetPayload -> if (it.id == "all_assets_manifest") "all_assets_manifest" else p.filePath
                is MentionPayload.ViewPayload -> p.viewId
                is MentionPayload.BlockPayload -> p.blockId
            }
            org.gemini.ui.forge.model.chat.ChatMentionTag(
                title = it.title,
                categoryName = it.category.name,
                path = resolvedPath
            )
        }

        val userMsg = VisualChatMessage(
            id = "msg_${now}_user",
            role = "user",
            prompt = userText,
            attachedMentions = mentionTags,
            inputImageUris = rawAttachments,
            timestamp = now,
            modelName = modelDisplayName
        )

        val updatedMessages = activeSession.messages + userMsg
        val updatedSession = activeSession.copy(
            messages = updatedMessages,
            title = if (activeSession.messages.isEmpty() || activeSession.title == "新布局讨论") userText.take(16) else activeSession.title,
            updatedAt = now
        )
        val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }

        state = state.copy(
            chatSessions = updatedSessions,
            aiChatAttachments = emptyList(),
            isAiChatSending = true
        )
        saveSession(updatedSession)

        activeChatJob = scope.launch(Dispatchers.Default) {
            try {
                // 若存在无 localUri 仅有 bytes 的内存附件，协程内异步落盘并补齐持久化 URI
                if (attachmentsList.any { it.localUri.isNullOrBlank() && it.bytes != null && it.bytes.isNotEmpty() }) {
                    val proj = state.project
                    val sanitized = proj?.projectId?.trim()?.replace(" ", "_") ?: "default"
                    val tempDir = "templates/$sanitized/chat_attachments"
                    val persistedUris = attachmentsList.mapNotNull { attach ->
                        if (!attach.localUri.isNullOrBlank()) {
                            attach.localUri
                        } else if (attach.bytes != null && attach.bytes.isNotEmpty()) {
                            try {
                                val fileName = attach.name.ifBlank { "paste_${getCurrentTimeMillis()}.png" }
                                val fileRelPath = "$tempDir/$fileName"
                                val file = TemplateFile(fileRelPath)
                                file.writeBytes(attach.bytes)
                                file.getAbsolutePath()
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }
                    if (persistedUris.isNotEmpty()) {
                        val correctedUserMsg = userMsg.copy(inputImageUris = persistedUris)
                        val correctedMessages = updatedSession.messages.map { if (it.id == userMsg.id) correctedUserMsg else it }
                        val correctedSession = updatedSession.copy(messages = correctedMessages)
                        state = state.copy(chatSessions = state.chatSessions.map { if (it.id == correctedSession.id) correctedSession else it })
                        saveSession(correctedSession)
                    }
                }
                val effectiveApiKey = apiKey.ifBlank {
                    configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey() ?: ""
                }

                if (effectiveApiKey.isBlank()) {
                    val errorMsg = "⚠️ 尚未检测到有效 Gemini API Key。\n请点击主界面右上角「设置」并在「AI 模型与网络」中配置您的 Gemini API Key，或在系统环境变量中设置 GEMINI_API_KEY。"
                    Toast.show("未配置 Gemini API Key，无法发起真实网络请求", ToastType.ERROR)
                    val assistantMsg = VisualChatMessage(
                        id = "msg_${getCurrentTimeMillis()}_assistant",
                        role = "model",
                        prompt = errorMsg,
                        timestamp = getCurrentTimeMillis()
                    )
                    val sessionAfterReply = (state.chatSessions.find { it.id == updatedSession.id } ?: updatedSession).let { cur ->
                        cur.copy(
                            messages = cur.messages + assistantMsg,
                            updatedAt = getCurrentTimeMillis()
                        )
                    }
                    val finalSessions = state.chatSessions.map { if (it.id == sessionAfterReply.id) sessionAfterReply else it }
                    state = state.copy(chatSessions = finalSessions, isAiChatSending = false)
                    saveSession(sessionAfterReply)
                    return@launch
                }

                val apiFlavor = state.projectAgentConfig.apiFlavor
                val systemPrompt = state.projectAgentConfig.systemPrompt.ifBlank {
                    PromptManager.getUiStudioAgentPrompt()
                }
                val selectedModel = state.projectAgentConfig.modelName.ifBlank { GeminiModel.GEMINI_3_8_FLASH.modelName }

                val preferCloud = attachmentsList.any { it.preferCloudUpload }
                val chatAttachments = attachmentsList.map { attach ->
                    GeminiChatAttachment(
                        name = attach.name,
                        localUri = attach.localUri,
                        bytes = attach.bytes,
                        mimeType = attach.mimeType
                    )
                }.toMutableList()

                // 若用户引用了切片素材，提取其本地文件以附件发送；若引用了文档且尚未填充内容，则预读文档
                val processedMentions = attachedMentionsList.map { mention ->
                    when (val p = mention.payload) {
                        is MentionPayload.DocPayload -> {
                            if (p.content.isNullOrBlank() && org.gemini.ui.forge.data.isFileExistsInternal(p.absolutePath)) {
                                val content = try {
                                    readLocalFileBytes(p.absolutePath)?.decodeToString() ?: ""
                                } catch (_: Exception) { "" }
                                mention.copy(payload = p.copy(content = content))
                            } else {
                                mention
                            }
                        }
                        else -> mention
                    }
                }

                if (attachedMentionsList.isNotEmpty()) {
                    for (mention in attachedMentionsList) {
                        when (val p = mention.payload) {
                            is MentionPayload.AssetPayload -> {
                                if (p.fileName != "ALL_ASSETS_MANIFEST") {
                                    try {
                                        val assetBytes = readLocalFileBytes(p.filePath)
                                        if (assetBytes != null && assetBytes.isNotEmpty()) {
                                            val mime = when {
                                                p.filePath.endsWith(".png", true) -> "image/png"
                                                p.filePath.endsWith(".jpg", true) || p.filePath.endsWith(".jpeg", true) -> "image/jpeg"
                                                p.filePath.endsWith(".webp", true) -> "image/webp"
                                                else -> "image/png"
                                            }
                                            chatAttachments.add(
                                                GeminiChatAttachment(
                                                    name = p.fileName,
                                                    localUri = p.filePath,
                                                    bytes = assetBytes,
                                                    mimeType = mime
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w("UIStudioViewModel", "读取引用素材文件失败: ${p.filePath}, ${e.message}")
                                    }
                                }
                            }
                            is MentionPayload.DocPayload -> {
                                if (org.gemini.ui.forge.data.isFileExistsInternal(p.absolutePath)) {
                                    try {
                                        val projId = state.project?.projectId ?: "default"
                                        ExternalDocManager.registerDoc(
                                            projectName = projId,
                                            fileName = p.fileName,
                                            absolutePath = p.absolutePath
                                        )
                                        registeredExternalDocs = ExternalDocManager.getDocsForProject(projId)
                                    } catch (e: Exception) {
                                        AppLogger.w("UIStudioViewModel", "登记历史文档失败: ${p.absolutePath}, ${e.message}")
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    // 发送完毕后清空已挂载实体药丸
                    mentionState.clearMentions()
                }

                // 智能判定激活的技能并注入工具与系统指令补丁
                val triggeredSkills = UiStudioSkillManager.resolveActiveSkills(
                    prompt = userText,
                    attachedMentions = attachedMentionsList,
                    mode = skillMode,
                    enabledSkillIds = enabledSkillIds
                )

                // 严格遵循不自动注入铁律：彻底删除基于关键词或意图的私自注入；仅当用户显式附带全部素材药丸时才装配清单
                var autoAssetManifestXml: String? = null
                val hasAllAssetsMention = attachedMentionsList.any {
                    val p = it.payload
                    p is MentionPayload.AssetPayload && p.fileName == "ALL_ASSETS_MANIFEST"
                }
                if (hasAllAssetsMention) {
                    val manifest = AssetManifestHelper.buildCompactManifest(state.assetLibrary.items)
                    autoAssetManifestXml = "  <asset_library_manifest total=\"${state.assetLibrary.items.size}\">\n$manifest\n  </asset_library_manifest>"
                }

                val skillPromptAddition = triggeredSkills.joinToString("\n\n") { it.effectiveInstructions }
                val effectiveSystemPrompt = if (skillPromptAddition.isNotBlank()) {
                    "$systemPrompt\n\n$skillPromptAddition".trim()
                } else {
                    systemPrompt
                }
                val toolConfigs = UiStudioSkillManager.buildToolConfigs(triggeredSkills)

                // 构建四层解耦的多层载荷：系统指令保持纯净静态，环境与参考数据注入当前轮次用户提问载荷中
                val currentView = state.currentView
                val workspaceContextXml = if (currentView != null) {
                    "  <current_view name=\"${currentView.nameStr}\" resolution=\"${currentView.width}x${currentView.height}\" block_count=\"${currentView.blocks.size}\" total_assets=\"${state.assetLibrary.items.size}\"/>"
                } else ""

                // 长对话智能上下文自愈与自动压缩：达到阈值时自动提炼设计记忆并压缩历史
                var sessionToUse = (state.chatSessions.find { it.id == updatedSession.id } ?: updatedSession)
                if (ChatCompressionManager.shouldCompress(sessionToUse)) {
                    AppLogger.i("UIStudioViewModel", "达到长对话阈值，正在自动提炼设计记忆...")
                    try {
                        sessionToUse = ChatCompressionManager.compressSession(
                            session = sessionToUse,
                            apiFlavor = apiFlavor,
                            chatEngine = chatEngine,
                            apiKey = effectiveApiKey,
                            customSystemPrompt = effectiveSystemPrompt
                        )
                        saveSession(sessionToUse)
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "自动压缩失败，继续使用未压缩上下文", e)
                    }
                }

                val snapshotAttachment = attachmentsList.find { it.name.contains("画布") }
                val canvasSnapshotMeta = if (snapshotAttachment != null) {
                    "已随附当前视图【${state.currentView?.nameStr ?: "当前视图"}】的画布快照 (${snapshotAttachment.name})。\n请结合该图片直接审视各图元的真实视觉渲染、切片重叠与层次关系、文字排版可读性与边距留白，并在回复中给出具象的视觉评估与改进建议。"
                } else null

                val assembledPayload = ChatPayloadAssembler.assemble(
                    baseSystemPrompt = effectiveSystemPrompt,
                    userRawText = userText,
                    workspaceContextXml = workspaceContextXml,
                    attachedMentions = processedMentions,
                    autoAssetManifestXml = autoAssetManifestXml,
                    priorDesignMemoryXml = sessionToUse.designMemoryContext,
                    canvasSnapshotMeta = canvasSnapshotMeta
                )

                // 注入历史消息（过滤已被压缩的消息）
                val historyItems = sessionToUse.messages.filter { !it.isCompressed && it.id != userMsg.id }.map { msg ->
                    GeminiChatHistoryItem(
                        role = msg.role,
                        text = msg.effectiveText
                    )
                }

                val resolvedModel = GeminiModel.fromModelName(selectedModel) ?: GeminiModel.GEMINI_3_8_FLASH
                val requestModel = resolveChatRequestModel(resolvedModel, userText)
                val isImageGenTurn = ImageGenIntentDetector.isImageSpecialized(requestModel)

                val chatRequest = GeminiChatRequest(
                    apiKey = effectiveApiKey,
                    model = requestModel,
                    apiFlavor = apiFlavor,
                    prompt = assembledPayload.compositeUserPrompt,
                    systemInstruction = assembledPayload.pureSystemInstruction,
                    tools = if (isImageGenTurn) emptyList() else toolConfigs,
                    attachments = chatAttachments,
                    preferCloudUpload = preferCloud,
                    history = historyItems,
                    previousInteractionId = sessionToUse.latestInteractionId,
                    generationConfig = GeminiGenerationConfig(
                        temperature = state.projectAgentConfig.temperature.toDouble(),
                        thinkingConfig = if (isImageGenTurn) null else state.projectAgentConfig.thinkingLevel.toGeminiThinkingConfig(requestModel)
                    )
                )

                var currentChatRequest = chatRequest
                val allExecutedToolRecords = mutableListOf<ChatToolCallRecord>()
                var round = 0
                val toolRoundStep = configManager.loadKey("AI_AGENT_MAX_TOOL_ROUNDS")?.toIntOrNull()?.coerceIn(5, 500) ?: 50
                var maxToolRounds = toolRoundStep
                var lastResult: org.gemini.ui.forge.service.ai.GeminiChatResult? = null
                val previousRoundsThoughts = mutableListOf<String>()

                val liveMsgId = "msg_${getCurrentTimeMillis()}_assistant"
                var liveAssistantMsg = VisualChatMessage(
                    id = liveMsgId,
                    role = "model",
                    prompt = "",
                    modelName = requestModel.modelName,
                    timestamp = getCurrentTimeMillis(),
                    toolCalls = emptyList()
                )
                var currentLiveSession = sessionToUse.copy(messages = sessionToUse.messages + liveAssistantMsg)
                state = state.copy(
                    chatSessions = state.chatSessions.map { if (it.id == currentLiveSession.id) currentLiveSession else it }
                )

                while (round < maxToolRounds) {
                    round++
                    val currentRound = round
                    var currentStreamingThoughtThisRound = ""
                    val result = try {
                        chatEngine.executeChat(
                            request = currentChatRequest,
                            onChunk = { chunk ->
                                val updatedPrompt = liveAssistantMsg.prompt + chunk
                                liveAssistantMsg = liveAssistantMsg.copy(
                                    prompt = updatedPrompt,
                                    variants = listOf(updatedPrompt)
                                )
                                currentLiveSession = currentLiveSession.copy(
                                    messages = currentLiveSession.messages.map { if (it.id == liveMsgId) liveAssistantMsg else it }
                                )
                                state = state.copy(
                                    chatSessions = state.chatSessions.map { if (it.id == currentLiveSession.id) currentLiveSession else it },
                                    streamingAiText = updatedPrompt
                                )
                            },
                            onThought = { thought: String, _ ->
                                if (thought.isNotBlank()) {
                                    currentStreamingThoughtThisRound = thought
                                    val fullThought = if (previousRoundsThoughts.isEmpty()) {
                                        thought
                                    } else {
                                        "${previousRoundsThoughts.joinToString("\n\n---\n\n")}\n\n---\n\n[第 $currentRound 轮思考]\n$thought"
                                    }
                                    liveAssistantMsg = liveAssistantMsg.copy(thought = fullThought)
                                    currentLiveSession = currentLiveSession.copy(
                                        messages = currentLiveSession.messages.map { if (it.id == liveMsgId) liveAssistantMsg else it }
                                    )
                                    state = state.copy(
                                        chatSessions = state.chatSessions.map { if (it.id == currentLiveSession.id) currentLiveSession else it },
                                        streamingAiThought = thought
                                    )
                                }
                            },
                            onRawTraffic = { direction, rawUrl, body ->
                                scope.launch(Dispatchers.Default) {
                                    val isReq = direction == TrafficDirection.REQ
                                    val stageTag = if (currentRound <= 1 && isReq) {
                                        "第 1 轮 · 意图输入"
                                    } else if (isReq) {
                                        "第 $currentRound 轮 · 工具结果反哺"
                                    } else {
                                        "第 $currentRound 轮 · 模型推理与工具决策"
                                    }
                                    val summary = TrafficSummaryExtractor.extractSummary(direction.name, body)
                                    val record = TrafficRecord(
                                        seq = 0,
                                        direction = direction,
                                        timestamp = getCurrentTimeMillis(),
                                        url = rawUrl,
                                        model = requestModel.modelName,
                                        body = body,
                                        roundIndex = currentRound,
                                        stageTag = stageTag,
                                        summary = summary
                                    )
                                    trafficStore.append(record, trafficScopeId, activeSession.id)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        AppLogger.e("UIStudioViewModel", "Gemini API 调用异常", e)
                        Toast.show("AI 回复失败: ${e.message}", ToastType.ERROR)
                        org.gemini.ui.forge.service.ai.GeminiChatResult(
                            text = "⚠️ 网络通信异常：${e.message ?: "未知网络错误"}\n可点击顶部网络通信图标 (Receipt) 查看原始 HTTP 报文排查问题。",
                            rawResponseJson = e.message ?: "",
                            isSuccess = false,
                            errorMessage = e.message
                        )
                    } finally {
                        state = state.copy(streamingAiText = "", streamingAiThought = "")
                    }

                    lastResult = result

                    // 沉淀本轮思考过程，保证多轮交互后完整呈现
                    val resolvedThoughtText = result.thoughtText?.ifBlank { null } ?: currentStreamingThoughtThisRound.ifBlank { null }
                    if (resolvedThoughtText != null) {
                        val labeledThought = if (currentRound <= 1) resolvedThoughtText else "[第 $currentRound 轮思考]\n$resolvedThoughtText"
                        previousRoundsThoughts.add(labeledThought)
                        liveAssistantMsg = liveAssistantMsg.copy(thought = previousRoundsThoughts.joinToString("\n\n---\n\n"))
                    }

                    if (!result.isSuccess) {
                        val errMsg = result.errorMessage ?: "服务调用未成功"
                        withContext(Dispatchers.Main) {
                            Toast.show("AI 响应异常: $errMsg", ToastType.ERROR)
                        }
                        break
                    }

                    val executedToolRecords = if (result.isSuccess) {
                        state = state.copy(streamingAiThought = "🛠️ 正在执行系统工具...")
                        try {
                            executeDetectedAiTools(result.rawResponseJson, result.text, result.functionCalls)
                        } finally {
                            state = state.copy(streamingAiThought = "")
                        }
                    } else emptyList()

                    allExecutedToolRecords.addAll(executedToolRecords)

                    // 实时将当前执行完毕的工具卡片同步刷新至正在展示的消息实体中
                    liveAssistantMsg = liveAssistantMsg.copy(toolCalls = allExecutedToolRecords.toList())
                    currentLiveSession = currentLiveSession.copy(
                        messages = currentLiveSession.messages.map { if (it.id == liveMsgId) liveAssistantMsg else it }
                    )
                    state = state.copy(
                        chatSessions = state.chatSessions.map { if (it.id == currentLiveSession.id) currentLiveSession else it }
                    )

                    val isLayoutTriggered = executedToolRecords.any { it.toolName == "apply_view_layout" } ||
                        result.rawResponseJson.contains("apply_view_layout")

                    // 如果没有工具调用，或者已经触发了排版装配，跳出循环
                    if (executedToolRecords.isEmpty() || isLayoutTriggered) {
                        break
                    }

                    // 到达当前最大轮次上限时，弹窗询问用户是否追加继续
                    if (round >= maxToolRounds) {
                        state = state.copy(
                            showMaxToolRoundsContinueDialog = true,
                            currentToolLoopRound = round,
                            maxToolLoopRoundLimit = maxToolRounds
                        )
                        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                        toolLoopContinueDeferred = deferred
                        val shouldContinue = try {
                            deferred.await()
                        } finally {
                            toolLoopContinueDeferred = null
                            state = state.copy(showMaxToolRoundsContinueDialog = false)
                        }
                        if (shouldContinue) {
                            maxToolRounds += toolRoundStep
                            withContext(Dispatchers.Main) {
                                Toast.show("已继续追加 $toolRoundStep 轮推理...", ToastType.INFO)
                            }
                        } else {
                            break
                        }
                    }

                    // 纯化反哺：对齐 Google Gemini 官方 Function Calling Turnaround 契约
                    val functionResponses = executedToolRecords.map { rec ->
                        val raw = rec.rawOutput?.ifBlank { null } ?: rec.resultSummary ?: "{}"
                        val finalRespJson = if (rec.isSuccess) {
                            raw
                        } else {
                            "{\"error\":${Json.encodeToString(raw)}}"
                        }
                        GeminiFunctionResponse(
                            name = rec.toolName,
                            callId = rec.callId,
                            responseJson = finalRespJson
                        )
                    }

                    val modelFunctionCalls = executedToolRecords.map {
                        GeminiFunctionCallRecord(
                            name = it.toolName,
                            argsJson = it.argumentsJson,
                            callId = it.callId
                        )
                    }

                    val updatedHistory = currentChatRequest.history.toMutableList().apply {
                        add(
                            GeminiChatHistoryItem(
                                role = "model",
                                text = result.text,
                                functionCalls = modelFunctionCalls
                            )
                        )
                        add(
                            GeminiChatHistoryItem(
                                role = "user",
                                text = "",
                                functionResults = functionResponses
                            )
                        )
                    }

                    currentChatRequest = currentChatRequest.copy(
                        history = updatedHistory,
                        prompt = "",
                        userPrompt = "",
                        functionResults = functionResponses,
                        previousInteractionId = result.interactionId ?: currentChatRequest.previousInteractionId
                    )
                }

                val finalResult = lastResult ?: org.gemini.ui.forge.service.ai.GeminiChatResult(
                    text = "（无响应结果）",
                    rawResponseJson = "",
                    isSuccess = false
                )

                val directGeneratedImageUri = finalResult.generatedImageData?.let { persistGeneratedChatImage(it) }
                val generatedImgPath = directGeneratedImageUri ?: extractGeneratedImageUri(allExecutedToolRecords)
                if (generatedImgPath != null) {
                    state.project?.projectId?.let { assetLibrary.loadAssetLibrary(it) }
                }

                val finalAssistantText = if (liveAssistantMsg.prompt.isNotBlank()) {
                    liveAssistantMsg.prompt
                } else if (finalResult.text.isNotBlank()) {
                    finalResult.text
                } else if (allExecutedToolRecords.isNotEmpty()) {
                    "" // 工具调用由结构化工具卡片纯净呈现，绝不擅自拼接机械文本污染界面
                } else if (!finalResult.isSuccess) {
                    finalResult.errorMessage ?: "服务端未返回错误详情"
                } else {
                    "（未收到有效文本内容）"
                }

                val finalThoughtText = if (previousRoundsThoughts.isNotEmpty()) {
                    previousRoundsThoughts.joinToString("\n\n---\n\n")
                } else {
                    finalResult.thoughtText ?: liveAssistantMsg.thought
                }

                val assistantMsg = liveAssistantMsg.copy(
                    prompt = finalAssistantText,
                    variants = listOf(finalAssistantText),
                    activeVariantIndex = 0,
                    thought = finalThoughtText,
                    thoughtSignature = finalResult.thoughtSignature,
                    interactionId = finalResult.interactionId,
                    modelName = finalResult.modelName ?: requestModel.modelName,
                    durationMs = finalResult.durationMs,
                    promptTokens = finalResult.promptTokens,
                    candidatesTokens = finalResult.candidatesTokens,
                    totalTokens = finalResult.totalTokens,
                    timestamp = getCurrentTimeMillis(),
                    isError = !finalResult.isSuccess,
                    errorMessage = finalResult.errorMessage,
                    toolCalls = allExecutedToolRecords,
                    generatedImageUri = generatedImgPath
                )

                val finalizedSession = currentLiveSession.copy(
                    messages = currentLiveSession.messages.map { if (it.id == liveMsgId) assistantMsg else it },
                    latestInteractionId = finalResult.interactionId ?: currentLiveSession.latestInteractionId,
                    updatedAt = getCurrentTimeMillis()
                )
                saveSession(finalizedSession)
                state = state.copy(
                    chatSessions = state.chatSessions.map { if (it.id == finalizedSession.id) finalizedSession else it },
                    isAiChatSending = false
                )

                // 自动检查大模型是否发起了 apply_view_layout 工具调用并自动装配
                if (finalResult.isSuccess) {
                    checkAndExecuteLayoutTool(finalResult.rawResponseJson, finalResult.text)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI Chat 处理异常", e)
                state = state.copy(isAiChatSending = false)
            } finally {
                activeChatJob = null
            }
        }
    }

    /**
     * 主动中止当前正在进行的 AI 流式生成或网络长请求
     */
    fun cancelCurrentChatGeneration() {
        val job = activeChatJob
        if (job != null && job.isActive) {
            job.cancel()
            activeChatJob = null
        }
        val currentStreamText = state.streamingAiText
        val activeSession = state.currentChatSession
        if (currentStreamText.isNotBlank() && activeSession != null) {
            val truncatedText = "$currentStreamText\n\n*(⏹ 用户已主动中止本次生成)*"
            val now = getCurrentTimeMillis()
            val stoppedMsg = VisualChatMessage(
                id = "msg_${now}_assistant_stopped",
                role = "model",
                prompt = truncatedText,
                variants = listOf(truncatedText),
                activeVariantIndex = 0,
                timestamp = now
            )
            val updatedMessages = activeSession.messages + stoppedMsg
            val updatedSession = activeSession.copy(
                messages = updatedMessages,
                updatedAt = now
            )
            val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }
            state = state.copy(
                chatSessions = updatedSessions,
                isAiChatSending = false,
                streamingAiText = ""
            )
            saveSession(updatedSession)
        } else {
            state = state.copy(
                isAiChatSending = false,
                streamingAiText = ""
            )
        }
        Toast.show("已停止 AI 生成", ToastType.INFO)
    }

    /**
     * 手动触发长对话上下文压缩与设计记忆结晶
     */
    fun compressCurrentSession() {
        val currentSession = state.currentChatSession ?: return
        if (currentSession.messages.size <= ChatCompressionManager.PRESERVE_RECENT_ROUNDS) {
            Toast.show("当前会话消息较少，无需压缩", ToastType.INFO)
            return
        }

        scope.launch(Dispatchers.Default) {
            val effectiveApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey() ?: ""
            if (effectiveApiKey.isBlank()) {
                Toast.show("请先在设置中配置有效 API Key", ToastType.ERROR)
                return@launch
            }
            Toast.show("正在提炼设计记忆并压缩上下文...", ToastType.INFO)
            try {
                val updatedSession = ChatCompressionManager.compressSession(
                    session = currentSession,
                    apiFlavor = state.projectAgentConfig.apiFlavor,
                    chatEngine = chatEngine,
                    apiKey = effectiveApiKey,
                    customSystemPrompt = state.projectAgentConfig.systemPrompt
                )
                state = state.copy(
                    chatSessions = state.chatSessions.map {
                        if (it.id == updatedSession.id) updatedSession else it
                    }
                )
                saveSession(updatedSession)
                val stats = ChatCompressionManager.calculateStats(updatedSession)
                Toast.show("长对话压缩成功！已提炼设计记忆，预估节省约 ${stats.estimatedTokensSaved} Tokens", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "长对话压缩失败", e)
                Toast.show("长对话压缩失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 自动检测并执行大模型调用的全量系统工具（MCP + 内置通用标配工具）
     */
    private suspend fun executeDetectedAiTools(
        rawResponseJson: String,
        responseText: String,
        detectedFunctionCalls: List<GeminiFunctionCallRecord> = emptyList()
    ): List<ChatToolCallRecord> {
        val records = mutableListOf<ChatToolCallRecord>()
        val detected = if (detectedFunctionCalls.isNotEmpty()) {
            detectedFunctionCalls
        } else {
            toolDispatcher.extractFunctionCallRecords(rawResponseJson, responseText)
        }
        for (fc in detected) {
            val name = fc.name
            val args = runCatching { jsonHelper.parseToJsonElement(fc.argsJson).jsonObject }.getOrElse { buildJsonObject {} }
            // 自动注入当前 UI Studio 工作区的工程与视图上下文（兜底纠偏模型可能缺省或虚构的工程名）
            val enrichedArgs = buildJsonObject {
                args.forEach { (k, v) -> put(k, v) }
                val currentProj = state.project?.projectId
                if (!currentProj.isNullOrBlank()) {
                    put("projectName", currentProj)
                }
                if (!args.containsKey("viewName")) {
                    state.currentView?.nameStr?.let { put("viewName", it) }
                }
            }

            if (name == "apply_view_layout") {
                checkAndExecuteLayoutTool(rawResponseJson, responseText)
                continue
            }
            state = state.copy(streamingAiText = "🛠️ 正在执行系统工具 [$name] 并保存到素材库...")
            val t0 = getCurrentTimeMillis()
            val toolResult = toolDispatcher.dispatchToolCall(name, enrichedArgs)
            val dur = getCurrentTimeMillis() - t0
            val record = toolDispatcher.buildToolCallRecord(
                id = "tool_${getCurrentTimeMillis()}_$name",
                toolName = name,
                arguments = enrichedArgs,
                result = toolResult,
                durationMs = dur,
                callId = fc.callId
            )
            records.add(record)
        }
        if (records.isNotEmpty()) {
            state = state.copy(streamingAiText = "")
        }
        return records
    }

    /**
     * 针对当前用户发送的文本意图与配置，自适应解析本次请求实际应调度的 Gemini 模型
     * 若检测到纯生图意图且 baseModel 非生图模型，静默分流至 Imagen 或指定生图模型
     */
    private suspend fun resolveChatRequestModel(baseModel: GeminiModel, userText: String): GeminiModel {
        val autoSwitch = configManager.loadKey("AI_AUTO_SWITCH_IMAGE_GEN_MODEL")?.toBooleanStrictOrNull() ?: true
        if (!autoSwitch) return baseModel
        if (ImageGenIntentDetector.isImageSpecialized(baseModel)) return baseModel
        if (!ImageGenIntentDetector.isImageGenIntent(userText)) return baseModel

        val preset = configManager.loadKey("AI_DEFAULT_IMAGE_MODEL")?.ifBlank { null }
        return ImageGenIntentDetector.resolveTargetGeminiModel(preset)
    }

    /**
     * 将 AI 生成的内联 Base64 图像持久化落盘至当前项目的 chat_attachments 目录
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun persistGeneratedChatImage(base64Data: String): String? {
        return try {
            val proj = state.project
            val sanitized = proj?.projectId?.trim()?.replace(" ", "_") ?: "default"
            val tempDir = "templates/$sanitized/chat_attachments"
            val pureB64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(pureB64)
            if (bytes.isEmpty()) return null
            val fileName = "gen_${getCurrentTimeMillis()}.png"
            val fileRelPath = "$tempDir/$fileName"
            val file = TemplateFile(fileRelPath)
            file.writeBytes(bytes)
            file.getAbsolutePath()
        } catch (e: Exception) {
            AppLogger.e("UIStudioViewModel", "持久化 AI 生成图像失败", e)
            null
        }
    }

    /**
     * 从执行完毕的系统工具记录中提取最新生成的图像物理落盘路径
     */
    private fun extractGeneratedImageUri(toolRecords: List<ChatToolCallRecord>): String? {
        for (record in toolRecords) {
            if (record.isSuccess && (record.toolName == "generate_image" || record.toolName == "chat_refine_image" || record.toolName == "generate_block_asset")) {
                // 1. 优先直接使用工具返回的高清多模态 imageUri (Data URI)
                if (!record.imageUri.isNullOrBlank()) {
                    return record.imageUri
                }
                // 2. 兼容落盘路径解析
                val summary = record.resultSummary
                if (!summary.isNullOrBlank()) {
                    val diskMarker = when {
                        summary.contains("📁 物理落盘: ") -> "📁 物理落盘: "
                        summary.contains("📁 素材库归档: ") -> "📁 素材库归档: "
                        summary.contains("📁 本地落盘: ") -> "📁 本地落盘: "
                        else -> null
                    }
                    if (diskMarker != null) {
                        val rawPath = summary.substringAfter(diskMarker).trim().lines().firstOrNull()?.trim()?.removeSurrounding("`")?.trim()
                        if (!rawPath.isNullOrBlank()) {
                            return rawPath
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 检查并自动执行大模型返回的 apply_view_layout 本地装配任务
     */
    private fun checkAndExecuteLayoutTool(rawResponseJson: String, responseText: String) {
        val extractedArgs = extractApplyViewLayoutArgs(rawResponseJson, responseText) ?: return
        val projId = state.project?.projectId ?: "default"
        val applyResult = UiStudioLayoutService.assembleViewPage(
            projectId = projId,
            assetLibrary = state.assetLibrary,
            args = extractedArgs
        )
        if (applyResult.isSuccess && applyResult.createdPage != null) {
            // 将待应用的排版结果交由人机交互决策弹窗进行确认，由用户选择替换当前视图还是新建独立视图
            state = state.copy(pendingLayoutResult = applyResult)
        }
    }

    /**
     * 从给定的消息或文本中重新提取排版方案并弹出应用决策弹窗
     */
    fun reapplyLayoutFromText(rawText: String) {
        checkAndExecuteLayoutTool(rawText, rawText)
    }

    /**
     * 确认应用 AI 生成的界面排版
     *
     * @param replaceCurrent 是否覆盖替换当前视图
     * @param targetName 目标视图名称
     */
    fun confirmApplyLayout(replaceCurrent: Boolean, targetName: String) {
        val result = state.pendingLayoutResult ?: return
        val rawPage = result.createdPage ?: return
        val existingPages = state.project?.studioPages ?: emptyList()

        val currentView = state.currentView ?: existingPages.firstOrNull()
        val finalPage = if (replaceCurrent) {
            // 替换当前正在编辑的视图，继承当前视图的 id 与 name
            val targetId = currentView?.id ?: rawPage.id
            val targetNameStr = currentView?.nameStr ?: targetName.ifBlank { rawPage.nameStr }
            rawPage.copy(
                id = targetId,
                nameStr = targetNameStr
            )
        } else {
            // 作为新视图创建
            val cleanName = targetName.trim().ifBlank { rawPage.nameStr }
            if (existingPages.any { it.nameStr.equals(cleanName, ignoreCase = true) }) {
                Toast.show("视图名称 \"$cleanName\" 已存在，请更换其他新名称", ToastType.ERROR)
                return
            }
            val newId = "page_${cleanName.replace(" ", "_")}_${getCurrentTimeMillis() % 10000}"
            rawPage.copy(
                id = newId,
                nameStr = cleanName
            )
        }

        val oldBlocks = if (replaceCurrent) currentView?.blocks ?: emptyList() else emptyList()
        val newBlocks = finalPage.blocks
        val diffResult = org.gemini.ui.forge.service.studio.LayoutDiffCalculator.calculateDiff(oldBlocks, newBlocks)
        val undoSnapshot = LayoutUndoSnapshot(
            viewId = finalPage.id,
            viewName = finalPage.nameStr,
            isReplaced = replaceCurrent,
            oldBlocks = oldBlocks,
            newBlocks = newBlocks,
            diff = diffResult
        )

        val updatedPages = if (replaceCurrent) {
            existingPages.map { if (it.id == finalPage.id) finalPage else it }
        } else {
            existingPages + finalPage
        }

        val updatedProject = state.project?.copy(studioPages = updatedPages)
        if (updatedProject != null) {
            state = state.copy(
                project = updatedProject,
                currentViewId = finalPage.id,
                openViewIds = (state.openViewIds + finalPage.id).distinct(),
                pendingLayoutResult = null,
                lastLayoutUndoSnapshot = undoSnapshot,
                isDirty = true,
                isFitToViewport = true,
                leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
            )
            saveProject(TemplateRepository(storage))
            val toastMsg = if (replaceCurrent) {
                "视图 [${finalPage.nameStr}] 已成功应用 AI 排版！"
            } else {
                "🎉 全新视图 [${finalPage.nameStr}] 已创建并在画布呈现！"
            }
            scope.launch(Dispatchers.Main) {
                Toast.show(toastMsg, ToastType.SUCCESS)
            }
        } else {
            state = state.copy(pendingLayoutResult = null)
        }
    }

    /**
     * 撤销上一次 AI 应用的界面排版变更，无损还原图元树或新建的视图
     */
    fun undoLastLayoutChanges() {
        val snapshot = state.lastLayoutUndoSnapshot ?: run {
            Toast.show("当前没有可撤回的 AI 排版变更", ToastType.INFO)
            return
        }
        val existingPages = state.project?.studioPages ?: return
        val updatedPages = if (snapshot.isReplaced) {
            // 还原被替换视图的图元树
            existingPages.map { page ->
                if (page.id == snapshot.viewId) {
                    page.copy(blocks = snapshot.oldBlocks)
                } else page
            }
        } else {
            // 新建视图场景，直接删除该新建页面
            existingPages.filter { it.id != snapshot.viewId }
        }

        val fallbackViewId = if (snapshot.isReplaced) {
            snapshot.viewId
        } else {
            updatedPages.firstOrNull()?.id
        }

        val updatedProject = state.project?.copy(studioPages = updatedPages)
        if (updatedProject != null) {
            state = state.copy(
                project = updatedProject,
                currentViewId = fallbackViewId,
                openViewIds = (state.openViewIds.filter { it != snapshot.viewId } + listOfNotNull(fallbackViewId)).distinct(),
                lastLayoutUndoSnapshot = null,
                isDirty = true,
                isFitToViewport = true,
                leftBottomTab = if (fallbackViewId != null) UIStudioLeftBottomTab.HIERARCHY else state.leftBottomTab
            )
            saveProject(TemplateRepository(storage))
            Toast.show("↩️ 已成功撤回 AI 布局调整，画布已还原", ToastType.SUCCESS)
        }
    }

    /**
     * 放弃或关闭 AI 排版确认弹窗
     */
    fun cancelApplyLayout() {
        state = state.copy(pendingLayoutResult = null)
    }

    /**
     * 从返回报文或正文代码块中鲁棒提取 apply_view_layout 参数
     */
    private fun extractApplyViewLayoutArgs(rawJson: String, responseText: String): kotlinx.serialization.json.JsonObject? {
        // 通道 1: 官方 Function Call 协议检查
        try {
            if (rawJson.contains("apply_view_layout")) {
                val element = jsonHelper.parseToJsonElement(rawJson)
                val fcObj = findFunctionCallRecursive(element, "apply_view_layout")
                if (fcObj != null) {
                    val args = fcObj["args"]?.jsonObject
                    if (args != null && args.containsKey("blocks")) {
                        return args
                    }
                }
            }
        } catch (_: Exception) {}

        // 通道 2: 文本 Markdown 代码块中嵌入的 JSON 参数检查
        try {
            val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val matches = codeBlockRegex.findAll(responseText)
            for (match in matches) {
                val jsonSnippet = match.groupValues[1].trim()
                if (jsonSnippet.startsWith("{") && (jsonSnippet.contains("blocks") || jsonSnippet.contains("apply_view_layout"))) {
                    val parsed = jsonHelper.parseToJsonElement(jsonSnippet).jsonObject
                    if (parsed.containsKey("blocks")) {
                        return parsed
                    }
                    if (parsed.containsKey("args")) {
                        val inner = parsed["args"]?.jsonObject
                        if (inner != null && inner.containsKey("blocks")) return inner
                    }
                }
            }
        } catch (_: Exception) {}

        // 通道 3: 提取裸输出中包含的 arguments 结构
        try {
            val targetKey = "apply_view_layout"
            val idx = responseText.indexOf(targetKey)
            if (idx >= 0) {
                val braceStart = responseText.indexOf('{', idx)
                if (braceStart >= 0) {
                    val snippet = extractBalancedJsonObject(responseText, braceStart)
                    if (snippet != null) {
                        val parsed = jsonHelper.parseToJsonElement(snippet).jsonObject
                        if (parsed.containsKey("blocks")) return parsed
                        val args = parsed["arguments"]?.jsonObject ?: parsed["args"]?.jsonObject
                        if (args != null && args.containsKey("blocks")) return args
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun extractBalancedJsonObject(text: String, startIndex: Int): String? {
        var depth = 0
        var start = -1
        for (i in startIndex until text.length) {
            val c = text[i]
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun findFunctionCallRecursive(element: kotlinx.serialization.json.JsonElement, targetName: String): kotlinx.serialization.json.JsonObject? {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                val fc = element["functionCall"]?.jsonObject ?: element["function_call"]?.jsonObject
                if (fc != null && fc["name"]?.jsonPrimitive?.contentOrNull == targetName) {
                    return fc
                }
                for ((_, v) in element) {
                    val found = findFunctionCallRecursive(v, targetName)
                    if (found != null) return found
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                for (item in element) {
                    val found = findFunctionCallRecursive(item, targetName)
                    if (found != null) return found
                }
            }
            else -> {}
        }
        return null
    }

    /**
     * 切换某条消息的版本展示（例如用户针对单条提问重新生成多次后，在 < 1/2 > 之间翻页切换）。
     */
    fun switchChatMessageVariant(messageId: String, variantIndex: Int) {
        val activeSession = state.currentChatSession ?: return
        val updatedMessages = activeSession.messages.map { msg ->
            if (msg.id == messageId) {
                val clamped = variantIndex.coerceIn(0, (msg.variants.size - 1).coerceAtLeast(0))
                val targetVariantText = msg.variants.getOrNull(clamped) ?: ""
                val isTargetFailed = targetVariantText.isBlank() || targetVariantText.startsWith("⚠️")
                msg.copy(
                    activeVariantIndex = clamped,
                    isError = isTargetFailed
                )
            } else msg
        }
        val updatedSession = activeSession.copy(messages = updatedMessages, updatedAt = getCurrentTimeMillis())
        val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }
        state = state.copy(chatSessions = updatedSessions)
        saveSession(updatedSession)
    }

    /**
     * 删除单条对话记录或一整轮对话。
     * 若删除的是用户提问，连带其后直接紧随的 AI 回答一并移除。
     */
    fun deleteChatMessage(messageId: String) {
        val activeSession = state.currentChatSession ?: return
        val targetIndex = activeSession.messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val targetMsg = activeSession.messages[targetIndex]

        val updatedMessages = if (targetMsg.role == "user") {
            activeSession.messages.filterIndexed { index, _ ->
                index != targetIndex && !(index == targetIndex + 1 && activeSession.messages.getOrNull(index)?.role == "model")
            }
        } else {
            activeSession.messages.filterIndexed { index, _ -> index != targetIndex }
        }
        val updatedSession = activeSession.copy(messages = updatedMessages, updatedAt = getCurrentTimeMillis())
        val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }
        state = state.copy(chatSessions = updatedSessions)
        saveSession(updatedSession)
        Toast.show("已删除该条对话记录", ToastType.INFO)
    }

    /**
     * 重新生成某条消息的回答。
     * 若传入的是 AI 消息，则针对其对应的提问重新生成，并将新结果存入该 AI 消息的 variants 列表中追加版本；
     * 若传入的是用户提问，则直接重新触发回答。
     */
    fun regenerateChatMessage(messageId: String) {
        val activeSession = state.currentChatSession ?: return
        val targetIndex = activeSession.messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return

        val (userMsg, modelMsg) = if (activeSession.messages[targetIndex].role == "model") {
            val prevUser = activeSession.messages.take(targetIndex).lastOrNull { it.role == "user" }
            prevUser to activeSession.messages[targetIndex]
        } else {
            val nextModel = activeSession.messages.drop(targetIndex + 1).firstOrNull { it.role == "model" }
            activeSession.messages[targetIndex] to nextModel
        }

        if (userMsg == null) {
            Toast.show("未找到对应的用户提问，无法重新生成", ToastType.INFO)
            return
        }

        state = state.copy(isAiChatSending = true, streamingAiText = "")

        activeChatJob = scope.launch(Dispatchers.Default) {
            val effectiveApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey() ?: ""
            if (effectiveApiKey.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.show("未配置 Gemini API Key，无法发起真实网络请求", ToastType.ERROR)
                    state = state.copy(isAiChatSending = false)
                }
                return@launch
            }
            try {
                val apiFlavor = state.projectAgentConfig.apiFlavor
                val systemPrompt = state.projectAgentConfig.systemPrompt.ifBlank {
                    PromptManager.getUiStudioAgentPrompt()
                }
                val selectedModel = state.projectAgentConfig.modelName.ifBlank { GeminiModel.GEMINI_3_8_FLASH.modelName }

                val chatAttachments = userMsg.inputImageUris.map { uri ->
                    GeminiChatAttachment(
                        name = uri.substringAfterLast("/"),
                        localUri = uri,
                        bytes = readLocalFileBytes(uri),
                        mimeType = if (uri.endsWith(".png", true)) "image/png" else "image/jpeg"
                    )
                }

                val historyItems = activeSession.messages.take(targetIndex).filter { !it.isCompressed && it.id != userMsg.id }.map { msg ->
                    GeminiChatHistoryItem(
                        role = msg.role,
                        text = msg.effectiveText
                    )
                }

                val resolvedModel = GeminiModel.fromModelName(selectedModel) ?: GeminiModel.GEMINI_3_8_FLASH
                val requestModel = resolveChatRequestModel(resolvedModel, userMsg.prompt)
                val isImageGenTurn = ImageGenIntentDetector.isImageSpecialized(requestModel)

                val previousInteractionId = activeSession.messages
                    .take(targetIndex)
                    .lastOrNull { !it.interactionId.isNullOrBlank() }
                    ?.interactionId ?: activeSession.latestInteractionId

                // 构建四层解耦的多层载荷：系统指令保持纯净静态，环境与前序设计记忆注入当前轮次用户提问载荷中
                val currentView = state.currentView
                val workspaceContextXml = if (currentView != null) {
                    "  <current_view name=\"${currentView.nameStr}\" resolution=\"${currentView.width}x${currentView.height}\" block_count=\"${currentView.blocks.size}\" total_assets=\"${state.assetLibrary.items.size}\"/>"
                } else ""
                val assembledPayload = ChatPayloadAssembler.assemble(
                    baseSystemPrompt = systemPrompt,
                    userRawText = userMsg.prompt,
                    workspaceContextXml = workspaceContextXml,
                    priorDesignMemoryXml = activeSession.designMemoryContext
                )

                val toolConfigs = UiStudioSkillManager.buildToolConfigs(enableGoogleSearch = true)

                val chatRequest = GeminiChatRequest(
                    apiKey = effectiveApiKey,
                    model = requestModel,
                    apiFlavor = apiFlavor,
                    prompt = assembledPayload.compositeUserPrompt,
                    systemInstruction = assembledPayload.pureSystemInstruction,
                    tools = if (isImageGenTurn) emptyList() else toolConfigs,
                    attachments = chatAttachments,
                    preferCloudUpload = true,
                    history = historyItems,
                    previousInteractionId = previousInteractionId,
                    generationConfig = GeminiGenerationConfig(
                        temperature = state.projectAgentConfig.temperature.toDouble(),
                        thinkingConfig = if (isImageGenTurn) null else state.projectAgentConfig.thinkingLevel.toGeminiThinkingConfig(requestModel)
                    )
                )

                val result = chatEngine.executeChat(
                    request = chatRequest,
                    onChunk = { chunk ->
                        state = state.copy(streamingAiText = state.streamingAiText + chunk)
                    },
                    onThought = { thought: String, _ ->
                        if (state.streamingAiText.isBlank() && thought.isNotBlank()) {
                            state = state.copy(streamingAiText = "🧠 " + thought.takeLast(120))
                        }
                    },
                    onRawTraffic = { direction, rawUrl, body ->
                        scope.launch(Dispatchers.Default) {
                            val record = TrafficRecord(
                                seq = 0,
                                direction = direction,
                                timestamp = getCurrentTimeMillis(),
                                url = rawUrl,
                                model = requestModel.modelName,
                                body = body
                            )
                            trafficStore.append(record, trafficScopeId, activeSession.id)
                        }
                    }
                )

                val executedToolRecords = if (result.isSuccess) {
                    state = state.copy(streamingAiThought = "🛠️ 正在执行系统工具 [generate_image] 生成并落盘素材...")
                    try {
                        executeDetectedAiTools(result.rawResponseJson, result.text, result.functionCalls)
                    } finally {
                        state = state.copy(streamingAiThought = "")
                    }
                } else emptyList()

                val directGeneratedImageUri = result.generatedImageData?.let { persistGeneratedChatImage(it) }
                val generatedImgPath = directGeneratedImageUri ?: extractGeneratedImageUri(executedToolRecords)
                if (generatedImgPath != null) {
                    state.project?.projectId?.let { assetLibrary.loadAssetLibrary(it) }
                }

                val finalResultText = if (result.text.isNotBlank()) {
                    result.text
                } else if (executedToolRecords.isNotEmpty()) {
                    "" // 工具执行状态由卡片呈现，不拼接机械废话
                } else if (!result.isSuccess) {
                    result.errorMessage ?: "服务端未返回错误详情"
                } else {
                    "（未收到有效文本内容）"
                }

                val updatedModelMsg = if (modelMsg != null) {
                    val currentVariants = if (modelMsg.variants.isEmpty()) listOf(modelMsg.prompt) else modelMsg.variants
                    val newVariants = currentVariants + finalResultText
                    modelMsg.copy(
                        prompt = finalResultText,
                        variants = newVariants,
                        activeVariantIndex = newVariants.lastIndex,
                        thought = result.thoughtText,
                        thoughtSignature = result.thoughtSignature,
                        interactionId = result.interactionId,
                        modelName = result.modelName ?: requestModel.modelName,
                        durationMs = result.durationMs,
                        promptTokens = result.promptTokens,
                        candidatesTokens = result.candidatesTokens,
                        totalTokens = result.totalTokens,
                        timestamp = getCurrentTimeMillis(),
                        isError = !result.isSuccess,
                        errorMessage = result.errorMessage,
                        toolCalls = executedToolRecords,
                        generatedImageUri = generatedImgPath ?: modelMsg.generatedImageUri
                    )
                } else {
                    VisualChatMessage(
                        id = "msg_${getCurrentTimeMillis()}_assistant",
                        role = "model",
                        prompt = finalResultText,
                        variants = listOf(finalResultText),
                        activeVariantIndex = 0,
                        thought = result.thoughtText,
                        thoughtSignature = result.thoughtSignature,
                        interactionId = result.interactionId,
                        modelName = result.modelName ?: requestModel.modelName,
                        durationMs = result.durationMs,
                        promptTokens = result.promptTokens,
                        candidatesTokens = result.candidatesTokens,
                        totalTokens = result.totalTokens,
                        timestamp = getCurrentTimeMillis(),
                        isError = !result.isSuccess,
                        errorMessage = result.errorMessage,
                        toolCalls = executedToolRecords,
                        generatedImageUri = generatedImgPath
                    )
                }

                val updatedMessages = if (modelMsg != null) {
                    activeSession.messages.map { if (it.id == modelMsg.id) updatedModelMsg else it }
                } else {
                    activeSession.messages + updatedModelMsg
                }

                val updatedSession = activeSession.copy(
                    messages = updatedMessages,
                    latestInteractionId = result.interactionId ?: activeSession.latestInteractionId,
                    updatedAt = getCurrentTimeMillis()
                )
                val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }
                state = state.copy(chatSessions = updatedSessions, isAiChatSending = false)
                saveSession(updatedSession)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "重新生成失败", e)
                Toast.show("重新生成失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isAiChatSending = false)
            } finally {
                activeChatJob = null
                state = state.copy(streamingAiText = "")
            }
        }
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
     * 打开导入图集配置弹窗。
     */
    fun openImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = true)
        if (state.sourceImagePath.isNotBlank()) {
            updateSourceImagePath(state.sourceImagePath)
        }
        if (state.sourceJsonPath.isNotBlank()) {
            updateSourceJsonPath(state.sourceJsonPath)
        }
    }

    /**
     * 关闭导入图集配置弹窗。
     */
    fun closeImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = false)
    }

    /**
     * 更新选中的源图片文件路径并进行磁盘真实物理校验。
     *
     * 在入口处第一时间无条件回显路径到状态层，避免文件选择器或输入迟滞，
     * 随后在后台通过 [isLocalFileExists] 安全探测物理文件真实性与图片格式。
     */
    fun updateSourceImagePath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            state = state.copy(
                sourceImagePath = "",
                isSourceImageFileExists = false,
                sourceImageError = null
            )
            return
        }

        // 1. 第一时间在主线程回显输入路径，保证界面 0 延迟响应
        state = state.copy(
            sourceImagePath = trimmed,
            sourceImageError = null
        )

        // 2. 异步执行跨平台轻量物理探测，杜绝任何崩溃或迟滞
        scope.launch(Dispatchers.Default) {
            val exists = runCatching { isFileExists(trimmed) }.getOrDefault(false)
            val isImage = isSupportedImageFile(trimmed)
            val error = when {
                !exists -> "大图文件在磁盘上不存在，请检查路径"
                !isImage -> "不支持的文件格式，仅支持 PNG、JPG、JPEG、WEBP"
                else -> null
            }
            withContext(Dispatchers.Main) {
                // 仅当用户未进行新输入时才写回校验状态
                if (state.sourceImagePath == trimmed) {
                    state = state.copy(
                        isSourceImageFileExists = exists && isImage,
                        sourceImageError = error
                    )
                }
            }
        }
    }

    /**
     * 更新选中的源 JSON 坐标文件路径并触发自适应解析与物理存在性校验。
     *
     * 在入口处第一时间无条件回显路径到状态层，随后在后台安全探测物理存在性并解析。
     */
    fun updateSourceJsonPath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            state = state.copy(
                sourceJsonPath = "",
                isSourceJsonFileExists = false,
                sourceJsonError = null,
                parseResult = null
            )
            return
        }

        // 1. 第一时间在主线程回显输入路径，保证界面 0 延迟响应
        state = state.copy(
            sourceJsonPath = trimmed,
            sourceJsonError = null
        )

        // 2. 异步执行跨平台轻量物理探测并自适应解析
        scope.launch(Dispatchers.Default) {
            val exists = runCatching { isFileExists(trimmed) }.getOrDefault(false)
            if (!exists) {
                withContext(Dispatchers.Main) {
                    if (state.sourceJsonPath == trimmed) {
                        state = state.copy(
                            isSourceJsonFileExists = false,
                            sourceJsonError = "坐标 JSON 文件在磁盘上不存在，请检查路径",
                            parseResult = null,
                            isParsingJson = false
                        )
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (state.sourceJsonPath == trimmed) {
                    state = state.copy(
                        isSourceJsonFileExists = true,
                        sourceJsonError = null
                    )
                }
            }
            parseJsonFile(trimmed)
        }
    }

    /**
     * 读取指定本地路径的 JSON 文件并进行零预设自适应结构解析。
     *
     * 支持绝对物理路径（如 E:\...）与相对工作区路径，调用跨平台安全读取器 [readLocalFileBytes]。
     */
    fun parseJsonFile(path: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val content = readLocalFileBytes(path)?.decodeToString()
                if (content.isNullOrBlank()) {
                    Toast.show("JSON 文件为空或无法读取", ToastType.ERROR)
                    state = state.copy(
                        isParsingJson = false,
                        isSourceJsonFileExists = false,
                        sourceJsonError = "JSON 文件内容为空或无法读取",
                        parseResult = null
                    )
                    return@launch
                }
                parseJsonContent(content)
            } catch (e: Exception) {
                Toast.show("读取文件失败: ${e.message}", ToastType.ERROR)
                state = state.copy(
                    isParsingJson = false,
                    isSourceJsonFileExists = false,
                    sourceJsonError = "读取文件失败: ${e.message}",
                    parseResult = null
                )
            }
        }
    }

    /**
     * 直接对文本内容执行零预设自适应解析。
     */
    fun parseJsonContent(content: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val result = AssetSheetNormalizer.normalizeJsonOffline(content)
                state = state.copy(
                    parseResult = result,
                    isParsingJson = false
                )
                if (result.items.isNotEmpty()) {
                    Toast.show("成功识别 ${result.items.size} 个图元组件", ToastType.SUCCESS)
                } else {
                    Toast.show("未能从内容中识别出有效的图元坐标", ToastType.INFO)
                }
            } catch (e: Exception) {
                state = state.copy(isParsingJson = false)
                Toast.show("解析失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 从导入配置弹窗流转进入切片可视化预审校对弹窗（阶段三）。
     */
    fun proceedToVerifyDialog() {
        val imagePath = state.sourceImagePath.trim()
        val imageExists = state.isSourceImageFileExists && imagePath.isNotBlank()
        if (!imageExists) {
            Toast.show("大图文件不存在或路径无效，无法进入校对", ToastType.ERROR)
            state = state.copy(
                isSourceImageFileExists = false,
                sourceImageError = "大图文件在磁盘上不存在，请检查路径"
            )
            return
        }

        val result = state.parseResult
        val items = result?.items ?: emptyList()
        if (items.isEmpty()) {
            Toast.show("图元列表为空，无法进入校对", ToastType.ERROR)
            return
        }

        state = state.copy(
            isImportSheetDialogVisible = false,
            isVerifySliceDialogVisible = true,
            verifySliceItems = items
        )
        // 自动落盘初始草稿
        saveDraft(items)
    }

    /**
     * 针对纯图场景调用 Gemini 视觉多模态大模型逆向检测图元坐标，并流转进入阶段三切片校对弹窗。
     *
     * @param apiKey 可选自定义 API Key，缺省时自动从本地配置中心读取
     */
    fun startAiDetectAndProceed(apiKey: String? = null) {
        val imagePath = state.sourceImagePath.trim()
        if (imagePath.isBlank()) {
            Toast.show("请先选择或输入待识别的图集大图路径", ToastType.INFO)
            return
        }
        val imageExists = state.isSourceImageFileExists && imagePath.isNotBlank()
        if (!imageExists) {
            Toast.show("大图文件不存在或路径无效，无法启动 AI 识别", ToastType.ERROR)
            state = state.copy(
                isSourceImageFileExists = false,
                sourceImageError = "大图文件在磁盘上不存在，请检查路径"
            )
            return
        }

        state = state.copy(
            isAiDetectingCoords = true,
            aiDetectStatusText = "正在准备上传图集图片并请求 Gemini 视觉大模型..."
        )

        scope.launch {
            try {
                val (result, _) = org.gemini.ui.forge.service.assetsheet.AssetSheetAiDetector.detectAssetSheet(
                    imagePath = imagePath,
                    apiKey = apiKey,
                    projectName = state.project?.projectId,
                    onProgress = { _, message ->
                        state = state.copy(aiDetectStatusText = message)
                    }
                )

                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = "",
                    parseResult = result,
                    isImportSheetDialogVisible = false,
                    isVerifySliceDialogVisible = true,
                    verifySliceItems = result.items
                )

                // 自动保存初始草稿
                saveDraft(result.items)
                Toast.show("AI 成功识别出 ${result.items.size} 个图元组件！", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI 视觉图集逆向识别失败: ${e.message}", e)
                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = ""
                )
                Toast.show("AI 识别失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 关闭可视化预审校对弹窗。
     */
    fun closeVerifySliceDialog() {
        state = state.copy(isVerifySliceDialogVisible = false)
    }

    /**
     * 确认校对切片并执行真正的物理透明切片与入库落盘（阶段四核心实现）。
     */
    fun onConfirmSlice(
        items: List<NormalizedAssetItem>,
        repo: TemplateRepository,
        onProjectUpdated: ((ProjectState) -> Unit)? = null
    ) {
        val proj = state.project ?: return
        val imagePath = state.sourceImagePath
        if (imagePath.isBlank() || items.isEmpty()) {
            Toast.show("未指定原图或图元列表为空", ToastType.INFO)
            return
        }

        val initialTaskItems = items.mapIndexed { idx, item ->
            val assetId = item.id.ifBlank { "asset_${getCurrentTimeMillis()}_$idx" }
            SliceTaskItemState(
                id = assetId,
                name = item.name,
                width = item.bounds.width.toInt().coerceAtLeast(1),
                height = item.bounds.height.toInt().coerceAtLeast(1),
                category = item.type.name.lowercase(),
                status = SliceItemStatus.PENDING
            )
        }

        val startTime = getCurrentTimeMillis()

        state = state.copy(
            isVerifySliceDialogVisible = false,
            verifySliceItems = items,
            isSlicingAssets = true,
            sliceProgress = 0f,
            sliceStatusText = "正在读取图集大图...",
            sliceTaskItems = initialTaskItems,
            sliceStartTimeMillis = startTime,
            sliceCurrentStage = SliceStage.PREPARING,
            sliceCurrentItemName = ""
        )

        scope.launch(Dispatchers.Default) {
            try {
                val imageBytes = readLocalFileBytes(imagePath)
                if (imageBytes == null || imageBytes.isEmpty()) {
                    Toast.show("无法读取图集文件: $imagePath", ToastType.ERROR)
                    state = state.copy(isSlicingAssets = false)
                    return@launch
                }

                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val canvasW = state.canvasWidth
                val canvasH = state.canvasHeight

                // ★ 关键性能突破：在循环外仅解码一次大图为共享不可变 Bitmap，彻底消除 N 次巨幅大图重复解码开销！
                val baseImage = try {
                    Image.makeFromEncoded(imageBytes)
                } catch (e: Exception) {
                    AppLogger.e("UIStudioViewModel", "❌ 解码大图失败", e)
                    null
                }
                if (baseImage == null) {
                    Toast.show("无法解析图集图像数据", ToastType.ERROR)
                    state = state.copy(isSlicingAssets = false)
                    return@launch
                }
                val imgWidth = baseImage.width
                val imgHeight = baseImage.height
                val srcBitmap = Bitmap.makeFromImage(baseImage).apply { setImmutable() }

                AppLogger.i("UIStudioViewModel", "=== [SLICE_VERIFY] 确认切图开始: 首个图元[${items.firstOrNull()?.name}] bounds=${items.firstOrNull()?.bounds}, 大图分辨率=${imgWidth}x${imgHeight} ===")

                val targetFolderId = state.selectedFolderId
                val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
                val folderSubDir = if (targetFolder != null) "${targetFolder.name.trim().replace(" ", "_")}/" else ""

                val total = items.size
                val shouldRemoveBg = state.autoRemoveBgOnSlice

                // ★ 阶段一：极速物理多核切片（零大图重复解码，切片流水线全量保底落盘）
                state = state.copy(
                    sliceCurrentStage = SliceStage.SLICING,
                    sliceProgress = 0f,
                    sliceStatusText = "正在执行微秒级物理多核切片 (0/$total)..."
                )

                val concurrencyLimit = 8
                val sliceSemaphore = Semaphore(concurrencyLimit)
                val progressMutex = Mutex()
                var slicedCount = 0

                data class SlicedItemMeta(
                    val index: Int,
                    val item: NormalizedAssetItem,
                    val assetId: String,
                    val relPath: String,
                    val diskAbsolutePath: String
                )

                val slicedMetas = coroutineScope {
                    items.mapIndexed { idx, item ->
                        async(Dispatchers.Default) {
                            sliceSemaphore.withPermit {
                                val assetId = initialTaskItems[idx].id
                                val relPath = "templates/$sanitizedName/assets/image/${folderSubDir}${assetId}.png"
                                val tFile = TemplateFile(relPath)
                                val diskAbsolutePath = tFile.getAbsolutePath()

                                // 更新单项状态为切片中
                                progressMutex.withLock {
                                    val currentList = state.sliceTaskItems.toMutableList()
                                    if (idx < currentList.size) {
                                        currentList[idx] = currentList[idx].copy(status = SliceItemStatus.SLICING)
                                        state = state.copy(
                                            sliceTaskItems = currentList,
                                            sliceCurrentItemName = item.name
                                        )
                                    }
                                }

                                // 1. 调用 Skia 执行微秒级物理切片 (原图绝对物理像素 1:1 精准无损裁切)
                                val sliceBytes = cropImage(
                                    srcBitmap = srcBitmap,
                                    imageWidth = imgWidth,
                                    imageHeight = imgHeight,
                                    bounds = item.bounds,
                                    originalWidth = imgWidth.toFloat(),
                                    originalHeight = imgHeight.toFloat(),
                                    isPng = true
                                )

                                // 2. 物理写入磁盘素材库目录
                                if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                                    tFile.writeBytes(sliceBytes)
                                }

                                // 视觉节流：保证流水线流转可感知（约 15~20ms）
                                delay(20)

                                // 3. 更新进度与单项状态 (已切片完成)
                                progressMutex.withLock {
                                    slicedCount++
                                    val currentList = state.sliceTaskItems.toMutableList()
                                    if (idx < currentList.size) {
                                        currentList[idx] = currentList[idx].copy(
                                            status = if (shouldRemoveBg) SliceItemStatus.SLICED else SliceItemStatus.SUCCESS
                                        )
                                        val slicePartProgress = slicedCount.toFloat() / total.coerceAtLeast(1)
                                        state = state.copy(
                                            sliceTaskItems = currentList,
                                            sliceProgress = slicePartProgress,
                                            sliceStatusText = "正在并发切片 ($slicedCount/$total): ${item.name}"
                                        )
                                    }
                                }

                                SlicedItemMeta(
                                    index = idx,
                                    item = item,
                                    assetId = assetId,
                                    relPath = relPath,
                                    diskAbsolutePath = diskAbsolutePath
                                )
                            }
                        }
                    }.awaitAll().sortedBy { it.index }
                }

                // 阶段一收口停留：让切片完成态清晰可见
                delay(200)

                // ★ 阶段二：若开启自动去背，启动常驻单进程 AI 批量去背景（仅启动 1 次 Python + 模型常驻复用！）
                if (shouldRemoveBg) {
                    state = state.copy(
                        sliceCurrentStage = SliceStage.MATTING,
                        sliceProgress = 0f,
                        sliceCurrentItemName = "",
                        sliceStatusText = "⚡ 正在初始化常驻 AI 去背引擎并批量处理..."
                    )

                    val mattingTasks = slicedMetas.map { meta ->
                        MattingTaskItem(
                            id = meta.assetId,
                            name = meta.item.name,
                            input = meta.diskAbsolutePath,
                            output = meta.diskAbsolutePath
                        )
                    }

                    val mattingSuccess = mattingService.batchRemoveBackground(
                        tasks = mattingTasks,
                        onProgress = { current, totalCount, itemId, itemName ->
                            val currentList = state.sliceTaskItems.toMutableList()
                            val targetIdx = currentList.indexOfFirst { it.id == itemId }
                            if (targetIdx != -1) {
                                currentList[targetIdx] = currentList[targetIdx].copy(status = SliceItemStatus.SUCCESS)
                            }
                            val mattingProgress = (current.toFloat() / totalCount.coerceAtLeast(1))
                            state = state.copy(
                                sliceTaskItems = currentList,
                                sliceProgress = mattingProgress,
                                sliceCurrentItemName = itemName,
                                sliceStatusText = "正在批量 AI 去背景 ($current/$totalCount): $itemName"
                            )
                        },
                        onLog = { logLine ->
                            AppLogger.d("UIStudioViewModel", "[BatchMattingLog] $logLine")
                        }
                    )

                    if (!mattingSuccess) {
                        AppLogger.w("UIStudioViewModel", "⚠️ 批量去背部分完成或异常，原始切片均已完整保底保留")
                    }
                }

                // ★ 阶段三：登记入库与素材库持久化
                state = state.copy(
                    sliceCurrentStage = SliceStage.REGISTERING,
                    sliceProgress = 0.96f,
                    sliceStatusText = "正在登记素材库并持久化..."
                )

                val newAssetItems = slicedMetas.map { meta ->
                    UIAssetItem(
                        id = meta.assetId,
                        relativePath = meta.relPath,
                        width = meta.item.bounds.width.toInt().coerceAtLeast(1),
                        height = meta.item.bounds.height.toInt().coerceAtLeast(1),
                        category = meta.item.type.name.lowercase(),
                        folderId = targetFolderId,
                        hash = calculateShortFileHash(meta.relPath)
                    )
                }

                val updatedLibrary = state.assetLibrary.copy(
                    items = state.assetLibrary.items.filterNot { old -> newAssetItems.any { it.id == old.id } } + newAssetItems
                )

                // 物理清理切片校对草稿文件
                try {
                    val draftPath = "templates/$sanitizedName/import_draft.json"
                    TemplateFile(draftPath).delete()
                } catch (_: Exception) {}

                state = state.copy(
                    assetLibrary = updatedLibrary,
                    sliceProgress = 1.0f,
                    sliceCurrentStage = SliceStage.FINISHED,
                    sliceStatusText = "🎉 全部切片与去背已完成！"
                )

                // 持久化素材库到 asset_library.json
                assetLibrary.saveAssetLibrary()

                withContext(Dispatchers.Main) {
                    onProjectUpdated?.invoke(proj)
                }

                delay(600)
                state = state.copy(
                    isSlicingAssets = false,
                    importDraft = null,
                    isDraftPromptVisible = false,
                    sliceStatusText = ""
                )

                Toast.show("🎉 成功切片并入库 ${newAssetItems.size} 个素材到素材库中！", ToastType.SUCCESS)
                AppLogger.i("UIStudioViewModel", "✅ 物理切片与素材库持久化完成: 共 ${newAssetItems.size} 个素材入库")

            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "❌ 切图入库失败", e)
                Toast.show("切片入库失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isSlicingAssets = false)
            }
        }
    }

    fun requestClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearCurrentSession() {
        val current = state.currentChatSession ?: return
        val updated = current.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        state = state.copy(
            isClearingCurrentSession = false,
            chatSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        )
        saveSession(updated)
        scope.launch(Dispatchers.Default) {
            trafficStore.deleteAll(trafficScopeId, current.id)
        }
        Toast.show("当前会话消息已清空", ToastType.INFO)
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
        loadProjectAgentConfig = ::loadProjectAgentConfig,
        loadChatSessions = ::loadChatSessions,
        refreshSkills = ::refreshSkills,
        startSkillsDirectoryWatcher = ::startSkillsDirectoryWatcher,
        stopSkillsDirectoryWatcher = ::stopSkillsDirectoryWatcher
    )
}

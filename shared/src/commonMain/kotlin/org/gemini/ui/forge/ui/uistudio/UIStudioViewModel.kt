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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.assetsheet.AssetImportDraft
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIPage
import kotlin.random.Random
import kotlinx.datetime.Clock
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.uistudio.model.ProjectAgentConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.readLocalFileBytes

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
    }

    private val storage = LocalFileStorage()
    private val mattingService = LocalMattingService(storage)
    private val configManager = ConfigManager()

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
        val firstPage = effectiveProject.pages.firstOrNull()
        val initialWidth = firstPage?.width?.takeIf { it > 0 } ?: 1920f
        val initialHeight = firstPage?.height?.takeIf { it > 0 } ?: 1080f

        state = state.copy(
            project = effectiveProject,
            currentViewId = firstPage?.id,
            canvasWidth = initialWidth,
            canvasHeight = initialHeight,
            scale = 0.6f,
            panOffset = Offset(100f, 60f),
            selectedBlockId = null
        )

        // 异步加载素材库 (asset_library.json)
        loadAssetLibrary(effectiveProject.projectId)

        // 异步检查并恢复未完成的切片校对草稿
        checkDraft(effectiveProject.projectId)
    }

    /**
     * 读取项目本地的 asset_library.json 加载素材库，并自动触发本地物理切片自愈扫描。
     */
    fun loadAssetLibrary(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/asset_library.json"
            var loadedLib: UIAssetLibrary? = null
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        loadedLib = jsonHelper.decodeFromString<UIAssetLibrary>(bytes.decodeToString())
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取素材库失败或不存在: ${e.message}")
            }

            val baseLib = loadedLib ?: UIAssetLibrary()
            // 触发物理切片与图元元数据自愈对齐引擎
            val healedLib = healAssetLibrary(sanitized, baseLib)
            state = state.copy(assetLibrary = healedLib)
            AppLogger.i("UIStudioViewModel", "已加载素材库: ${healedLib.items.size} 个素材, ${healedLib.folders.size} 个文件夹")

            // 若发生了自愈补齐，则自动持久化更新 asset_library.json
            if (healedLib.items.size != baseLib.items.size) {
                saveAssetLibrary()
            }
        }
    }

    /**
     * 素材库本地物理资产自愈扫描引擎。
     * 当本地已存在切片图片但 asset_library.json 未收录或为空时，
     * 自动从项目图元元数据及物理磁盘 assets 目录扫描补齐，并持久化保存。
     */
    private suspend fun healAssetLibrary(sanitized: String, initialLib: UIAssetLibrary): UIAssetLibrary {
        val existingPaths = initialLib.items.map { it.relativePath.replace("\\", "/") }.toMutableSet()
        val existingIds = initialLib.items.map { it.id }.toMutableSet()
        val newItems = mutableListOf<UIAssetItem>()

        // 1. 优先从当前项目所有页面的图元绑定资产中提取高精度元数据
        val allBlocks = state.project?.pages?.flatMap { it.blocks } ?: emptyList()
        for (block in allBlocks) {
            val tFile = block.currentImageUri ?: block.referenceImage
            val rawUri = tFile?.relativePath ?: ""
            val uri = rawUri.replace("\\", "/")
            if (uri.isNotBlank() && (uri.endsWith(".png", ignoreCase = true) || uri.endsWith(".webp", ignoreCase = true))) {
                if (!existingPaths.contains(uri) && !existingIds.contains(block.id)) {
                    val displayName = block.userPromptZh.ifBlank { block.userPromptEn.ifBlank { block.id } }
                    val item = UIAssetItem(
                        id = block.id,
                        name = displayName,
                        relativePath = uri,
                        width = block.bounds.width.toInt().coerceAtLeast(1),
                        height = block.bounds.height.toInt().coerceAtLeast(1),
                        category = block.type.name.lowercase(),
                        folderId = null,
                        createdAt = getCurrentTimeMillis()
                    )
                    newItems.add(item)
                    existingPaths.add(uri)
                    existingIds.add(block.id)
                }
            }
        }

        // 2. 深度扫描本地物理 assets 目录兜底自愈
        try {
            val assetsDirRel = "templates/$sanitized/assets"
            val directFiles = storage.listFiles(assetsDirRel)
            for (file in directFiles) {
                if (file.endsWith(".png", ignoreCase = true) || file.endsWith(".webp", ignoreCase = true)) {
                    val relPath = "$assetsDirRel/$file"
                    if (!existingPaths.contains(relPath)) {
                        val baseId = file.substringBeforeLast(".")
                        if (!existingIds.contains(baseId)) {
                            newItems.add(
                                UIAssetItem(
                                    id = baseId,
                                    name = baseId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    createdAt = getCurrentTimeMillis()
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(baseId)
                        }
                    }
                }
            }

            val subDirs = storage.listDirectories(assetsDirRel)
            for (sub in subDirs) {
                val subRel = "$assetsDirRel/$sub"
                val subFiles = storage.listFiles(subRel)
                for (file in subFiles) {
                    if (file.endsWith(".png", ignoreCase = true) || file.endsWith(".webp", ignoreCase = true)) {
                        val relPath = "$subRel/$file"
                        if (!existingPaths.contains(relPath)) {
                            val baseId = file.substringBeforeLast(".")
                            val effectiveId = if (existingIds.contains(baseId)) "${sub}_$baseId" else baseId
                            newItems.add(
                                UIAssetItem(
                                    id = effectiveId,
                                    name = if (sub.isNotBlank()) sub else baseId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    createdAt = getCurrentTimeMillis()
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(effectiveId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "物理目录自愈扫描部分跳过: ${e.message}")
        }

        return if (newItems.isNotEmpty()) {
            val updated = initialLib.copy(items = initialLib.items + newItems)
            AppLogger.i("UIStudioViewModel", "✨ 自愈补齐素材库完成: 新增 ${newItems.size} 个物理素材！")
            updated
        } else {
            initialLib
        }
    }

    /**
     * 对素材库中指定的单个素材执行一键去除背景生成透明 PNG。
     */
    fun removeAssetBackground(assetId: String) {
        val asset = state.assetLibrary.items.find { it.id == assetId } ?: return
        if (state.isMattingAsset) return

        state = state.copy(
            isMattingAsset = true,
            mattingAssetId = assetId
        )

        scope.launch(Dispatchers.Default) {
            try {
                Toast.show("🪄 正在调用本地引擎去除背景: ${asset.name}...", ToastType.INFO)
                val tFile = TemplateFile(asset.relativePath)
                val rawBytes = tFile.readBytes()
                if (rawBytes == null || rawBytes.isEmpty()) {
                    Toast.show("无法读取素材文件: ${asset.relativePath}", ToastType.ERROR)
                    state = state.copy(isMattingAsset = false, mattingAssetId = null)
                    return@launch
                }

                val transparentBytes = mattingService.removeBackground(rawBytes) { log ->
                    AppLogger.d("UIStudioViewModel", log)
                }

                if (transparentBytes != null && transparentBytes.isNotEmpty()) {
                    // 原地覆盖写入透明 PNG
                    tFile.writeBytes(transparentBytes)
                    // 更新素材更新时间以触发 UI 重组刷新
                    val updatedItems = state.assetLibrary.items.map {
                        if (it.id == assetId) it.copy(createdAt = getCurrentTimeMillis()) else it
                    }
                    state = state.copy(
                        assetLibrary = state.assetLibrary.copy(items = updatedItems),
                        isMattingAsset = false,
                        mattingAssetId = null
                    )
                    saveAssetLibrary()
                    Toast.show("✨ 成功去除背景并更新为透明图片！", ToastType.SUCCESS)
                } else {
                    state = state.copy(isMattingAsset = false, mattingAssetId = null)
                    Toast.show("抠图处理失败，请检查本地 Python 环境", ToastType.ERROR)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "去除背景异常", e)
                state = state.copy(isMattingAsset = false, mattingAssetId = null)
                Toast.show("去背异常: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 切换切图时是否自动执行一键去背生成透明图片。
     */
    fun updateAutoRemoveBgOnSlice(enabled: Boolean) {
        state = state.copy(autoRemoveBgOnSlice = enabled)
    }

    /**
     * 将当前素材库状态持久化保存至本地磁盘 templates/{proj}/asset_library.json。
     */
    fun saveAssetLibrary() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/asset_library.json"
        scope.launch(Dispatchers.Default) {
            try {
                val jsonStr = jsonHelper.encodeToString(UIAssetLibrary.serializer(), state.assetLibrary)
                TemplateFile(path).writeBytes(jsonStr.encodeToByteArray())
                AppLogger.i("UIStudioViewModel", "素材库保存成功: ${state.assetLibrary.items.size} 个素材")
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存素材库失败", e)
            }
        }
    }

    /**
     * 在素材库中创建新的分类文件夹。
     */
    fun createAssetFolder(name: String, parentFolderId: String? = state.selectedFolderId) {
        if (name.isBlank()) return
        val newFolder = UIAssetFolder(
            id = "folder_${getCurrentTimeMillis()}",
            name = name.trim(),
            parentFolderId = parentFolderId
        )
        val updatedLib = state.assetLibrary.copy(
            folders = state.assetLibrary.folders + newFolder
        )
        state = state.copy(
            assetLibrary = updatedLib,
            selectedFolderId = newFolder.id,
            isNewFolderDialogVisible = false
        )
        saveAssetLibrary()
        Toast.show("成功创建文件夹: $name", ToastType.SUCCESS)
    }

    /**
     * 删除指定素材文件夹，并将该文件夹下的素材平移至根目录。
     */
    fun deleteAssetFolder(folderId: String) {
        val updatedFolders = state.assetLibrary.folders.filterNot { it.id == folderId || it.parentFolderId == folderId }
        val updatedItems = state.assetLibrary.items.map {
            if (it.folderId == folderId) it.copy(folderId = null) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(folders = updatedFolders, items = updatedItems),
            selectedFolderId = if (state.selectedFolderId == folderId) null else state.selectedFolderId
        )
        saveAssetLibrary()
        Toast.show("已删除文件夹", ToastType.INFO)
    }

    /**
     * 选中素材文件夹。
     */
    fun selectAssetFolder(folderId: String?) {
        state = state.copy(selectedFolderId = folderId, selectedAssetId = null)
    }

    /**
     * 选中单个素材。
     */
    fun selectAsset(assetId: String?) {
        state = state.copy(selectedAssetId = assetId)
    }

    /**
     * 删除单个素材并清理磁盘文件。
     */
    fun deleteAsset(assetId: String) {
        val target = state.assetLibrary.items.find { it.id == assetId } ?: return
        val updatedItems = state.assetLibrary.items.filterNot { it.id == assetId }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems),
            selectedAssetId = null
        )
        saveAssetLibrary()
        scope.launch(Dispatchers.Default) {
            try {
                TemplateFile(target.relativePath).writeBytes(ByteArray(0))
            } catch (_: Exception) {}
        }
        Toast.show("已删除素材: ${target.name}", ToastType.INFO)
    }

    fun openNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = true)
    }

    fun closeNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = false)
    }

    // ====== 视图界面 (Views / Pages) 生命周期管理 ======

    /**
     * 选中并激活指定视图界面。
     */
    fun selectView(viewId: String) {
        val page = state.project?.pages?.find { it.id == viewId } ?: return
        state = state.copy(
            currentViewId = viewId,
            canvasWidth = page.width,
            canvasHeight = page.height,
            selectedBlockId = null
        )
    }

    /**
     * 新建一个视图界面（Page）。
     */
    fun createView(name: String, width: Float, height: Float, repo: TemplateRepository) {
        val proj = state.project ?: return
        val viewName = name.trim().ifBlank { "View_${proj.pages.size + 1}" }
        val newPage = UIPage(
            id = "view_${getCurrentTimeMillis()}",
            nameStr = viewName,
            width = width,
            height = height,
            blocks = emptyList()
        )
        val updatedPages = proj.pages + newPage
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(
            project = updatedProject,
            currentViewId = newPage.id,
            canvasWidth = width,
            canvasHeight = height,
            selectedBlockId = null,
            isNewViewDialogVisible = false
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("成功创建视图: $viewName", ToastType.SUCCESS)
    }

    /**
     * 删除指定视图界面。
     */
    fun deleteView(viewId: String, repo: TemplateRepository) {
        val proj = state.project ?: return
        if (proj.pages.size <= 1) {
            Toast.show("至少保留一个主视图界面", ToastType.INFO)
            return
        }
        val updatedPages = proj.pages.filterNot { it.id == viewId }
        val nextViewId = if (state.currentViewId == viewId) updatedPages.firstOrNull()?.id else state.currentViewId
        val nextPage = updatedPages.find { it.id == nextViewId }
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(
            project = updatedProject,
            currentViewId = nextViewId,
            canvasWidth = nextPage?.width ?: 1920f,
            canvasHeight = nextPage?.height ?: 1080f,
            selectedBlockId = null,
            deletingView = null
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("已删除视图", ToastType.INFO)
    }

    fun requestDeleteView(page: UIPage) {
        state = state.copy(deletingView = page)
    }

    fun cancelDeleteView() {
        state = state.copy(deletingView = null)
    }

    fun confirmDeleteView(repo: TemplateRepository) {
        val page = state.deletingView ?: return
        deleteView(page.id, repo)
    }

    fun updateViewSearchQuery(query: String) {
        state = state.copy(viewSearchQuery = query)
    }

    fun openNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = true)
    }

    fun closeNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = false)
    }

    fun openAiChatDialog() {
        state = state.copy(isAiChatDialogVisible = true)
        loadProjectAgentConfig()
        loadChatSessions()
    }

    fun closeAiChatDialog() {
        state = state.copy(isAiChatDialogVisible = false)
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
        val path = "templates/$sanitized/agent_config.json"
        scope.launch(Dispatchers.Default) {
            try {
                val file = TemplateFile(path)
                if (file.exists()) {
                    val text = file.readBytes()?.decodeToString()
                    if (!text.isNullOrBlank()) {
                        val jsonFormat = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val config = jsonFormat.decodeFromString(ProjectAgentConfig.serializer(), text)
                        state = state.copy(projectAgentConfig = config)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载项目 Agent 配置失败", e)
            }
        }
    }

    fun updateProjectAgentConfig(config: ProjectAgentConfig) {
        saveProjectAgentConfig(config)
    }

    fun updateProjectModel(modelName: String) {
        val updated = state.projectAgentConfig.copy(modelName = modelName)
        saveProjectAgentConfig(updated)
    }

    fun updateProjectModel(model: GeminiModel) {
        updateProjectModel(model.modelName)
    }

    fun updateProjectSystemPrompt(systemPrompt: String) {
        val updated = state.projectAgentConfig.copy(systemPrompt = systemPrompt)
        saveProjectAgentConfig(updated)
    }

    fun updateProjectApiFlavor(apiFlavor: ApiFlavor) {
        val updated = state.projectAgentConfig.copy(apiFlavor = apiFlavor)
        saveProjectAgentConfig(updated)
    }

    fun saveProjectAgentConfig(config: ProjectAgentConfig) {
        state = state.copy(projectAgentConfig = config)
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/agent_config.json"
        scope.launch(Dispatchers.Default) {
            try {
                val jsonFormat = kotlinx.serialization.json.Json {
                    encodeDefaults = true
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
                val text = jsonFormat.encodeToString(ProjectAgentConfig.serializer(), config)
                TemplateFile(path).writeBytes(text.encodeToByteArray())
                Toast.show("项目 Agent 配置已更新", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存项目 Agent 配置失败", e)
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
                    val activeId = state.currentChatSessionId ?: loadedSessions.first().id
                    state = state.copy(
                        chatSessions = loadedSessions,
                        currentChatSessionId = activeId
                    )
                    return@launch
                }

                // 首次进入无会话，生成默认初始会话并持久化
                val now = getCurrentTimeMillis()
                val welcomeMsg = VisualChatMessage(
                    id = "msg_${now}_welcome",
                    role = "model",
                    prompt = "🤖 你好！我是当前项目的专属 UI 智能排版架构师。当前视图:「${state.currentView?.nameStr ?: "未选择"}」(${state.currentView?.width?.toInt() ?: 0} × ${state.currentView?.height?.toInt() ?: 0})，素材库已就绪 ${state.assetLibrary.items.size} 个切片资产。你可以向我描述界面排版构想、上传原型草图或让我在当前视图规划组件布局！",
                    timestamp = now
                )
                val initialSession = VisualChatSession(
                    id = "session_${now}",
                    scopeId = sanitized,
                    title = "主界面架构与排版规划",
                    messages = listOf(welcomeMsg),
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

    fun createNewChatSession(title: String = "新布局讨论") {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val now = getCurrentTimeMillis()
        val welcomeMsg = VisualChatMessage(
            id = "msg_${now}_welcome",
            role = "model",
            prompt = "✨ 已开启新的排版讨论会话。请告诉我你想在视图「${state.currentView?.nameStr ?: "当前视图"}」中实现什么样的 UI 布局或业务功能？",
            timestamp = now
        )
        val newSession = VisualChatSession(
            id = "session_${now}",
            scopeId = sanitized,
            title = title,
            messages = listOf(welcomeMsg),
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

        val attachments = if (attachmentsList.isNotEmpty()) {
            attachmentsList.mapNotNull { it.localUri ?: it.name }
        } else {
            state.aiChatAttachments
        }
        val now = getCurrentTimeMillis()
        val userMsg = VisualChatMessage(
            id = "msg_${now}_user",
            role = "user",
            prompt = userText,
            inputImageUris = attachments,
            timestamp = now
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

        scope.launch(Dispatchers.Default) {
            try {
                val effectiveApiKey = apiKey.ifBlank { configManager.loadKey("GEMINI_API_KEY") ?: "" }
                val apiFlavor = state.projectAgentConfig.apiFlavor
                val systemPrompt = state.projectAgentConfig.systemPrompt
                val selectedModel = state.projectAgentConfig.modelName.ifBlank { GeminiModel.GEMINI_3_8_FLASH.modelName }
                val view = state.currentView
                val contextInfo = "当前视图: ${view?.nameStr ?: "无"} (${view?.width ?: 0f} x ${view?.height ?: 0f})，素材库共有 ${state.assetLibrary.items.size} 个资产。"

                var replyContent = ""
                if (effectiveApiKey.isNotBlank()) {
                    try {
                        val client = GeminiClient()
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:streamGenerateContent?key=$effectiveApiKey&alt=sse"
                        val fullPrompt = "$systemPrompt\n\n[上下文信息] $contextInfo\n\n[用户指令] $userText"
                        val requestBody = kotlinx.serialization.json.buildJsonObject {
                            put("contents", kotlinx.serialization.json.buildJsonArray {
                                add(kotlinx.serialization.json.buildJsonObject {
                                    put("parts", kotlinx.serialization.json.buildJsonArray {
                                        add(kotlinx.serialization.json.buildJsonObject { put("text", fullPrompt) })
                                    })
                                })
                            })
                            put("generationConfig", kotlinx.serialization.json.buildJsonObject {
                                put("temperature", state.projectAgentConfig.temperature)
                            })
                        }.toString()
                        val sb = StringBuilder()
                        client.streamGenerateContent(
                            url = url,
                            requestBody = requestBody,
                            onChunk = { chunk: String ->
                                sb.append(chunk)
                            }
                        )
                        replyContent = sb.toString()
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "Gemini API 调用异常，生成备选建议", e)
                        replyContent = "💡 基于驱动「${apiFlavor.displayNameZh}」与模型「$selectedModel」建议：\n1. 建议将核心交互组件置于视图中下方，预留 32dp 安全边距；\n2. 严格按 8pt 空间网格对齐；\n3. 推荐使用素材库已有素材保证风格统一。\n(通信说明: ${e.message ?: "未配置外部通道"})"
                    }
                } else {
                    replyContent = "🤖 [驱动: ${apiFlavor.displayNameZh} · 模型: $selectedModel]\n已接收排版意图：\"$userText\"\n\n【排版规划方案】\n1. 目标视图：${view?.nameStr ?: "MainPage"} (${view?.width?.toInt() ?: 0} × ${view?.height?.toInt() ?: 0})\n2. 推荐布局结构：\n   - 顶部导航栏（高度 64dp，贴合顶部，放置返回与标题）\n   - 中部主内容展示区（自适应居中，8pt 边距）\n   - 底部浮动控制栏（居中水平分布）\n3. 素材应用：素材库已有 ${state.assetLibrary.items.size} 个切片资产，可直接拖入画布对应区域完成装配。"
                }

                val assistantMsg = VisualChatMessage(
                    id = "msg_${getCurrentTimeMillis()}_assistant",
                    role = "model",
                    prompt = replyContent,
                    timestamp = getCurrentTimeMillis()
                )

                val sessionAfterReply = (state.chatSessions.find { it.id == activeSession.id } ?: activeSession).let { cur ->
                    cur.copy(
                        messages = cur.messages + assistantMsg,
                        updatedAt = getCurrentTimeMillis()
                    )
                }
                val finalSessions = state.chatSessions.map { if (it.id == sessionAfterReply.id) sessionAfterReply else it }
                state = state.copy(chatSessions = finalSessions, isAiChatSending = false)
                saveSession(sessionAfterReply)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI Chat 处理异常", e)
                state = state.copy(isAiChatSending = false)
            }
        }
    }

    // ====== 边栏尺寸与自适应居中 ======

    fun updateLeftPanelWidth(width: Dp) {
        state = state.copy(leftPanelWidth = width.coerceIn(200.dp, 500.dp))
    }

    fun updateRightPanelWidth(width: Dp) {
        state = state.copy(rightPanelWidth = width.coerceIn(200.dp, 520.dp))
    }

    fun updateViewsPanelHeightRatio(ratio: Float) {
        state = state.copy(viewsPanelHeightRatio = ratio.coerceIn(0.2f, 0.8f))
    }

    /**
     * 自动将当前视图的画布居中并自适应填充满工作区视口 (Fit to Viewport)。
     */
    fun fitToViewport(viewportWidth: Float, viewportHeight: Float) {
        val canvasW = state.canvasWidth
        val canvasH = state.canvasHeight
        if (viewportWidth <= 0f || viewportHeight <= 0f || canvasW <= 0f || canvasH <= 0f) return

        val padding = 64f
        val availW = (viewportWidth - padding).coerceAtLeast(100f)
        val availH = (viewportHeight - padding).coerceAtLeast(100f)

        val scaleX = availW / canvasW
        val scaleY = availH / canvasH
        val targetScale = minOf(scaleX, scaleY).coerceIn(0.1f, 3.0f)

        val panX = (viewportWidth - canvasW * targetScale) / 2f
        val panY = (viewportHeight - canvasH * targetScale) / 2f

        state = state.copy(
            scale = targetScale,
            panOffset = Offset(panX, panY),
            isFitToViewport = true
        )
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
                TemplateFile(draftPath).writeBytes(ByteArray(0)) // 清空草稿
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
     * 处理画布视口缩放手势。
     *
     * @param factor 缩放变化比率（大于 1 放大，小于 1 缩小）
     * @param pivot 缩放聚焦中心点屏幕绝对坐标
     */
    fun onZoom(factor: Float, pivot: Offset) {
        val oldScale = state.scale
        val newScale = (oldScale * factor).coerceIn(0.1f, 5.0f)
        if (oldScale == newScale) return

        // 以光标为锚点的无损缩放偏移补偿计算
        val scaleRatio = newScale / oldScale
        val newPanX = pivot.x - (pivot.x - state.panOffset.x) * scaleRatio
        val newPanY = pivot.y - (pivot.y - state.panOffset.y) * scaleRatio

        state = state.copy(
            scale = newScale,
            panOffset = Offset(newPanX, newPanY)
        )
    }

    /**
     * 处理画布视口平移拖拽手势。
     *
     * @param delta 平移位移增量向量
     */
    fun onPan(delta: Offset) {
        state = state.copy(
            panOffset = state.panOffset + delta
        )
    }

    /**
     * 重置画布视口至居中默认视野。
     */
    fun resetViewport() {
        state = state.copy(
            scale = 0.6f,
            panOffset = Offset(100f, 60f)
        )
    }

    /**
     * 选中或取消选中指定图元。
     *
     * @param blockId 目标图元 ID，传入 null 表示点击空白处取消选中
     */
    fun selectBlock(blockId: String?) {
        state = state.copy(selectedBlockId = blockId)
    }

    /**
     * 切换左侧素材库面板的展开/折叠显隐状态。
     */
    fun toggleAssetPanel() {
        state = state.copy(isAssetPanelVisible = !state.isAssetPanelVisible)
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
    }

    /**
     * 关闭导入图集配置弹窗。
     */
    fun closeImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = false)
    }

    /**
     * 更新选中的源图片文件路径。
     */
    fun updateSourceImagePath(path: String) {
        state = state.copy(sourceImagePath = path)
    }

    /**
     * 更新选中的源 JSON 坐标文件路径并触发自适应解析。
     */
    fun updateSourceJsonPath(path: String) {
        state = state.copy(sourceJsonPath = path)
        if (path.isNotBlank()) {
            parseJsonFile(path)
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
                    state = state.copy(isParsingJson = false)
                    return@launch
                }
                parseJsonContent(content)
            } catch (e: Exception) {
                Toast.show("读取文件失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isParsingJson = false)
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
        val result = state.parseResult
        val items = result?.items ?: emptyList()
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
        val imagePath = state.sourceImagePath
        if (imagePath.isBlank()) {
            Toast.show("请先选择或输入待识别的图集大图路径", ToastType.INFO)
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

        state = state.copy(
            isVerifySliceDialogVisible = false,
            verifySliceItems = items,
            isSlicingAssets = true,
            sliceProgress = 0f,
            sliceStatusText = "正在读取图集大图..."
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

                val targetFolderId = state.selectedFolderId
                val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
                val folderSubDir = if (targetFolder != null) "${targetFolder.name.trim().replace(" ", "_")}/" else ""

                val newAssetItems = mutableListOf<UIAssetItem>()
                val total = items.size

                val shouldRemoveBg = state.autoRemoveBgOnSlice
                for ((idx, item) in items.withIndex()) {
                    state = state.copy(
                        sliceProgress = (idx + 1).toFloat() / total,
                        sliceStatusText = if (shouldRemoveBg) {
                            "正在切片并去背 (${idx + 1}/$total): ${item.name}"
                        } else {
                            "正在切片 (${idx + 1}/$total): ${item.name}"
                        }
                    )

                    // 1. 调用 Skia 执行高质量透明 Alpha 通道图像物理切片
                    val sliceBytes = cropImage(
                        imageBytes = imageBytes,
                        bounds = item.bounds,
                        originalWidth = canvasW,
                        originalHeight = canvasH,
                        isPng = true
                    )

                    val assetId = item.id.ifBlank { "asset_${getCurrentTimeMillis()}_$idx" }
                    val relPath = "templates/$sanitizedName/assets/${folderSubDir}${assetId}.png"

                    // 2. 将切片物理写入磁盘素材库目录 (支持自动透明去背)
                    if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                        val finalBytes = if (shouldRemoveBg) {
                            val matted = mattingService.removeBackground(sliceBytes)
                            if (matted != null && matted.isNotEmpty()) matted else sliceBytes
                        } else {
                            sliceBytes
                        }
                        val tFile = TemplateFile(relPath)
                        tFile.writeBytes(finalBytes)
                    }

                    // 3. 构建对应的 UIAssetItem 资产实体并归入素材库
                    val assetItem = UIAssetItem(
                        id = assetId,
                        name = item.name,
                        relativePath = relPath,
                        width = item.bounds.width.toInt().coerceAtLeast(1),
                        height = item.bounds.height.toInt().coerceAtLeast(1),
                        category = item.type.name.lowercase(),
                        folderId = targetFolderId,
                        createdAt = getCurrentTimeMillis()
                    )
                    newAssetItems.add(assetItem)
                }

                // 4. 将切片生成的素材归入素材库 (Asset Library)，同步更新状态并持久化落盘
                val updatedLibrary = state.assetLibrary.copy(
                    items = state.assetLibrary.items.filterNot { old -> newAssetItems.any { it.id == old.id } } + newAssetItems
                )

                // 5. 清理切片校对草稿文件
                try {
                    val draftPath = "templates/$sanitizedName/import_draft.json"
                    TemplateFile(draftPath).writeBytes(ByteArray(0))
                } catch (_: Exception) {}

                state = state.copy(
                    assetLibrary = updatedLibrary,
                    isSlicingAssets = false,
                    importDraft = null,
                    isDraftPromptVisible = false,
                    sliceStatusText = ""
                )

                // 6. 持久化素材库到 asset_library.json
                saveAssetLibrary()

                withContext(Dispatchers.Main) {
                    onProjectUpdated?.invoke(proj)
                }

                Toast.show("🎉 成功切片并入库 ${newAssetItems.size} 个素材到素材库中！", ToastType.SUCCESS)
                AppLogger.i("UIStudioViewModel", "✅ 物理切片与素材库持久化完成: 共 ${newAssetItems.size} 个素材入库")

            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "❌ 切图入库失败", e)
                Toast.show("切片入库失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isSlicingAssets = false)
            }
        }
    }

    /**
     * 切换素材文件夹的展开/折叠状态。
     */
    fun toggleFolderExpanded(folderId: String) {
        val current = state.expandedFolderIds
        state = state.copy(
            expandedFolderIds = if (current.contains(folderId)) current - folderId else current + folderId
        )
    }

    fun startRenameFolder(folder: UIAssetFolder) { state = state.copy(renamingFolder = folder) }
    fun cancelRenameFolder() { state = state.copy(renamingFolder = null) }
    fun commitRenameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        val updatedFolders = state.assetLibrary.folders.map {
            if (it.id == folderId) it.copy(name = newName.trim()) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(folders = updatedFolders),
            renamingFolder = null
        )
        saveAssetLibrary()
        Toast.show("文件夹已重命名为: $newName", ToastType.SUCCESS)
    }

    fun startRenameAsset(asset: UIAssetItem) { state = state.copy(renamingAsset = asset) }
    fun cancelRenameAsset() { state = state.copy(renamingAsset = null) }
    fun commitRenameAsset(assetId: String, newName: String) {
        if (newName.isBlank()) return
        val updatedItems = state.assetLibrary.items.map {
            if (it.id == assetId) it.copy(name = newName.trim()) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems),
            renamingAsset = null
        )
        saveAssetLibrary()
        Toast.show("素材已重命名为: $newName", ToastType.SUCCESS)
    }

    fun revealAssetInExplorer(asset: UIAssetItem) {
        val path = TemplateFile(asset.relativePath).getAbsolutePath()
        org.gemini.ui.forge.getPlatform().openInFileExplorer(path)
    }

    fun revealFolderInExplorer(folderId: String? = state.selectedFolderId) {
        val proj = state.project?.projectId ?: "default"
        val folder = state.assetLibrary.folders.find { it.id == folderId }
        val subPath = if (folder != null) "templates/$proj/assets/${folder.name}" else "templates/$proj/assets"
        val path = TemplateFile(subPath).getAbsolutePath()
        org.gemini.ui.forge.getPlatform().openInFileExplorer(path)
    }

    fun addAssetToCanvas(asset: UIAssetItem, repo: TemplateRepository) {
        val view = state.currentView ?: run {
            Toast.show("当前没有处于活跃的视图界面，请先选择或新建视图", ToastType.INFO)
            return
        }
        val centerW = (view.width - asset.width.coerceAtLeast(32)).coerceAtLeast(0f) / 2f
        val centerH = (view.height - asset.height.coerceAtLeast(32)).coerceAtLeast(0f) / 2f
        val newBlock = UIBlock(
            id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
            type = UIBlockType.IMAGE,
            bounds = SerialRect(centerW, centerH, centerW + asset.width, centerH + asset.height),
            currentImageUri = TemplateFile(asset.relativePath),
            userPromptZh = asset.name,
            userPromptEn = asset.id
        )
        val updatedBlocks = view.blocks + newBlock
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: listOf(view.copy(blocks = updatedBlocks))
        val updatedProject = state.project?.copy(pages = updatedPages) ?: return
        state = state.copy(project = updatedProject, selectedBlockId = newBlock.id)
        saveProject(repo)
        Toast.show("已将素材添加到视图: ${asset.name}", ToastType.SUCCESS)
    }

    fun startRenameView(view: UIPage) { state = state.copy(renamingView = view) }
    fun cancelRenameView() { state = state.copy(renamingView = null) }
    fun commitRenameView(viewId: String, newName: String, repo: TemplateRepository) {
        if (newName.isBlank()) return
        val updatedPages = state.project?.pages?.map {
            if (it.id == viewId) it.copy(nameStr = newName.trim()) else it
        } ?: return
        val updatedProject = state.project?.copy(pages = updatedPages) ?: return
        state = state.copy(project = updatedProject, renamingView = null)
        saveProject(repo)
        Toast.show("视图已重命名为: $newName", ToastType.SUCCESS)
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, width: Float, height: Float, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(bounds = SerialRect(left, top, left + width, top + height)) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun updateBlockType(blockId: String, newType: UIBlockType, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(type = newType) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun updateBlockPrompts(blockId: String, userPromptZh: String, userPromptEn: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(userPromptZh = userPromptZh, userPromptEn = userPromptEn) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun updateBlockPureContainer(blockId: String, isPure: Boolean, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(isPureContainer = isPure) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun deleteSelectedBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.filterNot { it.id == blockId }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), selectedBlockId = null)
        saveProject(repo)
        Toast.show("已删除图元", ToastType.INFO)
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
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }

    /**
     * 创建一个带指定名称和分辨率的默认空白模板工程。
     */
    private fun createDefaultBlankProject(name: String, width: Float, height: Float): ProjectState {
        return ProjectState(
            projectId = name,
            pages = listOf(
                UIPage(
                    id = "page_main",
                    nameStr = "MainPage",
                    width = width,
                    height = height,
                    blocks = emptyList()
                )
            )
        )
    }
}

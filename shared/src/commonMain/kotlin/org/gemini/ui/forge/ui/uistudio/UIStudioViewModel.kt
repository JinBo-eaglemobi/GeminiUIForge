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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.data.TemplateFile
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
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIPage
import kotlin.random.Random
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.service.ai.GeminiChatRequest
import org.gemini.ui.forge.service.ai.GeminiChatAttachment
import org.gemini.ui.forge.service.ai.GeminiChatHistoryItem
import org.gemini.ui.forge.service.ai.GeminiGenerationConfig
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

    companion object {
        private const val CONTEXT_INFO_HEADER = "[上下文信息]"
    }

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
    val trafficStore = SessionTrafficStore()
    val trafficScopeId: String get() = "uistudio_${state.project?.projectId?.ifBlank { "default" } ?: "default"}"
    val chatEngine = GeminiChatEngine()

    /** AI 输入栏「@」上下文引用状态管理中心 */
    val mentionState = MentionState()

    init {
        scope.launch(Dispatchers.Default) {
            val skills = UiStudioSkillManager.getRegisteredSkills(bypassCache = true)
            allSkills = skills
            enabledSkillIds = skills.filter { it.isSkillEnabled }.map { it.id }.toSet()
        }
    }

    /** 当前工程引用的外部参考规范文档清单 */
    var registeredExternalDocs by mutableStateOf<List<ExternalDocRecord>>(emptyList())
        private set

    /** 所有已探测并注册的 UI Studio 技能清单 */
    var allSkills by mutableStateOf<List<UiStudioSkill>>(emptyList())
        private set

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
        val projId = state.project?.projectId ?: "default"

        if (registeredExternalDocs.isEmpty()) {
            loadExternalDocs(projId)
        }

        // 1. 历史引用 (Recent References) - 提升为最高第 1 优先级置顶展示，默认最多显示最近引用的 5 项
        if (registeredExternalDocs.isEmpty()) {
            loadExternalDocs(projId)
        }
        val allDocs = registeredExternalDocs.sortedByDescending { it.lastReferencedAt }

        val displayDocs = if (trimmed.isBlank()) {
            allDocs.take(5)
        } else {
            allDocs.filter { it.fileName.contains(trimmed, ignoreCase = true) || it.absolutePath.contains(trimmed, ignoreCase = true) }
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
        state.project?.pages?.forEach { page ->
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

        // 4. 当前视图图层图元 (Blocks - 沉底排列，剥离坐标仅保留尺寸)
        fun collectBlocks(blocks: List<UIBlock>) {
            blocks.forEach { block ->
                val w = block.bounds.width.toInt()
                val h = block.bounds.height.toInt()
                candidates.add(
                    MentionItem(
                        id = "block_${block.id}",
                        title = block.id,
                        subtitle = "类型: ${block.type.name} · 尺寸: ${w}x${h}",
                        category = MentionCategory.BLOCK,
                        icon = Icons.Default.Layers,
                        payload = MentionPayload.BlockPayload(
                            blockId = block.id,
                            blockName = block.id,
                            type = block.type.name,
                            bounds = "",
                            width = w,
                            height = h,
                            isContainer = block.isPureContainer
                        )
                    )
                )
                collectBlocks(block.children)
            }
        }
        state.currentView?.blocks?.let { collectBlocks(it) }

        if (trimmed.isBlank()) return candidates
        return candidates.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                    it.subtitle?.contains(trimmed, ignoreCase = true) == true
        }
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
        val firstPage = effectiveProject.pages.firstOrNull()
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
        loadViewportCache(effectiveProject.projectId)

        // 异步加载工作台边栏布局记忆配置 (workspace_layout.json)
        loadWorkspaceLayout(effectiveProject.projectId)

        // 异步加载素材库 (asset_library.json)
        loadAssetLibrary(effectiveProject.projectId)

        // 异步检查并恢复未完成的切片校对草稿
        checkDraft(effectiveProject.projectId)

        // 异步加载外部参考文档配置 (reference_docs.json)
        loadExternalDocs(effectiveProject.projectId)
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
        val trimmed = name.trim()
        val newFolder = UIAssetFolder(
            id = "folder_${getCurrentTimeMillis()}",
            name = trimmed,
            parentFolderId = parentFolderId
        )
        val sanitizedProject = state.project?.projectId?.replace(" ", "_") ?: "default"
        scope.launch(Dispatchers.Default) {
            try {
                val dirRel = "templates/$sanitizedProject/assets/image/$trimmed/placeholder.tmp"
                TemplateFile(dirRel).createParentDirs()
            } catch (_: Exception) {}
        }

        val updatedLib = state.assetLibrary.copy(
            folders = state.assetLibrary.folders + newFolder
        )
        state = state.copy(
            assetLibrary = updatedLib,
            selectedFolderId = newFolder.id,
            isNewFolderDialogVisible = false
        )
        saveAssetLibrary()
        Toast.show("成功创建文件夹: $trimmed", ToastType.SUCCESS)
    }

    /**
     * 将素材文件移动到指定的文件夹（若 targetFolderId == null 则移动到根目录）。
     * 同步物理移动磁盘文件并更新素材与项目图元引用。
     */
    fun moveAssetToFolder(assetId: String, targetFolderId: String?, repo: TemplateRepository) {
        val asset = state.assetLibrary.items.find { it.id == assetId } ?: return
        if (asset.folderId == targetFolderId) return

        val sanitizedProject = state.project?.projectId?.replace(" ", "_") ?: "default"
        val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
        val targetSubDir = if (targetFolder != null) "${targetFolder.name}/" else ""
        val fileName = asset.relativePath.substringAfterLast("/")
        val newRelPath = "templates/$sanitizedProject/assets/image/${targetSubDir}$fileName"

        scope.launch(Dispatchers.Default) {
            // 物理移动文件（具备旧路径失效智能探测自愈）
            try {
                var resolvedOldFile = TemplateFile(asset.relativePath)
                if (!resolvedOldFile.exists()) {
                    val candidate1 = TemplateFile("templates/$sanitizedProject/assets/image/$fileName")
                    val candidate2 = TemplateFile("templates/$sanitizedProject/assets/$assetId/$fileName")
                    if (candidate1.exists()) {
                        resolvedOldFile = candidate1
                    } else if (candidate2.exists()) {
                        resolvedOldFile = candidate2
                    }
                }
                if (resolvedOldFile.exists()) {
                    val bytes = resolvedOldFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val newFile = TemplateFile(newRelPath)
                        newFile.createParentDirs()
                        newFile.writeBytes(bytes)
                        if (resolvedOldFile.getAbsolutePath() != newFile.getAbsolutePath()) {
                            resolvedOldFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "物理移动素材文件失败: ${e.message}")
            }
        }

        // 更新素材库项
        val updatedItems = state.assetLibrary.items.map {
            if (it.id == assetId) {
                it.copy(folderId = targetFolderId, relativePath = newRelPath)
            } else it
        }

        // 同步递归更新 project 内所有图元（包括容器内嵌套图元）的关联图片路径
        fun updateBlockImages(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                val updatedChildren = updateBlockImages(b.children)
                if (b.id == assetId) {
                    b.copy(
                        currentImageUri = TemplateFile(newRelPath),
                        referenceImage = TemplateFile(newRelPath),
                        children = updatedChildren
                    )
                } else {
                    b.copy(children = updatedChildren)
                }
            }
        }

        val updatedPages = state.project?.pages?.map { page ->
            page.copy(blocks = updateBlockImages(page.blocks))
        }

        val updatedProject = if (updatedPages != null && state.project != null) {
            state.project!!.copy(pages = updatedPages)
        } else state.project

        state = state.copy(
            project = updatedProject,
            assetLibrary = state.assetLibrary.copy(items = updatedItems)
        )
        saveAssetLibrary()
        saveProject(repo)
        val destDesc = targetFolder?.name ?: "根目录"
        Toast.show("素材已移动至: $destDesc", ToastType.SUCCESS)
    }

    /**
     * 递归遍历并更新模块树中的目标图元。
     */
    private fun updateBlockInTreeRecursively(
        blocks: List<UIBlock>,
        targetId: String,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else if (b.children.isNotEmpty()) {
                b.copy(children = updateBlockInTreeRecursively(b.children, targetId, transform))
            } else {
                b
            }
        }
    }

    /**
     * 递归遍历整棵模块树并统一变换所有图元。
     */
    private fun mapAllBlocksRecursively(
        blocks: List<UIBlock>,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            val updated = transform(b)
            if (updated.children.isNotEmpty()) {
                updated.copy(children = mapAllBlocksRecursively(updated.children, transform))
            } else {
                updated
            }
        }
    }

    /**
     * 切换指定组件/图层的锁定状态（递归支持容器内的子图元）。
     */
    fun toggleBlockLock(blockId: String, repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(currentV.blocks, blockId) { b ->
            val newLock = !b.isLocked
            // 若为容器，级联锁定全部子项
            if (b.children.isNotEmpty()) {
                b.copy(isLocked = newLock, children = mapAllBlocksRecursively(b.children) { it.copy(isLocked = newLock) })
            } else {
                b.copy(isLocked = newLock)
            }
        }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
    }

    /**
     * 一键锁定/解锁当前视图的所有组件（全层级递归）：
     * 若存在任一未锁定的组件，则将全部组件锁定；
     * 若所有组件均已锁定，则全部解锁。
     */
    fun toggleAllBlocksLocked(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyUnlocked(list: List<UIBlock>): Boolean = list.any { !it.isLocked || hasAnyUnlocked(it.children) }
        val targetLock = hasAnyUnlocked(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isLocked = targetLock) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetLock) "已锁定当前视图全部组件" else "已解锁当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 一键显示/隐藏当前视图的所有组件（全层级递归）：
     * 若存在任一隐藏的组件，则将全部组件设为显示；
     * 若所有组件均已显示，则全部隐藏。
     */
    fun toggleAllBlocksVisibility(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyHidden(list: List<UIBlock>): Boolean = list.any { !it.isVisible || hasAnyHidden(it.children) }
        val targetVisible = hasAnyHidden(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isVisible = targetVisible) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetVisible) "已显示当前视图全部组件" else "已隐藏当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 快捷发起将两个组件合并至新建纯父容器的确认弹窗。
     */
    fun groupBlocksIntoContainer(draggedId: String, targetId: String) {
        state = state.copy(pendingCreateContainer = draggedId to targetId)
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
        state = state.copy(
            selectedAssetId = assetId,
            selectedBlockId = if (assetId != null) null else state.selectedBlockId
        )
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
     * 选中并激活指定视图界面，同时确保在顶部标签栏中处于打开状态。
     */
    fun selectView(viewId: String) {
        openViewTab(viewId)
    }

    fun updateDragCursorPosition(pos: Offset?) {
        state = state.copy(dragCursorPosition = pos)
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
        cacheCurrentViewport()
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
     * 仅且只将缩放值还原为 100% (1.0f)，绝不修改或重置画布的 X/Y 坐标，杜绝坐标跳动。
     */
    fun resetZoomTo100() {
        state = state.copy(
            scale = 1.0f,
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 暂存当前活跃视口（视图界面或父容器专属标签）的缩放比率与平移偏移量到内存缓存中，
     * 并同步持久化到本地磁盘 `templates/{projectId}/viewport_cache.json`。
     * 当切换标签页或关闭标签页时调用，以保证用户再次打开或切回时能够 100% 还原上次调整的视口布局。
     */
    fun cacheCurrentViewport() {
        val currentKey = state.activeContainerId ?: state.currentViewId ?: return
        val currentCache = state.viewViewportCache.toMutableMap()
        currentCache[currentKey] = ViewportState(
            scale = state.scale,
            panX = state.panOffset.x,
            panY = state.panOffset.y,
            isFitToViewport = state.isFitToViewport
        )
        state = state.copy(viewViewportCache = currentCache)
        state.project?.projectId?.let { saveViewportCache(it) }
    }

    /**
     * 还原指定视图界面或父容器标签页的视口布局。
     * - 若缓存中存在此前保存的视口记录，则精确恢复该记录的 scale、panOffset 以及是否自适应居中；
     * - 若该视图属于首次打开（无历史缓存），则严格以 100% (1.0f) 原始比例居中对齐呈现，绝不自动进行非 100% 的自适应缩放。
     *
     * @param targetKey 目标视图 ID 或容器 ID
     */
    private fun restoreViewportOrDefault(targetKey: String) {
        val cached = state.viewViewportCache[targetKey]
        if (cached != null) {
            state = state.copy(
                scale = cached.scale,
                panOffset = Offset(cached.panX, cached.panY),
                isFitToViewport = cached.isFitToViewport
            )
            if (cached.isFitToViewport) {
                state = state.copy(fitToViewportTrigger = state.fitToViewportTrigger + 1)
            }
        } else {
            // 首次打开默认严格为 100% 原始比例，并在当前舞台居中对齐
            resetZoom()
        }
    }

    /**
     * 从本地磁盘异步加载当前项目的视口历史记忆缓存文件 `viewport_cache.json`。
     */
    fun loadViewportCache(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/viewport_cache.json"
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val map = jsonHelper.decodeFromString<Map<String, ViewportState>>(bytes.decodeToString())
                        state = state.copy(viewViewportCache = map)
                        val activeKey = state.activeContainerId ?: state.currentViewId
                        if (activeKey != null) {
                            withContext(Dispatchers.Main) {
                                restoreViewportOrDefault(activeKey)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取视口缓存失败: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    state = state.copy(isViewportInitialized = true)
                }
            }
        }
    }

    /**
     * 将当前内存中的视口记忆映射持久化写入本地磁盘 `templates/{projectId}/viewport_cache.json`。
     */
    fun saveViewportCache(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/viewport_cache.json"
            try {
                val tFile = TemplateFile(path)
                val jsonStr = jsonHelper.encodeToString(state.viewViewportCache)
                tFile.writeBytes(jsonStr.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存视口缓存失败: ${e.message}")
            }
        }
    }

    /**
     * 从本地磁盘异步加载当前项目的工作台布局记忆配置 `workspace_layout.json`。
     */
    fun loadWorkspaceLayout(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/workspace_layout.json"
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val layout = jsonHelper.decodeFromString(UIStudioLayoutConfig.serializer(), bytes.decodeToString())
                        withContext(Dispatchers.Main) {
                            state = state.copy(
                                leftPanelWidth = layout.leftPanelWidthDp.dp,
                                rightPanelWidth = layout.rightPanelWidthDp.dp,
                                viewsPanelHeightRatio = layout.viewsPanelHeightRatio.coerceIn(0.05f, 0.95f),
                                propertyPanelHeightRatio = layout.propertyPanelHeightRatio.coerceIn(0.05f, 0.95f)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取工作区布局失败: ${e.message}")
            }
        }
    }

    /**
     * 将当前工作台边栏宽度与面板高度比例持久化写入本地磁盘 `templates/{projectId}/workspace_layout.json`。
     */
    fun saveWorkspaceLayout(projectId: String? = null) {
        val targetProjectId = projectId ?: state.project?.projectId ?: return
        scope.launch(Dispatchers.Default) {
            val sanitized = targetProjectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/workspace_layout.json"
            try {
                val layout = UIStudioLayoutConfig(
                    leftPanelWidthDp = state.leftPanelWidth.value,
                    rightPanelWidthDp = state.rightPanelWidth.value,
                    viewsPanelHeightRatio = state.viewsPanelHeightRatio,
                    propertyPanelHeightRatio = state.propertyPanelHeightRatio
                )
                val tFile = TemplateFile(path)
                tFile.createParentDirs()
                val jsonStr = jsonHelper.encodeToString(UIStudioLayoutConfig.serializer(), layout)
                tFile.writeBytes(jsonStr.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存工作区布局失败: ${e.message}")
            }
        }
    }

    fun zoomIn() {
        state = state.copy(scale = (state.scale * 1.2f).coerceIn(0.1f, 5.0f), isFitToViewport = false)
        cacheCurrentViewport()
        markDirty()
    }

    fun zoomOut() {
        state = state.copy(scale = (state.scale / 1.2f).coerceIn(0.1f, 5.0f), isFitToViewport = false)
        cacheCurrentViewport()
        markDirty()
    }

    fun updateDensity(density: Float) {
        if (state.density != density && density > 0f) {
            state = state.copy(density = density)
        }
    }

    fun resetZoom(viewportWidth: Float = 0f, viewportHeight: Float = 0f, density: Float? = null) {
        val currentDensity = (density ?: state.density).coerceAtLeast(0.1f)
        val activeContainer = state.activeContainerBlock
        val currentView = state.currentView
        val effectiveWidth = (activeContainer?.bounds?.width ?: currentView?.width ?: state.canvasWidth)
        val effectiveHeight = (activeContainer?.bounds?.height ?: currentView?.height ?: state.canvasHeight)
        val canvasPhysicalW = effectiveWidth * currentDensity
        val canvasPhysicalH = effectiveHeight * currentDensity

        val vw = if (viewportWidth > 0f) viewportWidth else (state.canvasWindowBounds?.let { it.right - it.left } ?: canvasPhysicalW)
        val vh = if (viewportHeight > 0f) viewportHeight else (state.canvasWindowBounds?.let { it.bottom - it.top } ?: canvasPhysicalH)

        val panX = (vw - canvasPhysicalW) / 2f
        val panY = (vh - canvasPhysicalH) / 2f

        state = state.copy(
            scale = 1.0f,
            panOffset = Offset(panX, panY),
            isFitToViewport = false
        )
        cacheCurrentViewport()
    }

    /**
     * 独立调节选中图元的缩放因数 scaleX 与 scaleY（完全不影响 bounds.width 与 bounds.height 固有尺寸）。
     */
    fun updateSelectedBlockScale(scaleX: Float, scaleY: Float, repo: TemplateRepository) {
        val selectedId = state.selectedBlockId ?: return
        updateBlockScale(selectedId, scaleX, scaleY, repo)
    }

    /**
     * 独立调节指定图元的缩放因数 scaleX 与 scaleY（完全不影响 bounds.width 与 bounds.height 固有尺寸）。
     */
    fun updateBlockScale(blockId: String, scaleX: Float, scaleY: Float, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(scaleX = scaleX, scaleY = scaleY)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    /**
     * 独立调节选中图元的不透明度 opacity（范围 0.0~1.0f）。
     */
    fun updateSelectedBlockOpacity(opacity: Float, repo: TemplateRepository = TemplateRepository()) {
        val selectedId = state.selectedBlockId ?: return
        updateBlockOpacity(selectedId, opacity, repo)
    }

    /**
     * 独立调节指定图元的不透明度 opacity（范围 0.0~1.0f）。
     */
    fun updateBlockOpacity(blockId: String, opacity: Float, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val clamped = opacity.coerceIn(0.0f, 1.0f)
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(opacity = clamped)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    /**
     * 一键重置图元的变换属性（双轴缩放还原 1.0，透明度还原 1.0）。
     */
    fun resetBlockTransform(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(scaleX = 1.0f, scaleY = 1.0f, opacity = 1.0f)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    fun triggerFitToViewport() {
        state = state.copy(
            isFitToViewport = true,
            fitToViewportTrigger = state.fitToViewportTrigger + 1
        )
    }

    /**
     * 在中间顶部标签栏中打开并激活指定视图界面。
     */
    fun openViewTab(viewId: String) {
        cacheCurrentViewport()
        val page = state.project?.pages?.find { it.id == viewId } ?: return
        val currentOpen = state.openViewIds
        val updatedOpen = if (currentOpen.contains(viewId)) currentOpen else currentOpen + viewId
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = viewId,
            canvasWidth = page.width,
            canvasHeight = page.height,
            activeContainerId = null,
            selectedBlockId = null,
            selectedAssetId = null
        )
        restoreViewportOrDefault(viewId)
    }

    /**
     * 选中并激活指定页面视图标签。
     */
    fun selectViewTab(viewId: String) {
        cacheCurrentViewport()
        selectView(viewId)
        state = state.copy(
            activeContainerId = null
        )
        restoreViewportOrDefault(viewId)
    }

    /**
     * 打开父容器的专属独立编辑视图标签页。
     */
    fun openContainerTab(containerId: String) {
        cacheCurrentViewport()
        val currentTabs = state.openContainerTabs
        val updatedTabs = if (currentTabs.contains(containerId)) currentTabs else currentTabs + containerId
        state = state.copy(
            openContainerTabs = updatedTabs,
            activeContainerId = containerId,
            selectedBlockId = containerId
        )
        restoreViewportOrDefault(containerId)
        Toast.show("已打开容器独立布局视图", ToastType.INFO)
    }

    /**
     * 关闭指定父容器的编辑标签页。
     */
    fun closeContainerTab(containerId: String) {
        val currentTabs = state.openContainerTabs
        if (!currentTabs.contains(containerId)) return
        val updatedTabs = currentTabs.filter { it != containerId }
        val newActiveId = if (state.activeContainerId == containerId) {
            updatedTabs.lastOrNull()
        } else {
            state.activeContainerId
        }
        state = state.copy(
            openContainerTabs = updatedTabs,
            activeContainerId = newActiveId
        )
        val targetKey = newActiveId ?: state.currentViewId
        if (targetKey != null) {
            restoreViewportOrDefault(targetKey)
        } else {
            resetZoom()
        }
    }

    /**
     * 切换选中指定父容器编辑标签页。
     */
    fun selectContainerTab(containerId: String) {
        cacheCurrentViewport()
        state = state.copy(
            activeContainerId = containerId,
            selectedBlockId = containerId
        )
        restoreViewportOrDefault(containerId)
    }

    /**
     * 关闭指定视图的标签页。
     */
    fun closeViewTab(viewId: String) {
        val currentOpen = state.openViewIds
        if (!currentOpen.contains(viewId)) return
        val updatedOpen = currentOpen.filter { it != viewId }
        val newActiveId = if (state.currentViewId == viewId) {
            val idx = currentOpen.indexOf(viewId)
            if (updatedOpen.isNotEmpty()) {
                val nextIdx = (idx - 1).coerceAtLeast(0)
                updatedOpen.getOrNull(nextIdx) ?: updatedOpen.firstOrNull()
            } else {
                null
            }
        } else {
            state.currentViewId
        }
        val nextPage = state.project?.pages?.find { it.id == newActiveId }
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = newActiveId,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            activeContainerId = null,
            selectedBlockId = null
        )
        if (newActiveId != null) {
            restoreViewportOrDefault(newActiveId)
        }
    }

    /**
     * 一键关闭中间顶部所有打开的视图标签页，彻底清空画布及组件渲染。
     */
    fun closeAllViewTabs() {
        state = state.copy(
            openViewIds = emptyList(),
            currentViewId = null,
            openContainerTabs = emptyList(),
            activeContainerId = null,
            selectedBlockId = null,
            selectedAssetId = null
        )
        Toast.show("已关闭所有视图标签", ToastType.INFO)
    }

    /**
     * 关闭除指定视图之外的其他所有视图标签页。
     */
    fun closeOtherViewTabs(viewId: String) {
        if (!state.openViewIds.contains(viewId)) return
        val nextPage = state.project?.pages?.find { it.id == viewId }
        state = state.copy(
            openViewIds = listOf(viewId),
            currentViewId = viewId,
            openContainerTabs = emptyList(),
            activeContainerId = null,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            selectedBlockId = null
        )
        Toast.show("已关闭其他视图标签", ToastType.INFO)
    }

    /**
     * 切换左侧边栏下半部分的工作区 Tab (素材库 / 图层层级)。
     */
    fun switchLeftBottomTab(tab: UIStudioLeftBottomTab) {
        state = state.copy(leftBottomTab = tab)
    }

    /**
     * 动态修改指定视图界面的画布宽高尺寸并持久化。
     */
    fun updatePageDimensions(viewId: String, newWidth: Float, newHeight: Float, repo: TemplateRepository) {
        val proj = state.project ?: return
        val targetPage = proj.pages.find { it.id == viewId } ?: return
        val clampedW = newWidth.coerceIn(100f, 10000f)
        val clampedH = newHeight.coerceIn(100f, 10000f)
        val updatedPage = targetPage.copy(width = clampedW, height = clampedH).postProcess()
        val updatedPages = proj.pages.map { if (it.id == viewId) updatedPage else it }
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(
            project = updatedProject,
            canvasWidth = if (state.currentViewId == viewId) clampedW else state.canvasWidth,
            canvasHeight = if (state.currentViewId == viewId) clampedH else state.canvasHeight,
            isDirty = true
        )
    }

    /**
     * 清空当前所有选中状态（组件与素材均重置），联动恢复页面主视图属性面板。
     */
    fun clearSelection() {
        if (state.selectedBlockId != null || state.selectedAssetId != null) {
            state = state.copy(
                selectedBlockId = null,
                selectedAssetId = null
            )
        }
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
        val updatedOpen = state.openViewIds + newPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = newPage.id,
            openViewIds = updatedOpen,
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
     * 复制并克隆指定视图界面 (含全部图元深度复制)。
     */
    fun duplicateView(page: UIPage, repo: TemplateRepository) {
        val proj = state.project ?: return
        val newViewName = "${page.nameStr}_副本"
        val timestamp = getCurrentTimeMillis()
        val clonedBlocks = page.blocks.map { block ->
            block.copy(id = "block_${timestamp}_${block.id.takeLast(6)}")
        }
        val duplicatedPage = UIPage(
            id = "view_$timestamp",
            nameStr = newViewName,
            width = page.width,
            height = page.height,
            blocks = clonedBlocks
        )
        val updatedPages = proj.pages + duplicatedPage
        val updatedProject = proj.copy(pages = updatedPages)
        val updatedOpen = state.openViewIds + duplicatedPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = duplicatedPage.id,
            openViewIds = updatedOpen,
            canvasWidth = duplicatedPage.width,
            canvasHeight = duplicatedPage.height,
            selectedBlockId = null
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("已克隆视图: $newViewName", ToastType.SUCCESS)
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
        refreshSkills(userInitiated = false)
        startSkillsDirectoryWatcher()
    }

    fun closeAiChatDialog() {
        state = state.copy(isAiChatDialogVisible = false)
        stopSkillsDirectoryWatcher()
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
            enabledSkillIds = enabledSkillIds.filter { it in validIds }.toSet()
            if (enabledSkillIds.isEmpty() && newSkills.isNotEmpty()) {
                enabledSkillIds = newSkills.filter { it.isSkillEnabled }.map { it.id }.toSet()
            }
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
                    enabledSkillIds = enabledSkillIds.filter { it in validIds }.toSet()
                    if (enabledSkillIds.isEmpty() && newSkills.isNotEmpty()) {
                        enabledSkillIds = newSkills.filter { it.isSkillEnabled }.map { it.id }.toSet()
                    }
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

                // 2. 无感平滑清理：检测并安全删除单项目目录下遗留的旧版 agent_config.json
                val legacyFile = TemplateFile(legacyPath)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    AppLogger.i("UIStudioViewModel", "🧹 已清理项目私有遗留配置文件: $legacyPath")
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载全局 Agent 人设配置失败", e)
            }
        }
    }

    fun updateProjectAgentConfig(config: ProjectAgentConfig) {
        saveProjectAgentConfig(config)
    }

    fun updateProjectModel(modelName: String) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(modelName = modelName))
        Toast.show("已切换模型: $modelName", ToastType.SUCCESS)
    }

    fun updateProjectModel(model: GeminiModel) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(modelName = model.modelName))
        Toast.show("已切换模型: ${model.displayName}", ToastType.SUCCESS)
    }

    fun updateProjectSystemPrompt(systemPrompt: String) {
        val updated = state.projectAgentConfig.copy(systemPrompt = systemPrompt)
        saveProjectAgentConfig(updated, notifyUser = true)
    }

    fun updateProjectApiFlavor(apiFlavor: ApiFlavor) {
        state = state.copy(projectAgentConfig = state.projectAgentConfig.copy(apiFlavor = apiFlavor))
        Toast.show("已切换通信协议: ${apiFlavor.displayNameZh}", ToastType.SUCCESS)
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

        scope.launch(Dispatchers.Default) {
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
                    val sessionAfterReply = (state.chatSessions.find { it.id == activeSession.id } ?: activeSession).let { cur ->
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
                val view = state.currentView
                val contextInfo = "当前视图: ${view?.nameStr ?: "无"} (${view?.width ?: 0f} x ${view?.height ?: 0f})，素材库共有 ${state.assetLibrary.items.size} 个资产。"

                val preferCloud = attachmentsList.any { it.preferCloudUpload }
                val chatAttachments = attachmentsList.map { attach ->
                    GeminiChatAttachment(
                        name = attach.name,
                        localUri = attach.localUri,
                        bytes = attach.bytes,
                        mimeType = attach.mimeType
                    )
                }.toMutableList()

                // 提取已选中的 @ 上下文实体并组装格式化 XML (仅包含真实存在的物理实体)
                val mentionContextBuilder = StringBuilder()

                if (attachedMentionsList.isNotEmpty()) {
                    mentionContextBuilder.append("\n\n<context_attachments>\n")
                    for (mention in attachedMentionsList) {
                        when (val p = mention.payload) {
                            is MentionPayload.ViewPayload -> {
                                mentionContextBuilder.append("  <view id=\"${p.viewId}\" name=\"${p.viewName}\" resolution=\"${p.width}x${p.height}\" block_count=\"${p.blockCount}\"/>\n")
                            }
                            is MentionPayload.BlockPayload -> {
                                val dim = if (p.width > 0 && p.height > 0) " dimensions=\"${p.width}x${p.height}\"" else ""
                                mentionContextBuilder.append("  <block id=\"${p.blockId}\" name=\"${p.blockName}\" type=\"${p.type}\"$dim is_container=\"${p.isContainer}\"/>\n")
                            }
                            is MentionPayload.AssetPayload -> {
                                if (p.fileName == "ALL_ASSETS_MANIFEST") {
                                    val manifest = AssetManifestHelper.buildCompactManifest(state.assetLibrary.items)
                                    mentionContextBuilder.append("  <asset_library_manifest total=\"${state.assetLibrary.items.size}\">\n$manifest\n  </asset_library_manifest>\n")
                                } else {
                                    val role = AssetManifestHelper.inferAssetRole(p.fileName, p.filePath)
                                    mentionContextBuilder.append("  <asset id=\"${p.assetId}\" file_name=\"${p.fileName}\" dimensions=\"${p.width}x${p.height}\" role=\"$role\" path=\"${p.filePath}\"/>\n")
                                    // 若为切片素材，自动尝试读取本地文件字节流以多模态附件发送
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
                                    val content = p.content ?: try {
                                        readLocalFileBytes(p.absolutePath)?.decodeToString() ?: ""
                                    } catch (e: Exception) { "" }
                                    mentionContextBuilder.append("  <document name=\"${p.fileName}\" path=\"${p.absolutePath}\">\n$content\n  </document>\n")
                                    // 随消息真实发送后，正式登记沉淀为该工程的历史引用记录
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
                        }
                    }
                    mentionContextBuilder.append("</context_attachments>\n")
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

                // 若用户意图涉及界面生成或排版，且素材库不为空且未手动挂载全部素材清单，则自动补充注入素材库切片清单
                val hasAllAssetsMention = attachedMentionsList.any {
                    val p = it.payload
                    p is MentionPayload.AssetPayload && p.fileName == "ALL_ASSETS_MANIFEST"
                }
                if (!hasAllAssetsMention && state.assetLibrary.items.isNotEmpty()) {
                    val isLayoutIntent = triggeredSkills.any { it.id == UiStudioSkillManager.SPEC_TO_VIEW_SKILL_ID } ||
                            userText.contains("生成界面") || userText.contains("创建界面") || userText.contains("排版") ||
                            userText.contains("布局") || userText.contains("老虎机") || userText.contains("slot")
                    if (isLayoutIntent) {
                        val manifest = AssetManifestHelper.buildCompactManifest(state.assetLibrary.items)
                        mentionContextBuilder.append("\n<asset_library_manifest total=\"${state.assetLibrary.items.size}\">\n$manifest\n</asset_library_manifest>\n")
                    }
                }

                val skillPromptAddition = triggeredSkills.joinToString("\n\n") { it.effectiveInstructions }
                val effectiveSystemPrompt = if (skillPromptAddition.isNotBlank()) {
                    "$systemPrompt\n\n$skillPromptAddition".trim()
                } else {
                    systemPrompt
                }
                val toolConfigs = UiStudioSkillManager.buildToolConfigs(triggeredSkills)

                // 注入历史消息
                val historyItems = activeSession.messages.filter { it.id != userMsg.id }.map { msg ->
                    GeminiChatHistoryItem(
                        role = msg.role,
                        text = msg.effectiveText
                    )
                }

                val resolvedModel = GeminiModel.fromModelName(selectedModel) ?: GeminiModel.GEMINI_3_8_FLASH

                val chatRequest = GeminiChatRequest(
                    apiKey = effectiveApiKey,
                    model = resolvedModel,
                    apiFlavor = apiFlavor,
                    prompt = userText,
                    systemInstruction = "$effectiveSystemPrompt\n\n$CONTEXT_INFO_HEADER $contextInfo$mentionContextBuilder",
                    tools = toolConfigs,
                    attachments = chatAttachments,
                    preferCloudUpload = preferCloud,
                    history = historyItems,
                    previousInteractionId = activeSession.latestInteractionId,
                    generationConfig = GeminiGenerationConfig(
                        temperature = state.projectAgentConfig.temperature.toDouble()
                    )
                )

                val result = try {
                    chatEngine.executeChat(
                        request = chatRequest,
                        onChunk = { chunk ->
                            state = state.copy(streamingAiText = state.streamingAiText + chunk)
                        },
                        onRawTraffic = { direction, rawUrl, body ->
                            scope.launch(Dispatchers.Default) {
                                val record = TrafficRecord(
                                    seq = 0,
                                    direction = direction,
                                    timestamp = getCurrentTimeMillis(),
                                    url = rawUrl,
                                    model = selectedModel,
                                    body = body
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
                    state = state.copy(streamingAiText = "")
                }

                if (!result.isSuccess) {
                    val errMsg = result.errorMessage ?: "服务调用未成功"
                    withContext(Dispatchers.Main) {
                        Toast.show("AI 响应异常: $errMsg", ToastType.ERROR)
                    }
                }

                val finalAssistantText = if (result.text.isNotBlank()) {
                    result.text
                } else if (!result.isSuccess) {
                    result.errorMessage ?: "服务端未返回错误详情"
                } else {
                    "（未收到有效文本内容）"
                }

                val assistantMsg = VisualChatMessage(
                    id = "msg_${getCurrentTimeMillis()}_assistant",
                    role = "model",
                    prompt = finalAssistantText,
                    variants = listOf(finalAssistantText),
                    activeVariantIndex = 0,
                    thought = result.thoughtText,
                    thoughtSignature = result.thoughtSignature,
                    interactionId = result.interactionId,
                    modelName = result.modelName ?: selectedModel,
                    durationMs = result.durationMs,
                    promptTokens = result.promptTokens,
                    candidatesTokens = result.candidatesTokens,
                    totalTokens = result.totalTokens,
                    timestamp = getCurrentTimeMillis(),
                    isError = !result.isSuccess,
                    errorMessage = result.errorMessage
                )

                val sessionAfterReply = (state.chatSessions.find { it.id == activeSession.id } ?: activeSession).let { cur ->
                    cur.copy(
                        messages = cur.messages + assistantMsg,
                        latestInteractionId = result.interactionId ?: cur.latestInteractionId,
                        updatedAt = getCurrentTimeMillis()
                    )
                }
                val finalSessions = state.chatSessions.map { if (it.id == sessionAfterReply.id) sessionAfterReply else it }
                state = state.copy(chatSessions = finalSessions, isAiChatSending = false)
                saveSession(sessionAfterReply)

                // 自动检查大模型是否发起了 apply_view_layout 工具调用并自动装配
                if (result.isSuccess) {
                    checkAndExecuteLayoutTool(result.rawResponseJson, result.text)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI Chat 处理异常", e)
                state = state.copy(isAiChatSending = false)
            }
        }
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
     * 确认应用 AI 生成的界面排版
     *
     * @param replaceCurrent 是否覆盖替换当前视图
     * @param targetName 目标视图名称
     */
    fun confirmApplyLayout(replaceCurrent: Boolean, targetName: String) {
        val result = state.pendingLayoutResult ?: return
        val rawPage = result.createdPage ?: return
        val existingPages = state.project?.pages ?: emptyList()

        val finalPage = if (replaceCurrent) {
            // 替换当前正在编辑的视图，继承当前视图的 id 与 name
            val currentView = state.currentView ?: existingPages.firstOrNull()
            val targetId = currentView?.id ?: rawPage.id
            val targetNameStr = currentView?.nameStr ?: targetName.ifBlank { rawPage.nameStr }
            rawPage.copy(
                id = targetId,
                nameStr = targetNameStr
            )
        } else {
            // 作为新视图创建
            val cleanName = targetName.ifBlank { rawPage.nameStr }
            val newId = "page_${cleanName.replace(" ", "_")}_${getCurrentTimeMillis() % 10000}"
            rawPage.copy(
                id = newId,
                nameStr = cleanName
            )
        }

        val updatedPages = if (replaceCurrent) {
            existingPages.map { if (it.id == finalPage.id) finalPage else it }
        } else {
            existingPages + finalPage
        }

        val updatedProject = state.project?.copy(pages = updatedPages)
        if (updatedProject != null) {
            state = state.copy(
                project = updatedProject,
                currentViewId = finalPage.id,
                openViewIds = (state.openViewIds + finalPage.id).distinct(),
                pendingLayoutResult = null,
                isDirty = true,
                isFitToViewport = true
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

        scope.launch(Dispatchers.Default) {
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
                val view = state.currentView
                val contextInfo = "当前视图: ${view?.nameStr ?: "无"} (${view?.width ?: 0f} x ${view?.height ?: 0f})，素材库共有 ${state.assetLibrary.items.size} 个资产。"

                val chatAttachments = userMsg.inputImageUris.map { uri ->
                    GeminiChatAttachment(
                        name = uri.substringAfterLast("/"),
                        localUri = uri,
                        bytes = readLocalFileBytes(uri),
                        mimeType = if (uri.endsWith(".png", true)) "image/png" else "image/jpeg"
                    )
                }

                val historyItems = activeSession.messages.take(targetIndex).filter { it.id != userMsg.id }.map { msg ->
                    GeminiChatHistoryItem(
                        role = msg.role,
                        text = msg.effectiveText
                    )
                }

                val resolvedModel = GeminiModel.fromModelName(selectedModel) ?: GeminiModel.GEMINI_3_8_FLASH

                val previousInteractionId = activeSession.messages
                    .take(targetIndex)
                    .lastOrNull { !it.interactionId.isNullOrBlank() }
                    ?.interactionId ?: activeSession.latestInteractionId

                val chatRequest = GeminiChatRequest(
                    apiKey = effectiveApiKey,
                    model = resolvedModel,
                    apiFlavor = apiFlavor,
                    prompt = userMsg.prompt,
                    systemInstruction = "$systemPrompt\n\n$CONTEXT_INFO_HEADER $contextInfo",
                    attachments = chatAttachments,
                    preferCloudUpload = true,
                    history = historyItems,
                    previousInteractionId = previousInteractionId,
                    generationConfig = GeminiGenerationConfig(
                        temperature = state.projectAgentConfig.temperature.toDouble()
                    )
                )

                val result = chatEngine.executeChat(
                    request = chatRequest,
                    onChunk = { chunk ->
                        state = state.copy(streamingAiText = state.streamingAiText + chunk)
                    },
                    onRawTraffic = { direction, rawUrl, body ->
                        scope.launch(Dispatchers.Default) {
                            val record = TrafficRecord(
                                seq = 0,
                                direction = direction,
                                timestamp = getCurrentTimeMillis(),
                                url = rawUrl,
                                model = selectedModel,
                                body = body
                            )
                            trafficStore.append(record, trafficScopeId, activeSession.id)
                        }
                    }
                )

                val finalResultText = if (result.text.isNotBlank()) {
                    result.text
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
                        modelName = result.modelName ?: selectedModel,
                        durationMs = result.durationMs,
                        promptTokens = result.promptTokens,
                        candidatesTokens = result.candidatesTokens,
                        totalTokens = result.totalTokens,
                        timestamp = getCurrentTimeMillis(),
                        isError = !result.isSuccess,
                        errorMessage = result.errorMessage
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
                        modelName = result.modelName ?: selectedModel,
                        durationMs = result.durationMs,
                        promptTokens = result.promptTokens,
                        candidatesTokens = result.candidatesTokens,
                        totalTokens = result.totalTokens,
                        timestamp = getCurrentTimeMillis(),
                        isError = !result.isSuccess,
                        errorMessage = result.errorMessage
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
                state = state.copy(streamingAiText = "")
            }
        }
    }

    // ====== 边栏尺寸与自适应居中 ======

    fun updateLeftPanelWidth(width: Dp, totalContainerWidth: Dp? = null) {
        val minWidth = 60.dp
        val maxWidth = if (totalContainerWidth != null && totalContainerWidth > minWidth) {
            val intersectWidth = (totalContainerWidth - state.rightPanelWidth).coerceAtLeast(minWidth)
            (intersectWidth * 0.90f).coerceAtLeast(minWidth)
        } else {
            1200.dp
        }
        state = state.copy(leftPanelWidth = width.coerceIn(minWidth, maxWidth))
    }

    fun updateRightPanelWidth(width: Dp, totalContainerWidth: Dp? = null) {
        val minWidth = 60.dp
        val maxWidth = if (totalContainerWidth != null && totalContainerWidth > minWidth) {
            val leftW = if (state.isAssetPanelVisible) state.leftPanelWidth else 0.dp
            val intersectWidth = (totalContainerWidth - leftW).coerceAtLeast(minWidth)
            (intersectWidth * 0.90f).coerceAtLeast(minWidth)
        } else {
            1200.dp
        }
        state = state.copy(rightPanelWidth = width.coerceIn(minWidth, maxWidth))
    }

    fun updateViewsPanelHeightRatio(ratio: Float) {
        state = state.copy(viewsPanelHeightRatio = ratio.coerceIn(0.05f, 0.95f))
    }

    /**
     * 自动将当前视图的画布居中并自适应填充满工作区视口 (Fit to Viewport)。
     * 支持传入自定义页面/容器宽高（若未传则优先使用当前激活容器的宽高，其次使用主视图页面宽高）。
     */
    fun fitToViewport(
        viewportWidth: Float,
        viewportHeight: Float,
        customWidth: Float? = null,
        customHeight: Float? = null,
        density: Float? = null
    ) {
        val currentDensity = (density ?: state.density).coerceAtLeast(0.1f)
        val activeContainer = state.activeContainerBlock
        val currentView = state.currentView
        val effectiveWidth = (customWidth ?: activeContainer?.bounds?.width ?: currentView?.width ?: state.canvasWidth)
        val effectiveHeight = (customHeight ?: activeContainer?.bounds?.height ?: currentView?.height ?: state.canvasHeight)
        if (viewportWidth <= 0f || viewportHeight <= 0f || effectiveWidth <= 0f || effectiveHeight <= 0f) return

        val canvasPhysicalW = effectiveWidth * currentDensity
        val canvasPhysicalH = effectiveHeight * currentDensity

        val paddingPx = 64f * currentDensity
        val availW = (viewportWidth - paddingPx).coerceAtLeast(100f)
        val availH = (viewportHeight - paddingPx).coerceAtLeast(100f)

        val scaleX = availW / canvasPhysicalW
        val scaleY = availH / canvasPhysicalH
        val targetScale = minOf(scaleX, scaleY).coerceIn(0.1f, 3.0f)

        val panX = (viewportWidth - canvasPhysicalW * targetScale) / 2f
        val panY = (viewportHeight - canvasPhysicalH * targetScale) / 2f

        state = state.copy(
            scale = targetScale,
            panOffset = Offset(panX, panY),
            isFitToViewport = true
        )
        cacheCurrentViewport()
    }

    /**
     * 更新指定页面的画布参考底色（用于在编辑中直观区隔渲染区域与无限舞台背景）。
     */
    fun updatePageCanvasBackgroundColor(pageId: String, colorHex: String?, repo: TemplateRepository) {
        val proj = state.project ?: return
        val updatedPages = proj.pages.map { page ->
            if (page.id == pageId) page.copy(canvasBackgroundColor = colorHex) else page
        }
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(project = updatedProject)
        markDirty()
        scope.launch(Dispatchers.Default) {
            try {
                repo.saveTemplate(updatedProject.projectId, updatedProject)
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存页面参考背景色失败", e)
            }
        }
    }

    /**
     * 更新右侧属性面板与预览面板的高度比例。
     */
    fun updatePropertyPanelHeightRatio(ratio: Float) {
        state = state.copy(propertyPanelHeightRatio = ratio.coerceIn(0.05f, 0.95f))
    }

    /**
     * 设置素材拖拽悬停至画布的高亮状态。
     */
    fun setCanvasDragHovered(isHovered: Boolean) {
        if (state.isCanvasDragHovered != isHovered) {
            state = state.copy(isCanvasDragHovered = isHovered)
        }
    }

    /**
     * 更新画布在窗口中的绝对物理矩形包围盒。
     */
    fun updateCanvasWindowBounds(bounds: org.gemini.ui.forge.model.ui.SerialRect?) {
        state = state.copy(canvasWindowBounds = bounds)
    }

    /**
     * 更新素材拖拽悬停时的最近光标绝对坐标。
     */
    fun updateLastDragHoverPos(pos: androidx.compose.ui.geometry.Offset?) {
        state = state.copy(lastDragHoverPos = pos)
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
            panOffset = Offset(newPanX, newPanY),
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 处理画布视口平移拖拽手势。
     *
     * @param delta 平移位移增量向量
     */
    fun onPan(delta: Offset) {
        state = state.copy(
            panOffset = state.panOffset + delta,
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 重置画布视口至居中 100% (1.0f) 原寸视野。
     */
    fun resetViewport() {
        resetZoom()
    }

    /**
     * 选中或取消选中指定图元。
     * 若处于全局主视图模式且选中的是父容器内部子组件，根据层级门禁规则强制纠偏选中其根父组件。
     *
     * @param blockId 目标图元 ID，传入 null 表示点击空白处取消选中
     */
    fun selectBlock(blockId: String?) {
        if (blockId == null) {
            state = state.copy(
                selectedBlockId = null,
                selectedAssetId = state.selectedAssetId
            )
            return
        }
        val currentV = state.currentView
        val effectiveId = if (state.activeContainerId == null && currentV != null) {
            val rootParent = currentV.blocks.find { root ->
                root.id == blockId || root.children.any { c -> c.id == blockId || c.children.any { cc -> cc.id == blockId } }
            }
            rootParent?.id ?: blockId
        } else {
            blockId
        }
        state = state.copy(
            selectedBlockId = effectiveId,
            selectedAssetId = null
        )
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
                saveViewportCache(proj.projectId)
                saveWorkspaceLayout(proj.projectId)
                state = state.copy(isDirty = false)
                Toast.show("项目保存成功", ToastType.SUCCESS)
            } catch (e: Exception) {
                Toast.show("保存失败: ${e.message}", ToastType.ERROR)
            } finally {
                state = state.copy(isSaving = false)
            }
        }
    }

    /** 执行深拷贝以避免快照与内存状态的引用污染 */
    private fun deepCopyProject(project: ProjectState): ProjectState {
        val copiedPages = project.pages.map { page ->
            page.copy(blocks = deepCopyBlocks(page.blocks))
        }
        return project.copy(pages = copiedPages)
    }

    private fun deepCopyBlocks(blocks: List<UIBlock>): List<UIBlock> {
        return blocks.map { block ->
            block.copy(
                bounds = block.bounds.copy(),
                cropRect = block.cropRect?.copy(),
                ninePatchConfig = block.ninePatchConfig.copy(),
                properties = block.properties,
                children = deepCopyBlocks(block.children)
            )
        }
    }

    /**
     * 保存当前项目状态至撤销快照栈 (最大深度 50)。
     */
    fun saveSnapshot(label: String = "操作") {
        val proj = state.project ?: return
        val snapshot = deepCopyProject(proj)
        val entry = UIStudioHistoryEntry(label = label, project = snapshot)
        val newUndo = (state.undoStack + entry).let { if (it.size > 50) it.drop(1) else it }
        state = state.copy(undoStack = newUndo, redoStack = emptyList())
    }

    /**
     * 撤销上一步操作 (Ctrl+Z / Cmd+Z)。
     */
    fun undo() {
        if (state.undoStack.isEmpty()) {
            Toast.show("暂无可撤销的历史记录", ToastType.INFO)
            return
        }
        val currentProj = state.project ?: return
        val currentSnapshot = deepCopyProject(currentProj)
        val lastEntry = state.undoStack.last()
        val newUndo = state.undoStack.dropLast(1)
        val newRedo = state.redoStack + UIStudioHistoryEntry(label = "撤销", project = currentSnapshot)
        state = state.copy(
            project = deepCopyProject(lastEntry.project),
            undoStack = newUndo,
            redoStack = newRedo,
            isDirty = true
        )
        Toast.show("已撤销: ${lastEntry.label}", ToastType.INFO)
    }

    /**
     * 重做下一步操作 (Ctrl+Y / Ctrl+Shift+Z / Cmd+Shift+Z)。
     */
    fun redo() {
        if (state.redoStack.isEmpty()) {
            Toast.show("已是最新操作，无可重做记录", ToastType.INFO)
            return
        }
        val currentProj = state.project ?: return
        val currentSnapshot = deepCopyProject(currentProj)
        val nextEntry = state.redoStack.last()
        val newRedo = state.redoStack.dropLast(1)
        val newUndo = state.undoStack + UIStudioHistoryEntry(label = "重做", project = currentSnapshot)
        state = state.copy(
            project = deepCopyProject(nextEntry.project),
            undoStack = newUndo,
            redoStack = newRedo,
            isDirty = true
        )
        Toast.show("已重做: ${nextEntry.label}", ToastType.INFO)
    }

    fun copySelectedBlock() {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要复制的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = view.blocks.findBlockById(blockId) ?: return
        state = state.copy(clipboardBlock = target)
        Toast.show("已复制组件: ${target.userPromptZh.ifBlank { target.id }}", ToastType.SUCCESS)
    }

    fun cutSelectedBlock(repo: TemplateRepository = TemplateRepository()) {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要剪切的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = view.blocks.findBlockById(blockId) ?: return
        saveSnapshot("剪切组件")
        state = state.copy(clipboardBlock = target)
        deleteSelectedBlock(blockId, repo)
        Toast.show("已剪切组件: ${target.userPromptZh.ifBlank { target.id }}", ToastType.SUCCESS)
    }

    fun pasteBlock(repo: TemplateRepository = TemplateRepository()) {
        val copied = state.clipboardBlock ?: run {
            Toast.show("剪贴板中暂无组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        saveSnapshot("粘贴组件")
        val timestamp = getCurrentTimeMillis()
        val newId = "block_${timestamp % 100000}_${(100..999).random()}"
        val offsetDistance = 20f
        val newBounds = SerialRect(
            copied.bounds.left + offsetDistance,
            copied.bounds.top + offsetDistance,
            copied.bounds.right + offsetDistance,
            copied.bounds.bottom + offsetDistance
        )
        val newBlock = copied.copy(
            id = newId,
            bounds = newBounds
        )
        val updatedBlocks = (view.blocks + newBlock).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = newBlock.id)
        Toast.show("已粘贴组件: ${newBlock.userPromptZh.ifBlank { newBlock.id }}", ToastType.SUCCESS)
    }

    fun duplicateSelectedBlock(repo: TemplateRepository = TemplateRepository()) {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要克隆的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = view.blocks.findBlockById(blockId) ?: return
        saveSnapshot("克隆组件")
        val timestamp = getCurrentTimeMillis()
        val newId = "block_${timestamp % 100000}_${(100..999).random()}"
        val offsetDistance = 20f
        val newBounds = SerialRect(
            target.bounds.left + offsetDistance,
            target.bounds.top + offsetDistance,
            target.bounds.right + offsetDistance,
            target.bounds.bottom + offsetDistance
        )
        val newBlock = target.copy(
            id = newId,
            bounds = newBounds
        )
        val updatedBlocks = (view.blocks + newBlock).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = newBlock.id)
        Toast.show("已克隆组件: ${newBlock.userPromptZh.ifBlank { newBlock.id }}", ToastType.SUCCESS)
    }

    fun nudgeSelectedBlock(dx: Float, dy: Float) {
        val blockId = state.selectedBlockId ?: return
        saveSnapshot("微调组件")
        moveBlockDelta(blockId, dx, dy)
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

                val targetFolderId = state.selectedFolderId
                val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
                val folderSubDir = if (targetFolder != null) "${targetFolder.name.trim().replace(" ", "_")}/" else ""

                val total = items.size
                val shouldRemoveBg = state.autoRemoveBgOnSlice

                // ★ 多协程多核并发池：充分榨干 CPU 多核并发切片算力
                val concurrencyLimit = 8
                val sliceSemaphore = Semaphore(concurrencyLimit)
                val mattingSemaphore = Semaphore(2) // 去背脚本独立并发限制，防止子进程打满
                val progressMutex = Mutex()
                var completedCount = 0

                val newAssetItems = coroutineScope {
                    items.mapIndexed { idx, item ->
                        async(Dispatchers.Default) {
                            sliceSemaphore.withPermit {
                                // 1. 调用 Skia 执行微秒级物理切片（零大图重复解码）
                                val sliceBytes = cropImage(
                                    srcBitmap = srcBitmap,
                                    imageWidth = imgWidth,
                                    imageHeight = imgHeight,
                                    bounds = item.bounds,
                                    originalWidth = canvasW,
                                    originalHeight = canvasH,
                                    isPng = true
                                )

                                val assetId = item.id.ifBlank { "asset_${getCurrentTimeMillis()}_$idx" }
                                val relPath = "templates/$sanitizedName/assets/image/${folderSubDir}${assetId}.png"

                                // 2. 将切片物理写入磁盘素材库目录 (支持自动透明去背)
                                if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                                    val finalBytes = if (shouldRemoveBg) {
                                        mattingSemaphore.withPermit {
                                            val matted = mattingService.removeBackground(sliceBytes)
                                            if (matted != null && matted.isNotEmpty()) matted else sliceBytes
                                        }
                                    } else {
                                        sliceBytes
                                    }
                                    val tFile = TemplateFile(relPath)
                                    tFile.writeBytes(finalBytes)
                                }

                                // 3. 线程安全更新切片进度与状态
                                val currentDone = progressMutex.withLock {
                                    completedCount++
                                    completedCount
                                }
                                state = state.copy(
                                    sliceProgress = currentDone.toFloat() / total,
                                    sliceStatusText = if (shouldRemoveBg) {
                                        "正在并发切片并去背 ($currentDone/$total): ${item.name}"
                                    } else {
                                        "正在并发切片 ($currentDone/$total): ${item.name}"
                                    }
                                )

                                // 4. 构建资产项实体 (带索引保证最终稳定保序)
                                IndexedValue(
                                    idx,
                                    UIAssetItem(
                                        id = assetId,
                                        name = item.name,
                                        relativePath = relPath,
                                        width = item.bounds.width.toInt().coerceAtLeast(1),
                                        height = item.bounds.height.toInt().coerceAtLeast(1),
                                        category = item.type.name.lowercase(),
                                        folderId = targetFolderId,
                                        createdAt = getCurrentTimeMillis()
                                    )
                                )
                            }
                        }
                    }.awaitAll()
                        .sortedBy { it.index }
                        .map { it.value }
                }

                // 4. 将切片生成的素材归入素材库 (Asset Library)，同步更新状态并持久化落盘
                val updatedLibrary = state.assetLibrary.copy(
                    items = state.assetLibrary.items.filterNot { old -> newAssetItems.any { it.id == old.id } } + newAssetItems
                )

                // 5. 物理清理切片校对草稿文件
                try {
                    val draftPath = "templates/$sanitizedName/import_draft.json"
                    TemplateFile(draftPath).delete()
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

    /**
     * 一键全部展开或全部折叠所有素材文件夹。
     */
    fun toggleAllFoldersExpanded() {
        val folders = state.assetLibrary.folders
        if (folders.isEmpty()) return
        val allExpanded = folders.all { state.expandedFolderIds.contains(it.id) }
        state = state.copy(
            expandedFolderIds = if (allExpanded) emptySet() else folders.map { it.id }.toSet()
        )
    }

    fun setDraggedAssetId(id: String?) { state = state.copy(draggedAssetId = id) }
    fun setHoveredFolderId(id: String?) { state = state.copy(hoveredFolderId = id) }

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

    fun addAssetToCanvas(
        asset: UIAssetItem,
        logicalX: Float? = null,
        logicalY: Float? = null,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: run {
            Toast.show("当前没有处于活跃的视图界面，请先选择或新建视图", ToastType.INFO)
            return
        }
        val activeContainer = state.activeContainerBlock
        val targetW = asset.width.coerceAtLeast(32)
        val targetH = asset.height.coerceAtLeast(32)
        saveSnapshot("添加素材: ${asset.name}")

        if (activeContainer != null) {
            // 当前处于父组件打开的独立编辑视口：添加到该父组件内（居中落位在光标松手点）
            val posX = if (logicalX != null) logicalX - (targetW / 2f) else ((activeContainer.bounds.width - targetW).coerceAtLeast(0f) / 2f)
            val posY = if (logicalY != null) logicalY - (targetH / 2f) else ((activeContainer.bounds.height - targetH).coerceAtLeast(0f) / 2f)
            val newBlock = UIBlock(
                id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
                type = UIBlockType.IMAGE,
                bounds = SerialRect(posX, posY, posX + targetW, posY + targetH),
                currentImageUri = TemplateFile(asset.relativePath),
                userPromptZh = asset.name,
                userPromptEn = asset.id
            )
            val updatedContainer = activeContainer.copy(children = activeContainer.children + newBlock)
            val updatedBlocks = view.blocks.updateBlockInList(activeContainer.id) { updatedContainer }.bindParents()
            updateViewBlocksAndSave(view.id, updatedBlocks, repo)
            state = state.copy(selectedBlockId = newBlock.id)
            Toast.show("已将素材添加到容器「${activeContainer.id}」: ${asset.name}", ToastType.SUCCESS)
        } else {
            // 主视图界面：添加到页面根级 blocks（居中落位在光标松手点）
            val posX = if (logicalX != null) logicalX - (targetW / 2f) else ((view.width - targetW).coerceAtLeast(0f) / 2f)
            val posY = if (logicalY != null) logicalY - (targetH / 2f) else ((view.height - targetH).coerceAtLeast(0f) / 2f)
            val newBlock = UIBlock(
                id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
                type = UIBlockType.IMAGE,
                bounds = SerialRect(posX, posY, posX + targetW, posY + targetH),
                currentImageUri = TemplateFile(asset.relativePath),
                userPromptZh = asset.name,
                userPromptEn = asset.id
            )
            val updatedBlocks = (view.blocks + newBlock).bindParents()
            updateViewBlocksAndSave(view.id, updatedBlocks, repo)
            state = state.copy(selectedBlockId = newBlock.id)
            Toast.show("已将素材添加到视图: ${asset.name}", ToastType.SUCCESS)
        }
    }

    fun startRenameView(view: UIPage) { state = state.copy(renamingView = view) }
    fun cancelRenameView() { state = state.copy(renamingView = null) }
    fun commitRenameView(viewId: String, newName: String, repo: TemplateRepository = TemplateRepository()) {
        if (newName.isBlank()) return
        val updatedPages = state.project?.pages?.map {
            if (it.id == viewId) it.copy(nameStr = newName.trim()) else it
        } ?: return
        val updatedProject = state.project?.copy(pages = updatedPages) ?: return
        state = state.copy(project = updatedProject, renamingView = null, isDirty = true)
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("视图已重命名为: $newName", ToastType.SUCCESS)
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, width: Float, height: Float, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(bounds = SerialRect(left, top, left + width, top + height)) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    /**
     * 实时平移单个图元（高频拖拽中毫秒级更新内存坐标，递归支持子图元）。
     * 若处于全局主视图模式，子组件严禁独立位移，强制收拢至其根父容器整体平移。
     */
    fun moveBlockDelta(blockId: String, dx: Float, dy: Float) {
        val view = state.currentView ?: return
        val effectiveId = if (state.activeContainerId == null) {
            val rootParent = view.blocks.find { root ->
                root.id == blockId || root.children.any { c -> c.id == blockId || c.children.any { cc -> cc.id == blockId } }
            }
            rootParent?.id ?: blockId
        } else {
            blockId
        }
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, effectiveId) { block ->
            val b = block.bounds
            val w = b.right - b.left
            val h = b.bottom - b.top
            val newLeft = b.left + dx
            val newTop = b.top + dy
            block.copy(bounds = SerialRect(newLeft, newTop, newLeft + w, newTop + h))
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    /**
     * 图元拖拽释放后标记未保存状态（仅更新内存，不写磁盘）。
     */
    fun commitBlockMove(repo: TemplateRepository = TemplateRepository()) {
        state = state.copy(isDirty = true)
    }

    fun updateBlockType(blockId: String, newType: UIBlockType, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(type = newType)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    fun updateBlockPrompts(blockId: String, userPromptZh: String, userPromptEn: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(userPromptZh = userPromptZh, userPromptEn = userPromptEn) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    fun updateBlockPureContainer(blockId: String, isPure: Boolean, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(isPureContainer = isPure) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages), isDirty = true)
    }

    fun deleteSelectedBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        saveSnapshot("删除组件")
        val updatedBlocks = removeBlockFromTree(view.blocks, blockId).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = null)
        Toast.show("已删除组件", ToastType.INFO)
    }

    /**
     * 递归从模块树中安全移除指定 ID 的图元对象。
     */
    private fun removeBlockFromTree(list: List<UIBlock>, id: String): List<UIBlock> {
        return list.filterNot { it.id == id }.map { block ->
            if (block.children.any { it.id == id }) {
                block.copy(children = block.children.filterNot { it.id == id })
            } else {
                block.copy(children = removeBlockFromTree(block.children, id))
            }
        }
    }

    /**
     * 移动图层层级（支持 BEFORE / AFTER 同级排序及 INSIDE 父子容器嵌套约束）。
     */
    fun moveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        if (draggedId == targetId) return
        val draggedBlock = view.blocks.findBlockById(draggedId) ?: return

        val oldParentId = view.blocks.findParentBlockId(draggedId)
        val oldParent = oldParentId?.let { view.blocks.findBlockById(it) }
        val targetParentId = if (targetId != null) view.blocks.findParentBlockId(targetId) else null
        val isMovingOutOfParent = oldParentId != null && (
            (position == DropPosition.INSIDE && targetId != oldParentId) ||
            (position != DropPosition.INSIDE && targetParentId != oldParentId) ||
            targetId == null
        )

        // 移出最后一个子组件时强校验与弹窗拦截
        if (isMovingOutOfParent && oldParent != null && oldParent.children.size <= 1) {
            state = state.copy(
                pendingDragRemoveLastChild = DragRemoveLastChildContext(
                    draggedId = draggedId,
                    oldParentId = oldParent.id,
                    targetId = targetId,
                    position = position
                )
            )
            return
        }

        executeMoveHierarchyBlock(
            draggedId = draggedId,
            targetId = targetId,
            position = position,
            repo = repo,
            dissolveContainerId = null
        )
    }

    /**
     * 用户确认移除最后一个子组件操作：可选择保留或解散父容器。
     */
    fun confirmDragRemoveLastChild(keepContainer: Boolean, repo: TemplateRepository) {
        val ctx = state.pendingDragRemoveLastChild ?: return
        state = state.copy(pendingDragRemoveLastChild = null)
        executeMoveHierarchyBlock(
            draggedId = ctx.draggedId,
            targetId = ctx.targetId,
            position = ctx.position,
            repo = repo,
            dissolveContainerId = if (keepContainer) null else ctx.oldParentId
        )
    }

    /**
     * 取消最后一个子组件的移出操作，恢复原始层级状态。
     */
    fun cancelDragRemoveLastChild() {
        state = state.copy(pendingDragRemoveLastChild = null)
    }

    private fun executeMoveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository,
        dissolveContainerId: String? = null
    ) {
        val view = state.currentView ?: return
        val draggedBlock = view.blocks.findBlockById(draggedId) ?: return

        // 目标为 null：移至列表末尾（容器视口下移至当前激活容器末尾，整页视口下移至根列表末尾）
        if (targetId == null) {
            val activeContainer = state.activeContainerBlock
            if (state.activeContainerId != null && activeContainer != null) {
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val containerAbsBounds = view.blocks.calculateBlockAbsoluteBounds(activeContainer.id) ?: activeContainer.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - containerAbsBounds.left,
                    top = draggedAbsBounds.top - containerAbsBounds.top,
                    right = draggedAbsBounds.right - containerAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - containerAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                val updatedDragged = draggedBlock.copy(bounds = localBounds)
                val newBlocks = updateBlockInTreeRecursively(cleanedBlocks, activeContainer.id) { c ->
                    val newChildren = c.children.filterNot { it.id == draggedId } + updatedDragged
                    c.copy(children = newChildren)
                }.bindParents()
                val finalBlocks = if (dissolveContainerId != null) {
                    removeBlockFromTree(newBlocks, dissolveContainerId).bindParents()
                } else newBlocks
                updateViewBlocksAndSave(view.id, finalBlocks, repo)
                return
            }

            val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
            val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            var newBlocks = (cleanedBlocks + updatedDragged).bindParents()
            if (dissolveContainerId != null) {
                newBlocks = removeBlockFromTree(newBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, newBlocks, repo)
            return
        }

        val targetBlock = view.blocks.findBlockById(targetId) ?: return
        val targetParentId = view.blocks.findParentBlockId(targetId)

        if (position == DropPosition.INSIDE) {
            // 移入目标成组逻辑判定：
            // 1. 若目标本身就是纯容器
            if (targetBlock.isPureContainer || targetBlock.type == UIBlockType.CONTAINER) {
                val targetAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: targetBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - targetAbsBounds.left,
                    top = draggedAbsBounds.top - targetAbsBounds.top,
                    right = draggedAbsBounds.right - targetAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - targetAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                val updatedTarget = targetBlock.copy(children = targetBlock.children + draggedBlock.copy(bounds = localBounds))
                var resultBlocks = cleanedBlocks.updateBlockInList(targetId) { updatedTarget }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入容器: ${targetBlock.id}", ToastType.SUCCESS)
                return
            }

            // 2. 若目标所处已有父容器
            if (targetParentId != null) {
                val parentBlock = view.blocks.findBlockById(targetParentId) ?: return
                val parentAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - parentAbsBounds.left,
                    top = draggedAbsBounds.top - parentAbsBounds.top,
                    right = draggedAbsBounds.right - parentAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - parentAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                    p.copy(children = p.children + draggedBlock.copy(bounds = localBounds))
                }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入同级容器", ToastType.SUCCESS)
                return
            }

            // 3. 目标为独立根图层且无父容器：触发二次确认弹窗
            state = state.copy(pendingCreateContainer = draggedId to targetId)
            return
        }

        // BEFORE 或 AFTER：同级相邻排序
        val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
        val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)

        if (targetParentId != null) {
            val parentBlock = cleanedBlocks.findBlockById(targetParentId) ?: return
            val parentAbsBounds = cleanedBlocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
            val localBounds = SerialRect(
                left = draggedAbsBounds.left - parentAbsBounds.left,
                top = draggedAbsBounds.top - parentAbsBounds.top,
                right = draggedAbsBounds.right - parentAbsBounds.left,
                bottom = draggedAbsBounds.bottom - parentAbsBounds.top
            )
            val updatedDragged = draggedBlock.copy(bounds = localBounds)
            var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                val list = p.children.toMutableList()
                val targetIndex = list.indexOfFirst { it.id == targetId }
                val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
                list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
                p.copy(children = list)
            }.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        } else {
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            val list = cleanedBlocks.toMutableList()
            val targetIndex = list.indexOfFirst { it.id == targetId }
            val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
            list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
            var resultBlocks = list.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        }
    }

    /**
     * 用户确认创建父容器约束。
     */
    fun confirmCreateContainer(draggedId: String, targetId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val blockA = view.blocks.findBlockById(draggedId) ?: return
        val blockB = view.blocks.findBlockById(targetId) ?: return

        val boundsA = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: blockA.bounds
        val boundsB = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: blockB.bounds

        val unionLeft = kotlin.math.min(boundsA.left, boundsB.left)
        val unionTop = kotlin.math.min(boundsA.top, boundsB.top)
        val unionRight = kotlin.math.max(boundsA.right, boundsB.right)
        val unionBottom = kotlin.math.max(boundsA.bottom, boundsB.bottom)
        val unionRect = SerialRect(unionLeft, unionTop, unionRight, unionBottom)

        val localA = blockA.copy(bounds = SerialRect(boundsA.left - unionLeft, boundsA.top - unionTop, boundsA.right - unionLeft, boundsA.bottom - unionTop))
        val localB = blockB.copy(bounds = SerialRect(boundsB.left - unionLeft, boundsB.top - unionTop, boundsB.right - unionLeft, boundsB.bottom - unionTop))

        val containerId = "Container_${getCurrentTimeMillis() % 10000}"
        val container = UIBlock(
            id = containerId,
            type = UIBlockType.CONTAINER,
            isPureContainer = true,
            bounds = unionRect,
            children = listOf(localB, localA)
        )

        val remaining = removeBlockFromTree(removeBlockFromTree(view.blocks, draggedId), targetId)
        val targetIdx = view.blocks.indexOfFirst { it.id == targetId }
        val mutable = remaining.toMutableList()
        val insertIdx = if (targetIdx >= 0) targetIdx.coerceAtMost(mutable.size) else mutable.size
        mutable.add(insertIdx, container)
        val resultBlocks = mutable.bindParents()

        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        state = state.copy(pendingCreateContainer = null, selectedBlockId = containerId)
        Toast.show("成功创建父约束容器: $containerId", ToastType.SUCCESS)
    }

    fun cancelCreateContainer() {
        state = state.copy(pendingCreateContainer = null)
    }

    /**
     * 请求将组件移出父容器。若为容器内最后一个组件，触发确认提示。
     */
    fun requestRemoveBlockFromParent(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId) ?: return
        val parent = view.blocks.findBlockById(parentId) ?: return
        if (parent.children.size <= 1) {
            state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, false))
        } else {
            performRemoveBlockFromParent(parentId, blockId, false, repo)
        }
    }

    /**
     * 请求删除组件。若属于容器且为最后一个子组件，触发确认提示。
     */
    fun requestDeleteBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId)
        if (parentId != null) {
            val parent = view.blocks.findBlockById(parentId)
            if (parent != null && parent.children.size <= 1) {
                state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, true))
                return
            }
        }
        deleteSelectedBlock(blockId, repo)
    }

    fun confirmRemoveLastChildAction(repo: TemplateRepository) {
        val action = state.pendingRemoveLastChildAction ?: return
        val (containerId, childId, isDelete) = action
        performRemoveBlockFromParent(containerId, childId, isDelete, repo)
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    fun cancelRemoveLastChildAction() {
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    private fun performRemoveBlockFromParent(
        containerId: String,
        childId: String,
        isDelete: Boolean,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        val child = view.blocks.findBlockById(childId) ?: return
        val childAbsBounds = view.blocks.calculateBlockAbsoluteBounds(childId) ?: child.bounds
        val parent = view.blocks.findBlockById(containerId) ?: return

        val parentIndexInRoot = view.blocks.indexOfFirst { it.id == containerId }
        val remainingInContainer = parent.children.filterNot { it.id == childId }

        var newBlocks = removeBlockFromTree(view.blocks, childId)
        if (remainingInContainer.isEmpty()) {
            newBlocks = removeBlockFromTree(newBlocks, containerId)
        }

        if (!isDelete) {
            val mutable = newBlocks.toMutableList()
            val targetInsertIndex = if (parentIndexInRoot >= 0) {
                if (remainingInContainer.isNotEmpty()) {
                    val currentParentIdx = mutable.indexOfFirst { it.id == containerId }
                    if (currentParentIdx >= 0) currentParentIdx + 1 else mutable.size
                } else {
                    parentIndexInRoot.coerceIn(0, mutable.size)
                }
            } else {
                mutable.size
            }
            mutable.add(targetInsertIndex, child.copy(bounds = childAbsBounds))
            newBlocks = mutable
        }

        val resultBlocks = newBlocks.bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        val actionText = if (isDelete) "组件已删除" else "组件已移出父容器"
        val containerText = if (remainingInContainer.isEmpty()) "，原父容器已自动解散清理" else ""
        Toast.show("$actionText$containerText", ToastType.SUCCESS)
    }

    /**
     * 解散父容器：将其内部所有子图层平移释放至根列表，并销毁该纯容器。
     */
    fun ungroupContainer(containerId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val container = view.blocks.findBlockById(containerId) ?: return
        val containerAbsBounds = view.blocks.calculateBlockAbsoluteBounds(containerId) ?: container.bounds
        val releasedChildren = container.children.map { child ->
            val absLeft = containerAbsBounds.left + child.bounds.left
            val absTop = containerAbsBounds.top + child.bounds.top
            child.copy(bounds = SerialRect(absLeft, absTop, absLeft + child.bounds.width, absTop + child.bounds.height))
        }
        val withoutContainer = removeBlockFromTree(view.blocks, containerId)
        val resultBlocks = (withoutContainer + releasedChildren).bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        Toast.show("已解散父容器: ${container.id}", ToastType.SUCCESS)
    }

    private fun updateViewBlocksAndSave(viewId: String, newBlocks: List<UIBlock>, repo: TemplateRepository = TemplateRepository()) {
        val updatedPages = state.project?.pages?.map {
            if (it.id == viewId) it.copy(blocks = newBlocks) else it
        } ?: return
        state = state.copy(
            project = state.project?.copy(pages = updatedPages),
            isDirty = true
        )
    }

    /**
     * 切换指定组件的可见性状态 (显示/隐藏，递归支持容器内的子图元)。
     */
    fun toggleBlockVisibility(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) { b ->
            val newVisible = !b.isVisible
            if (b.children.isNotEmpty()) {
                b.copy(isVisible = newVisible, children = mapAllBlocksRecursively(b.children) { it.copy(isVisible = newVisible) })
            } else {
                b.copy(isVisible = newVisible)
            }
        }.bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
    }

    /** 兄弟列表内重排动作枚举 */
    private enum class SiblingReorderAction { MOVE_UP, MOVE_DOWN, TO_FRONT, TO_BACK }

    /**
     * 在图元树中递归定位 blockId 所在的兄弟列表，并在该列表内执行重排。
     * 返回重建后的新树；返回 null 表示已在边界无需移动（或未找到）。
     * 纯平移语义：仅调整列表顺序，绝不改动任何图元 bounds 与宽高。
     */
    private fun rerouteWithinSiblings(
        blocks: List<UIBlock>,
        blockId: String,
        action: SiblingReorderAction
    ): List<UIBlock>? {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index >= 0) {
            val mutable = blocks.toMutableList()
            when (action) {
                SiblingReorderAction.MOVE_UP ->
                    if (index == mutable.lastIndex) return null else {
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                    }
                SiblingReorderAction.MOVE_DOWN ->
                    if (index == 0) return null else {
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                    }
                SiblingReorderAction.TO_FRONT -> {
                    if (index == mutable.lastIndex) return null
                    val item = mutable.removeAt(index)
                    mutable.add(item)
                }
                SiblingReorderAction.TO_BACK -> {
                    if (index == 0) return null
                    val item = mutable.removeAt(index)
                    mutable.add(0, item)
                }
            }
            return mutable
        }
        // 递归深入各容器的 children 子树
        for (i in blocks.indices) {
            val child = blocks[i]
            if (child.children.isEmpty()) continue
            val newChildren = rerouteWithinSiblings(child.children, blockId, action) ?: continue
            return blocks.toMutableList().also { it[i] = child.copy(children = newChildren) }
        }
        return null
    }

    /**
     * 将指定组件置于顶层（移至渲染列表尾部）。
     */
    fun bringBlockToFront(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.TO_FRONT) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
        Toast.show("已将组件置于顶层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件置于底层（移至渲染列表头部）。
     */
    fun sendBlockToBack(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.TO_BACK) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
        Toast.show("已将组件置于底层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件上移一层。
     */
    fun moveBlockUp(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.MOVE_UP) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
    }

    /**
     * 将指定组件下移一层。
     */
    fun moveBlockDown(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.MOVE_DOWN) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
    }

    /**
     * 重命名组件（递归支持子组件）。
     */
    fun renameBlock(blockId: String, newName: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val target = view.blocks.findBlockById(blockId) ?: return
        val trimmed = newName.trim().ifBlank { target.id }
        if (trimmed == blockId) {
            state = state.copy(renamingBlockId = null)
            return
        }
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(id = trimmed)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(
            project = state.project?.copy(pages = updatedPages),
            selectedBlockId = if (state.selectedBlockId == blockId) trimmed else state.selectedBlockId,
            renamingBlockId = null,
            isDirty = true
        )
        Toast.show("组件已重命名为: $trimmed", ToastType.SUCCESS)
    }

    fun startRenameBlock(blockId: String) {
        state = state.copy(renamingBlockId = blockId)
    }

    fun cancelRenameBlock() {
        state = state.copy(renamingBlockId = null)
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

package org.gemini.ui.forge.ui.uistudio.delegate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.manager.ExternalDocManager
import org.gemini.ui.forge.manager.ExternalDocRecord
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.model.chat.ThinkingLevel
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.state.MentionState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.ui.uistudio.model.AgentPreference
import org.gemini.ui.forge.ui.uistudio.model.ProjectAgentConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.MentionFuzzyMatcher
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import org.gemini.ui.forge.utils.looseJson

/**
 * UI Studio 智能体配置领域委托 (aiConfig)
 *
 * 职责范围：
 * - Agent 人设与偏好配置：全局系统指令弹窗、模型/思考等级/通信协议切换、
 *   agent_pref.json 双落盘持久化与启动加载自愈；
 * - 技能体系：技能注册清单探测、启用/停用状态持久化、目录指纹监视循环与自动同步；
 * - 外部参考文档与 @ Mention 引用体系：历史引用登记、拖拽文件分流挂载、
 *   多源候选（文档/素材/视图/图元）模糊检索。
 *
 * 跨域依赖通过构造注入：
 * - [regenerateChatMessage]：降级自愈后触发消息重新生成的函数引用（aiAgent 域）。
 */
class AiConfigDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val jsonHelper: Json,
    private val storage: LocalFileStorage,
    private val regenerateChatMessage: (String) -> Unit,
) {

    /** 状态桥接属性：使平移自 ViewModel 的方法体可零改动读写宿主状态 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

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
            AppLogger.w("AiConfigDelegate", "持久化禁用技能配置失败: ${e.message}")
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

    /** 输入栏 @ 引用体系共享状态 */
    val mentionState = MentionState()

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
     * 消息发送时正式沉淀文档引用到当前工程历史引用库并异步刷新清单
     */
    fun commitDocReference(fileName: String, absolutePath: String) {
        val projId = state.project?.projectId ?: "default"
        scope.launch(Dispatchers.Default) {
            try {
                ExternalDocManager.registerDoc(
                    projectName = projId,
                    fileName = fileName,
                    absolutePath = absolutePath
                )
                registeredExternalDocs = ExternalDocManager.getDocsForProject(projId)
            } catch (e: Exception) {
                AppLogger.w("AiConfigDelegate", "登记历史文档失败: $absolutePath, ${e.message}")
            }
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
    internal fun startSkillsDirectoryWatcher() {
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

    internal fun stopSkillsDirectoryWatcher() {
        skillsWatcherJob?.cancel()
        skillsWatcherJob = null
    }

    fun openSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = true)
    }

    fun closeSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = false)
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
                    AppLogger.i("AiConfigDelegate", "✨ 已成功恢复智能设计助理偏好: model=${pref.modelName}, thinking=${pref.thinkingLevel}, flavor=${pref.apiFlavor}")
                }

                // 3. 无感平滑清理：检测并安全删除单项目目录下遗留的旧版 agent_config.json
                val legacyFile = TemplateFile(legacyPath)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    AppLogger.i("AiConfigDelegate", "🧹 已清理项目私有遗留配置文件: $legacyPath")
                }
            } catch (e: Exception) {
                AppLogger.e("AiConfigDelegate", "加载全局 Agent 人设配置或用户偏好失败", e)
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
                AppLogger.d("AiConfigDelegate", "💾 已持久化智能设计助理用户偏好: model=${pref.modelName}, thinking=${pref.thinkingLevel}, flavor=${pref.apiFlavor}")
            } catch (e: Exception) {
                AppLogger.e("AiConfigDelegate", "持久化智能设计助理偏好失败", e)
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
                AppLogger.e("AiConfigDelegate", "保存全局 Agent 人设失败", e)
            }
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.delegate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.isFileExistsInternal
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.chat.ChatMentionTag
import org.gemini.ui.forge.model.chat.ChatToolCallRecord
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.service.tool.ai.AiToolExecutionDispatcher
import org.gemini.ui.forge.service.ai.ChatCompressionManager
import org.gemini.ui.forge.service.ai.ChatPayloadAssembler
import org.gemini.ui.forge.service.ai.GeminiChatAttachment
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.service.ai.GeminiChatHistoryItem
import org.gemini.ui.forge.service.ai.GeminiChatRequest
import org.gemini.ui.forge.service.ai.GeminiChatResult
import org.gemini.ui.forge.service.ai.GeminiFunctionCallRecord
import org.gemini.ui.forge.service.ai.GeminiFunctionResponse
import org.gemini.ui.forge.service.ai.GeminiGenerationConfig
import org.gemini.ui.forge.service.ai.ImageGenIntentDetector
import org.gemini.ui.forge.service.canvas.CanvasScreenshotService
import org.gemini.ui.forge.service.canvas.CanvasSnapshotMode
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.chat.CommonChatAttachment
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.TrafficSummaryExtractor
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * AI 智能体领域委托：承载 AI 对话消息发送、流式思考生成、多轮工具调用循环、
 * 用户续轮确认通道、画布多模态快照挂载、生成中止、消息版本切换/删除/重新生成
 * 等 UI Studio 核心 AI 交互能力。
 *
 * 构造注入各兄弟域委托引用以完成跨域协同（素材库刷新、会话持久化、
 * Mention 上下文、布局工具装配），方法体自宿主零改动平移。
 */
class AiAgentDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val jsonHelper: Json,
    private val configManager: ConfigManager,
    private val chatEngine: GeminiChatEngine,
    private val toolDispatcher: AiToolExecutionDispatcher,
    private val trafficStore: SessionTrafficStore,
    private val trafficScopeIdProvider: () -> String,
    private val canvasScreenshotService: CanvasScreenshotService,
    private val assetLibrary: AssetLibraryDelegate,
    private val chatSession: ChatSessionDelegate,
    private val aiConfig: AiConfigDelegate,
    private val aiLayout: AiLayoutDelegate,
) {

    /** 状态桥接属性：使方法体内 state 读写与原宿主实现零改动平移 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /** 通信归档作用域标识：动态求值（依赖当前已加载工程），杜绝构造期快照错位 */
    private val trafficScopeId: String
        get() = trafficScopeIdProvider()

    /** 当前是否正在捕获画布快照 */
    var isCapturingCanvasSnapshot by mutableStateOf(false)
        private set

    /** 达到最大轮询次数时的用户确认续轮通道 */
    private var toolLoopContinueDeferred: CompletableDeferred<Boolean>? = null

    /** 当前活跃的 AI 流式生成后台作业，用于支持实时中止 */
    private var activeChatJob: Job? = null

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
                AppLogger.e("AiAgentDelegate", "捕获画布快照失败", e)
                Toast.show("快照失败: ${e.message}", ToastType.ERROR)
            } finally {
                isCapturingCanvasSnapshot = false
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
            chatSession.saveSession(updatedSession)
        } else {
            state = state.copy(
                isAiChatSending = false,
                streamingAiText = ""
            )
        }
        Toast.show("已停止 AI 生成", ToastType.INFO)
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
                // 静默解析排版数据并缓存，绝不主动在画布中央弹窗打断（保留气泡按钮由用户显式点击）
                aiLayout.checkAndExecuteLayoutTool(rawResponseJson, responseText, autoPromptDialog = false)
                continue
            }

            if (name == "patch_view_elements") {
                val t0 = getCurrentTimeMillis()
                val patchResult = aiLayout.executePatchViewElements(enrichedArgs)
                val dur = getCurrentTimeMillis() - t0
                val record = toolDispatcher.buildToolCallRecord(
                    id = "tool_${getCurrentTimeMillis()}_$name",
                    toolName = name,
                    arguments = enrichedArgs,
                    result = if (patchResult.isSuccess) {
                        ToolResult.success(patchResult.summary)
                    } else {
                        ToolResult.error(patchResult.errorMessage ?: "局部补丁执行失败")
                    },
                    durationMs = dur,
                    callId = fc.callId
                )
                records.add(record)
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
            AppLogger.e("AiAgentDelegate", "持久化 AI 生成图像失败", e)
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
        chatSession.saveSession(updatedSession)
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
        chatSession.saveSession(updatedSession)
        Toast.show("已删除该条对话记录", ToastType.INFO)
    }

    fun sendChatMessage(
        userText: String,
        attachmentsList: List<CommonChatAttachment> = emptyList(),
        apiKey: String = ""
    ) {
        if (userText.isBlank() && attachmentsList.isEmpty()) return
        val activeSession = state.currentChatSession ?: run {
            chatSession.createNewChatSession()
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

        val attachedMentionsList = aiConfig.mentionState.attachedMentions.toList()

        val mentionTags = attachedMentionsList.map {
            val resolvedPath = when (val p = it.payload) {
                is MentionPayload.DocPayload -> p.absolutePath
                is MentionPayload.AssetPayload -> if (it.id == "all_assets_manifest") "all_assets_manifest" else p.filePath
                is MentionPayload.ViewPayload -> p.viewId
                is MentionPayload.BlockPayload -> p.blockId
                is MentionPayload.AssetFolderPayload -> "asset_folder:${p.folderId}"
            }
            ChatMentionTag(
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
        chatSession.saveSession(updatedSession)

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
                        chatSession.saveSession(correctedSession)
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
                    chatSession.saveSession(sessionAfterReply)
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
                            if (p.content.isNullOrBlank() && isFileExistsInternal(p.absolutePath)) {
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
                                        AppLogger.w("AiAgentDelegate", "读取引用素材文件失败: ${p.filePath}, ${e.message}")
                                    }
                                }
                            }
                            is MentionPayload.DocPayload -> {
                                if (isFileExistsInternal(p.absolutePath)) {
                                    aiConfig.commitDocReference(fileName = p.fileName, absolutePath = p.absolutePath)
                                }
                            }
                            else -> {}
                        }
                    }
                    // 发送完毕后清空已挂载实体药丸
                    aiConfig.mentionState.clearMentions()
                }

                // 智能判定激活的技能并注入工具与系统指令补丁
                val triggeredSkills = UiStudioSkillManager.resolveActiveSkills(
                    prompt = userText,
                    attachedMentions = attachedMentionsList,
                    mode = aiConfig.skillMode,
                    enabledSkillIds = aiConfig.enabledSkillIds
                )

                // 严格遵循不自动注入铁律：彻底删除基于关键词或意图的私自注入；仅当用户显式附带全部素材药丸或指定素材文件夹药丸时才装配清单
                var autoAssetManifestXml: String? = null
                val manifestSb = StringBuilder()
                val hasAllAssetsMention = attachedMentionsList.any {
                    val p = it.payload
                    p is MentionPayload.AssetPayload && p.fileName == "ALL_ASSETS_MANIFEST"
                }
                if (hasAllAssetsMention) {
                    val manifest = AssetManifestHelper.buildCompactManifest(state.assetLibrary.items)
                    manifestSb.appendLine("  <asset_library_manifest total=\"${state.assetLibrary.items.size}\">")
                    manifestSb.appendLine(manifest)
                    manifestSb.appendLine("  </asset_library_manifest>")
                }

                val folderMentions = attachedMentionsList.mapNotNull { it.payload as? MentionPayload.AssetFolderPayload }
                for (folderMention in folderMentions) {
                    val folderItems = state.assetLibrary.getItemsInFolderRecursive(folderMention.folderId)
                    val manifest = AssetManifestHelper.buildCompactManifest(folderItems)
                    manifestSb.appendLine("  <asset_folder_manifest folder=\"${folderMention.folderName}\" total=\"${folderItems.size}\">")
                    manifestSb.appendLine(manifest)
                    manifestSb.appendLine("  </asset_folder_manifest>")
                }

                if (manifestSb.isNotBlank()) {
                    autoAssetManifestXml = manifestSb.toString().trimEnd()
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
                    AppLogger.i("AiAgentDelegate", "达到长对话阈值，正在自动提炼设计记忆...")
                    try {
                        sessionToUse = ChatCompressionManager.compressSession(
                            session = sessionToUse,
                            apiFlavor = apiFlavor,
                            chatEngine = chatEngine,
                            apiKey = effectiveApiKey,
                            customSystemPrompt = effectiveSystemPrompt
                        )
                        chatSession.saveSession(sessionToUse)
                    } catch (e: Exception) {
                        AppLogger.w("AiAgentDelegate", "自动压缩失败，继续使用未压缩上下文", e)
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
                var lastResult: GeminiChatResult? = null
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
                        AppLogger.e("AiAgentDelegate", "Gemini API 调用异常", e)
                        Toast.show("AI 回复失败: ${e.message}", ToastType.ERROR)
                        GeminiChatResult(
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
                        val deferred = CompletableDeferred<Boolean>()
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

                val finalResult = lastResult ?: GeminiChatResult(
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
                } else if (generatedImgPath != null) {
                    "" // 图像已成功生成并挂载卡片，无需拼接机械的“未收到有效内容”提示
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
                chatSession.saveSession(finalizedSession)
                state = state.copy(
                    chatSessions = state.chatSessions.map { if (it.id == finalizedSession.id) finalizedSession else it },
                    isAiChatSending = false
                )

                // 自动检查大模型是否发起了 apply_view_layout 工具调用并自动装配（静默缓存，用户点击气泡才弹窗）
                if (finalResult.isSuccess) {
                    aiLayout.checkAndExecuteLayoutTool(finalResult.rawResponseJson, finalResult.text, autoPromptDialog = false)
                }
            } catch (e: Exception) {
                AppLogger.e("AiAgentDelegate", "AI Chat 处理异常", e)
                state = state.copy(isAiChatSending = false)
            } finally {
                activeChatJob = null
            }
        }
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
                } else if (generatedImgPath != null) {
                    "" // 图像已成功生成并挂载卡片，保持界面纯净
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
                chatSession.saveSession(updatedSession)
            } catch (e: Exception) {
                AppLogger.e("AiAgentDelegate", "重新生成失败", e)
                Toast.show("重新生成失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isAiChatSending = false)
            } finally {
                activeChatJob = null
                state = state.copy(streamingAiText = "")
            }
        }
    }
}

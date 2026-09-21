package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.SessionCacheManager
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.ContextCompressionEngine
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 视觉工作室 UI 状态数据类
 */
data class VisualChatStudioState(
    val scopeId: String = "",
    val currentSession: VisualChatSession? = null,
    val historySessions: List<VisualChatSession> = emptyList(),
    val isGenerating: Boolean = false,
    val isOptimizingPrompt: Boolean = false,
    val statusLog: String = "",
    val streamingText: String = "",
    val pendingCount: Int = 0,
    val hasCompressedContext: Boolean = false,
    val selectedModel: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_IMAGE, // 默认采用 Nano Banana
    val generationCount: Int = 1,
    val activeReferenceImageUri: String? = null,
    val previewMemoryBytes: ByteArray? = null,
    val pendingCropBounds: SerialRect? = null,
    val isImageToImageMode: Boolean = true,
    val thinkingLevel: org.gemini.ui.forge.model.chat.ThinkingLevel = org.gemini.ui.forge.model.chat.ThinkingLevel.DEFAULT
) {
    /** 提取当前会话中生成的所有历史图片集合 */
    val sessionGeneratedImages: List<String>
        get() = currentSession?.messages?.mapNotNull { it.generatedImageUri }?.filter { it.isNotBlank() } ?: emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as VisualChatStudioState
        if (scopeId != other.scopeId) return false
        if (currentSession != other.currentSession) return false
        if (historySessions != other.historySessions) return false
        if (isGenerating != other.isGenerating) return false
        if (isOptimizingPrompt != other.isOptimizingPrompt) return false
        if (statusLog != other.statusLog) return false
        if (streamingText != other.streamingText) return false
        if (pendingCount != other.pendingCount) return false
        if (hasCompressedContext != other.hasCompressedContext) return false
        if (selectedModel != other.selectedModel) return false
        if (generationCount != other.generationCount) return false
        if (activeReferenceImageUri != other.activeReferenceImageUri) return false
        if (previewMemoryBytes != null) {
            if (other.previewMemoryBytes == null) return false
            if (!previewMemoryBytes.contentEquals(other.previewMemoryBytes)) return false
        } else if (other.previewMemoryBytes != null) return false
        if (pendingCropBounds != other.pendingCropBounds) return false
        if (isImageToImageMode != other.isImageToImageMode) return false
        return true
    }

    override fun hashCode(): Int {
        var result = scopeId.hashCode()
        result = 31 * result + (currentSession?.hashCode() ?: 0)
        result = 31 * result + historySessions.hashCode()
        result = 31 * result + isGenerating.hashCode()
        result = 31 * result + isOptimizingPrompt.hashCode()
        result = 31 * result + statusLog.hashCode()
        result = 31 * result + streamingText.hashCode()
        result = 31 * result + pendingCount
        result = 31 * result + hasCompressedContext.hashCode()
        result = 31 * result + selectedModel.hashCode()
        result = 31 * result + generationCount
        result = 31 * result + (activeReferenceImageUri?.hashCode() ?: 0)
        result = 31 * result + (previewMemoryBytes?.contentHashCode() ?: 0)
        result = 31 * result + (pendingCropBounds?.hashCode() ?: 0)
        result = 31 * result + isImageToImageMode.hashCode()
        return result
    }
}

/**
 * AI 视觉交互工作室 ViewModel
 */
class VisualChatStudioViewModel(
    private val scopeId: String,
    private val projectName: String,
    private val block: UIBlock?,
    private val initialReferenceImageUri: String?,
    private val aiService: AIGenerationService,
    private val templateRepo: TemplateRepository,
    private val sessionManager: SessionCacheManager
) : ViewModel() {

    private val TAG = "VisualChatStudioVM"
    private val configManager = ConfigManager()
    val trafficStore = SessionTrafficStore()
    private val compressionEngine = ContextCompressionEngine()
    private var currentGenerationJob: Job? = null

    private val _uiState = MutableStateFlow(
        VisualChatStudioState(
            scopeId = scopeId,
            activeReferenceImageUri = initialReferenceImageUri?.ifBlank { null },
            isImageToImageMode = !initialReferenceImageUri.isNullOrBlank()
        )
    )
    val uiState: StateFlow<VisualChatStudioState> = _uiState.asStateFlow()

    init {
        initSession()
        validateInitialReferencePhysicalFile()
        loadPersistedModel()
    }

    /**
     * 自动从本地持久化配置中恢复用户上次选中的生图/多模态模型
     */
    private fun loadPersistedModel() {
        viewModelScope.launch {
            val savedModelName = configManager.loadKey("STUDIO_SELECTED_MODEL")
            if (!savedModelName.isNullOrBlank()) {
                val matchedModel = GeminiModel.entries.firstOrNull { it.modelName == savedModelName }
                if (matchedModel != null) {
                    _uiState.update { it.copy(selectedModel = matchedModel) }
                    AppLogger.d(TAG, "已从本地配置恢复上次选中的模型: ${matchedModel.modelName}")
                }
            }
        }
    }

    /**
     * 物理存在性安全校验：如果缓存或记录的参考图文件不存在，还原为未设置状态
     */
    private fun validateInitialReferencePhysicalFile() {
        viewModelScope.launch {
            val initialUri = initialReferenceImageUri?.ifBlank { null }
            if (!initialUri.isNullOrBlank()) {
                val exists = isFileExists(initialUri)
                if (!exists) {
                    _uiState.update {
                        it.copy(
                            activeReferenceImageUri = null,
                            isImageToImageMode = false
                        )
                    }
                    AppLogger.w(TAG, "参考图物理文件不存在，已自动安全降级为未设置状态: $initialUri")
                }
            }
        }
    }

    /**
     * 初始化会话：自动检索并恢复该模块最后一次活跃的历史会话
     */
    private fun initSession() {
        viewModelScope.launch {
            val sessions = sessionManager.listSessions(scopeId)
            val lastActive = sessionManager.loadLastActiveSession(scopeId)

            if (lastActive != null && lastActive.messages.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        currentSession = lastActive,
                        historySessions = sessions,
                        hasCompressedContext = compressionEngine.shouldCompress(lastActive)
                    )
                }
                AppLogger.d(TAG, "已自动恢复最后一次活动会话: ${lastActive.id} (${lastActive.messages.size} 条记录)")
            } else {
                val titleStr = block?.id?.ifBlank { block.type.name } ?: "视觉设计会话"
                val newSession = sessionManager.createNewSession(
                    scopeId = scopeId,
                    title = titleStr
                )
                _uiState.update {
                    it.copy(
                        currentSession = newSession,
                        historySessions = listOf(newSession) + sessions,
                        hasCompressedContext = false
                    )
                }
            }
        }
    }

    /**
     * 针对本地物理缓存可能已被删除的图片，自动从该会话的原始通信档案中恢复并重新写入本地缓存文件。
     * 若文件在本地依然存在，直接返回其路径；若文件已丢失，回溯读取 RESP 报文提取 Base64 重新落盘。
     *
     * @param targetImagePath 原始记录的图片物理路径或 URI
     * @return 恢复或原本可用的物理文件路径；若无法恢复则原样返回
     */
    suspend fun ensureImageCached(targetImagePath: String): String {
        if (targetImagePath.isBlank()) {
            return targetImagePath
        }

        // 1. 若为 Base64 Data URI (data:image/...)，自动物理落盘到模块资产目录并同步更新当前会话消息
        if (targetImagePath.startsWith("data:image")) {
            val b64 = if (targetImagePath.contains(",")) targetImagePath.substringAfter(",") else targetImagePath
            return try {
                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                val bytes = kotlin.io.encoding.Base64.decode(b64)
                val isPng = targetImagePath.contains("image/png", ignoreCase = true)
                val targetBlockId = block?.id?.ifBlank { "chat_gen" } ?: "chat_gen"
                val savedFile = templateRepo.saveBlockResource(
                    templateName = projectName,
                    blockId = targetBlockId,
                    fileNamePrefix = "chat_gen_${getCurrentTimeMillis()}",
                    bytes = bytes,
                    isPng = isPng
                )
                val savedAbsPath = savedFile.getAbsolutePath()
                AppLogger.i(TAG, "已成功将 Data URI 转换为物理资产落盘: $savedAbsPath")

                // 同步回写当前会话消息中的 URI，实现自愈更新与持久化
                val current = _uiState.value.currentSession
                if (current != null) {
                    val updatedMessages = current.messages.map { msg ->
                        if (msg.generatedImageUri == targetImagePath) {
                            msg.copy(generatedImageUri = savedAbsPath)
                        } else {
                            msg
                        }
                    }
                    val updatedSession = current.copy(messages = updatedMessages, updatedAt = getCurrentTimeMillis())
                    sessionManager.saveSession(updatedSession)
                    _uiState.update { it.copy(currentSession = updatedSession) }
                }
                savedAbsPath
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析或保存 Data URI 图片异常", e)
                targetImagePath
            }
        }

        val cleanPath = targetImagePath.replace("\\", "/")
        if (org.gemini.ui.forge.utils.isFileExists(cleanPath)) {
            return cleanPath
        }

        AppLogger.w(TAG, "检测到本地图片已缺失: $cleanPath，尝试从会话历史通信档案恢复...")
        val current = _uiState.value.currentSession ?: return targetImagePath
        val records = trafficStore.listRecords(scopeId, current.id)
        if (records.isEmpty()) {
            AppLogger.w(TAG, "未查询到会话 ${current.id} 的通信报文，无法自动恢复缓存")
            return targetImagePath
        }

        // 从最新的 RESP 响应报文中正则匹配 Base64 数据并尝试恢复
        val regex = Regex(""""(data|bytesBase64Encoded)"\s*:\s*"([A-Za-z0-9+/=]{128,})"""")
        for (rec in records.reversed()) {
            if (rec.direction == org.gemini.ui.forge.model.chat.TrafficDirection.RESP) {
                val match = regex.find(rec.body)
                if (match != null) {
                    val b64 = match.groupValues[2]
                    try {
                        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                        val bytes = kotlin.io.encoding.Base64.decode(b64)
                        val isPng = cleanPath.endsWith(".png", ignoreCase = true)
                        val targetBlockId = block?.id?.ifBlank { "chat_gen" } ?: "chat_gen"
                        val restoredFile = templateRepo.saveBlockResource(
                            templateName = projectName,
                            blockId = targetBlockId,
                            fileNamePrefix = "chat_recovered",
                            bytes = bytes,
                            isPng = isPng
                        )
                        val restoredAbsPath = restoredFile.getAbsolutePath()
                        AppLogger.i(TAG, "已成功从原始通信报文恢复图片缓存: $restoredAbsPath")
                        org.gemini.ui.forge.utils.Toast.show("本地缓存已重新生成", org.gemini.ui.forge.ui.component.ToastType.SUCCESS)

                        // 同步更新消息中的路径
                        val updatedMessages = current.messages.map { msg ->
                            if (msg.generatedImageUri == targetImagePath || msg.generatedImageUri?.replace("\\", "/") == cleanPath) {
                                msg.copy(generatedImageUri = restoredAbsPath)
                            } else {
                                msg
                            }
                        }
                        val updatedSession = current.copy(messages = updatedMessages, updatedAt = getCurrentTimeMillis())
                        sessionManager.saveSession(updatedSession)
                        _uiState.update { it.copy(currentSession = updatedSession) }

                        return restoredAbsPath
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "解码或保存恢复图片异常", e)
                    }
                }
            }
        }
        return targetImagePath
    }

    /**
     * 加载当前模块在磁盘资产目录下的全量历史生成与切图图片（对齐属性面板历史数据源）
     */
    suspend fun loadModuleHistoricalImages(): List<TemplateFile> {
        val targetId = block?.id?.ifBlank { "chat_gen" } ?: "chat_gen"
        val rootDir = templateRepo.getDataDir()
        val sanitizedProject = projectName.replace(" ", "_")
        val dir = "$rootDir/templates/$sanitizedProject/assets/$targetId"
        return org.gemini.ui.forge.utils.listFilesInLocalDirectory(dir)
            .filter { it.endsWith(".png", ignoreCase = true) || it.endsWith(".jpg", ignoreCase = true) }
            .map { absPath ->
                val rel = absPath.replace("\\", "/").let {
                    val root = org.gemini.ui.forge.utils.GlobalAppEnv.currentRootPath
                    if (it.startsWith(root)) it.removePrefix(root).removePrefix("/") else it
                }
                TemplateFile(rel)
            }
    }

    /**
     * 切换当前会话专属生图模型并自动持久化，重启应用时自动恢复
     */
    fun updateModel(model: GeminiModel) {
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            configManager.saveKey("STUDIO_SELECTED_MODEL", model.modelName)
            AppLogger.d(TAG, "已持久化保存选中的模型: ${model.modelName}")
        }
    }

    /**
     * 切换当前会话单次生图数量
     */
    fun updateGenerationCount(count: Int) {
        _uiState.update { it.copy(generationCount = count.coerceIn(1, 4)) }
    }

    /**
     * 切换创作模式：从零直接创建新图 (false) vs 基于参考图以图生图 (true)
     */
    fun setCreationMode(isImageToImage: Boolean) {
        _uiState.update { it.copy(isImageToImageMode = isImageToImage) }
    }

    /**
     * 更新当前活动的基准参考图（支持本地外部文件选取，重置内存切图暂存）
     */
    fun updateActiveReferenceImage(uri: String?) {
        _uiState.update { 
            it.copy(
                activeReferenceImageUri = uri,
                previewMemoryBytes = null,
                pendingCropBounds = null,
                isImageToImageMode = if (uri != null) true else it.isImageToImageMode
            ) 
        }
    }

    /**
     * 暂存用户框选的参考图范围与内存字节流（延迟物理切图：仅驻留内存供预览，绝不写入磁盘垃圾碎片）
     */
    suspend fun setPendingReferenceArea(
        pageSourceUri: String,
        bounds: SerialRect,
        pageWidth: Float,
        pageHeight: Float
    ) {
        withContext(Dispatchers.Default) {
            try {
                val croppedBytes = cropImage(
                    imageSource = pageSourceUri,
                    bounds = bounds,
                    logicalWidth = pageWidth,
                    logicalHeight = pageHeight,
                    isPng = true
                )
                if (croppedBytes != null) {
                    _uiState.update {
                        it.copy(
                            previewMemoryBytes = croppedBytes,
                            pendingCropBounds = bounds,
                            isImageToImageMode = true,
                            statusLog = "已暂存自定义参考范围（内存预览，发送时自动物理切图）"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "截取预览区域失败", e)
            }
        }
    }

    /**
     * 清除当前参考底图（包含内存与物理引用）
     */
    fun clearReferenceImage() {
        _uiState.update {
            it.copy(
                activeReferenceImageUri = null,
                previewMemoryBytes = null,
                pendingCropBounds = null,
                isImageToImageMode = false,
                statusLog = "已还原为未设置参考图状态"
            )
        }
    }

    /**
     * 切换到指定的历史设计会话（若上一个会话尚未发送任何真实对话，则自动丢弃不残留）
     */
    fun switchSession(targetSession: VisualChatSession) {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        val previous = _uiState.value.currentSession
        val updatedHistory = if (previous != null && previous.id != targetSession.id && previous.messages.none { it.role == "user" }) {
            // 上一个会话未发送过任何真实消息，自动从列表剔除
            _uiState.value.historySessions.filterNot { it.id == previous.id }
        } else {
            _uiState.value.historySessions
        }

        _uiState.update {
            it.copy(
                currentSession = targetSession,
                historySessions = updatedHistory,
                isGenerating = false,
                statusLog = "已切换至会话: ${targetSession.title}",
                streamingText = "",
                pendingCount = 0,
                hasCompressedContext = compressionEngine.shouldCompress(targetSession)
            )
        }
    }

    /**
     * 删除指定的会话
     */
    fun deleteSession(targetSession: VisualChatSession) {
        viewModelScope.launch {
            sessionManager.deleteSession(scopeId, targetSession.id)
            val updatedSessions = sessionManager.listSessions(scopeId)
            val nextSession = if (_uiState.value.currentSession?.id == targetSession.id) {
                updatedSessions.firstOrNull() ?: sessionManager.createNewSession(scopeId, "新视觉设计会话").also {
                    sessionManager.saveSession(it)
                }
            } else {
                _uiState.value.currentSession
            }

            _uiState.update {
                it.copy(
                    currentSession = nextSession,
                    historySessions = if (updatedSessions.isEmpty()) listOfNotNull(nextSession) else updatedSessions,
                    hasCompressedContext = nextSession?.let { s -> compressionEngine.shouldCompress(s) } ?: false
                )
            }
            Toast.show("会话已删除", ToastType.INFO)
        }
    }

    /**
     * 新建会话（未发送前仅保存在内存中，不保存到磁盘）
     */
    fun createNewChat() {
        viewModelScope.launch {
            val current = _uiState.value.currentSession
            if (current != null && current.messages.none { it.role == "user" }) {
                Toast.show("当前已处于新会话，可直接输入指令", ToastType.INFO)
                return@launch
            }

            val freshSession = sessionManager.createNewSession(
                scopeId = scopeId,
                title = "新探索会话 ${getCurrentTimeMillis() % 10000}"
            )
            // 保持内存展示，未发送消息前不落盘
            val existing = _uiState.value.historySessions
            val allSessions = listOf(freshSession) + existing.filterNot { it.id == freshSession.id }
            _uiState.update {
                it.copy(
                    currentSession = freshSession,
                    historySessions = allSessions,
                    hasCompressedContext = false,
                    statusLog = "已开启全新设计会话",
                    streamingText = "",
                    pendingCount = 0
                )
            }
            Toast.show("已开启新会话", ToastType.INFO)
        }
    }

    /**
     * 中止当前正在运行的 AI 生成任务
     */
    fun cancelCurrentGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                statusLog = "已中止当前生成任务",
                streamingText = "",
                pendingCount = 0
            )
        }
        Toast.show("已中止生成任务", ToastType.INFO)
    }

    /** 更新当前思考模式等级配置 */
    fun updateThinkingLevel(level: org.gemini.ui.forge.model.chat.ThinkingLevel) {
        _uiState.update { it.copy(thinkingLevel = level) }
    }

    /** 将外部物理图片设为当前专属参考底图，并切换为以图生图模式 */
    fun setCustomReferenceImage(filePath: String) {
        _uiState.update {
            it.copy(
                activeReferenceImageUri = filePath,
                isImageToImageMode = true,
                previewMemoryBytes = null,
                pendingCropBounds = null
            )
        }
    }

    /**
     * AI 提示词一键优化（全链路支持流式增量生成与网络通信报文捕获）
     */
    fun optimizePrompt(
        sourceText: String, 
        apiKey: String, 
        onChunk: ((String) -> Unit)? = null,
        onResult: (String) -> Unit
    ) {
        if (sourceText.isBlank() || apiKey.isBlank()) return
        val current = _uiState.value.currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOptimizingPrompt = true) }
            try {
                var accumulated = ""
                val optimized = aiService.optimizePrompt(
                    originalPrompt = sourceText,
                    apiKey = apiKey,
                    onChunk = { chunk ->
                        accumulated += chunk
                        onChunk?.invoke(accumulated)
                    },
                    onRawTraffic = { direction, url, body ->
                        viewModelScope.launch {
                            val record = TrafficRecord(
                                seq = 0,
                                direction = direction,
                                timestamp = getCurrentTimeMillis(),
                                url = url,
                                model = "prompt-optimizer",
                                body = body
                            )
                            trafficStore.append(record, scopeId, current.id)
                        }
                    }
                )
                val cleanOptimized = optimized.trim()
                if (cleanOptimized.isNotBlank()) {
                    onResult(cleanOptimized)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to optimize prompt", e)
                Toast.show("提示词优化失败: ${e.message}", ToastType.ERROR)
            } finally {
                _uiState.update { it.copy(isOptimizingPrompt = false) }
            }
        }
    }

    /**
     * 发送生图/改图多轮指令（先存本地磁盘物理文件再渲染，彻底杜绝 Base64 传递与假透明）
     */
    fun sendGenerationRequest(
        userPrompt: String,
        activeLang: PromptLanguage = PromptLanguage.ZH,
        apiKey: String,
        model: GeminiModel = _uiState.value.selectedModel,
        generationCount: Int = _uiState.value.generationCount,
        referenceImageUri: String? = null,
        isImageToImage: Boolean = _uiState.value.isImageToImageMode,
        isPng: Boolean = true,
        isUploadToCloud: Boolean = false,
        customImageBytes: ByteArray? = null
    ) {
        val current = _uiState.value.currentSession ?: return
        if (apiKey.isBlank()) {
            Toast.show("请先配置 API Key", ToastType.ERROR)
            return
        }

        currentGenerationJob?.cancel()
        currentGenerationJob = viewModelScope.launch {
            var finalRefUri = if (isImageToImage) {
                (referenceImageUri ?: _uiState.value.activeReferenceImageUri ?: initialReferenceImageUri)?.ifBlank { null }
            } else {
                null
            }

            // ★ 延时按需切图机制：仅在向 AI 发送生图请求的瞬间，才真正将内存预览字节流物理落盘
            val memBytes = customImageBytes ?: _uiState.value.previewMemoryBytes
            if (isImageToImage && memBytes != null) {
                try {
                    val targetBlockId = block?.id?.ifBlank { "chat_gen" } ?: "chat_gen"
                    val now = getCurrentTimeMillis()
                    val savedTFile = templateRepo.saveBlockResource(
                        templateName = projectName,
                        blockId = targetBlockId,
                        fileNamePrefix = "crop_ref_${now}",
                        bytes = memBytes,
                        isPng = true
                    )
                    val realPath = savedTFile.getAbsolutePath()
                    finalRefUri = realPath
                    _uiState.update { 
                        it.copy(
                            activeReferenceImageUri = realPath,
                            previewMemoryBytes = null,
                            pendingCropBounds = null
                        ) 
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "延时参考切图物理落盘异常", e)
                }
            }

            // 1. 构造生图参数：★ 所见即所发，严格以当前输入框中的纯净单语提示词为准
            val isEn = activeLang == PromptLanguage.EN
            val effectivePrompt = userPrompt.trim()

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    statusLog = if (finalRefUri != null) "AI 正在结合参考底图生成图像方案..." else "AI 正在从零分析设计意图并生成图像...",
                    streamingText = "AI 正在分析设计意图与材质构图...",
                    pendingCount = generationCount
                )
            }

            val now = getCurrentTimeMillis()
            // ★ 所发即所存：消息实体中的数据必须是上传给 AI 的内容，由发给 AI 的数据决定存储与渲染
            val userMsg = VisualChatMessage(
                id = "user_$now",
                role = "user",
                prompt = effectivePrompt,
                textZh = if (isEn) "" else effectivePrompt,
                textEn = if (isEn) effectivePrompt else "",
                inputImageUris = listOfNotNull(finalRefUri),
                timestamp = now
            )

            // 先将用户消息加入时间线
            val updatedMessages = current.messages + userMsg
            val interimSession = current.copy(messages = updatedMessages, updatedAt = now)
            _uiState.update { it.copy(currentSession = interimSession) }

            try {
                val targetW = block?.bounds?.width ?: 512f
                val targetH = block?.bounds?.height ?: 512f

                // 依据当前代际模型特征自适应组装思考配置（生图模型安全拦截返回 null）
                val thinkingJson = _uiState.value.thinkingLevel.toThinkingConfigJson(model)

                var latestInteractionId: String? = null
                var latestThought: String? = null
                var latestSignature: String? = null

                // 2. 执行多模态图像生成 (支持基于 previousInteractionId 继承多轮会话)
                val generatedRawUris = aiService.generateImages(
                    model = model,
                    blockType = block?.type?.name ?: "UI_ELEMENT",
                    userPrompt = effectivePrompt,
                    apiKey = apiKey,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    isPng = isPng,
                    generationCount = generationCount,
                    referenceImageUri = finalRefUri,
                    thinkingConfigJson = thinkingJson,
                    previousInteractionId = current.latestInteractionId,
                    onLog = { log ->
                        // 纯净的人类可读业务状态才输出给对话界面
                        if (!log.contains("[AI REQUEST]") && !log.contains("URL:") && !log.contains("Body:")) {
                            _uiState.update { it.copy(statusLog = log) }
                        }
                    },
                    onRawTraffic = { direction, url, body ->
                        viewModelScope.launch {
                            val record = TrafficRecord(
                                seq = 0,
                                direction = direction,
                                timestamp = getCurrentTimeMillis(),
                                url = url,
                                model = model.modelName,
                                body = body
                            )
                            trafficStore.append(record, scopeId, current.id)
                        }
                    },
                    onResult = { unifiedResult ->
                        latestInteractionId = unifiedResult.interactionId
                        latestThought = unifiedResult.thoughtText
                        latestSignature = unifiedResult.thoughtSignature
                    }
                )

                // 3. 将返回的 Base64 字节流在本地物理落盘，生成真实 TemplateFile，并进行透明抠图处理
                val targetBlockId = block?.id?.ifBlank { "chat_gen" } ?: "chat_gen"
                val finalPhysicalFiles = generatedRawUris.mapIndexed { index, rawUri ->
                    try {
                        val rawBytes = if (rawUri.startsWith("data:image")) {
                            val b64 = if (rawUri.contains(",")) rawUri.substringAfter(",") else rawUri
                            @OptIn(ExperimentalEncodingApi::class)
                            Base64.decode(b64)
                        } else {
                            readLocalFileBytes(rawUri)
                        } ?: throw Exception("无法获取生图原始数据")

                        val finalBytes = if (isPng) {
                            _uiState.update { it.copy(statusLog = "正在执行本地 AI 抠图去除背景...", streamingText = "正在执行本地 AI 抠图剥离背景...") }
                            try {
                                aiService.removeBackgroundLocal(rawBytes) ?: rawBytes
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "本地自动去背景异常，保留原图", e)
                                rawBytes
                            }
                        } else {
                            rawBytes
                        }

                        // ★ 核心落地：保存为项目真实的物理资产文件
                        val savedTFile = templateRepo.saveBlockResource(
                            templateName = projectName,
                            blockId = targetBlockId,
                            fileNamePrefix = "chat_gen_${now}_$index",
                            bytes = finalBytes,
                            isPng = isPng
                        )
                        savedTFile.getAbsolutePath()
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "物理文件落盘异常", e)
                        rawUri
                    }
                }

                // 4. 将生成的物理文件作为 model 消息加入时间线
                val newModelMessages = finalPhysicalFiles.mapIndexed { index, physicalPath ->
                    VisualChatMessage(
                        id = "model_${getCurrentTimeMillis()}_$index",
                        role = "model",
                        textZh = if (finalPhysicalFiles.size > 1) "生成方案 ${index + 1}/${finalPhysicalFiles.size}" else "已根据您的指令完成渲染并落盘",
                        textEn = "Generated asset ${index + 1}/${finalPhysicalFiles.size}",
                        generatedImageUri = physicalPath,
                        thought = latestThought,
                        thoughtSignature = latestSignature,
                        interactionId = latestInteractionId,
                        timestamp = getCurrentTimeMillis() + index
                    )
                }

                val finalSession = interimSession.copy(
                    messages = interimSession.messages + newModelMessages,
                    latestInteractionId = latestInteractionId ?: interimSession.latestInteractionId,
                    updatedAt = getCurrentTimeMillis(),
                    designMemoryContext = if (compressionEngine.shouldCompress(interimSession)) {
                        compressionEngine.distillDesignMemory(interimSession)
                    } else interimSession.designMemoryContext
                )

                // 立即持久化保存到磁盘
                sessionManager.saveSession(finalSession)
                val allUpdatedSessions = sessionManager.listSessions(scopeId)
                _uiState.update {
                    it.copy(
                        currentSession = finalSession,
                        historySessions = allUpdatedSessions,
                        isGenerating = false,
                        statusLog = "生成完成 (共 ${finalPhysicalFiles.size} 张)！",
                        streamingText = "",
                        pendingCount = 0,
                        hasCompressedContext = compressionEngine.shouldCompress(finalSession)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLogger.i(TAG, "Generation job cancelled by user")
                } else {
                    AppLogger.e(TAG, "Multi-turn generation failed", e)
                    Toast.show("生成失败: ${e.message}", ToastType.ERROR)
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            statusLog = "生成失败: ${e.message}",
                            streamingText = "",
                            pendingCount = 0
                        )
                    }
                }
            }
        }
    }
}

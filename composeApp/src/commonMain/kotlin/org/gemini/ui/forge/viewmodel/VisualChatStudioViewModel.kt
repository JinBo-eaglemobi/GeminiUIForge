package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.SessionCacheManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.ContextCompressionEngine
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast
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
    val generationCount: Int = 1
) {
    /** 提取当前会话中生成的所有历史图片集合 */
    val sessionGeneratedImages: List<String>
        get() = currentSession?.messages?.mapNotNull { it.generatedImageUri }?.filter { it.isNotBlank() } ?: emptyList()
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
    private val compressionEngine = ContextCompressionEngine()
    private var currentGenerationJob: Job? = null

    private val _uiState = MutableStateFlow(VisualChatStudioState(scopeId = scopeId))
    val uiState: StateFlow<VisualChatStudioState> = _uiState.asStateFlow()

    init {
        initSession()
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
                sessionManager.saveSession(newSession)
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
     * 切换当前会话专属生图模型
     */
    fun updateModel(model: GeminiModel) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    /**
     * 切换当前会话单次生图数量
     */
    fun updateGenerationCount(count: Int) {
        _uiState.update { it.copy(generationCount = count.coerceIn(1, 4)) }
    }

    /**
     * 新建会话（不带入前序历史）
     */
    fun createNewChat() {
        viewModelScope.launch {
            val freshSession = sessionManager.createNewSession(
                scopeId = scopeId,
                title = "新探索会话 ${getCurrentTimeMillis() % 10000}"
            )
            sessionManager.saveSession(freshSession)
            val allSessions = sessionManager.listSessions(scopeId)
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

    /**
     * AI 提示词一键优化
     */
    fun optimizePrompt(sourceText: String, apiKey: String, onResult: (String) -> Unit) {
        if (sourceText.isBlank() || apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOptimizingPrompt = true) }
            try {
                val optimized = aiService.optimizePrompt(
                    originalPrompt = sourceText,
                    apiKey = apiKey
                )
                if (optimized.isNotBlank()) {
                    onResult(optimized)
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
        promptZh: String,
        promptEn: String,
        activeLang: PromptLanguage = PromptLanguage.ZH,
        apiKey: String,
        model: GeminiModel = _uiState.value.selectedModel,
        generationCount: Int = _uiState.value.generationCount,
        referenceImageUri: String? = null,
        isPng: Boolean = true,
        useCloudBgRemoval: Boolean = false,
        isUploadToCloud: Boolean = false
    ) {
        val current = _uiState.value.currentSession ?: return
        if (apiKey.isBlank()) {
            Toast.show("请先配置 API Key", ToastType.ERROR)
            return
        }

        currentGenerationJob?.cancel()
        currentGenerationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    statusLog = "正在准备多轮对话上下文...",
                    streamingText = "AI 正在分析设计意图与材质构图...",
                    pendingCount = generationCount
                )
            }

            val now = getCurrentTimeMillis()
            val userMsg = VisualChatMessage(
                id = "user_$now",
                role = "user",
                textZh = promptZh,
                textEn = promptEn,
                inputImageUris = listOfNotNull(referenceImageUri ?: initialReferenceImageUri),
                timestamp = now
            )

            // 先将用户消息加入时间线
            val updatedMessages = current.messages + userMsg
            val interimSession = current.copy(messages = updatedMessages, updatedAt = now)
            _uiState.update { it.copy(currentSession = interimSession) }

            try {
                // 1. 构造生图参数：★ 严格取当前激活 Tab 对应的提示词
                val effectivePrompt = if (activeLang == PromptLanguage.ZH) {
                    promptZh.ifBlank { promptEn }
                } else {
                    promptEn.ifBlank { promptZh }
                }
                val targetW = block?.bounds?.width ?: 512f
                val targetH = block?.bounds?.height ?: 512f

                // 2. 执行多模态图像生成
                val generatedRawUris = aiService.generateImages(
                    model = model,
                    blockType = block?.type?.name ?: "UI_ELEMENT",
                    userPrompt = effectivePrompt,
                    apiKey = apiKey,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    isPng = isPng,
                    generationCount = generationCount,
                    referenceImageUri = referenceImageUri ?: initialReferenceImageUri,
                    onLog = { log ->
                        _uiState.update { it.copy(statusLog = log) }
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
                        timestamp = getCurrentTimeMillis() + index
                    )
                }

                val finalSession = interimSession.copy(
                    messages = interimSession.messages + newModelMessages,
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

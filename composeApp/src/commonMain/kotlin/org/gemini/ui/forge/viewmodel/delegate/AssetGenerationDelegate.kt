package org.gemini.ui.forge.viewmodel.delegate

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.getProcessorCount
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.WorkerStatus
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.formatSize
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 批量生成结果封装
 */
data class WorkspaceBatchResult(val block: UIBlock, val candidates: List<TemplateFile>)

/**
 * 资产生成逻辑委托。
 * 负责与 AIGenerationService 交互，处理单次、批量以及多态资源的生成流程。
 */
class AssetGenerationDelegate(
    private val scope: CoroutineScope,
    private val aiService: AIGenerationService,
    private val templateRepo: TemplateRepository,
    private val getState: () -> ProjectWorkspaceState,
    private val updateState: ((ProjectWorkspaceState) -> ProjectWorkspaceState) -> Unit,
    private val notifySelectionHandled: () -> Unit
) {

    private var generationJob: Job? = null
    private val batchResultChannel = Channel<WorkspaceBatchResult>(Channel.UNLIMITED)
    private var confirmationDeferred: CompletableDeferred<Unit>? = null

    init {
        // 启动后台结果消费者：负责按顺序驱动 UI 确认弹窗
        scope.launch {
            for (result in batchResultChannel) {
                updateState { 
                    it.copy(
                        batchPendingConfirmBlock = result.block,
                        generatedCandidates = result.candidates
                    ) 
                }
                
                val deferred = CompletableDeferred<Unit>()
                confirmationDeferred = deferred
                deferred.await()
                
                updateState { 
                    it.copy(
                        batchPendingConfirmBlock = null,
                        generatedCandidates = emptyList()
                    ) 
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        updateState { it.copy(isGenerating = false) }
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
    fun onRequestGeneration(apiKey: String, customPrompt: String) {
        val currentState = getState()
        val block = currentState.selectedBlock ?: return
        val projectName = currentState.projectName
        val isTransparent = currentState.isGenerateTransparent
        val prioritizeCloud = currentState.isPrioritizeCloudRemoval
        val globalStyle = currentState.globalStyle
        val refUri = currentState.referenceImageUri
        val selectedModel = currentState.selectedModel

        generationJob?.cancel()
        generationJob = scope.launch {
            updateState { it.copy(
                isGenerating = true, 
                generationLogs = emptyList(), 
                showAITaskDialog = true,
                batchProgress = null,
                currentTaskStatus = "准备环境..."
            ) }
            
            addLog(">>> [任务开始] 为模块 [${block.id}] 生成资源 <<<")
            
            try {
                coroutineScope {
                    aiService.generateImages(
                        model = selectedModel,
                        apiKey = apiKey,
                        blockType = block.type.name,
                        userPrompt = customPrompt,
                        maxRetries = 3,
                        targetWidth = block.bounds.width,
                        targetHeight = block.bounds.height,
                        isPng = isTransparent,
                        style = globalStyle,
                        referenceImageUri = block.referenceImage?.getAbsolutePath() ?: refUri?.getAbsolutePath(),
                        onLog = { 
                            addLog("[AI-SDK] $it")
                            updateStatus("AI 交互中: $it")
                        },
                        onImageGenerated = { base64 ->
                            launch {
                                val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                                val bytes = Base64.decode(pure)
                                val timestamp = getCurrentTimeMillis()
                                val idx = getState().generatedCandidates.size
                                val sizeStr = formatSize(bytes.size)

                                updateStatus("正在接收数据: $sizeStr")
                                addLog("[IO] 收到原始图像数据 ($sizeStr)，正在保存...")
                                
                                val shortUuid = Uuid.random().toString().substringBefore('-')
                                val originalTFile = templateRepo.saveBlockResource(projectName, block.id, "gen_${idx}_${timestamp}_$shortUuid", bytes, isPng = false)
                                
                                var displayFile = originalTFile

                                if (isTransparent) {
                                    var processedBytes: ByteArray? = null
                                    if (prioritizeCloud && apiKey.isNotBlank()) {
                                        try {
                                            updateStatus("云端抠图中...")
                                            processedBytes = aiService.removeBackgroundCloud(bytes, apiKey) { addLog("[Cloud] $it") }
                                        } catch (e: Exception) { addLog("⚠️ [Cloud] 失败: ${e.message}") }
                                    }
                                    if (processedBytes == null) {
                                        try {
                                            updateStatus("本地抠图中...")
                                            processedBytes = aiService.removeBackgroundLocal(bytes) { addLog("[Local] $it") }
                                        } catch (e: Exception) { addLog("❌ [Local] 异常") }
                                    }
                                    if (processedBytes != null) {
                                        displayFile = templateRepo.saveBlockResource(projectName, block.id, "processed_${idx}_$timestamp", processedBytes, isPng = true)
                                    }
                                }

                                updateState { s -> s.copy(generatedCandidates = s.generatedCandidates + displayFile) }
                                addLog("✅ [完成] 预览图 [${idx + 1}] 就绪")
                                updateStatus("任务就绪")
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                addLog(">>> [致命错误] 生成失败: ${e.message} <<<")
                updateStatus("生成失败: ${e.message}")
            } finally {
                updateState { it.copy(isGenerating = false) }
            }
        }
    }

    fun startBatchGeneration(apiKey: String, selectedBlocks: List<UIBlock>) {
        if (selectedBlocks.isEmpty()) return
        val currentState = getState()
        val projectName = currentState.projectName
        val isTransparent = currentState.isGenerateTransparent
        val prioritizeCloud = currentState.isPrioritizeCloudRemoval
        val globalStyle = currentState.globalStyle
        val refUri = currentState.referenceImageUri
        val selectedModel = currentState.selectedModel
        val coreCount = getProcessorCount().coerceAtLeast(2)
        val semaphore = Semaphore(coreCount)

        generationJob?.cancel()
        generationJob = scope.launch {
            val initialWorkers = List(coreCount) { WorkerStatus(id = it) }
            updateState { it.copy(
                batchProgress = 0 to selectedBlocks.size, 
                showAITaskDialog = true, 
                generationLogs = emptyList(),
                isGenerating = true,
                currentTaskStatus = "分配并行队列...",
                activeWorkers = initialWorkers
            ) }
            
            val jobs = selectedBlocks.mapIndexed { index, block ->
                launch {
                    semaphore.withPermit {
                        val slotId = getState().activeWorkers.find { !it.isBusy }?.id ?: (index % coreCount)
                        updateWorker(slotId, blockId = block.id, action = "初始化", isBusy = true)
                        
                        val currentCandidates = mutableListOf<TemplateFile>()
                        try {
                            coroutineScope {
                                aiService.generateImages(
                                    model = selectedModel,
                                    apiKey = apiKey,
                                    blockType = block.type.name,
                                    userPrompt = block.fullPrompt,
                                    maxRetries = 3,
                                    targetWidth = block.bounds.width,
                                    targetHeight = block.bounds.height,
                                    isPng = isTransparent,
                                    style = globalStyle,
                                    referenceImageUri = block.referenceImage?.getAbsolutePath() ?: refUri?.getAbsolutePath(),
                                    onLog = { log -> updateWorker(slotId, blockId = block.id, action = "AI 生成中", info = log) },
                                    onImageGenerated = { base64 ->
                                        launch {
                                            val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                                            @OptIn(ExperimentalEncodingApi::class)
                                            val bytes = Base64.decode(pure)
                                            val originalTFile = templateRepo.saveBlockResource(projectName, block.id, "batch_${getCurrentTimeMillis()}", bytes, isPng = false)
                                            
                                            var displayFile = originalTFile
                                            if (isTransparent) {
                                                var processedBytes: ByteArray? = null
                                                if (prioritizeCloud && apiKey.isNotBlank()) {
                                                    try { processedBytes = aiService.removeBackgroundCloud(bytes, apiKey) } catch (e: Exception) {}
                                                }
                                                if (processedBytes == null) {
                                                    try { processedBytes = aiService.removeBackgroundLocal(bytes) } catch (e: Exception) {}
                                                }
                                                if (processedBytes != null) {
                                                    displayFile = templateRepo.saveBlockResource(projectName, block.id, "batch_proc_${getCurrentTimeMillis()}", processedBytes, isPng = true)
                                                }
                                            }
                                            currentCandidates.add(displayFile)
                                        }
                                    }
                                )
                            }
                            batchResultChannel.send(WorkspaceBatchResult(block, currentCandidates.toList()))
                            updateWorker(slotId, blockId = block.id, action = "✅ 已完成", isBusy = true, isCompleted = true)
                        } catch (e: Exception) {
                            updateWorker(slotId, blockId = block.id, action = "❌ 失败", info = e.message ?: "", isBusy = true)
                        } finally {
                            updateState { s -> s.copy(batchProgress = (s.batchProgress?.first ?: 0) + 1 to selectedBlocks.size) }
                            delay(1000)
                            updateWorker(slotId, isBusy = false) 
                        }
                    }
                }
            }
            jobs.joinAll()
            updateStatus("流程已就绪")
            updateState { it.copy(isGenerating = false) }
        }
    }

    // --- 按钮多态生成 ---

    fun executeButtonStateGen(apiKey: String, target: ButtonGenTarget = ButtonGenTarget.ALL) {
        val currentState = getState()
        val block = currentState.selectedBlock ?: return
        val baseImageUri = block.currentImageUri?.getAbsolutePath() ?: return
        
        generationJob?.cancel()
        generationJob = scope.launch {
            updateState { it.copy(isButtonGenInProgress = true, showAITaskDialog = true) }
            try {
                coroutineScope {
                    val pressedDeferred = if (target == ButtonGenTarget.ALL || target == ButtonGenTarget.PRESSED) {
                        async {
                            generateSingleDerivedState(apiKey, currentState.buttonPressedPrompt, "pressed", block, currentState)
                        }
                    } else null

                    val disabledDeferred = if (target == ButtonGenTarget.ALL || target == ButtonGenTarget.DISABLED) {
                        async {
                            generateSingleDerivedState(apiKey, currentState.buttonDisabledPrompt, "disabled", block, currentState)
                        }
                    } else null

                    val finalPressedUri = pressedDeferred?.await() ?: if (target != ButtonGenTarget.ALL) currentState.buttonPressedCandidate else null
                    val finalDisabledUri = disabledDeferred?.await() ?: if (target != ButtonGenTarget.ALL) currentState.buttonDisabledCandidate else null

                    updateState { it.copy(
                        buttonPressedCandidate = finalPressedUri,
                        buttonDisabledCandidate = finalDisabledUri,
                        isButtonGenInProgress = false
                    ) }
                }
            } catch (e: Exception) {
                updateState { it.copy(isButtonGenInProgress = false) }
            }
        }
    }

    private suspend fun generateSingleDerivedState(
        apiKey: String,
        prompt: String,
        prefix: String,
        block: UIBlock,
        currentState: ProjectWorkspaceState
    ): TemplateFile {
        val deferredBytes = CompletableDeferred<ByteArray>()
        aiService.generateImages(
            model = currentState.selectedModel,
            apiKey = apiKey,
            blockType = block.type.name,
            userPrompt = prompt,
            maxRetries = 3,
            targetWidth = block.bounds.width,
            targetHeight = block.bounds.height,
            isPng = currentState.isGenerateTransparent,
            style = currentState.globalStyle,
            referenceImageUri = block.currentImageUri?.getAbsolutePath(),
            onLog = { addLog("[$prefix] $it") },
            onImageGenerated = { base64 ->
                val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                @OptIn(ExperimentalEncodingApi::class)
                deferredBytes.complete(Base64.decode(pure))
            }
        )
        val bytes = deferredBytes.await()
        var processedBytes = bytes
        if (currentState.isGenerateTransparent) {
            try { processedBytes = aiService.removeBackgroundLocal(bytes) ?: bytes } catch (e: Exception) {}
        }
        return templateRepo.saveBlockResource(currentState.projectName, block.id, "${prefix}_${getCurrentTimeMillis()}", processedBytes, isPng = currentState.isGenerateTransparent)
    }

    // --- 内部辅助 ---

    private fun addLog(msg: String) {
        AppLogger.i("AssetGen", msg)
        updateState { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) }
    }

    private fun updateStatus(msg: String) {
        updateState { it.copy(currentTaskStatus = msg) }
    }

    private fun updateWorker(slotId: Int, blockId: String = "", action: String = "", info: String = "", isBusy: Boolean = true, isCompleted: Boolean = false) {
        updateState { s ->
            val newWorkers = s.activeWorkers.map { 
                if (it.id == slotId) it.copy(blockId = blockId, action = action, info = info, isBusy = isBusy, isCompleted = isCompleted) 
                else it 
            }
            s.copy(activeWorkers = newWorkers)
        }
    }

    fun completeConfirmation() {
        confirmationDeferred?.complete(Unit)
    }

    enum class ButtonGenTarget { ALL, PRESSED, DISABLED }
}

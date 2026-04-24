package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.getProcessorCount
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.service.*
import org.gemini.ui.forge.utils.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.gemini.ui.forge.manager.CloudAssetManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BatchResult(val block: UIBlock, val candidates: List<TemplateFile>)

/**
 * 资产生成器的专属 ViewModel
 */
class TemplateAssetGenViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService,
    private val onDirtyChanged: (Boolean) -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(
        TemplateAssetGenState(
            project = initialProject,
            projectName = initialProjectName,
            currentLang = initialLang
        )
    )
    val state: StateFlow<TemplateAssetGenState> = _state.asStateFlow()

    private fun markDirty() {
        onDirtyChanged(true)
    }

    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    private var generationJob: kotlinx.coroutines.Job? = null

    // --- 批量确认队列机制 ---
    private val batchResultChannel = Channel<BatchResult>(Channel.UNLIMITED)
    private var confirmationDeferred: CompletableDeferred<Unit>? = null

    init {
        // 启动后台结果消费者：负责按顺序驱动 UI 确认弹窗
        viewModelScope.launch {
            for (result in batchResultChannel) {
                // 1. 设置当前待确认的 Block 和候选图
                _state.update { 
                    it.copy(
                        batchPendingConfirmBlock = result.block,
                        generatedCandidates = result.candidates
                    ) 
                }
                
                // 2. 创建一个 Deferred 并挂起，等待用户完成选择
                val deferred = CompletableDeferred<Unit>()
                confirmationDeferred = deferred
                deferred.await()
                
                // 3. 重置状态，准备处理下一个结果
                _state.update { 
                    it.copy(
                        batchPendingConfirmBlock = null,
                        generatedCandidates = emptyList()
                    ) 
                }
            }
        }
    }

    /** 强制重载最新的 ProjectState（解决重入时旧状态残留的问题） */
    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        _state.update { 
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null,
                globalStyle = newProject.globalStyle,
                referenceImageUri = newProject.styleReferenceUri
            ) 
        }
    }

    /** 
     * 保存当前的风格设置与参考图，并执行磁盘缓存管理 
     */
    fun saveStyleSettings(onComplete: () -> Unit) {
        val currentStyle = _state.value.globalStyle
        val currentRefUri = _state.value.referenceImageUri
        val projectName = _state.value.projectName
        val sanitizedProjectName = projectName.replace(" ", "_")
        
        viewModelScope.launch {
            var finalRefUri: TemplateFile? = currentRefUri

            if (currentRefUri != null) {
                val destDir = "templates/$sanitizedProjectName/assets/style_ref"
                val currentRelPath = currentRefUri.relativePath
                
                try {
                    // 如果参考图不在当前的目标文件夹内，说明需要执行更新逻辑
                    if (!currentRelPath.contains(destDir)) {
                        // 1. 流式计算新参考图的哈希值
                        val newHash = calculateFileHash(currentRefUri.getAbsolutePath())
                        var isSame = false

                        if (newHash != null) {
                            // 使用 TemplateFile 扫描现有文件
                            val destDirFile = TemplateFile(destDir)
                            val existingFiles = if (destDirFile.exists()) listFilesInLocalDirectory(destDirFile.getAbsolutePath()) else emptyList()
                            
                            // 2. 检查现有文件是否具有相同的哈希
                            if (existingFiles.isNotEmpty()) {
                                val oldFilePath = existingFiles.first()
                                val oldHash = calculateFileHash(oldFilePath)
                                if (oldHash == newHash) {
                                    isSame = true
                                    // 转换为 TemplateFile 存储
                                    val rel = oldFilePath.replace("\\", "/").let {
                                        val root = org.gemini.ui.forge.state.GlobalAppEnv.currentRootPath
                                        if (it.startsWith(root)) it.removePrefix(root).removePrefix("/") else it
                                    }
                                    finalRefUri = TemplateFile(rel)
                                    AppLogger.d("AssetGenVM", "✅ 参考图内容一致 (SHA-256: $newHash)，跳过复制")
                                }
                            }
                            
                            // 3. 哈希不同或不存在旧文件，执行覆盖
                            if (!isSame) {
                                AppLogger.i("AssetGenVM", "🔄 发现新的参考图，开始清理旧图并执行流式复制...")
                                existingFiles.forEach { deleteLocalFile(it) }
                                
                                val ext = currentRelPath.substringAfterLast(".", "jpg")
                                val targetFileName = "active_style_ref_${newHash.take(8)}.$ext"
                                val relDestPath = "$destDir/$targetFileName"
                                val tFile = TemplateFile(relDestPath)
                                
                                // 流式复制新图
                                val success = copyLocalFile(currentRefUri.getAbsolutePath(), tFile.getAbsolutePath())
                                if (success) {
                                    finalRefUri = tFile
                                    _state.update { it.copy(referenceImageUri = finalRefUri) }
                                    AppLogger.i("AssetGenVM", "✅ 参考图流式复制成功: $targetFileName")
                                } else {
                                    AppLogger.e("AssetGenVM", "❌ 复制失败")
                                    finalRefUri = currentRefUri
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("AssetGenVM", "处理风格图失败: ${e.message}")
                }
            } else {
                // 用户清除了参考图，清理缓存目录
                try {
                    val destDir = "templates/$sanitizedProjectName/assets/style_ref"
                    val tDir = TemplateFile(destDir)
                    if (tDir.exists()) {
                        tDir.delete(recursive = true)
                    }
                } catch (e: Exception) {}
            }

            // 更新内存并在后台同步保存 JSON
            val updatedProject = _state.value.project.copy(
                globalStyle = currentStyle,
                styleReferenceUri = finalRefUri
            )
            
            _state.update { it.copy(project = updatedProject) }
            markDirty()
            
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // --- 页面与选择逻辑 ---

    fun onPageSelected(pageId: String) {
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }
        markDirty()
    }

    fun onBlockClicked(blockId: String?) =
        _state.update { it.copy(selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId) }

    fun onBlockDoubleClicked(blockId: String) {
        val block = findBlockById(_state.value.project.pages.flatMap { it.blocks }, blockId)
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    fun exitGroupEditMode() {
        _state.update { it.copy(editingGroupId = null) }
    }

    // --- 资源生成核心逻辑 ---

    fun onRequestGeneration(apiKey: String, customPrompt: String) {
        val block = _state.value.selectedBlock ?: return
        val submitPrompt = customPrompt
        val projectName = _state.value.projectName
        val isTransparent = _state.value.isGenerateTransparent
        val prioritizeCloud = _state.value.isPrioritizeCloudRemoval
        val globalStyle = _state.value.globalStyle
        val refUri = _state.value.referenceImageUri
        val selectedModel = _state.value.selectedModel

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            addGenLog(">>> 开始为模块 [${block.id}] 生成资源 <<<")
            try {
                aiService.generateImages(
                    model = selectedModel,
                    apiKey = apiKey,
                    blockType = block.type.name,
                    userPrompt = submitPrompt,
                    maxRetries = 3,
                    targetWidth = block.bounds.width,
                    targetHeight = block.bounds.height,
                    isPng = isTransparent,
                    style = globalStyle,
                    referenceImageUri = refUri?.getAbsolutePath(),
                    onLog = { addGenLog(it) },
                    onImageGenerated = { base64 ->
                        // 异步处理单张图片，不阻塞后续图片的接收
                        viewModelScope.launch {
                            val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                            @OptIn(ExperimentalEncodingApi::class)
                            val bytes = Base64.decode(pure)
                            val timestamp = getCurrentTimeMillis()
                            val idx = _state.value.generatedCandidates.size

                            val originalTFile = templateRepo.saveBlockResource(projectName, block.id, "gen_${idx}_$timestamp", bytes, isPng = false)
                            var displayFile = originalTFile

                            if (isTransparent) {
                                var processedBytes: ByteArray? = null
                                if (prioritizeCloud && apiKey.isNotBlank()) {
                                    try {
                                        processedBytes = aiService.removeBackgroundCloud(bytes, apiKey) { addGenLog(it) }
                                    } catch (e: Exception) {
                                        addGenLog("⚠️ 预览图 [${idx+1}] 云端抠图失败: ${e.message}")
                                    }
                                }
                                if (processedBytes == null) {
                                    try {
                                        processedBytes = aiService.removeBackgroundLocal(bytes) { addGenLog(it) }
                                    } catch (e: Exception) {
                                        addGenLog("❌ 预览图 [${idx+1}] 本地抠图失败")
                                    }
                                }
                                if (processedBytes != null) {
                                    displayFile = templateRepo.saveBlockResource(projectName, block.id, "processed_${idx}_$timestamp", processedBytes, isPng = true)
                                }
                            }

                            // 实时同步到 UI
                            _state.update { s ->
                                s.copy(generatedCandidates = s.generatedCandidates + displayFile)
                            }
                            addGenLog("✅ 预览图 [${idx + 1}] 已生成并就绪")
                        }
                    }
                )
            } catch (e: Exception) {
                addGenLog(">>> 生成失败: ${e.message} <<<")
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    // --- 批量生成逻辑 ---

    fun openBatchGenDialog() {
        _state.update { it.copy(showBatchGenDialog = true) }
    }

    fun closeBatchGenDialog() {
        _state.update { it.copy(showBatchGenDialog = false) }
    }

    fun startBatchGeneration(apiKey: String, selectedBlocks: List<UIBlock>) {
        if (selectedBlocks.isEmpty()) return
        val projectName = _state.value.projectName
        val isTransparent = _state.value.isGenerateTransparent
        val prioritizeCloud = _state.value.isPrioritizeCloudRemoval
        val globalStyle = _state.value.globalStyle
        val refUri = _state.value.referenceImageUri
        val selectedModel = _state.value.selectedModel
        val coreCount = getProcessorCount().coerceAtLeast(2)
        val semaphore = Semaphore(coreCount)

        closeBatchGenDialog()
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.update { it.copy(batchProgress = 0 to selectedBlocks.size, showAITaskDialog = true, generationLogs = emptyList()) }
            addGenLog("🚀 开始批量任务：共 ${selectedBlocks.size} 个模块，并发限制：$coreCount")

            selectedBlocks.forEach { block ->
                launch {
                    semaphore.withPermit {
                        addGenLog("--- 正在为 [${block.id}] 请求生成 ---")
                        val currentCandidates = mutableListOf<TemplateFile>()
                        try {
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
                                referenceImageUri = refUri?.getAbsolutePath(),
                                onLog = { /* 内部静默记录或按需输出 */ },
                                onImageGenerated = { base64 ->
                                    val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                                    @OptIn(ExperimentalEncodingApi::class)
                                    val bytes = Base64.decode(pure)
                                    val timestamp = getCurrentTimeMillis()
                                    
                                    viewModelScope.launch {
                                        val originalTFile = templateRepo.saveBlockResource(projectName, block.id, "batch_gen_${getCurrentTimeMillis()}", bytes, isPng = false)
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
                            // 等待一小会儿确保保存逻辑完成
                            kotlinx.coroutines.delay(1000) 
                            batchResultChannel.send(BatchResult(block, currentCandidates.toList()))
                            addGenLog("✅ 模块 [${block.id}] 已完成生成，进入待确认队列")
                        } catch (e: Exception) {
                            addGenLog("❌ 模块 [${block.id}] 生成失败: ${e.message}")
                        } finally {
                            _state.update { s -> s.copy(batchProgress = (s.batchProgress?.first ?: 0) + 1 to selectedBlocks.size) }
                        }
                    }
                }
            }
        }
    }

    // --- 资产操作逻辑 ---

    fun onImageSelected(imageUri: TemplateFile) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.batchPendingConfirmBlock?.id ?: _state.value.selectedBlockId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) {
                    it.copy(
                        currentImageUri = imageUri
                    )
                }) else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                generatedCandidates = if (currentState.batchPendingConfirmBlock != null) currentState.generatedCandidates else emptyList()
            )
        }
        markDirty()
        
        // 如果是在批量确认流程中，释放锁，触发下一个弹窗
        confirmationDeferred?.complete(Unit)
    }

    fun skipCurrentBatchConfirmation() {
        confirmationDeferred?.complete(Unit)
    }

    /** 
     * 执行真正的图片裁剪，并将裁剪后的新图片保存绑定给当前的 UIBlock。
     * @param originalUri 原始底图 (注意：这里可能是非 TemplateFile 的路径，如相册路径)
     * @param cropRect 相对原始尺寸(1.0x1.0) 的裁剪比例坐标 (left, top, right, bottom)
     */
    suspend fun onImageCroppedAndSelected(originalUri: String, cropRect: SerialRect): Boolean {
        val blockId = _state.value.selectedBlockId ?: return false
        val projectName = _state.value.projectName

        return withContext(Dispatchers.Default) {
            try {
                AppLogger.i("TemplateAssetGenViewModel", "✂️ 开始执行图片裁剪操作...")
                AppLogger.d("TemplateAssetGenViewModel", "原图: $originalUri")
                AppLogger.d("TemplateAssetGenViewModel", "相对裁剪比例: left=${cropRect.left}, top=${cropRect.top}, right=${cropRect.right}, bottom=${cropRect.bottom}")

                // 读取原图实际尺寸
                val size = getImageSize(originalUri)
                if (size == null) {
                    AppLogger.e("TemplateAssetGenViewModel", "❌ 无法读取原图实际尺寸，裁剪失败")
                    return@withContext false
                }
                
                val originalW = size.first.toFloat()
                val originalH = size.second.toFloat()
                AppLogger.d("TemplateAssetGenViewModel", "原图物理像素尺寸: ${size.first}x${size.second}")

                // 根据用户传回的相对比例 (0.0~1.0)，换算为绝对逻辑坐标
                val absLeft = cropRect.left * originalW
                val absTop = cropRect.top * originalH
                val absRight = cropRect.right * originalW
                val absBottom = cropRect.bottom * originalH
                val bounds = SerialRect(absLeft, absTop, absRight, absBottom)
                AppLogger.d("TemplateAssetGenViewModel", "换算后的绝对像素裁剪框: $bounds")

                val targetBlock = _state.value.selectedBlock
                val forceW = targetBlock?.bounds?.width?.toInt()?.coerceAtLeast(1)
                val forceH = targetBlock?.bounds?.height?.toInt()?.coerceAtLeast(1)
                
                AppLogger.d("TemplateAssetGenViewModel", "📏 强制输出尺寸: ${forceW}x${forceH}")

                val croppedBytes = cropImage(
                    imageSource = originalUri,
                    bounds = bounds,
                    logicalWidth = originalW,
                    logicalHeight = originalH,
                    isPng = true,
                    forceWidth = forceW,
                    forceHeight = forceH
                )

                if (croppedBytes != null) {
                    AppLogger.i("TemplateAssetGenViewModel", "✅ 像素裁剪成功，正在保存新文件... (${croppedBytes.size / 1024} KB)")
                    // 保存新生成的裁剪后图片
                    val croppedTFile = templateRepo.saveBlockResource(
                        projectName,
                        blockId,
                        "cropped_${getCurrentTimeMillis()}",
                        croppedBytes,
                        isPng = true
                    )
                    
                    AppLogger.i("TemplateAssetGenViewModel", "✅ 新文件已落盘: ${croppedTFile.relativePath}，正在绑定到模块 $blockId")
                    // 切换回主线程更新 UI 状态
                    withContext(Dispatchers.Main) {
                        onImageSelected(croppedTFile)
                    }
                    true
                } else {
                    AppLogger.e("TemplateAssetGenViewModel", "❌ 裁剪引擎返回空数据，操作失败")
                    false
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateAssetGenViewModel", "❌ 图片裁剪抛出异常", e)
                false
            }
        }
    }

    fun deleteImages(uris: List<TemplateFile>) {
        viewModelScope.launch {
            uris.forEach { it.delete() }
            _state.update { currentState -> currentState.copy(generatedCandidates = currentState.generatedCandidates.filter { it !in uris }) }
        }
        markDirty()
    }

    fun batchRemoveBackgroundLocal(uris: List<TemplateFile>, onSuccess: (List<TemplateFile>) -> Unit = {}) {
        val block = _state.value.selectedBlock ?: return
        val projectName = _state.value.projectName

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.update { it.copy(isLocalProcessing = true) }
            
            try {
                val newCandidatePaths = mutableListOf<TemplateFile>()
                for ((index, tFile) in uris.withIndex()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null) {
                        val processedBytes = aiService.removeBackgroundLocal(bytes)
                        if (processedBytes != null) {
                            val timestamp = getCurrentTimeMillis()
                            val newTFile = templateRepo.saveBlockResource(
                                projectName,
                                block.id,
                                "batch_processed_${index}_$timestamp",
                                processedBytes,
                                isPng = true
                            )
                            newCandidatePaths.add(newTFile)
                        }
                    }
                }
                
                if (newCandidatePaths.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onSuccess(newCandidatePaths)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateAssetGenViewModel", "批量处理异常", e)
            } finally {
                _state.update { it.copy(isLocalProcessing = false) }
            }
        }
    }

    fun appendCandidates(newPaths: List<TemplateFile>) {
        _state.update { it.copy(generatedCandidates = it.generatedCandidates + newPaths) }
    }

    fun clearCandidates() = _state.update { it.copy(generatedCandidates = emptyList()) }
    fun clearSelectedImage(blockId: String) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(
                        page.blocks,
                        blockId
                    ) { it.copy(currentImageUri = null) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    suspend fun loadHistoricalImages(blockId: String): List<TemplateFile> {
        val rootDir = templateRepo.getDataDir()
        val dir = "$rootDir/templates/${_state.value.projectName.replace(" ", "_")}/assets/$blockId"
        return listFilesInLocalDirectory(dir)
            .filter { it.endsWith(".png") || it.endsWith(".jpg") }
            .map { absPath ->
                val rel = absPath.replace("\\", "/").let {
                    val root = org.gemini.ui.forge.state.GlobalAppEnv.currentRootPath
                    if (it.startsWith(root)) it.removePrefix(root).removePrefix("/") else it
                }
                TemplateFile(rel)
            }
    }

    // --- 布局同步逻辑 ---

    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId) ?: return@update currentState
            val newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)
            val resultBlocks =
                if (targetId == null) newBlocks + draggedBlock else if (dropPosition == DropPosition.INSIDE) updateBlockInList(
                    newBlocks,
                    targetId
                ) { it.copy(children = it.children + draggedBlock) } else insertBlockSibling(
                    newBlocks,
                    targetId,
                    draggedBlock,
                    dropPosition
                )
            currentState.copy(project = currentState.project.copy(pages = currentState.project.pages.map {
                if (it.id == pageId) it.copy(
                    blocks = resultBlocks
                ) else it
            }))
        }
        markDirty()
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(
                        page.blocks,
                        blockId
                    ) { b ->
                        b.copy(
                            bounds = b.bounds.copy(
                                left = b.bounds.left + dx,
                                top = b.bounds.top + dy,
                                right = b.bounds.right + dx,
                                bottom = b.bounds.bottom + dy
                            )
                        )
                    }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun renameBlock(oldId: String, newId: String) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(
                        page.blocks,
                        oldId
                    ) { it.copy(id = newId) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun addCustomBlock(id: String, type: UIBlockType, w: Float, h: Float) {
        val finalId = if (id.isBlank()) "block_${getCurrentTimeMillis()}" else id
        val newBlock = UIBlock(finalId, type, SerialRect(0f, 0f, w, h))
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = page.blocks + newBlock) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = finalId)
        }
        markDirty()
    }

    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(
                        page.blocks,
                        blockId
                    ) { it.copy(isVisible = isVisible) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        _state.update { currentState ->
            fun updateVis(list: List<UIBlock>): List<UIBlock> =
                list.map { it.copy(isVisible = isVisible, children = updateVis(it.children)) }

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateVis(page.blocks)) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    // --- UI 辅助 ---

    fun setGenerateTransparent(enabled: Boolean) {
        _state.update { it.copy(isGenerateTransparent = enabled) }
    }

    fun setPrioritizeCloudRemoval(enabled: Boolean) {
        _state.update { it.copy(isPrioritizeCloudRemoval = enabled) }
    }

    fun setGlobalStyle(style: String) {
        _state.update { it.copy(globalStyle = style) }
        markDirty()
    }

    fun setImageGenModel(model: GeminiModel) {
        _state.update { it.copy(selectedModel = model) }
    }

    /**
     * 设置参考图。支持传入外部绝对路径，会自动执行即时归档。
     */
    fun setReferenceImageExternal(uriOrPath: String?) {
        if (uriOrPath == null) {
            _state.update { it.copy(referenceImageUri = null) }
            markDirty()
            return
        }

        viewModelScope.launch {
            // 如果已经是相对路径，尝试直接构造
            val tFile = if (!uriOrPath.startsWith("/") && !uriOrPath.contains(":\\")) {
                TemplateFile(uriOrPath)
            } else {
                // 外部路径，执行归档
                templateRepo.archiveExternalImages(_state.value.projectName, listOf(uriOrPath)).firstOrNull()
            }

            if (tFile != null) {
                _state.update { it.copy(referenceImageUri = tFile) }
                markDirty()
            }
        }
    }

    /** 内部使用的强类型设置方法 */
    fun setReferenceImage(tFile: TemplateFile?) {
        _state.update { it.copy(referenceImageUri = tFile) }
        markDirty()
    }

    fun toggleVisualMode() {
        _state.update { it.copy(isVisualMode = !it.isVisualMode) }
    }

    fun cancelGeneration() {
        generationJob?.cancel(); _state.update { it.copy(isGenerating = false) }
    }

    fun toggleGenerationLogVisibility() {
        _state.update { it.copy(isGenerationLogVisible = !it.isGenerationLogVisible) }
    }

    fun closeAITaskDialog() {
        _state.update { it.copy(showAITaskDialog = false) }
    }

    private fun addGenLog(msg: String) {
        _state.update { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) }
    }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block; findBlockById(block.children, id)?.let { return it }
        }
        return null
    }

    private fun updateBlockInList(
        blocks: List<UIBlock>,
        blockId: String,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { block ->
            if (block.id == blockId) transform(block)
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children) block.copy(children = newChildren) else block
            }
        }
    }

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> =
        blocks.filterNot { it.id == idToRemove }
            .map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }

    private fun insertBlockSibling(
        blocks: List<UIBlock>,
        targetId: String,
        blockToInsert: UIBlock,
        position: DropPosition
    ): List<UIBlock> {
        val index = blocks.indexOfFirst { it.id == targetId }
        if (index != -1) {
            val result = blocks.toMutableList()
            if (position == DropPosition.BEFORE) result.add(index, blockToInsert) else result.add(
                index + 1,
                blockToInsert
            )
            return result
        }
        return blocks.map { b ->
            val newChildren = insertBlockSibling(b.children, targetId, blockToInsert, position)
            if (newChildren !== b.children) b.copy(children = newChildren) else b
        }
    }
}

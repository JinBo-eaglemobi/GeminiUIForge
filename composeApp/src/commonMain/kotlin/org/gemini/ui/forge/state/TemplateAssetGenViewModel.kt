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
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.service.*
import org.gemini.ui.forge.utils.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 资产生成器的专属 ViewModel
 */
class TemplateAssetGenViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService
) : ViewModel() {

    private val _state = MutableStateFlow(
        TemplateAssetGenState(
            project = initialProject,
            projectName = initialProjectName,
            selectedPageId = initialProject.pages.firstOrNull()?.id,
            currentLang = initialLang
        )
    )
    val state: StateFlow<TemplateAssetGenState> = _state.asStateFlow()

    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    private var generationJob: kotlinx.coroutines.Job? = null

    /** 强制重载最新的 ProjectState（解决重入时旧状态残留的问题） */
    fun reload(newProject: ProjectState) {
        if (_state.value.project === newProject) return
        _state.update { 
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null
            ) 
        }
    }

    // --- 页面与选择逻辑 ---

    fun onPageSelected(pageId: String) =
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }

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

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            addGenLog(">>> 开始为模块 [${block.id}] 生成资源 <<<")
            try {
                val candidatesBase64 = aiService.generateImages(
                    apiKey = apiKey,
                    blockType = block.type.name,
                    userPrompt = submitPrompt,
                    maxRetries = 3,
                    targetWidth = block.bounds.width,
                    targetHeight = block.bounds.height,
                    isPng = isTransparent,
                    onLog = { addGenLog(it) }
                )

                val candidatePaths = withContext(Dispatchers.Default) {
                    candidatesBase64.mapIndexed { index, base64 ->
                        val pure = if (base64.contains(",")) base64.substringAfter(",") else base64

                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = Base64.decode(pure)
                        val timestamp = getCurrentTimeMillis()
                        val originalUri =
                            templateRepo.saveBlockResource(projectName, block.id, "gen_${index}_$timestamp", bytes, isPng = false)

                        if (isTransparent) {
                            var processedBytes: ByteArray? = null
                            
                            // 优先尝试云端抠图 (如果用户勾选了且有 API Key)
                            if (prioritizeCloud && apiKey.isNotBlank()) {
                                try {
                                    processedBytes = aiService.removeBackgroundCloud(bytes, apiKey) { addGenLog(it) }
                                } catch (e: Exception) {
                                    addGenLog("⚠️ 云端抠图失败，尝试回退到本地引擎: ${e.message}")
                                }
                            }
                            
                            // 如果云端未开启，或者云端处理失败，回退到本地 Python 脚本抠图
                            if (processedBytes == null) {
                                try {
                                    processedBytes = aiService.removeBackgroundLocal(bytes) { addGenLog(it) }
                                } catch (e: Exception) {
                                    addGenLog("❌ 本地抠图也失败了: ${e.message}")
                                }
                            }

                            if (processedBytes != null) {
                                return@mapIndexed templateRepo.saveBlockResource(
                                    projectName,
                                    block.id,
                                    "processed_${index}_$timestamp",
                                    processedBytes,
                                    isPng = true
                                )
                            }
                        }
                        originalUri
                    }
                }
                _state.update { it.copy(generatedCandidates = candidatePaths) }
            } catch (e: Exception) {
                addGenLog(">>> 生成失败: ${e.message} <<<")
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    // --- 资产操作逻辑 ---

    fun onImageSelected(imageUri: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
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
                generatedCandidates = emptyList()
            )
        }
    }

    /** 
     * 执行真正的图片裁剪，并将裁剪后的新图片保存绑定给当前的 UIBlock。
     * @param originalUri 原始底图 URI
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
                // 因为 cropImage 函数预期的是基于逻辑宽高的绝对坐标
                val absLeft = cropRect.left * originalW
                val absTop = cropRect.top * originalH
                val absRight = cropRect.right * originalW
                val absBottom = cropRect.bottom * originalH
                val bounds = SerialRect(absLeft, absTop, absRight, absBottom)
                AppLogger.d("TemplateAssetGenViewModel", "换算后的绝对像素裁剪框: $bounds")

                // 执行裁剪 (传入 originalW, originalH 作为 logicalWidth/Height)
                // 核心改进：强制输出尺寸与模块逻辑尺寸 (bounds.width/height) 对齐，解决像素不一致问题
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
                    val croppedUri = templateRepo.saveBlockResource(
                        projectName,
                        blockId,
                        "cropped_${getCurrentTimeMillis()}",
                        croppedBytes,
                        isPng = true
                    )
                    
                    AppLogger.i("TemplateAssetGenViewModel", "✅ 新文件已落盘: $croppedUri，正在绑定到模块 $blockId")
                    // 切换回主线程更新 UI 状态
                    withContext(Dispatchers.Main) {
                        onImageSelected(croppedUri)
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

    fun deleteImages(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { deleteLocalFile(it) }
            _state.update { currentState -> currentState.copy(generatedCandidates = currentState.generatedCandidates.filter { it !in uris }) }
        }
    }

    fun batchRemoveBackgroundLocal(uris: List<String>, onSuccess: (List<String>) -> Unit = {}) {
        val block = _state.value.selectedBlock ?: return
        val projectName = _state.value.projectName

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.update { it.copy(isLocalProcessing = true) }
            
            try {
                val newCandidatePaths = mutableListOf<String>()
                for ((index, uri) in uris.withIndex()) {
                    val bytes = readLocalFileBytes(uri)
                    if (bytes != null) {
                        val processedBytes = aiService.removeBackgroundLocal(bytes)
                        if (processedBytes != null) {
                            val timestamp = getCurrentTimeMillis()
                            val newUri = templateRepo.saveBlockResource(
                                projectName,
                                block.id,
                                "batch_processed_${index}_$timestamp",
                                processedBytes,
                                isPng = true
                            )
                            newCandidatePaths.add(newUri)
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

    fun appendCandidates(newPaths: List<String>) {
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

    suspend fun loadHistoricalImages(blockId: String): List<String> {
        val rootDir = templateRepo.getDataDir()
        val dir = "$rootDir/templates/${_state.value.projectName.replace(" ", "_")}/assets/$blockId"
        return listFilesInLocalDirectory(dir).filter { it.endsWith(".png") || it.endsWith(".jpg") }
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
    }

    // --- UI 辅助 ---

    fun setGenerateTransparent(enabled: Boolean) {
        _state.update { it.copy(isGenerateTransparent = enabled) }
    }

    fun setPrioritizeCloudRemoval(enabled: Boolean) {
        _state.update { it.copy(isPrioritizeCloudRemoval = enabled) }
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

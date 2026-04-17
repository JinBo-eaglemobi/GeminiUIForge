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
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateAssetGenState(
        project = initialProject,
        projectName = initialProjectName,
        selectedPageId = initialProject.pages.firstOrNull()?.id
    ))
    val state: StateFlow<TemplateAssetGenState> = _state.asStateFlow()

    private var generationJob: kotlinx.coroutines.Job? = null

    // --- 页面与选择逻辑 ---

    fun onPageSelected(pageId: String) = _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }
    fun onBlockClicked(blockId: String?) = _state.update { it.copy(selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId) }
    fun onBlockDoubleClicked(blockId: String) {
        val block = findBlockById(_state.value.project.pages.flatMap { it.blocks }, blockId)
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }
    fun exitGroupEditMode() { _state.update { it.copy(editingGroupId = null) } }

    // --- 资源生成核心逻辑 ---

    fun onRequestGeneration(apiKey: String, currentLang: PromptLanguage) {
        val block = _state.value.selectedBlock ?: return
        val submitPrompt = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
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
                    isPng = isTransparent
                )
                
                val candidatePaths = withContext(Dispatchers.Default) {
                    candidatesBase64.mapIndexed { index, base64 ->
                        val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = Base64.decode(pure)
                        val timestamp = getCurrentTimeMillis()
                        val originalUri = templateRepo.saveBlockResource(projectName, block.id, "gen_${index}_$timestamp", bytes)
                        
                        if (isTransparent) {
                            var processedBytes: ByteArray? = null
                            if (prioritizeCloud) {
                                processedBytes = aiService.removeBackgroundCloud(bytes, apiKey)
                            }
                            if (processedBytes != null) {
                                return@mapIndexed templateRepo.saveBlockResource(projectName, block.id, "cloud_${index}_$timestamp", processedBytes)
                            }
                        }
                        originalUri
                    }
                }
                _state.update { it.copy(generatedCandidates = candidatePaths) }
            } catch (e: Exception) { addGenLog(">>> 生成失败: ${e.message} <<<") } 
            finally { _state.update { it.copy(isGenerating = false) } }
        }
    }

    // --- 资产操作逻辑 ---

    fun onImageSelected(imageUri: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = imageUri) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), generatedCandidates = emptyList())
        }
        viewModelScope.launch { templateRepo.saveTemplate(_state.value.projectName, _state.value.project) }
    }

    fun deleteImages(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { deleteLocalFile(it) }
            _state.update { currentState -> currentState.copy(generatedCandidates = currentState.generatedCandidates.filter { it !in uris }) }
        }
    }

    fun clearCandidates() = _state.update { it.copy(generatedCandidates = emptyList()) }
    fun clearSelectedImage(blockId: String) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = null) }) else page
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
            val resultBlocks = if (targetId == null) newBlocks + draggedBlock else if (dropPosition == DropPosition.INSIDE) updateBlockInList(newBlocks, targetId) { it.copy(children = it.children + draggedBlock) } else insertBlockSibling(newBlocks, targetId, draggedBlock, dropPosition)
            currentState.copy(project = currentState.project.copy(pages = currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }))
        }
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { b -> b.copy(bounds = b.bounds.copy(left = b.bounds.left + dx, top = b.bounds.top + dy, right = b.bounds.right + dx, bottom = b.bounds.bottom + dy)) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun renameBlock(oldId: String, newId: String) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateBlockInList(page.blocks, oldId) { it.copy(id = newId) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun addCustomBlock(id: String, type: UIBlockType, w: Float, h: Float) {
        val newBlock = UIBlock(id.ifEmpty { "block_${getCurrentTimeMillis()}" }, type, SerialRect(0f, 0f, w, h))
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = page.blocks + newBlock) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(isVisible = isVisible) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        _state.update { currentState ->
            fun updateVis(list: List<UIBlock>): List<UIBlock> = list.map { it.copy(isVisible = isVisible, children = updateVis(it.children)) }
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(blocks = updateVis(page.blocks)) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    // --- UI 辅助 ---

    fun setGenerateTransparent(enabled: Boolean) { _state.update { it.copy(isGenerateTransparent = enabled) } }
    fun setPrioritizeCloudRemoval(enabled: Boolean) { _state.update { it.copy(isPrioritizeCloudRemoval = enabled) } }
    fun toggleVisualMode() { _state.update { it.copy(isVisualMode = !it.isVisualMode) } }
    fun cancelGeneration() { generationJob?.cancel(); _state.update { it.copy(isGenerating = false) } }
    fun toggleGenerationLogVisibility() { _state.update { it.copy(isGenerationLogVisible = !it.isGenerationLogVisible) } }
    fun closeAITaskDialog() { _state.update { it.copy(showAITaskDialog = false) } }
    private fun addGenLog(msg: String) { _state.update { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) } }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) { if (block.id == id) return block; findBlockById(block.children, id)?.let { return it } }
        return null
    }

    private fun updateBlockInList(blocks: List<UIBlock>, blockId: String, transform: (UIBlock) -> UIBlock): List<UIBlock> {
        return blocks.map { block ->
            if (block.id == blockId) transform(block)
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children) block.copy(children = newChildren) else block
            }
        }
    }

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> = blocks.filterNot { it.id == idToRemove }.map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }

    private fun insertBlockSibling(blocks: List<UIBlock>, targetId: String, blockToInsert: UIBlock, position: DropPosition): List<UIBlock> {
        val index = blocks.indexOfFirst { it.id == targetId }
        if (index != -1) {
            val result = blocks.toMutableList()
            if (position == DropPosition.BEFORE) result.add(index, blockToInsert) else result.add(index + 1, blockToInsert)
            return result
        }
        return blocks.map { b ->
            val newChildren = insertBlockSibling(b.children, targetId, blockToInsert, position)
            if (newChildren !== b.children) b.copy(children = newChildren) else b
        }
    }
}

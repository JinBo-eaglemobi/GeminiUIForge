package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.utils.*
import kotlin.collections.ArrayDeque

/**
 * 布局编辑器的专属 ViewModel
 * 生命周期随 TemplateEditorScreen 绑定，退出即销毁，释放撤销栈等资源
 */
class TemplateEditorViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateEditorState(
        project = initialProject,
        projectName = initialProjectName,
        selectedPageId = initialProject.pages.firstOrNull()?.id
    ))
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    private var currentGenJob: kotlinx.coroutines.Job? = null
    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

    init {
        viewModelScope.launch {
            val updateInstruction = aiService.promptManager.getPrompt("refine_instruction_update")
            val newInstruction = aiService.promptManager.getPrompt("refine_instruction_new")
            _state.update { it.copy(
                defaultRefineInstructionUpdate = updateInstruction, 
                defaultRefineInstructionNew = newInstruction 
            ) }
        }
    }

    // --- 撤销重做逻辑 ---

    fun saveSnapshot() {
        val currentProject = _state.value.project
        if (undoStack.lastOrNull() != currentProject) {
            undoStack.addLast(currentProject)
            if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _state.value.project
            redoStack.addLast(currentState)
            val previousState = undoStack.removeLast()
            _state.update { it.copy(project = previousState) }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _state.value.project
            undoStack.addLast(currentState)
            val nextState = redoStack.removeLast()
            _state.update { it.copy(project = nextState) }
        }
    }

    // --- 页面与块操作逻辑 ---

    fun onPageSelected(pageId: String) = _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, editingGroupId = null) }
    fun onBlockClicked(blockId: String?) = _state.update { it.copy(selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId) }

    fun onBlockDoubleClicked(blockId: String) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) }
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    fun exitGroupEditMode() { _state.update { it.copy(editingGroupId = null) } }

    fun renameBlock(oldId: String, newId: String) {
        if (oldId == newId || newId.isBlank()) return
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, oldId) { block -> block.copy(id = newId) })
                } else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = if (currentState.selectedBlockId == oldId) newId else currentState.selectedBlockId,
                editingGroupId = if (currentState.editingGroupId == oldId) newId else currentState.editingGroupId
            )
        }
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, right: Float, bottom: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(bounds = SerialRect(left, top, right, bottom))
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun addBlock(type: UIBlockType) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${getCurrentTimeMillis()}"
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            val width = 400f; val height = 300f
            var left = (currentPage.width - width) / 2f
            var top = (currentPage.height - height) / 2f
            if (editingGroupId != null) {
                findBlockById(currentPage.blocks, editingGroupId)?.let { group ->
                    left = (group.bounds.width - width) / 2f
                    top = (group.bounds.height - height) / 2f
                }
            }
            val newBlock = UIBlock(newBlockId, type, SerialRect(left, top, left + width, top + height))
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(blocks = updateBlockInList(page.blocks, editingGroupId) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    fun deleteBlock(blockId: String) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = removeBlockRecursive(page.blocks, blockId)) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = if (currentState.selectedBlockId == blockId) null else currentState.selectedBlockId)
        }
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(bounds = block.bounds.copy(
                            left = block.bounds.left + dx,
                            top = block.bounds.top + dy,
                            right = block.bounds.right + dx,
                            bottom = block.bounds.bottom + dy
                        ))
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        if (draggedBlockId == targetId) return
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId) ?: return@update currentState
            if (targetId != null && isDescendantOfBlock(targetId, draggedBlock)) return@update currentState
            val draggedAbsBounds = getAbsoluteBounds(currentPage.blocks, draggedBlockId) ?: draggedBlock.bounds
            val newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)
            val actualParentId = if (targetId == null) null else if (dropPosition == DropPosition.INSIDE) targetId else getParentIdOf(currentPage.blocks, targetId)
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null
            val newRelativeBounds = if (targetAbsBounds != null) SerialRect(left = draggedAbsBounds.left - targetAbsBounds.left, top = draggedAbsBounds.top - targetAbsBounds.top, right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width, bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height) else draggedAbsBounds
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)
            val resultBlocks = if (targetId == null) newBlocks + updatedDraggedBlock else if (dropPosition == DropPosition.INSIDE) updateBlockInList(newBlocks, targetId) { it.copy(children = it.children + updatedDraggedBlock) } else insertBlockSibling(newBlocks, targetId, updatedDraggedBlock, dropPosition)
            saveSnapshot()
            val updatedPages = currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }
            currentState.copy(project = currentState.project.copy(pages = updatedPages, createdAt = getCurrentTimeMillis()))
        }
    }

    // --- AI 重塑逻辑 ---

    fun onRefineArea(blockId: String, bounds: SerialRect, userInstruction: String, apiKey: String, onComplete: (Boolean) -> Unit) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: return
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            try {
                _state.update { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList()) }
                val logger = { msg: String -> addGenLog(msg) }
                val croppedBytes = cropImage(imageSource = originalImage, bounds = bounds, logicalWidth = currentPage.width, logicalHeight = currentPage.height) ?: throw Exception("裁剪失败")
                val originalBytes = readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()
                var originalFileUri = cloudAssetManager.assets.value.find { it.displayName?.contains(fingerprint) == true && it.state == "ACTIVE" }?.uri ?: ""
                if (originalFileUri.isBlank()) {
                    originalFileUri = cloudAssetManager.getOrUploadFile(originalImage.substringAfterLast("/"), originalBytes, getMimeType(originalImage)) { _, status -> logger("[$status]") } ?: ""
                }
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(originalImageUri = originalFileUri, croppedBytes = croppedBytes, currentJson = currentJson, userInstruction = userInstruction, apiKey = apiKey, onLog = logger)
                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) { addGenLog("❌ 错误: ${e.message}"); onComplete(false) }
            } finally { _state.update { it.copy(isGenerating = false) } }
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, currentLang: PromptLanguage) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) } ?: return
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            try {
                val systemInstruction = if (currentLang == PromptLanguage.EN) aiService.promptManager.getPrompt("optimize_instruction_en") else aiService.promptManager.getPrompt("optimize_instruction_zh")
                val optimized = aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, 3)
                onUserPromptChanged(blockId, optimized, currentLang)
            } catch (e: Exception) { addGenLog(">>> 优化失败: ${e.message} <<<") } 
            finally { _state.update { it.copy(isGenerating = false) } }
        }
    }

    fun onUserPromptChanged(blockId: String, newPrompt: String, lang: PromptLanguage) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        if (lang == PromptLanguage.EN) block.copy(userPromptEn = newPrompt) else block.copy(userPromptZh = newPrompt)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    // --- 内部辅助方法 ---

    private fun addGenLog(msg: String) { _state.update { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) } }
    fun closeAITaskDialog() { _state.update { it.copy(showAITaskDialog = false, generationLogs = emptyList()) } }
    fun cancelAITask() { currentGenJob?.cancel(); currentGenJob = null; _state.update { it.copy(isGenerating = false) } }

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

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> {
        return blocks.filterNot { it.id == idToRemove }.map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }
    }

    private fun getAbsoluteBounds(blocks: List<UIBlock>, targetId: String, currentOffsetX: Float = 0f, currentOffsetY: Float = 0f): SerialRect? {
        for (block in blocks) {
            val absL = currentOffsetX + block.bounds.left; val absT = currentOffsetY + block.bounds.top
            if (block.id == targetId) return SerialRect(absL, absT, absL + block.bounds.width, absT + block.bounds.height)
            getAbsoluteBounds(block.children, targetId, absL, absT)?.let { return it }
        }
        return null
    }

    private fun isDescendantOfBlock(targetId: String, currentBlock: UIBlock): Boolean = currentBlock.id == targetId || currentBlock.children.any { isDescendantOfBlock(targetId, it) }

    private fun getParentIdOf(blocks: List<UIBlock>, targetId: String, currentParentId: String? = null): String? {
        for (block in blocks) {
            if (block.id == targetId) return currentParentId
            getParentIdOf(block.children, targetId, block.id)?.let { return it }
        }
        return null
    }

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

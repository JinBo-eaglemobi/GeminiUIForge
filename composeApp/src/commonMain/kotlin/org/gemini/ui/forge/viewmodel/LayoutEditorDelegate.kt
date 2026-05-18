package org.gemini.ui.forge.viewmodel

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.api.ChatMessage
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.AITaskStatus
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.utils.*

/**
 * 布局编辑逻辑委托。
 * 负责处理 UI 块的增删改、层级移动、撤销重做、剪切板以及 AI 布局重构。
 */
class LayoutEditorDelegate(
    private val scope: CoroutineScope,
    private val aiService: AIGenerationService,
    private val templateRepo: org.gemini.ui.forge.data.repository.TemplateRepository,
    private val cloudAssetManager: org.gemini.ui.forge.manager.CloudAssetManager,
    private val getState: () -> ProjectWorkspaceState,
    private val updateState: ((ProjectWorkspaceState) -> ProjectWorkspaceState) -> Unit,
    private val markDirty: () -> Unit,
    private val onRequestRename: () -> Unit
) {

    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()
    private var clipboardBlock: UIBlock? = null
    private var currentGenJob: Job? = null

    // --- 撤销/重做 ---

    fun saveSnapshot() {
        val currentProject = getState().project
        if (undoStack.lastOrNull() != currentProject) {
            undoStack.addLast(currentProject)
            if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = getState().project
            redoStack.addLast(currentState)
            val previousState = undoStack.removeLast()
            updateState { it.copy(project = previousState) }
            markDirty()
            Toast.show("已撤销操作", ToastType.INFO)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = getState().project
            undoStack.addLast(currentState)
            val nextState = redoStack.removeLast()
            updateState { it.copy(project = nextState) }
            markDirty()
            Toast.show("已重做操作", ToastType.INFO)
        }
    }

    // --- 块操作 ---

    fun renameBlock(oldId: String, newId: String) {
        if (oldId == newId || newId.isBlank()) return
        saveSnapshot()
        val pageId = getState().selectedPageId ?: return
        updateState { currentState ->
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
        markDirty()
    }

    fun addBlock(type: UIBlockType) {
        saveSnapshot()
        val pageId = getState().selectedPageId ?: return
        val newBlockId = "block_${getCurrentTimeMillis()}"
        updateState { currentState ->
            val currentPage = currentState.currentPage ?: return@updateState currentState
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
        markDirty()
    }

    fun deleteBlock(blockId: String) {
        saveSnapshot()
        val pageId = getState().selectedPageId ?: return
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = removeBlockRecursive(page.blocks, blockId)) else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = if (currentState.selectedBlockId == blockId) null else currentState.selectedBlockId,
                editingGroupId = if (currentState.editingGroupId == blockId) null else currentState.editingGroupId
            )
        }
        markDirty()
        Toast.show("已删除模块 $blockId", ToastType.INFO)
    }

    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        val pageId = getState().selectedPageId ?: return
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(isVisible = isVisible) })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        val pageId = getState().selectedPageId ?: return
        
        fun updateAll(blocks: List<UIBlock>): List<UIBlock> = blocks.map { 
            it.copy(isVisible = isVisible, children = updateAll(it.children))
        }

        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateAll(page.blocks)) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        if (draggedBlockId == targetId) return
        val pageId = getState().selectedPageId ?: return
        updateState { currentState ->
            val currentPage = currentState.currentPage ?: return@updateState currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId) ?: return@updateState currentState
            if (targetId != null && isDescendantOfBlock(targetId, draggedBlock)) return@updateState currentState
            
            val draggedAbsBounds = getAbsoluteBounds(currentPage.blocks, draggedBlockId) ?: draggedBlock.bounds
            val newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)
            
            val actualParentId = if (targetId == null) null else if (dropPosition == DropPosition.INSIDE) targetId else getParentIdOf(currentPage.blocks, targetId)
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null
            
            val newRelativeBounds = if (targetAbsBounds != null) SerialRect(
                left = draggedAbsBounds.left - targetAbsBounds.left,
                top = draggedAbsBounds.top - targetAbsBounds.top,
                right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width,
                bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height
            ) else draggedAbsBounds
            
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)
            val resultBlocks = if (targetId == null) newBlocks + updatedDraggedBlock 
                else if (dropPosition == DropPosition.INSIDE) updateBlockInList(newBlocks, targetId) { it.copy(children = it.children + updatedDraggedBlock) } 
                else insertBlockSibling(newBlocks, targetId, updatedDraggedBlock, dropPosition)
            
            saveSnapshot()
            val updatedPages = currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }
            currentState.copy(project = currentState.project.copy(pages = updatedPages, createdAt = getCurrentTimeMillis()))
        }
        markDirty()
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = getState().selectedPageId ?: return
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(bounds = block.bounds.copy(
                            left = block.bounds.left + dx, top = block.bounds.top + dy,
                            right = block.bounds.right + dx, bottom = block.bounds.bottom + dy
                        ))
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    // --- 剪切板 ---

    fun copy() {
        val selectedId = getState().selectedBlockId ?: return
        val block = getState().currentPage?.blocks?.let { findBlockById(it, selectedId) }
        if (block != null) {
            clipboardBlock = block
            Toast.show("已复制模块 ${block.id}", ToastType.SUCCESS)
        }
    }

    fun cut() {
        val selectedId = getState().selectedBlockId ?: return
        val block = getState().currentPage?.blocks?.let { findBlockById(it, selectedId) }
        if (block != null) {
            clipboardBlock = block
            deleteBlock(selectedId)
            Toast.show("已剪切模块 ${block.id}", ToastType.SUCCESS)
        }
    }

    fun paste() {
        val source = clipboardBlock ?: return
        saveSnapshot()
        val pageId = getState().selectedPageId ?: return
        
        fun deepCopyAndRename(block: UIBlock): UIBlock {
            val newId = "block_copy_${getCurrentTimeMillis()}_${(0..999).random()}"
            return block.copy(id = newId, children = block.children.map { deepCopyAndRename(it) })
        }

        val newBlock = deepCopyAndRename(source).let {
            it.copy(bounds = it.bounds.copy(
                left = it.bounds.left + 20f, top = it.bounds.top + 20f,
                right = it.bounds.right + 20f, bottom = it.bounds.bottom + 20f
            ))
        }
        
        updateState { currentState ->
            val editingGroupId = currentState.editingGroupId
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(blocks = updateBlockInList(page.blocks, editingGroupId) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlock.id)
        }
        markDirty()
        Toast.show("已粘贴模块 ${newBlock.id}", ToastType.SUCCESS)
    }

    // --- AI 辅助重构 ---

    fun onRefineArea(
        blockId: String?,
        bounds: SerialRect,
        userInstruction: String,
        apiKey: String,
        useChatContext: Boolean = false,
        onComplete: (Boolean) -> Unit
    ) {
        val currentState = getState()
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: return
        currentGenJob?.cancel()
        
        val task = aiService.createTask<ProjectState>("区域重构", scope)
        currentGenJob = scope.launch {
            launch { task.status.collect { status -> updateState { it.copy(isGenerating = status == AITaskStatus.RUNNING) } } }
            launch { task.currentStatus.collect { status -> updateState { it.copy(currentTaskStatus = status) } } }
            
            updateState { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList(), currentTaskStatus = "准备中...") }
            
            task.execute {
                try {
                    val logger = { msg: String -> addLog(msg); log(msg) }
                    val croppedBytes = cropImage(originalImage.getAbsolutePath(), bounds, currentPage.width, currentPage.height) ?: throw Exception("裁剪失败")
                    val originalBytes = originalImage.readBytes() ?: throw Exception("无法读取原图")
                    val fingerprint = originalBytes.calculateMd5()
                    
                    var originalFileUri = cloudAssetManager.assets.value.find { it.displayName?.contains(fingerprint) == true && it.state == "ACTIVE" }?.uri ?: ""
                    if (originalFileUri.isBlank()) {
                        originalFileUri = cloudAssetManager.getOrUploadFile(originalImage.relativePath.substringAfterLast("/"), originalBytes, getMimeType(originalImage.getAbsolutePath())) { _, _ -> } ?: ""
                    }
                    
                    val historyKey = blockId ?: "GLOBAL_REFINE"
                    val history = if (useChatContext) currentState.chatHistories[historyKey] ?: emptyList() else emptyList()
                    
                    val updatedPages = aiService.refineAreaForTemplate(
                        originalImageUri = originalFileUri,
                        croppedBytes = croppedBytes,
                        currentJson = Json.encodeToString(currentState.project.pages),
                        userInstruction = userInstruction,
                        apiKey = apiKey,
                        history = history,
                        onLog = logger
                    )
                    
                    updateState { s ->
                        val newHistory = history + ChatMessage("user", userInstruction) + ChatMessage("model", "已重塑 UI 结构。")
                        s.copy(
                            project = s.project.copy(pages = updatedPages),
                            chatHistories = s.chatHistories + (historyKey to newHistory)
                        )
                    }
                    markDirty()
                    onComplete(true)
                    getState().project
                } catch (e: Exception) {
                    onComplete(false); throw e
                }
            }
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, lang: PromptLanguage, useChatContext: Boolean = false) {
        val block = findBlockById(getState().project.pages.flatMap { it.blocks }, blockId) ?: return
        val textToOptimize = if (lang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return
        
        currentGenJob?.cancel()
        val task = aiService.createTask<String>("提示词优化", scope)
        currentGenJob = scope.launch {
            launch { task.status.collect { status -> updateState { it.copy(isGenerating = status == AITaskStatus.RUNNING) } } }
            updateState { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            
            task.execute {
                val logger = { msg: String -> addLog(msg); log(msg) }
                val systemInstruction = if (lang == PromptLanguage.EN) aiService.promptManager.getPrompt("optimize_instruction_en") else aiService.promptManager.getPrompt("optimize_instruction_zh")
                val historyKey = "PROMPT_$blockId"
                val history = if (useChatContext) getState().chatHistories[historyKey] ?: emptyList() else emptyList()
                
                val optimized = aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, 3, history = history)
                
                updateState { currentState ->
                    val updatedPages = currentState.project.pages.map { page ->
                        page.copy(blocks = updateBlockInList(page.blocks, blockId) { b ->
                            if (lang == PromptLanguage.EN) b.copy(userPromptEn = optimized) else b.copy(userPromptZh = optimized)
                        })
                    }
                    val newHistory = history + ChatMessage("user", textToOptimize) + ChatMessage("model", optimized)
                    currentState.copy(project = currentState.project.copy(pages = updatedPages), chatHistories = currentState.chatHistories + (historyKey to newHistory))
                }
                markDirty()
                optimized
            }
        }
    }

    // --- 快捷键处理 ---

    fun handleShortcutAction(action: ShortcutAction) {
// ... existing handleShortcutAction ...
    }

    fun onSetReferenceArea(
        blockId: String,
        bounds: SerialRect
    ) {
        val currentState = getState()
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: return

        scope.launch {
            try {
                AppLogger.d("LayoutEditor", "✂️ 正在提取并保存模块 $blockId 的局部参考图...")
                val croppedBytes = cropImage(
                    imageSource = originalImage.getAbsolutePath(),
                    bounds = bounds,
                    logicalWidth = currentPage.width,
                    logicalHeight = currentPage.height
                ) ?: throw Exception("裁剪局部参考图失败")

                val savedFile = templateRepo.saveBlockResource(
                    templateName = currentState.projectName,
                    blockId = blockId,
                    fileNamePrefix = "ref",
                    bytes = croppedBytes,
                    isPng = false
                )

                updateState { state ->
                    val updatedPages = state.project.pages.map { page ->
                        if (page.id == currentPage.id) {
                            page.copy(
                                blocks = updateBlockInList(page.blocks, blockId) { block ->
                                    block.copy(referenceImage = savedFile)
                                }
                            )
                        } else page
                    }
                    state.copy(project = state.project.copy(pages = updatedPages))
                }
                markDirty()
                AppLogger.d("LayoutEditor", "✅ 局部参考图保存成功: ${savedFile.relativePath}")
                Toast.show("局部参考图设置成功", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("LayoutEditor", "❌ 设置局部参考图失败", e)
                Toast.show("设置局部参考图失败", ToastType.ERROR)
            }
        }
    }

    // --- 内部辅助 ---

    private fun addLog(msg: String) {
        updateState { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) }
    }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
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
        return blocks.filterNot { it.id == idToRemove }
            .map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }
    }

    private fun getAbsoluteBounds(blocks: List<UIBlock>, targetId: String, ox: Float = 0f, oy: Float = 0f): SerialRect? {
        for (block in blocks) {
            val absL = ox + block.bounds.left; val absT = oy + block.bounds.top
            if (block.id == targetId) return SerialRect(absL, absT, absL + block.bounds.width, absT + block.bounds.height)
            val found = getAbsoluteBounds(block.children, targetId, absL, absT)
            if (found != null) return found
        }
        return null
    }

    private fun isDescendantOfBlock(targetId: String, currentBlock: UIBlock): Boolean =
        currentBlock.id == targetId || currentBlock.children.any { isDescendantOfBlock(targetId, it) }

    private fun getParentIdOf(blocks: List<UIBlock>, targetId: String, currentParentId: String? = null): String? {
        for (block in blocks) {
            if (block.id == targetId) return currentParentId
            val found = getParentIdOf(block.children, targetId, block.id)
            if (found != null) return found
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

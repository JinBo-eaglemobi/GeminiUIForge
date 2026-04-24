package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.utils.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.collections.ArrayDeque

/**
 * 布局编辑器的专属 ViewModel。
 * 负责管理当前正在编辑的模板项目状态、处理 UI 块的增删改查、撤销重做逻辑，以及与 AI 服务的交互。
 */
class TemplateEditorViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService,
    private val onDirtyChanged: (Boolean) -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(
        TemplateEditorState(
            project = initialProject,
            projectName = initialProjectName,
            selectedPageId = initialProject.pages.firstOrNull()?.id,
            currentLang = initialLang
        )
    )

    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    private val _requestRenameEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRenameEvent: SharedFlow<Unit> = _requestRenameEvent.asSharedFlow()

    private fun markDirty() {
        onDirtyChanged(true)
    }

    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    private var currentGenJob: kotlinx.coroutines.Job? = null
    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

    init {
        viewModelScope.launch {
            val updateInstruction = aiService.promptManager.getPrompt("refine_instruction_update")
            val newInstruction = aiService.promptManager.getPrompt("refine_instruction_new")
            _state.update {
                it.copy(
                    defaultRefineInstructionUpdate = updateInstruction,
                    defaultRefineInstructionNew = newInstruction
                )
            }
        }
    }

    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        _state.update {
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null
            )
        }
        undoStack.clear()
        redoStack.clear()
    }

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
            markDirty()
            AppLogger.d("TemplateEditorViewModel", "↩️ 执行撤销成功，剩余可撤销步数: ${undoStack.size}")
            Toast.show("已撤销操作", org.gemini.ui.forge.ui.component.ToastType.INFO)
        } else {
            AppLogger.d("TemplateEditorViewModel", "↩️ 无法撤销：撤销栈为空")
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _state.value.project
            undoStack.addLast(currentState)
            val nextState = redoStack.removeLast()
            _state.update { it.copy(project = nextState) }
            markDirty()
            AppLogger.d("TemplateEditorViewModel", "↪️ 执行重做成功，剩余可重做步数: ${redoStack.size}")
            Toast.show("已重做操作", org.gemini.ui.forge.ui.component.ToastType.INFO)
        } else {
            AppLogger.d("TemplateEditorViewModel", "↪️ 无法重做：重做栈为空")
        }
    }

    fun onPageSelected(pageId: String) =
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, editingGroupId = null) }

    fun onBlockClicked(blockId: String?) {
        _state.update { currentState ->
            val newSelectedId =
                if (blockId == null) null else if (currentState.selectedBlockId == blockId) null else blockId
            currentState.copy(selectedBlockId = newSelectedId)
        }
    }

    fun onBlockDoubleClicked(blockId: String) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) }
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    fun exitGroupEditMode() {
        _state.update {
            it.copy(editingGroupId = null)
        }
    }

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
        markDirty()
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
        markDirty()
    }

    fun updateBlockType(blockId: String, newType: UIBlockType) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(type = newType)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun addBlock(type: UIBlockType) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${getCurrentTimeMillis()}"
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            val width = 400f;
            val height = 300f
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
                        page.copy(
                            blocks = updateBlockInList(
                                page.blocks,
                                editingGroupId
                            ) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
        markDirty()
    }

    fun addCustomBlock(id: String, type: UIBlockType, w: Float, h: Float) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val finalId = if (id.isBlank()) "block_${getCurrentTimeMillis()}" else id
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            var left = (currentPage.width - w) / 2f
            var top = (currentPage.height - h) / 2f
            if (editingGroupId != null) {
                findBlockById(currentPage.blocks, editingGroupId)?.let { group ->
                    left = (group.bounds.width - w) / 2f
                    top = (group.bounds.height - h) / 2f
                }
            }
            val newBlock = UIBlock(finalId, type, SerialRect(left, top, left + w, top + h))
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(
                            blocks = updateBlockInList(
                                page.blocks,
                                editingGroupId
                            ) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = finalId)
        }
        markDirty()
    }

    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(
                        blocks = updateBlockInList(
                            page.blocks,
                            blockId
                        ) { block -> block.copy(isVisible = isVisible) })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            fun updateVis(list: List<UIBlock>): List<UIBlock> =
                list.map { it.copy(isVisible = isVisible, children = updateVis(it.children)) }

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateVis(page.blocks)) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun deleteBlock(blockId: String) {
        AppLogger.d("TemplateEditorViewModel", "🗑️ 准备删除模块: $blockId")
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
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
        AppLogger.d("TemplateEditorViewModel", "🗑️ 模块 $blockId 及其子节点已成功删除")
        Toast.show("已删除模块 $blockId", org.gemini.ui.forge.ui.component.ToastType.INFO)
    }

    private var clipboardBlock: UIBlock? = null

    fun copy() {
        val selectedId = _state.value.selectedBlockId ?: return
        val block = _state.value.currentPage?.blocks?.let { findBlockById(it, selectedId) }
        if (block != null) {
            clipboardBlock = block
            AppLogger.d("TemplateEditorViewModel", "📋 已复制模块: ${block.id}")
            Toast.show("已复制模块 ${block.id}", org.gemini.ui.forge.ui.component.ToastType.SUCCESS)
        } else {
            AppLogger.e("TemplateEditorViewModel", "⚠️ 复制失败：找不到指定的模块 $selectedId")
        }
    }

    fun cut() {
        val selectedId = _state.value.selectedBlockId ?: return
        val block = _state.value.currentPage?.blocks?.let { findBlockById(it, selectedId) }
        if (block != null) {
            clipboardBlock = block
            deleteBlock(selectedId)
            AppLogger.d("TemplateEditorViewModel", "✂️ 已剪切模块: ${block.id}")
            Toast.show("已剪切模块 ${block.id}", org.gemini.ui.forge.ui.component.ToastType.SUCCESS)
        } else {
            AppLogger.e("TemplateEditorViewModel", "⚠️ 剪切失败：找不到指定的模块 $selectedId")
        }
    }

    fun paste() {
        val source = clipboardBlock ?: run {
            AppLogger.e("TemplateEditorViewModel", "⚠️ 粘贴失败：剪贴板为空")
            Toast.show("粘贴失败：剪贴板为空", org.gemini.ui.forge.ui.component.ToastType.ERROR)
            return
        }
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        fun deepCopyAndRename(block: UIBlock): UIBlock {
            val newId = "block_copy_${getCurrentTimeMillis()}_${(0..999).random()}"
            return block.copy(id = newId, children = block.children.map { deepCopyAndRename(it) })
        }

        val newBlock = deepCopyAndRename(source).let {
            val newBounds = it.bounds.copy(
                left = it.bounds.left + 20f,
                top = it.bounds.top + 20f,
                right = it.bounds.right + 20f,
                bottom = it.bounds.bottom + 20f
            )
            it.copy(bounds = newBounds)
        }
        _state.update { currentState ->
            val editingGroupId = currentState.editingGroupId
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(
                            blocks = updateBlockInList(
                                page.blocks,
                                editingGroupId
                            ) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlock.id)
        }
        markDirty()
        AppLogger.d("TemplateEditorViewModel", "📋 已粘贴模块: ${newBlock.id}")
        Toast.show("已粘贴模块 ${newBlock.id}", org.gemini.ui.forge.ui.component.ToastType.SUCCESS)
    }

    fun handleShortcutAction(action: ShortcutAction) {
        AppLogger.d("TemplateEditorViewModel", "🎯 响应编辑快捷键动作: ${action.label}")
        when (action) {
            ShortcutAction.UNDO -> undo()
            ShortcutAction.REDO -> redo()
            ShortcutAction.DELETE -> _state.value.selectedBlockId?.let { deleteBlock(it) }
                ?: AppLogger.d("TemplateEditorViewModel", "没有选中的模块可以删除")

            ShortcutAction.COPY -> copy()
            ShortcutAction.PASTE -> paste()
            ShortcutAction.CUT -> cut()
            ShortcutAction.SAVE -> {}
            ShortcutAction.RENAME -> {
                viewModelScope.launch {
                    _requestRenameEvent.emit(Unit)
                }
            }
        }
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(
                            bounds = block.bounds.copy(
                                left = block.bounds.left + dx,
                                top = block.bounds.top + dy,
                                right = block.bounds.right + dx,
                                bottom = block.bounds.bottom + dy
                            )
                        )
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
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
            val actualParentId =
                if (targetId == null) null else if (dropPosition == DropPosition.INSIDE) targetId else getParentIdOf(
                    currentPage.blocks,
                    targetId
                )
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null
            val newRelativeBounds = if (targetAbsBounds != null) SerialRect(
                left = draggedAbsBounds.left - targetAbsBounds.left,
                top = draggedAbsBounds.top - targetAbsBounds.top,
                right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width,
                bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height
            ) else draggedAbsBounds
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)
            val resultBlocks =
                if (targetId == null) newBlocks + updatedDraggedBlock else if (dropPosition == DropPosition.INSIDE) updateBlockInList(
                    newBlocks,
                    targetId
                ) { it.copy(children = it.children + updatedDraggedBlock) } else insertBlockSibling(
                    newBlocks,
                    targetId,
                    updatedDraggedBlock,
                    dropPosition
                )
            saveSnapshot()
            val updatedPages =
                currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }
            currentState.copy(
                project = currentState.project.copy(
                    pages = updatedPages,
                    createdAt = getCurrentTimeMillis()
                )
            )
        }
        markDirty()
    }

    fun onRefineArea(
        blockId: String?,
        bounds: SerialRect,
        userInstruction: String,
        apiKey: String,
        useChatContext: Boolean = false,
        onComplete: (Boolean) -> Unit
    ) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: return
        currentGenJob?.cancel()
        val task = aiService.createTask<ProjectState>("区域重构", viewModelScope)
        currentGenJob = viewModelScope.launch {
            launch { task.status.collect { status -> _state.update { it.copy(isGenerating = status == org.gemini.ui.forge.service.AITaskStatus.RUNNING) } } }
            _state.update { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList()) }
            task.execute {
                try {
                    val logger = { msg: String -> addGenLog(msg); log(msg) }
                    logger("✂️ 正在提取区域图像...")
                    val croppedBytes = cropImage(
                        imageSource = originalImage.getAbsolutePath(),
                        bounds = bounds,
                        logicalWidth = currentPage.width,
                        logicalHeight = currentPage.height
                    ) ?: throw Exception("裁剪失败")
                    val originalBytes = originalImage.readBytes() ?: throw Exception("无法读取原图")
                    val fingerprint = originalBytes.calculateMd5()
                    logger("☁️ 正在同步云端原图...")
                    var originalFileUri =
                        cloudAssetManager.assets.value.find { it.displayName?.contains(fingerprint) == true && it.state == "ACTIVE" }?.uri
                            ?: ""
                    if (originalFileUri.isBlank()) {
                        originalFileUri = cloudAssetManager.getOrUploadFile(
                            originalImage.relativePath.substringAfterLast("/"),
                            originalBytes,
                            getMimeType(originalImage.getAbsolutePath())
                        ) { _, status -> logger("[$status]") } ?: ""
                    }
                    val historyKey = blockId ?: "GLOBAL_REFINE"
                    val history =
                        if (useChatContext) _state.value.chatHistories[historyKey] ?: emptyList() else emptyList()
                    logger("🤖 正在向 AI 发送区域重写请求...")
                    val currentJson = Json.encodeToString(currentState.project.pages)
                    val updatedPages = aiService.refineAreaForTemplate(
                        originalImageUri = originalFileUri,
                        croppedBytes = croppedBytes,
                        currentJson = currentJson,
                        userInstruction = userInstruction,
                        apiKey = apiKey,
                        history = history,
                        onLog = logger
                    )
                    val newUserMsg = org.gemini.ui.forge.model.api.ChatMessage("user", userInstruction)
                    val newModelMsg = org.gemini.ui.forge.model.api.ChatMessage("model", "已重塑 UI 结构。")
                    _state.update { s ->
                        val newHistory = history + newUserMsg + newModelMsg; s.copy(
                        project = s.project.copy(pages = updatedPages),
                        chatHistories = s.chatHistories + (historyKey to newHistory)
                    )
                    }
                    markDirty()
                    onComplete(true)
                    _state.value.project
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        val errMsg = "❌ 错误: ${e.message}"; addGenLog(errMsg); log(errMsg); onComplete(false)
                    }; throw e
                }
            }
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, currentLang: PromptLanguage, useChatContext: Boolean = false) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) } ?: return
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return
        currentGenJob?.cancel()
        val task = aiService.createTask<String>("提示词优化", viewModelScope)
        currentGenJob = viewModelScope.launch {
            launch { task.status.collect { status -> _state.update { it.copy(isGenerating = status == org.gemini.ui.forge.service.AITaskStatus.RUNNING) } } }
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            task.execute {
                try {
                    val logger = { msg: String -> addGenLog(msg); log(msg) }
                    val systemInstruction =
                        if (currentLang == PromptLanguage.EN) aiService.promptManager.getPrompt("optimize_instruction_en") else aiService.promptManager.getPrompt(
                            "optimize_instruction_zh"
                        )
                    val historyKey = "PROMPT_$blockId"
                    val history =
                        if (useChatContext) _state.value.chatHistories[historyKey] ?: emptyList() else emptyList()
                    logger(">>> 正在使用 AI 优化提示词 (${currentLang.displayName})...")
                    val optimized =
                        aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, 3, history = history)
                    logger(">>> 优化完成！")
                    onUserPromptChanged(blockId, optimized, currentLang)
                    val newUserMsg = org.gemini.ui.forge.model.api.ChatMessage("user", textToOptimize)
                    val newModelMsg = org.gemini.ui.forge.model.api.ChatMessage("model", optimized)
                    _state.update { s ->
                        val newHistory =
                            history + newUserMsg + newModelMsg; s.copy(chatHistories = s.chatHistories + (historyKey to newHistory))
                    }
                    optimized
                } catch (e: Exception) {
                    val errMsg = ">>> 优化失败: ${e.message} <<<"; addGenLog(errMsg); log(errMsg); throw e
                }
            }
        }
    }

    fun onUserPromptChanged(blockId: String, newPrompt: String, lang: PromptLanguage) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(
                        blocks = updateBlockInList(
                            page.blocks,
                            blockId
                        ) { block ->
                            if (lang == PromptLanguage.EN) block.copy(userPromptEn = newPrompt) else block.copy(
                                userPromptZh = newPrompt
                            )
                        })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun updatePageSize(newWidth: Float, newHeight: Float) {
        val pageId = _state.value.selectedPageId ?: return
        saveSnapshot()
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(
                    width = newWidth,
                    height = newHeight
                ) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun updateStageBackgroundColor(colorHex: String) {
        _state.update { it.copy(stageBackgroundColor = colorHex) }
    }

    private fun addGenLog(msg: String) {
        _state.update { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) }
    }

    fun closeAITaskDialog() {
        _state.update { it.copy(showAITaskDialog = false, generationLogs = emptyList()) }
    }

    fun cancelAITask() {
        currentGenJob?.cancel(); currentGenJob = null; _state.update { it.copy(isGenerating = false) }
    }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
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

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> {
        return blocks.filterNot { it.id == idToRemove }
            .map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }
    }

    private fun getAbsoluteBounds(
        blocks: List<UIBlock>,
        targetId: String,
        currentOffsetX: Float = 0f,
        currentOffsetY: Float = 0f
    ): SerialRect? {
        for (block in blocks) {
            val absL = currentOffsetX + block.bounds.left;
            val absT = currentOffsetY + block.bounds.top
            if (block.id == targetId) return SerialRect(
                absL,
                absT,
                absL + block.bounds.width,
                absT + block.bounds.height
            )
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

package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.domain.UIPage
import org.gemini.ui.forge.domain.DropPosition
import org.gemini.ui.forge.domain.ShortcutAction
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.TemplateRepository
import org.gemini.ui.forge.service.ConfigManager
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.calculateMd5
import org.gemini.ui.forge.utils.getMimeType
import kotlin.collections.ArrayDeque

/**
 * 编辑器页面的 ViewModel，负责 UI 逻辑处理与状态管理
 */
class EditorViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val templateRepo: TemplateRepository = TemplateRepository(),
    val cloudAssetManager: CloudAssetManager = CloudAssetManager(configManager),
    private val aiService: AIGenerationService = AIGenerationService(cloudAssetManager)
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    
    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

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

    fun renameBlock(oldId: String, newId: String) {
        if (oldId == newId || newId.isBlank()) return
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, oldId) { block ->
                        block.copy(id = newId)
                    })
                } else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = if (currentState.selectedBlockId == oldId) newId else currentState.selectedBlockId,
                editingGroupId = if (currentState.editingGroupId == oldId) newId else currentState.editingGroupId
            )
        }
    }

    fun saveShortcut(action: ShortcutAction, keyChord: String) {
        viewModelScope.launch {
            configManager.saveKey("SHORTCUT_${action.name}", keyChord)
            _state.update { currentState ->
                val newShortcuts = currentState.globalState.shortcuts.toMutableMap()
                newShortcuts[action] = keyChord
                currentState.copy(globalState = currentState.globalState.copy(shortcuts = newShortcuts))
            }
        }
    }

    init {
        loadBaseTemplate()
        viewModelScope.launch {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val apiKey = configManager.loadKey("GEMINI_API_KEY") ?: ""
        val globalKey = configManager.loadGlobalGeminiKey() ?: ""
        val languageCode = configManager.loadKey("APP_LANGUAGE") ?: "zh"
        val promptLangStr = configManager.loadKey("PROMPT_LANGUAGE_PREF") ?: "AUTO"
        val promptLang = try { PromptLanguage.valueOf(promptLangStr) } catch (e: Exception) { PromptLanguage.AUTO }
        
        val effectiveKey = apiKey.ifBlank { globalKey }
        val storageDir = templateRepo.getDataDir()
        val retriesStr = configManager.loadKey("API_MAX_RETRIES") ?: "3"
        val retries = retriesStr.toIntOrNull() ?: 3

        val customShortcuts = ShortcutAction.entries.associate { action ->
            val saved = configManager.loadKey("SHORTCUT_${action.name}")
            action to (saved ?: action.defaultKey)
        }
        
        _state.update { it.copy(
            globalState = it.globalState.copy(
                apiKey = apiKey, 
                effectiveApiKey = effectiveKey, 
                templateStorageDir = storageDir,
                languageCode = languageCode,
                promptLangPref = promptLang,
                maxRetries = retries,
                shortcuts = customShortcuts
            ),
            currentEditingPromptLang = if (promptLang == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH
        ) }
    }

    fun loadProject(projectName: String, projectState: ProjectState) {
        _state.update { 
            it.copy(
                project = projectState,
                projectName = projectName,
                selectedPageId = projectState.pages.firstOrNull()?.id,
                selectedBlockId = null,
                generatedCandidates = emptyList()
            )
        }
        viewModelScope.launch {
            templateRepo.cleanupExpiredCache(projectName)
        }
    }

    private fun loadBaseTemplate() {
        val mainBlocks = listOf(
            UIBlock("bg_1", UIBlockType.BACKGROUND, SerialRect(0f, 0f, 1080f, 1920f)),
            UIBlock("reels_1", UIBlockType.REEL, SerialRect(100f, 400f, 980f, 1400f)),
            UIBlock("spin_1", UIBlockType.SPIN_BUTTON, SerialRect(400f, 1500f, 680f, 1780f)),
            UIBlock("win_1", UIBlockType.WIN_DISPLAY, SerialRect(200f, 150f, 880f, 300f))
        )
        val mainPage = UIPage(id = "page_1", nameStr = "Main Game", width = 1080f, height = 1920f, sourceImageUri = null, blocks = mainBlocks)
        val bonusPage = UIPage(id = "page_2", nameStr = "Bonus Game", width = 1080f, height = 1920f, sourceImageUri = null, blocks = emptyList())
        _state.update { it.copy(project = it.project.copy(pages = listOf(mainPage, bonusPage)), selectedPageId = "page_1") }
    }

    fun onPageSelected(pageId: String) = _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, editingGroupId = null, generatedCandidates = emptyList()) }
    fun onBlockClicked(blockId: String) = _state.update { it.copy(selectedBlockId = if (it.selectedBlockId == blockId) null else blockId) }

    /** 双击组件逻辑：只要包含子组件，则进入孤立编辑模式 */
    fun onBlockDoubleClicked(blockId: String) {
        val block = state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) }
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    /** 退出组编辑模式 */
    fun exitGroupEditMode() {
        _state.update { it.copy(editingGroupId = null) }
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
            if (block.id == blockId) {
                transform(block)
            } else {
                block.copy(children = updateBlockInList(block.children, blockId, transform))
            }
        }
    }

    fun onUserPromptChanged(newPrompt: String, lang: PromptLanguage? = null) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val targetLang = lang ?: _state.value.currentEditingPromptLang
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        if (targetLang == PromptLanguage.EN) block.copy(userPromptEn = newPrompt) else block.copy(userPromptZh = newPrompt)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, onComplete: (String) -> Unit) {
        val block = state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) } ?: return
        val currentLang = _state.value.currentEditingPromptLang
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return

        viewModelScope.launch {
            try {
                val systemInstruction = if (currentLang == PromptLanguage.EN) {
                    "Please optimize and polish the following English image generation prompt. Make it highly descriptive, artistic, and technical (lighting, texture, style). Keep it in English: "
                } else {
                    "请优化并润色以下关于 UI 组件的中文描述，使其更具设计感、更详尽且生动。请保持使用中文回答： "
                }
                val optimized = aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, _state.value.globalState.maxRetries)
                onUserPromptChanged(optimized, currentLang)
                onComplete(optimized)
            } catch (e: Exception) {
                AppLogger.e("EditorViewModel", "Failed to optimize prompt", e)
            }
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
    }

    fun addBlock(type: UIBlockType) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${org.gemini.ui.forge.getCurrentTimeMillis()}"
        
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            
            val width = 400f
            val height = 300f
            var left = (currentPage.width - width) / 2f
            var top = (currentPage.height - height) / 2f
            
            if (editingGroupId != null) {
                val groupBlock = findBlockById(currentPage.blocks, editingGroupId)
                if (groupBlock != null) {
                    left = (groupBlock.bounds.width - width) / 2f
                    top = (groupBlock.bounds.height - height) / 2f
                }
            }
            
            val newBlock = UIBlock(newBlockId, type, SerialRect(left, top, left + width, top + height))

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(blocks = updateBlockInList(page.blocks, editingGroupId) { group ->
                            group.copy(children = group.children + newBlock)
                        })
                    } else {
                        page.copy(blocks = page.blocks + newBlock)
                    }
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    fun addCustomBlock(id: String, type: UIBlockType, width: Float, height: Float) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = if (id.isBlank()) "block_${org.gemini.ui.forge.getCurrentTimeMillis()}" else id
        
        AppLogger.d("EditorViewModel", "Adding custom block: $newBlockId with size ${width}x${height}")

        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            
            var left = (currentPage.width - width) / 2f
            var top = (currentPage.height - height) / 2f
            
            if (editingGroupId != null) {
                val groupBlock = findBlockById(currentPage.blocks, editingGroupId)
                if (groupBlock != null) {
                    left = (groupBlock.bounds.width - width) / 2f
                    top = (groupBlock.bounds.height - height) / 2f
                }
            }
            
            val newBlock = UIBlock(newBlockId, type, SerialRect(left, top, left + width, top + height))

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(blocks = updateBlockInList(page.blocks, editingGroupId) { group ->
                            group.copy(children = group.children + newBlock)
                        })
                    } else {
                        page.copy(blocks = page.blocks + newBlock)
                    }
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    // 组概念已被移除，统一使用嵌套组件

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(
                            bounds = SerialRect(
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
    }

    fun deleteBlock(blockId: String) {
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
    }

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> {
        return blocks.filterNot { it.id == idToRemove }.map {
            it.copy(children = removeBlockRecursive(it.children, idToRemove))
        }
    }

    private fun getAbsoluteBounds(blocks: List<UIBlock>, targetId: String, currentOffsetX: Float = 0f, currentOffsetY: Float = 0f): SerialRect? {
        for (block in blocks) {
            val absL = currentOffsetX + block.bounds.left
            val absT = currentOffsetY + block.bounds.top
            if (block.id == targetId) {
                return SerialRect(absL, absT, absL + block.bounds.width, absT + block.bounds.height)
            }
            val hit = getAbsoluteBounds(block.children, targetId, absL, absT)
            if (hit != null) return hit
        }
        return null
    }

    private fun isDescendantOfBlock(targetId: String, currentBlock: UIBlock): Boolean {
        if (currentBlock.id == targetId) return true
        return currentBlock.children.any { isDescendantOfBlock(targetId, it) }
    }

    private fun getParentIdOf(blocks: List<UIBlock>, targetId: String, currentParentId: String? = null): String? {
        for (block in blocks) {
            if (block.id == targetId) return currentParentId
            val p = getParentIdOf(block.children, targetId, block.id)
            if (p != null) return p
        }
        return null
    }

    private fun insertBlockSibling(blocks: List<UIBlock>, targetId: String, blockToInsert: UIBlock, position: DropPosition): List<UIBlock> {
        val result = mutableListOf<UIBlock>()
        var inserted = false
        for (b in blocks) {
            if (b.id == targetId) {
                if (position == DropPosition.BEFORE) result.add(blockToInsert)
                result.add(b)
                if (position == DropPosition.AFTER) result.add(blockToInsert)
                inserted = true
            } else {
                val newChildren = insertBlockSibling(b.children, targetId, blockToInsert, position)
                if (newChildren.size != b.children.size) {
                    inserted = true
                    result.add(b.copy(children = newChildren))
                } else {
                    result.add(b)
                }
            }
        }
        return result
    }

    /**
     * 将一个拖拽的图层移动到另一个图层，支持内部 (INSIDE)、前方 (BEFORE)、后方 (AFTER)
     */
    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        AppLogger.d("EditorViewModel", "moveBlock requested: dragged=$draggedBlockId, target=$targetId, pos=$dropPosition")
        if (draggedBlockId == targetId) {
            AppLogger.d("EditorViewModel", "moveBlock ignored: dragged and target are same.")
            return
        }
        
        val pageId = _state.value.selectedPageId ?: return
        
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId)
            if (draggedBlock == null) {
                AppLogger.d("EditorViewModel", "moveBlock failed: draggedBlock not found.")
                return@update currentState
            }
            
            if (targetId != null && isDescendantOfBlock(targetId, draggedBlock)) {
                AppLogger.d("EditorViewModel", "moveBlock failed: target is descendant of dragged block.")
                return@update currentState
            }

            saveSnapshot() // 只有在确定要修改且合法时才保存快照
            
            val draggedAbsBounds = getAbsoluteBounds(currentPage.blocks, draggedBlockId) ?: draggedBlock.bounds
            var newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)
            
            val actualParentId = if (targetId == null) {
                null
            } else if (dropPosition == DropPosition.INSIDE) {
                targetId
            } else {
                getParentIdOf(currentPage.blocks, targetId)
            }
            
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null
            
            val newRelativeBounds = if (targetAbsBounds != null) {
                SerialRect(
                    left = draggedAbsBounds.left - targetAbsBounds.left,
                    top = draggedAbsBounds.top - targetAbsBounds.top,
                    right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width,
                    bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height
                )
            } else {
                draggedAbsBounds
            }
            
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)
            
            if (targetId == null) {
                AppLogger.d("EditorViewModel", "moveBlock: appending to root.")
                newBlocks = newBlocks + updatedDraggedBlock
            } else if (dropPosition == DropPosition.INSIDE) {
                AppLogger.d("EditorViewModel", "moveBlock: inserting into target $targetId.")
                newBlocks = updateBlockInList(newBlocks, targetId) { targetGroup ->
                    targetGroup.copy(
                        children = targetGroup.children + updatedDraggedBlock
                    )
                }
            } else {
                AppLogger.d("EditorViewModel", "moveBlock: inserting as sibling of $targetId at $dropPosition.")
                newBlocks = insertBlockSibling(newBlocks, targetId, updatedDraggedBlock, dropPosition)
                AppLogger.d("EditorViewModel", "moveBlock: insertBlockSibling finished. draggedBlock found? ${findBlockById(newBlocks, draggedBlockId) != null}")
            }
            
            val updatedPages = currentState.project.pages.map {
                if (it.id == pageId) it.copy(blocks = newBlocks) else it
            }
            
            val newState = currentState.copy(
                project = currentState.project.copy(pages = updatedPages, createdAt = org.gemini.ui.forge.getCurrentTimeMillis())
            )
            AppLogger.d("EditorViewModel", "moveBlock: updating state. are equal? ${newState == currentState}")
            newState
        }
        AppLogger.d("EditorViewModel", "moveBlock END: current blocks=${_state.value.currentPage?.blocks?.map { it.id }}")
    }

    /**
     * 针对选定区域调用 AI 进行结构重塑 (基于视觉选择生成的 Rect)
     */
    fun onRefineArea(
        blockId: String, 
        bounds: SerialRect,
        userInstruction: String, 
        onLog: (String) -> Unit = {},
        onChunk: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit
    ) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: throw Exception("找不到当前页面的原始参考图")
        val apiKey = currentState.globalState.effectiveApiKey

        viewModelScope.launch {
            try {
                onLog("正在准备局部细节裁剪...")
                val croppedBytes = org.gemini.ui.forge.utils.cropImage(
                    imageSource = originalImage, 
                    bounds = bounds,
                    logicalWidth = currentPage.width,
                    logicalHeight = currentPage.height
                ) ?: throw Exception("图像裁剪失败")

                val cachePath = templateRepo.saveCacheImage(currentState.projectName, "refine_crop_${blockId}", croppedBytes)
                onLog("📸 局部裁剪图已暂存至本地: $cachePath")

                onLog("正在分析原图指纹以同步云端上下文...")
                val originalBytes = org.gemini.ui.forge.utils.readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()
                
                var originalFileUri = cloudAssetManager.assets.value.find { (it.displayName?.contains(fingerprint) == true) && it.state == "ACTIVE" }?.uri ?: ""

                if (originalFileUri.isBlank()) {
                    onLog("🚀 云端未命中指纹，正在针对当前页面自动重传参考图...")
                    val displayName = originalImage.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "reference.jpg" }
                    val mimeType = org.gemini.ui.forge.utils.getMimeType(originalImage)
                    originalFileUri = cloudAssetManager.getOrUploadFile(displayName, originalBytes, mimeType) { _, status -> onLog("[$status]") } ?: ""
                }

                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(originalImageUri = originalFileUri, croppedBytes = croppedBytes, currentJson = currentJson, userInstruction = userInstruction, apiKey = apiKey, onLog = onLog, onChunk = onChunk)

                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)
            } catch (e: Exception) {
                AppLogger.e("EditorViewModel", "区域重塑失败", e)
                onLog("❌ 错误: ${e.message}")
                onComplete(false)
            }
        }
    }

    /**
     * 针对手动框选区域调用 AI 进行结构重塑
     */
    fun onRefineCustomArea(
        bounds: SerialRect, 
        userInstruction: String, 
        onLog: (String) -> Unit = {},
        onChunk: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit
    ) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: throw Exception("找不到当前页面的原始参考图")
        val apiKey = currentState.globalState.effectiveApiKey

        viewModelScope.launch {
            try {
                onLog("正在导出选区细节...")
                val croppedBytes = org.gemini.ui.forge.utils.cropImage(
                    imageSource = originalImage, 
                    bounds = bounds,
                    logicalWidth = currentPage.width,
                    logicalHeight = currentPage.height
                ) ?: throw Exception("图像裁剪失败")

                val cachePath = templateRepo.saveCacheImage(currentState.projectName, "custom_refine_crop", croppedBytes)
                onLog("📸 框选裁剪图已暂存至本地: $cachePath")

                onLog("正在分析原图指纹以同步云端上下文...")
                val originalBytes = org.gemini.ui.forge.utils.readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()
                
                var originalFileUri = cloudAssetManager.assets.value.find { (it.displayName?.contains(fingerprint) == true) && it.state == "ACTIVE" }?.uri ?: ""

                if (originalFileUri.isBlank()) {
                    onLog("🚀 云端未命中指纹，正在针对当前页面自动重传参考图...")
                    val displayName = originalImage.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "reference.jpg" }
                    val mimeType = org.gemini.ui.forge.utils.getMimeType(originalImage)
                    originalFileUri = cloudAssetManager.getOrUploadFile(displayName, originalBytes, mimeType) { _, status -> onLog("[$status]") } ?: ""
                }

                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(originalImageUri = originalFileUri, croppedBytes = croppedBytes, currentJson = currentJson, userInstruction = userInstruction, apiKey = apiKey, onLog = onLog, onChunk = onChunk)

                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)
            } catch (e: Exception) {
                AppLogger.e("EditorViewModel", "手动区域重塑失败", e)
                onLog("❌ 错误: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun onRequestGeneration(apiKey: String) {
        val block = state.value.selectedBlock ?: return
        val currentLang = _state.value.currentEditingPromptLang
        val submitPrompt = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        val projectName = _state.value.projectName

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generatedCandidates = emptyList()) }
            try {
                val candidatesBase64 = aiService.generateImages(blockType = block.type.name, userPrompt = submitPrompt, apiKey = apiKey, maxRetries = _state.value.globalState.maxRetries)
                val candidatePaths = candidatesBase64.mapIndexed { index, base64 ->
                    val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                    val bytes = kotlin.io.encoding.Base64.Default.decode(pure)
                    templateRepo.saveCacheImage(projectName, "candidate_${block.id}_$index", bytes)
                }
                _state.update { it.copy(generatedCandidates = candidatePaths, isGenerating = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun onImageSelected(imageUri: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val projectName = _state.value.projectName
        viewModelScope.launch {
            val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(imageUri) ?: return@launch
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            val base64Data = "data:image/jpeg;base64," + kotlin.io.encoding.Base64.Default.encode(bytes)
            val localImagePath = templateRepo.saveResource(projectName, blockId, base64Data)
            _state.update { currentState ->
                val updatedPages = currentState.project.pages.map { page ->
                    if (page.id == pageId) {
                        page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                            block.copy(currentImageUri = localImagePath)
                        })
                    } else page
                }
                currentState.copy(project = currentState.project.copy(pages = updatedPages), generatedCandidates = emptyList())
            }
            templateRepo.saveTemplate(projectName, _state.value.project)
        }
    }

    fun navigateTo(screen: AppScreen) = _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }
    fun setThemeMode(mode: ThemeMode) = _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    fun saveApiKey(newKey: String) = viewModelScope.launch { configManager.saveKey("GEMINI_API_KEY", newKey); val globalKey = configManager.loadGlobalGeminiKey() ?: ""; val effectiveKey = newKey.ifBlank { globalKey }; _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey, effectiveApiKey = effectiveKey)) } }
    fun setLanguage(code: String) = viewModelScope.launch { configManager.saveKey("APP_LANGUAGE", code); _state.update { it.copy(globalState = it.globalState.copy(languageCode = code)) } }
    fun updateStorageDir(newPath: String) = viewModelScope.launch { if (templateRepo.updateStorageDir(newPath)) _state.update { it.copy(globalState = it.globalState.copy(templateStorageDir = newPath)) } }
    fun setMaxRetries(count: Int) = viewModelScope.launch { configManager.saveKey("API_MAX_RETRIES", count.toString()); _state.update { it.copy(globalState = it.globalState.copy(maxRetries = count)) } }
    fun switchEditingLanguage(lang: PromptLanguage) = _state.update { it.copy(currentEditingPromptLang = lang) }
    
    fun setReferenceMode(mode: ReferenceDisplayMode) {
        _state.update { it.copy(referenceMode = mode) }
    }

    fun setReferenceOpacity(opacity: Float) {
        _state.update { it.copy(referenceOpacity = opacity) }
    }

    fun setPromptLanguagePref(pref: PromptLanguage) = viewModelScope.launch { configManager.saveKey("PROMPT_LANGUAGE_PREF", pref.name); _state.update { it.copy(globalState = it.globalState.copy(promptLangPref = pref), currentEditingPromptLang = if (pref == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH) } }
}

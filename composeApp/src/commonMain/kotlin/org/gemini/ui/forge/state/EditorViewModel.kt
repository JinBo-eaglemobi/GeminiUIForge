package org.gemini.ui.forge.state
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.utils.io.release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.files.sink
import kotlinx.io.files.source
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.utils.calculateMd5
import org.gemini.ui.forge.utils.getMimeType
import kotlin.collections.ArrayDeque
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.model.app.ThemeMode
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.service.ConfigManager
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.executeSystemCommand
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 编辑器页面的 ViewModel，负责 UI 逻辑处理与状态管理
 */
class EditorViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val templateRepo: TemplateRepository = TemplateRepository(),
    val cloudAssetManager: CloudAssetManager = CloudAssetManager(configManager),
    private val aiService: AIGenerationService = AIGenerationService(cloudAssetManager),
    private val envService: org.gemini.ui.forge.service.EnvironmentCheckService = org.gemini.ui.forge.service.createEnvironmentCheckService(),
    private val updateService: org.gemini.ui.forge.service.UpdateService = org.gemini.ui.forge.service.UpdateService("1.0.0")
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var currentGenJob: kotlinx.coroutines.Job? = null

    /** 检查软件更新 */
    fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.Checking) }
            val info = updateService.checkUpdate()
            if (info != null) {
                _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.Available(info)) }
            } else {
                _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.UpToDate) }
            }
        }
    }

    /** 开始下载并执行更新重启 */
    fun performUpdate(info: org.gemini.ui.forge.model.app.UpdateInfo) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val storageDir = templateRepo.getDataDir()
                val tempPath = Path(storageDir, "update_" + info.fileName)
                updateService.downloadUpdate(info.downloadUrl, tempPath).collect { progress ->
                    _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.Downloading(progress)) }
                }
                _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.ReadyToInstall) }
                delay(1000)
                org.gemini.ui.forge.getPlatform().applyUpdateAndRestart(tempPath.toString())
            } catch (e: Exception) {
                _state.update { it.copy(updateStatus = org.gemini.ui.forge.model.app.UpdateStatus.Error(e.message ?: "Unknown Error")) }
            }
        }
    }

    /** 执行全量环境检查 */
    fun checkEnvironment() {
        viewModelScope.launch {
            _state.update { it.copy(globalState = it.globalState.copy(envStatus = it.globalState.envStatus.copy(isChecking = true))) }
            val status = envService.checkAll()
            _state.update { it.copy(globalState = it.globalState.copy(envStatus = status)) }
        }
    }

    /** 安装特定的环境依赖项 */
    fun installEnvironmentItem(name: String) {
        viewModelScope.launch {
            _state.update { s ->
                val newItems = s.globalState.envStatus.items.map { 
                    if (it.name == name) it.copy(isInstalling = true, installLogs = emptyList()) else it 
                }
                s.copy(globalState = s.globalState.copy(envStatus = s.globalState.envStatus.copy(items = newItems)))
            }
            envService.installItem(name).collect { log ->
                _state.update { s ->
                    val newItems = s.globalState.envStatus.items.map { 
                        if (it.name == name) it.copy(installLogs = it.installLogs + log) else it 
                    }
                    s.copy(globalState = s.globalState.copy(envStatus = s.globalState.envStatus.copy(items = newItems)))
                }
            }
            checkEnvironment()
        }
    }

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
            checkEnvironment()
            val updateInstruction = aiService.promptManager.getPrompt("refine_instruction_update")
            val newInstruction = aiService.promptManager.getPrompt("refine_instruction_new")
            _state.update { it.copy(defaultRefineInstructionUpdate = updateInstruction, defaultRefineInstructionNew = newInstruction) }
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
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = apiKey, effectiveApiKey = effectiveKey, templateStorageDir = storageDir, languageCode = languageCode, promptLangPref = promptLang, maxRetries = retries, shortcuts = customShortcuts), currentEditingPromptLang = if (promptLang == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH) }
    }

    fun loadProject(projectName: String, projectState: ProjectState) {
        _state.update { it.copy(project = projectState, projectName = projectName, selectedPageId = projectState.pages.firstOrNull()?.id, selectedBlockId = null, generatedCandidates = emptyList()) }
        viewModelScope.launch { templateRepo.cleanupExpiredCache(projectName) }
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
    fun onBlockClicked(blockId: String?) = _state.update { it.copy(selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId) }

    fun onBlockDoubleClicked(blockId: String) {
        val block = state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) }
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    fun exitGroupEditMode() { _state.update { it.copy(editingGroupId = null) } }

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }

    private fun updateBlockInList(blocks: List<UIBlock>, blockId: String, transform: (UIBlock) -> UIBlock): List<UIBlock> {
        var changed = false
        val newList = blocks.map { block ->
            if (block.id == blockId) { changed = true; transform(block) }
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children) { changed = true; block.copy(children = newChildren) } else block
            }
        }
        return if (changed) newList else blocks
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
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    fun addCustomBlock(id: String, type: UIBlockType, width: Float, height: Float) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = if (id.isBlank()) "block_${org.gemini.ui.forge.getCurrentTimeMillis()}" else id
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
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(bounds = SerialRect(left = block.bounds.left + dx, top = block.bounds.top + dy, right = block.bounds.right + dx, bottom = block.bounds.bottom + dy))
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
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = if (currentState.selectedBlockId == blockId) null else currentState.selectedBlockId, editingGroupId = if (currentState.editingGroupId == blockId) null else currentState.editingGroupId)
        }
    }

    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> {
        return blocks.filterNot { it.id == idToRemove }.map { it.copy(children = removeBlockRecursive(it.children, idToRemove)) }
    }

    private fun getAbsoluteBounds(blocks: List<UIBlock>, targetId: String, currentOffsetX: Float = 0f, currentOffsetY: Float = 0f): SerialRect? {
        for (block in blocks) {
            val absL = currentOffsetX + block.bounds.left
            val absT = currentOffsetY + block.bounds.top
            if (block.id == targetId) return SerialRect(absL, absT, absL + block.bounds.width, absT + block.bounds.height)
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
        val index = blocks.indexOfFirst { it.id == targetId }
        if (index != -1) {
            val result = blocks.toMutableList()
            if (position == DropPosition.BEFORE) result.add(index, blockToInsert) else result.add(index + 1, blockToInsert)
            return result
        }
        var changed = false
        val newBlocks = blocks.map { b ->
            val newChildren = insertBlockSibling(b.children, targetId, blockToInsert, position)
            if (newChildren !== b.children) { changed = true; b.copy(children = newChildren) } else b
        }
        return if (changed) newBlocks else blocks
    }

    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        if (draggedBlockId == targetId) return
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId) ?: return@update currentState
            if (targetId != null && isDescendantOfBlock(targetId, draggedBlock)) return@update currentState
            val draggedAbsBounds = getAbsoluteBounds(currentPage.blocks, draggedBlockId) ?: draggedBlock.bounds
            var newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)
            val actualParentId = if (targetId == null) null else if (dropPosition == DropPosition.INSIDE) targetId else getParentIdOf(currentPage.blocks, targetId)
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null
            val newRelativeBounds = if (targetAbsBounds != null) SerialRect(left = draggedAbsBounds.left - targetAbsBounds.left, top = draggedAbsBounds.top - targetAbsBounds.top, right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width, bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height) else draggedAbsBounds
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)
            val resultBlocks = if (targetId == null) newBlocks + updatedDraggedBlock else if (dropPosition == DropPosition.INSIDE) updateBlockInList(newBlocks, targetId) { it.copy(children = it.children + updatedDraggedBlock) } else insertBlockSibling(newBlocks, targetId, updatedDraggedBlock, dropPosition)
            if (resultBlocks == removeBlockRecursive(currentPage.blocks, draggedBlockId) && updatedDraggedBlock.bounds == draggedBlock.bounds) return@update currentState
            saveSnapshot()
            val updatedPages = currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }
            currentState.copy(project = currentState.project.copy(pages = updatedPages, createdAt = org.gemini.ui.forge.getCurrentTimeMillis()))
        }
    }

    fun onRefineArea(blockId: String, bounds: SerialRect, userInstruction: String, onLog: (String) -> Unit = {}, onChunk: (String) -> Unit = {}, onComplete: (Boolean) -> Unit) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: throw Exception("找不到当前页面的原始参考图")
        val apiKey = currentState.globalState.effectiveApiKey
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            try {
                _state.update { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList()) }
                val logger = { msg: String -> addGenLog(msg); onLog(msg) }
                logger("正在导出模块细节...")
                val croppedBytes = cropImage(imageSource = originalImage, bounds = bounds, logicalWidth = currentPage.width, logicalHeight = currentPage.height) ?: throw Exception("图像裁剪失败")
                val cachePath = templateRepo.saveCacheImage(currentState.projectName, "refine_crop_${blockId}", croppedBytes)
                logger("📸 裁剪图已暂存: $cachePath")
                logger("正在分析云端资源上下文...")
                val originalBytes = readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()
                var originalFileUri = cloudAssetManager.assets.value.find { (it.displayName?.contains(fingerprint) == true) && it.state == "ACTIVE" }?.uri ?: ""
                if (originalFileUri.isBlank()) {
                    logger("🚀 正在自动同步参考图到云端...")
                    val displayName = originalImage.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "reference.jpg" }
                    originalFileUri = cloudAssetManager.getOrUploadFile(displayName, originalBytes, getMimeType(originalImage)) { _, status -> logger("[$status]") } ?: ""
                }
                logger("正在通过 Gemini AI 重塑 UI 结构...")
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(originalImageUri = originalFileUri, croppedBytes = croppedBytes, currentJson = currentJson, userInstruction = userInstruction, apiKey = apiKey, onLog = logger, onChunk = onChunk)
                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                logger("✅ 区域重塑成功！")
                onComplete(true)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    AppLogger.e("EditorViewModel", "区域重塑失败", e); addGenLog("❌ 错误: ${e.message}"); onComplete(false)
                }
            } finally { _state.update { it.copy(isGenerating = false) } }
        }
    }

    fun onRefineCustomArea(bounds: SerialRect, userInstruction: String, onLog: (String) -> Unit = {}, onChunk: (String) -> Unit = {}, onComplete: (Boolean) -> Unit) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: throw Exception("找不到当前页面的原始参考图")
        val apiKey = currentState.globalState.effectiveApiKey
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            try {
                _state.update { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList()) }
                val logger = { msg: String -> addGenLog(msg); onLog(msg) }
                logger("正在导出选区细节...")
                val croppedBytes = cropImage(imageSource = originalImage, bounds = bounds, logicalWidth = currentPage.width, logicalHeight = currentPage.height) ?: throw Exception("图像裁剪失败")
                val cachePath = templateRepo.saveCacheImage(currentState.projectName, "custom_refine_crop", croppedBytes)
                logger("📸 选区已暂存: $cachePath")
                logger("正在分析云端资源上下文...")
                val originalBytes = readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()
                var originalFileUri = cloudAssetManager.assets.value.find { (it.displayName?.contains(fingerprint) == true) && it.state == "ACTIVE" }?.uri ?: ""
                if (originalFileUri.isBlank()) {
                    logger("🚀 正在自动同步参考图...")
                    val displayName = originalImage.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "reference.jpg" }
                    originalFileUri = cloudAssetManager.getOrUploadFile(displayName, originalBytes, getMimeType(originalImage)) { _, status -> logger("[$status]") } ?: ""
                }
                logger("正在通过 Gemini AI 重构新区域...")
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(originalImageUri = originalFileUri, croppedBytes = croppedBytes, currentJson = currentJson, userInstruction = userInstruction, apiKey = apiKey, onLog = logger, onChunk = onChunk)
                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                logger("✅ 手动重构完成！")
                onComplete(true)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    AppLogger.e("EditorViewModel", "手动区域重塑失败", e); addGenLog("❌ 错误: ${e.message}"); onComplete(false)
                }
            } finally { _state.update { it.copy(isGenerating = false) } }
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, onComplete: (String) -> Unit) {
        val block = state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) } ?: return
        val currentLang = _state.value.currentEditingPromptLang
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            addGenLog(">>> 开始智能优化提示词 [${block.id}] <<<")
            try {
                val systemInstruction = if (currentLang == PromptLanguage.EN) aiService.promptManager.getPrompt("optimize_instruction_en") else aiService.promptManager.getPrompt("optimize_instruction_zh")
                addGenLog("正在连接 Gemini AI 模型进行润色...")
                val optimized = aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, _state.value.globalState.maxRetries)
                onUserPromptChanged(optimized, currentLang)
                addGenLog("优化完成！已应用到描述框。")
                onComplete(optimized)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) { addGenLog(">>> 优化失败: ${e.message} <<<"); AppLogger.e("EditorViewModel", "Failed to optimize prompt", e) }
            } finally { _state.update { it.copy(isGenerating = false) }; currentGenJob = null }
        }
    }

    fun onRequestGeneration(apiKey: String) {
        val block = state.value.selectedBlock ?: return
        val currentLang = _state.value.currentEditingPromptLang
        val submitPrompt = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        val projectName = _state.value.projectName
        val isTransparent = _state.value.isGenerateTransparent
        val prioritizeCloud = _state.value.isPrioritizeCloudRemoval
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            addGenLog(">>> 开始为模块 [${block.id}] 生成资源 <<<")
            try {
                addGenLog("正在连接 AI 生图模型 (Imagen)...")
                val candidatesBase64 = aiService.generateImages(blockType = block.type.name, userPrompt = submitPrompt, apiKey = apiKey, maxRetries = _state.value.globalState.maxRetries, targetWidth = block.bounds.width, targetHeight = block.bounds.height, isPng = isTransparent)
                addGenLog("模型生成成功，获得 ${candidatesBase64.size} 张候选图。开始预处理...")
                val candidatePaths = withContext(Dispatchers.Default) {
                    candidatesBase64.mapIndexed { index, base64 ->
                        val pure = if (base64.contains(",")) base64.substringAfter(",") else base64
                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = Base64.decode(pure)
                        val timestamp = getCurrentTimeMillis()
                        val originalUri = templateRepo.saveBlockResource(projectName, block.id, "gen_${index}_$timestamp", bytes)
                        withContext(Dispatchers.Main) { addGenLog("[候选图 ${index + 1}] 已缓存。") }
                        if (isTransparent) {
                            var finalProcessedBytes: ByteArray? = null
                            if (prioritizeCloud) {
                                withContext(Dispatchers.Main) { addGenLog("[候选图 ${index + 1}] 正在调用云端执行背景移除...") }
                                finalProcessedBytes = aiService.removeBackgroundCloud(bytes, apiKey)
                            }
                            if (finalProcessedBytes == null) {
                                withContext(Dispatchers.Main) { addGenLog("[候选图 ${index + 1}] 正在执行本地 Python 脚本抠图...") }
                                val outputUri = originalUri.replace("gen_", "local_trans_").replace(".jpg", ".png").replace(".jpeg", ".png")
                                val localFileStorage = org.gemini.ui.forge.service.LocalFileStorage()
                                val scriptCacheName = "scripts/remove_bg.py"
                                try {
                                    if (!localFileStorage.exists(scriptCacheName)) {
                                        @OptIn(InternalResourceApi::class)
                                        val scriptBytes = readResourceBytes("scripts/remove_bg.py")
                                        localFileStorage.saveBytesToFile(scriptCacheName, scriptBytes)
                                    }
                                    val actualScriptPath = localFileStorage.getFilePath(scriptCacheName)
                                    val success = executeSystemCommand("python", listOf(actualScriptPath, originalUri, outputUri), onLog = { launch(Dispatchers.Main) { addGenLog(" > $it") } })
                                    if (success && isFileExists(outputUri)) {
                                        withContext(Dispatchers.Main) { addGenLog("[候选图 ${index + 1}] 本地扣图成功，正在裁剪...") }
                                        val trimmedBytes = org.gemini.ui.forge.utils.trimTransparency(outputUri)
                                        if (trimmedBytes != null) {
                                            val finalUri = templateRepo.saveBlockResource(projectName, block.id, outputUri.substringAfterLast("/").substringBeforeLast(".") + "_trimmed", trimmedBytes)
                                            try { org.gemini.ui.forge.utils.deleteLocalFile(outputUri) } catch (e: Exception) {}
                                            return@mapIndexed finalUri
                                        }
                                        return@mapIndexed outputUri
                                    } else return@mapIndexed originalUri
                                } catch (e: Exception) { return@mapIndexed originalUri }
                            } else {
                                val cloudUri = originalUri.replace("gen_", "cloud_trans_").replace(".jpg", ".png").replace(".jpeg", ".png")
                                val base64ForTrim = "data:image/png;base64," + Base64.encode(finalProcessedBytes)
                                val trimmedBytes = org.gemini.ui.forge.utils.trimTransparency(base64ForTrim) ?: finalProcessedBytes
                                return@mapIndexed templateRepo.saveBlockResource(projectName, block.id, cloudUri.substringAfterLast("/").substringBeforeLast("."), trimmedBytes)
                            }
                        } else originalUri
                    }
                }
                _state.update { it.copy(generatedCandidates = candidatePaths) }
                addGenLog(">>> 资源处理全部完成 <<<")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) addGenLog(">>> 生成失败: ${e.message} <<<")
            } finally { _state.update { it.copy(isGenerating = false) }; currentGenJob = null }
        }
    }

    fun performCropAndApply(blockId: String, sourceUri: String, cropRect: SerialRect) {
        val projectName = _state.value.projectName
        viewModelScope.launch {
            try {
                val imageSize = org.gemini.ui.forge.utils.getImageSize(sourceUri) ?: return@launch
                val physicalRect = SerialRect(left = cropRect.left * imageSize.first, top = cropRect.top * imageSize.second, right = cropRect.right * imageSize.first, bottom = cropRect.bottom * imageSize.second)
                val croppedBytes = org.gemini.ui.forge.utils.cropImage(imageSource = sourceUri, bounds = physicalRect, logicalWidth = imageSize.first.toFloat(), logicalHeight = imageSize.second.toFloat(), isPng = sourceUri.endsWith(".png", ignoreCase = true)) ?: throw Exception("裁剪失败")
                val newUri = templateRepo.saveBlockResource(projectName, blockId, "crop_${org.gemini.ui.forge.getCurrentTimeMillis()}", croppedBytes)
                _state.update { currentState ->
                    val pageId = currentState.selectedPageId ?: return@update currentState
                    val updatedPages = currentState.project.pages.map { page ->
                        if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = newUri, cropRect = cropRect) }) else page
                    }
                    currentState.copy(project = currentState.project.copy(pages = updatedPages))
                }
                templateRepo.saveTemplate(projectName, _state.value.project)
            } catch (e: Exception) { AppLogger.e("EditorViewModel", "裁剪保存失败", e) }
        }
    }

    fun onImageSelected(imageUri: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        viewModelScope.launch {
            _state.update { currentState ->
                val updatedPages = currentState.project.pages.map { page ->
                    if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = imageUri) }) else page
                }
                currentState.copy(project = currentState.project.copy(pages = updatedPages), generatedCandidates = emptyList())
            }
            templateRepo.saveTemplate(_state.value.projectName, _state.value.project)
        }
    }

    fun clearSelectedImage(blockId: String) {
        saveSnapshot()
        _state.update { currentState ->
            val pageId = currentState.selectedPageId ?: return@update currentState
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = null) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        viewModelScope.launch { templateRepo.saveTemplate(_state.value.projectName, _state.value.project) }
    }

    fun deleteImages(uris: List<String>) {
        if (uris.isEmpty()) return
        val blockId = _state.value.selectedBlockId ?: return
        viewModelScope.launch {
            uris.forEach { org.gemini.ui.forge.utils.deleteLocalFile(it) }
            _state.update { currentState ->
                val pageId = currentState.selectedPageId ?: return@update currentState
                var needsUpdate = false
                val updatedPages = currentState.project.pages.map { page ->
                    if (page.id == pageId) {
                        page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                            if (block.currentImageUri in uris) { needsUpdate = true; block.copy(currentImageUri = null) } else block
                        })
                    } else page
                }
                val filteredCandidates = currentState.generatedCandidates.filter { it !in uris }
                if (filteredCandidates.size != currentState.generatedCandidates.size) needsUpdate = true
                if (needsUpdate) currentState.copy(project = currentState.project.copy(pages = updatedPages), generatedCandidates = filteredCandidates) else currentState
            }
            templateRepo.saveTemplate(_state.value.projectName, _state.value.project)
        }
    }

    fun clearCandidates() { _state.update { it.copy(generatedCandidates = emptyList()) } }

    suspend fun loadBlockHistoricalImages(blockId: String): List<String> {
        return try {
            val rootDir = templateRepo.getDataDir()
            val templateDir = "$rootDir/templates/${_state.value.projectName.replace(" ", "_")}/assets/$blockId"
            org.gemini.ui.forge.utils.listFilesInLocalDirectory(templateDir).filter { it.endsWith(".png") || it.endsWith(".jpg") }
        } catch (e: Exception) { emptyList() }
    }

    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(isVisible = isVisible) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    fun updateVis(list: List<UIBlock>): List<UIBlock> = list.map { it.copy(isVisible = isVisible, children = updateVis(it.children)) }
                    page.copy(blocks = updateVis(page.blocks))
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun navigateTo(screen: AppScreen) = _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }
    fun setThemeMode(mode: ThemeMode) = _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    fun saveApiKey(newKey: String) = viewModelScope.launch { configManager.saveKey("GEMINI_API_KEY", newKey); val globalKey = configManager.loadGlobalGeminiKey() ?: ""; val effectiveKey = newKey.ifBlank { globalKey }; _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey, effectiveApiKey = effectiveKey)) } }
    fun setLanguage(code: String) = viewModelScope.launch { configManager.saveKey("APP_LANGUAGE", code); _state.update { it.copy(globalState = it.globalState.copy(languageCode = code)) } }
    fun updateStorageDir(newPath: String) = viewModelScope.launch { if (templateRepo.updateStorageDir(newPath)) _state.update { it.copy(globalState = it.globalState.copy(templateStorageDir = newPath)) } }
    fun setMaxRetries(count: Int) = viewModelScope.launch { configManager.saveKey("API_MAX_RETRIES", count.toString()); _state.update { it.copy(globalState = it.globalState.copy(maxRetries = count)) } }
    fun switchEditingLanguage(lang: PromptLanguage) = _state.update { it.copy(currentEditingPromptLang = lang) }
    fun toggleGenerationLogVisibility() { _state.update { it.copy(isGenerationLogVisible = !it.isGenerationLogVisible) } }
    fun closeAITaskDialog() { _state.update { it.copy(showAITaskDialog = false, generationLogs = emptyList()) } }
    private fun addGenLog(msg: String) { AppLogger.i("EditorViewModel", msg); _state.update { it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true) } }
    fun cancelGeneration() { currentGenJob?.cancel(); currentGenJob = null; _state.update { it.copy(isGenerating = false, generationLogs = it.generationLogs + ">>> 用户已手动中断处理 <<<") } }
    fun setGenerateTransparent(enabled: Boolean) { _state.update { it.copy(isGenerateTransparent = enabled) } }
    fun setPrioritizeCloudRemoval(enabled: Boolean) { _state.update { it.copy(isPrioritizeCloudRemoval = enabled) } }
    fun toggleVisualMode() { _state.update { it.copy(isVisualMode = !it.isVisualMode) } }
    fun setReferenceMode(mode: ReferenceDisplayMode) { _state.update { it.copy(referenceMode = mode) } }
    fun setReferenceOpacity(opacity: Float) { _state.update { it.copy(referenceOpacity = opacity) } }
    fun setPromptLanguagePref(pref: PromptLanguage) = viewModelScope.launch { configManager.saveKey("PROMPT_LANGUAGE_PREF", pref.name); _state.update { it.copy(globalState = it.globalState.copy(promptLangPref = pref), currentEditingPromptLang = if (pref == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH) } }
}

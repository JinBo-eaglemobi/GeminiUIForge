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
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.TemplateRepository
import org.gemini.ui.forge.service.ConfigManager
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.calculateMd5
import org.gemini.ui.forge.utils.getMimeType

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
        
        _state.update { it.copy(
            globalState = it.globalState.copy(
                apiKey = apiKey, 
                effectiveApiKey = effectiveKey, 
                templateStorageDir = storageDir,
                languageCode = languageCode,
                promptLangPref = promptLang,
                maxRetries = retries
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

    fun onPageSelected(pageId: String) = _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }
    fun onBlockClicked(blockId: String) = _state.update { it.copy(selectedBlockId = if (it.selectedBlockId == blockId) null else blockId) }

    fun onUserPromptChanged(newPrompt: String, lang: PromptLanguage? = null) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val targetLang = lang ?: _state.value.currentEditingPromptLang
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    val updatedBlocks = page.blocks.map { block ->
                        if (block.id == blockId) {
                            if (targetLang == PromptLanguage.EN) block.copy(userPromptEn = newPrompt) else block.copy(userPromptZh = newPrompt)
                        } else block
                    }
                    page.copy(blocks = updatedBlocks)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun optimizePrompt(blockId: String, apiKey: String, onComplete: (String) -> Unit) {
        val block = state.value.project.pages.flatMap { it.blocks }.find { it.id == blockId } ?: return
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
                    val updatedBlocks = page.blocks.map { block ->
                        if (block.id == blockId) block.copy(bounds = SerialRect(left, top, right, bottom)) else block
                    }
                    page.copy(blocks = updatedBlocks)
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
                    val updatedBlocks = page.blocks.map { block ->
                        if (block.id == blockId) block.copy(type = newType) else block
                    }
                    page.copy(blocks = updatedBlocks)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    fun addBlock(type: UIBlockType) {
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${org.gemini.ui.forge.getCurrentTimeMillis()}"
        val newBlock = UIBlock(newBlockId, type, SerialRect(100f, 100f, 400f, 300f))
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = page.blocks + newBlock) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    fun deleteBlock(blockId: String) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = page.blocks.filterNot { it.id == blockId }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = if (currentState.selectedBlockId == blockId) null else currentState.selectedBlockId)
        }
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
                        val updatedBlocks = page.blocks.map { block ->
                            if (block.id == blockId) block.copy(currentImageUri = localImagePath) else block
                        }
                        page.copy(blocks = updatedBlocks)
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
    
    /** 切换参考图显示模式 */
    fun setReferenceMode(mode: ReferenceDisplayMode) {
        _state.update { it.copy(referenceMode = mode) }
    }

    /** 调整参考图透明度 */
    fun setReferenceOpacity(opacity: Float) {
        _state.update { it.copy(referenceOpacity = opacity) }
    }

    fun setPromptLanguagePref(pref: PromptLanguage) = viewModelScope.launch { configManager.saveKey("PROMPT_LANGUAGE_PREF", pref.name); _state.update { it.copy(globalState = it.globalState.copy(promptLangPref = pref), currentEditingPromptLang = if (pref == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH) } }
}

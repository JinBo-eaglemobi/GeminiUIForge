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

/**
 * 编辑器页面的 ViewModel，负责 UI 逻辑处理与状态管理
 * @param configManager 配置与密钥管理工具
 * @param templateRepo 模板持久化仓库
 * @param cloudAssetManager 云端资产管理器
 * @param aiService AI 生成服务，用于调用 Imagen 和 Gemini
 */
class EditorViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val templateRepo: TemplateRepository = TemplateRepository(),
    val cloudAssetManager: CloudAssetManager = CloudAssetManager(configManager),
    private val aiService: AIGenerationService = AIGenerationService(cloudAssetManager)
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    /** 向外暴露的只读 UI 状态流 */
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        loadBaseTemplate()
        viewModelScope.launch {
            loadSettings()
        }
    }

    /** 从本地持久化存储加载配置信息 */
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
            // 初始化编辑语言：如果偏好是 EN 则显示 EN，否则默认 ZH
            currentEditingPromptLang = if (promptLang == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH
        ) }
    }

    /**
     * 设置提示词语言偏好
     */
    fun setPromptLanguagePref(pref: PromptLanguage) {
        viewModelScope.launch {
            configManager.saveKey("PROMPT_LANGUAGE_PREF", pref.name)
            _state.update { it.copy(
                globalState = it.globalState.copy(promptLangPref = pref),
                currentEditingPromptLang = if (pref == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH
            ) }
        }
    }

    /**
     * 在 UI 上临时切换当前正在编辑的语言（不改变全局偏好）
     */
    fun switchEditingLanguage(lang: PromptLanguage) {
        _state.update { it.copy(currentEditingPromptLang = lang) }
    }

    /**
     * 保存并更新 Gemini API 密钥
     * @param newKey 新的 API Key 字符串
     */
    fun saveApiKey(newKey: String) {
        viewModelScope.launch {
            configManager.saveKey("GEMINI_API_KEY", newKey)
            val globalKey = configManager.loadGlobalGeminiKey() ?: ""
            val effectiveKey = newKey.ifBlank { globalKey }
            _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey, effectiveApiKey = effectiveKey)) }
        }
    }

    /**
     * 设置并保存应用语言
     * @param code 语言代码 (en, zh)
     */
    fun setLanguage(code: String) {
        viewModelScope.launch {
            configManager.saveKey("APP_LANGUAGE", code)
            _state.update { it.copy(globalState = it.globalState.copy(languageCode = code)) }
        }
    }

    /**
     * 更新模板存储目录
     * @param newPath 新的目录路径
     */
    fun updateStorageDir(newPath: String) {
        viewModelScope.launch {
            if (templateRepo.updateStorageDir(newPath)) {
                _state.update { it.copy(globalState = it.globalState.copy(templateStorageDir = newPath)) }
            }
        }
    }

    /**
     * 设置最大重试次数
     * @param count 重试次数
     */
    fun setMaxRetries(count: Int) {
        viewModelScope.launch {
            configManager.saveKey("API_MAX_RETRIES", count.toString())
            _state.update { it.copy(globalState = it.globalState.copy(maxRetries = count)) }
        }
    }

    /** 加载初始的基础 UI 模板（如背景、卷轴、旋转按钮等） */
    private fun loadBaseTemplate() {
        val mainBlocks = listOf(
            UIBlock("bg_1", UIBlockType.BACKGROUND, SerialRect(0f, 0f, 1080f, 1920f)),
            UIBlock("reels_1", UIBlockType.REEL, SerialRect(100f, 400f, 980f, 1400f)),
            UIBlock("spin_1", UIBlockType.SPIN_BUTTON, SerialRect(400f, 1500f, 680f, 1780f)),
            UIBlock("win_1", UIBlockType.WIN_DISPLAY, SerialRect(200f, 150f, 880f, 300f))
        )
        val mainPage = UIPage("page_1", "Main Game", 1080f, 1920f, mainBlocks)
        val bonusPage = UIPage("page_2", "Bonus Game", 1080f, 1920f, emptyList())

        _state.update { 
            it.copy(
                project = it.project.copy(pages = listOf(mainPage, bonusPage)),
                selectedPageId = "page_1"
            ) 
        }
    }
    
    /**
     * 加载指定的项目数据
     * @param projectName 项目名称
     * @param projectState 项目的状态数据
     */
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
    }

    /**
     * 页面切换回调
     * @param pageId 目标页面 ID
     */
    fun onPageSelected(pageId: String) {
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }
    }

    /**
     * UI 组件点击选择回调
     * @param blockId 被点击的组件 ID
     */
    fun onBlockClicked(blockId: String) {
        _state.update { it.copy(selectedBlockId = if (it.selectedBlockId == blockId) null else blockId) }
    }

    /**
     * 响应用户针对特定组件输入的 Prompt 变化
     * @param newPrompt 新的 Prompt 文本
     * @param lang 指定更新哪种语言，如果不传则根据当前 UI 状态自动判定
     */
    fun onUserPromptChanged(newPrompt: String, lang: PromptLanguage? = null) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val targetLang = lang ?: _state.value.currentEditingPromptLang
        
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    val updatedBlocks = page.blocks.map { block ->
                        if (block.id == blockId) {
                            if (targetLang == PromptLanguage.EN) {
                                block.copy(userPromptEn = newPrompt)
                            } else {
                                block.copy(userPromptZh = newPrompt)
                            }
                        } else block
                    }
                    page.copy(blocks = updatedBlocks)
                } else {
                    page
                }
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    /**
     * 调用 AI 优化指定组件的 Prompt。
     * 现在支持根据当前选择的语言进行针对性优化，而不再强制转为英文。
     * @param blockId 组件 ID
     * @param apiKey Gemini API Key
     */
    fun optimizePrompt(blockId: String, apiKey: String, onComplete: (String) -> Unit) {
        val block = state.value.project.pages.flatMap { it.blocks }.find { it.id == blockId } ?: return
        val currentLang = _state.value.currentEditingPromptLang
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        
        if (textToOptimize.isBlank()) return

        viewModelScope.launch {
            try {
                // 构造更加动态的优化指令
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

    /**
     * 更新 UI 组件的位置和大小
     * @param blockId 组件 ID
     * @param left 左边界坐标
     * @param top 上边界坐标
     * @param right 右边界坐标
     * @param bottom 下边界坐标
     */
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

    /**
     * 更改 UI 组件的类型（如背景改为按钮）
     * @param blockId 组件 ID
     * @param newType 目标组件类型
     */
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

    /**
     * 在当前选中的页面添加一个新的 UI 组件
     * @param type 组件类型
     */
    fun addBlock(type: UIBlockType) {
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${org.gemini.ui.forge.getCurrentTimeMillis()}"
        val defaultBounds = SerialRect(100f, 100f, 400f, 300f)
        val newBlock = UIBlock(newBlockId, type, defaultBounds)

        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = newBlockId
            )
        }
    }

    /**
     * 从当前页面删除指定的 UI 组件
     * @param blockId 要删除的组件 ID
     */
    fun deleteBlock(blockId: String) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = page.blocks.filterNot { it.id == blockId })
                } else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = if (currentState.selectedBlockId == blockId) null else currentState.selectedBlockId
            )
        }
    }

    /**
     * 针对选定区域调用 AI 进行结构重塑
     * @param blockId 目标组件 ID
     * @param userInstruction 用户修正指令
     */
    fun onRefineArea(blockId: String, userInstruction: String, onComplete: (Boolean) -> Unit) {
        val currentState = _state.value
        val block = currentState.project.pages.flatMap { it.blocks }.find { it.id == blockId } ?: return
        val originalImage = currentState.project.coverImage ?: return // 必须有原始参考图
        val apiKey = currentState.globalState.effectiveApiKey

        viewModelScope.launch {
            try {
                // 1. 执行物理裁剪
                val croppedBytes = org.gemini.ui.forge.utils.cropImage(originalImage, block.bounds)
                    ?: throw Exception("图像裁剪失败")

                // 2. 序列化当前项目状态作为上下文
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)

                // 3. 尝试获取原图的云端 URI（如果管理器中有）
                val originalFileName = originalImage.substringAfterLast("/").substringAfterLast("\\")
                val originalFileUri = cloudAssetManager.assets.value.find { it.displayName == originalFileName }?.uri
                    ?: "" // 如果云端没有，AI 将仅依赖裁剪图

                // 4. 调用重塑服务
                val updatedProject = aiService.refineAreaForTemplate(
                    originalImageUri = originalFileUri,
                    croppedBytes = croppedBytes,
                    currentJson = currentJson,
                    userInstruction = userInstruction,
                    apiKey = apiKey,
                    onLog = { AppLogger.d("Refine", it) }
                )

                // 5. 更新本地状态并保存
                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)

            } catch (e: Exception) {
                AppLogger.e("EditorViewModel", "区域重塑失败", e)
                onComplete(false)
            }
        }
    }

    /**
     * 针对手动框选区域调用 AI 进行结构重塑
     * @param bounds 用户框选的逻辑坐标区域
     * @param userInstruction 用户修正指令
     */
    fun onRefineCustomArea(bounds: SerialRect, userInstruction: String, onComplete: (Boolean) -> Unit) {
        val currentState = _state.value
        val originalImage = currentState.project.coverImage ?: return
        val apiKey = currentState.globalState.effectiveApiKey

        viewModelScope.launch {
            try {
                onComplete(false) // 标记开始
                // 1. 执行物理裁剪
                val croppedBytes = org.gemini.ui.forge.utils.cropImage(originalImage, bounds)
                    ?: throw Exception("图像裁剪失败")

                // 2. 序列化当前项目状态作为上下文
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)

                // 3. 尝试获取原图的云端 URI
                val originalFileName = originalImage.substringAfterLast("/").substringAfterLast("\\")
                val originalFileUri = cloudAssetManager.assets.value.find { it.displayName == originalFileName }?.uri ?: ""

                // 4. 调用重塑服务
                val updatedProject = aiService.refineAreaForTemplate(
                    originalImageUri = originalFileUri,
                    croppedBytes = croppedBytes,
                    currentJson = currentJson,
                    userInstruction = userInstruction,
                    apiKey = apiKey,
                    onLog = { AppLogger.d("Refine", it) }
                )

                // 5. 更新本地状态并保存
                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)

            } catch (e: Exception) {
                AppLogger.e("EditorViewModel", "手动区域重塑失败", e)
                onComplete(false)
            }
        }
    }

    /**
     * 请求 AI 根据当前组件的 Prompt 生成候选图片资源
     * @param apiKey 用于认证的 Gemini API 密钥
     */
    fun onRequestGeneration(apiKey: String) {
        val block = state.value.selectedBlock ?: return
        val currentLang = _state.value.currentEditingPromptLang
        
        // 根据当前 UI 选择的语言提交提示词进行生图
        val submitPrompt = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generatedCandidates = emptyList()) }
            try {
                val candidates = aiService.generateImages(
                    blockType = block.type.name,
                    userPrompt = submitPrompt,
                    apiKey = apiKey,
                    maxRetries = _state.value.globalState.maxRetries
                )
                _state.update { it.copy(generatedCandidates = candidates, isGenerating = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    /**
     * 确认选择一张生成的图片作为该组件的正式资源
     * @param base64Data 被选中的图片的 Base64 数据
     */
    fun onImageSelected(base64Data: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val projectName = _state.value.projectName

        viewModelScope.launch {
            val localImagePath = templateRepo.saveResource(projectName, blockId, base64Data)

            _state.update { currentState ->
                val updatedPages = currentState.project.pages.map { page ->
                    if (page.id == pageId) {
                        val updatedBlocks = page.blocks.map { block ->
                            if (block.id == blockId) block.copy(currentImageUri = localImagePath) else block
                        }
                        page.copy(blocks = updatedBlocks)
                    } else {
                        page
                    }
                }
                val newState = currentState.copy(
                    project = currentState.project.copy(pages = updatedPages),
                    generatedCandidates = emptyList()
                )
                
                newState
            }
            val latestState = _state.value
            templateRepo.saveTemplate(projectName, latestState.project)
        }
    }

    /**
     * 全局页面导航跳转
     * @param screen 目标页面枚举
     */
    fun navigateTo(screen: AppScreen) {
        _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }
    }

    /**
     * 设置应用主题模式
     * @param mode 主题模式 (SYSTEM, LIGHT, DARK)
     */
    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    }
}

package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.page_bonus
import geminiuiforge.composeapp.generated.resources.page_main
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.domain.UIPage
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.TemplateRepository

import org.gemini.ui.forge.service.EnvManager

/**
 * 编辑器页面的 UI 状态模型
 * @property globalState 应用全局状态
 * @property project 当前正在编辑的项目数据状态
 * @property projectName 项目名称
 * @property selectedPageId 当前选中的页面 ID
 * @property selectedBlockId 当前选中的 UI 组件 ID
 * @property isGenerating 是否正在通过 AI 生成资源
 * @property generatedCandidates AI 生成的候选图片数据列表 (Base64)
 */
data class EditorState(
    val globalState: AppGlobalState = AppGlobalState(),
    val project: ProjectState = ProjectState(),
    val projectName: String = "Untitled",
    val selectedPageId: String? = null,
    val selectedBlockId: String? = null,
    val isGenerating: Boolean = false,
    val generatedCandidates: List<String> = emptyList()
) {
    /** 获取当前选中的页面对象 */
    val currentPage: UIPage?
        get() = project.pages.find { it.id == selectedPageId }
        
    /** 获取当前选中的 UI 组件对象 */
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.find { it.id == selectedBlockId }
}

/**
 * 编辑器页面的 ViewModel，负责 UI 逻辑处理与状态管理
 * @param aiService AI 生成服务，用于调用 Imagen 和 Gemini
 * @param templateRepo 模板持久化仓库
 * @param envManager 环境变量与密钥管理工具
 */
class EditorViewModel(
    private val aiService: AIGenerationService = AIGenerationService(),
    private val templateRepo: TemplateRepository = TemplateRepository(),
    private val envManager: EnvManager = EnvManager()
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    /** 向外暴露的只读 UI 状态流 */
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        loadBaseTemplate()
        loadSettings()
    }

    /** 从本地持久化存储加载配置信息 */
    private fun loadSettings() {
        val apiKey = envManager.loadKey("GEMINI_API_KEY") ?: ""
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = apiKey)) }
    }

    /**
     * 保存并更新 Gemini API 密钥
     * @param newKey 新的 API Key 字符串
     */
    fun saveApiKey(newKey: String) {
        envManager.saveKey("GEMINI_API_KEY", newKey)
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey)) }
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
     */
    fun onUserPromptChanged(newPrompt: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    val updatedBlocks = page.blocks.map { block ->
                        if (block.id == blockId) block.copy(userPrompt = newPrompt) else block
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
     * 调用 AI 优化指定组件的 Prompt
     * @param blockId 组件 ID
     * @param apiKey Gemini API Key
     */
    fun optimizePrompt(blockId: String, apiKey: String, onComplete: (String) -> Unit) {
        val block = state.value.project.pages.flatMap { it.blocks }.find { it.id == blockId } ?: return
        if (block.userPrompt.isBlank()) return

        viewModelScope.launch {
            try {
                val optimized = aiService.optimizePrompt(block.userPrompt, apiKey)
                onUserPromptChanged(optimized)
                onComplete(optimized)
            } catch (e: Exception) {
                org.gemini.ui.forge.utils.AppLogger.e("EditorViewModel", "Failed to optimize prompt", e)
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
                        if (block.id == blockId) block.copy(bounds = org.gemini.ui.forge.domain.SerialRect(left, top, right, bottom)) else block
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
        // 默认生成在可见区域内
        val defaultBounds = org.gemini.ui.forge.domain.SerialRect(100f, 100f, 400f, 300f)
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
     * 请求 AI 根据当前组件的 Prompt 生成候选图片资源
     * @param apiKey 用于认证的 Gemini API 密钥
     */
    fun onRequestGeneration(apiKey: String) {
        val block = state.value.selectedBlock ?: return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generatedCandidates = emptyList()) }
            try {
                val candidates = aiService.generateImages(
                    blockType = block.type.name,
                    userPrompt = block.fullPrompt,
                    apiKey = apiKey
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

        // 1. 保存资源并获得其本地引用 (返回落盘后的本地绝对路径)
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
            
            // 2. 自动保存整个项目 JSON
            templateRepo.saveTemplate(projectName, newState.project)
            
            newState
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

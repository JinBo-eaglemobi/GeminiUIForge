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

data class EditorState(
    val globalState: AppGlobalState = AppGlobalState(),
    val project: ProjectState = ProjectState(),
    val projectName: String = "Untitled",
    val selectedPageId: String? = null,
    val selectedBlockId: String? = null,
    val isGenerating: Boolean = false,
    val generatedCandidates: List<String> = emptyList()
) {
    val currentPage: UIPage?
        get() = project.pages.find { it.id == selectedPageId }
        
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.find { it.id == selectedBlockId }
}

class EditorViewModel(
    private val aiService: AIGenerationService = AIGenerationService(),
    private val templateRepo: TemplateRepository = TemplateRepository(),
    private val envManager: EnvManager = EnvManager()
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        loadBaseTemplate()
        loadSettings()
    }

    private fun loadSettings() {
        val apiKey = envManager.loadKey("GEMINI_API_KEY") ?: ""
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = apiKey)) }
    }

    fun saveApiKey(newKey: String) {
        envManager.saveKey("GEMINI_API_KEY", newKey)
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey)) }
    }

    private fun loadBaseTemplate() {
        val mainBlocks = listOf(
            UIBlock("bg_1", UIBlockType.BACKGROUND, SerialRect(0f, 0f, 1080f, 1920f)),
            UIBlock("reels_1", UIBlockType.REEL, SerialRect(100f, 400f, 980f, 1400f)),
            UIBlock("spin_1", UIBlockType.SPIN_BUTTON, SerialRect(400f, 1500f, 680f, 1780f)),
            UIBlock("win_1", UIBlockType.WIN_DISPLAY, SerialRect(200f, 150f, 880f, 300f))
        )
        val mainPage = UIPage("page_1", "Main Game", mainBlocks)
        val bonusPage = UIPage("page_2", "Bonus Game", emptyList())

        _state.update { 
            it.copy(
                project = it.project.copy(pages = listOf(mainPage, bonusPage)),
                selectedPageId = "page_1"
            ) 
        }
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
    }

    fun onPageSelected(pageId: String) {
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, generatedCandidates = emptyList()) }
    }

    fun onBlockClicked(blockId: String) {
        _state.update { it.copy(selectedBlockId = if (it.selectedBlockId == blockId) null else blockId) }
    }

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
        // Default bounds somewhere visible
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

    fun onImageSelected(base64Data: String) {
        val pageId = _state.value.selectedPageId ?: return
        val blockId = _state.value.selectedBlockId ?: return
        val projectName = _state.value.projectName

        // 1. 保存资源并获得其本地引用 (现在返回的是落盘后的本地绝对路径，例如: 某模板目录/spin_1_xxxx.png)
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

    fun navigateTo(screen: AppScreen) {
        _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    }
}
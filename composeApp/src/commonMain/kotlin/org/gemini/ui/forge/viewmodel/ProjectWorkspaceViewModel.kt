package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.state.EditorMode
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 统一工作区 ViewModel。
 * 组合了布局编辑、资产生成和资产管理三大核心逻辑委托。
 */
class ProjectWorkspaceViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService,
    private val onDirtyChanged: (Boolean) -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProjectWorkspaceState(
            project = initialProject,
            projectName = initialProjectName,
            currentLang = initialLang,
            selectedPageId = initialProject.pages.firstOrNull()?.id
        )
    )
    val state: StateFlow<ProjectWorkspaceState> = _state.asStateFlow()

    private fun markDirty() {
        onDirtyChanged(true)
    }

    // --- 模式切换 ---
    fun setMode(mode: EditorMode) {
        _state.update { it.copy(mode = mode) }
    }

    // --- 状态更新辅助 ---
    fun updateState(transform: (ProjectWorkspaceState) -> ProjectWorkspaceState) {
        _state.update(transform)
    }

    // --- 核心方法 ---
    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        _state.update {
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null,
                globalStyle = newProject.globalStyle,
                referenceImageUri = newProject.styleReferenceUri
            )
        }
    }

    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    fun switchPage(pageId: String) {
        _state.update { 
            it.copy(
                selectedPageId = pageId,
                selectedBlockId = null,
                editingGroupId = null
            ) 
        }
    }

    fun updatePageSize(width: Float, height: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { 
                if (it.id == pageId) it.copy(width = width, height = height) else it 
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun updateStageBackgroundColor(color: String) {
        _state.update { it.copy(stageBackgroundColor = color) }
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, right: Float, bottom: Float) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { 
                        it.copy(bounds = org.gemini.ui.forge.model.ui.SerialRect(left, top, right, bottom))
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    fun updateBlockType(blockId: String, type: org.gemini.ui.forge.model.ui.UIBlockType) {
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { it.copy(type = type) })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    private fun updateBlockInList(blocks: List<org.gemini.ui.forge.model.ui.UIBlock>, blockId: String, transform: (org.gemini.ui.forge.model.ui.UIBlock) -> org.gemini.ui.forge.model.ui.UIBlock): List<org.gemini.ui.forge.model.ui.UIBlock> {
        return blocks.map { block ->
            if (block.id == blockId) transform(block)
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children) block.copy(children = newChildren) else block
            }
        }
    }

    fun onBlockClicked(blockId: String?) {
        _state.update {
            it.copy(
                selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId
            )
        }
    }

    fun onBlockDoubleClicked(blockId: String) {
        _state.update {
            val newGroupId = if (it.editingGroupId == blockId) null else blockId
            it.copy(
                editingGroupId = newGroupId,
                selectedBlockId = if (newGroupId != null) null else it.selectedBlockId // 进入组编辑时清除选中
            )
        }
    }

    // --- 按钮多态管理 ---

    fun openButtonGenDialog() {
        val block = state.value.selectedBlock
        if (block?.type != org.gemini.ui.forge.model.ui.UIBlockType.BUTTON) return
        val props = block.properties as? org.gemini.ui.forge.model.ui.BlockProperties.ButtonProperties
        _state.update { it.copy(
            showButtonGenDialog = true,
            buttonPressedPrompt = props?.pressedPrompt ?: "",
            buttonDisabledPrompt = props?.disabledPrompt ?: "",
            buttonPressedCandidate = null,
            buttonDisabledCandidate = null
        ) }
    }

    fun closeButtonGenDialog() {
        _state.update { it.copy(showButtonGenDialog = false) }
    }

    fun updateButtonGenPrompts(pressed: String, disabled: String) {
        _state.update { it.copy(buttonPressedPrompt = pressed, buttonDisabledPrompt = disabled) }
    }

    fun confirmButtonStates() {
        val block = state.value.selectedBlock ?: return
        val pressed = state.value.buttonPressedCandidate
        val disabled = state.value.buttonDisabledCandidate
        
        if (pressed != null || disabled != null) {
            val existingProps = block.properties as? org.gemini.ui.forge.model.ui.BlockProperties.ButtonProperties 
                ?: org.gemini.ui.forge.model.ui.BlockProperties.ButtonProperties()
            
            val newProps = existingProps.copy(
                pressedUri = pressed ?: existingProps.pressedUri,
                disabledUri = disabled ?: existingProps.disabledUri,
                pressedPrompt = state.value.buttonPressedPrompt,
                disabledPrompt = state.value.buttonDisabledPrompt,
                isMultiState = true
            )
            assetManager.updateBlockProperties(block.id, newProps)
        }
        closeButtonGenDialog()
    }

    private val _requestRenameEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRenameEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _requestRenameEvent.asSharedFlow()

    // --- 逻辑委托实例化 ---

    val assetGen = AssetGenerationDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        getState = { _state.value },
        updateState = { _state.update(it) },
        notifySelectionHandled = { /* 内部逻辑已处理 */ }
    )

    val layoutEditor = LayoutEditorDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        cloudAssetManager = cloudAssetManager,
        getState = { _state.value },
        updateState = { _state.update(it) },
        markDirty = { markDirty() },
        onRequestRename = { viewModelScope.launch { _requestRenameEvent.emit(Unit) } }
    )

    val assetManager = AssetManagerDelegate(
        scope = viewModelScope,
        templateRepo = templateRepo,
        getState = { _state.value },
        updateState = { _state.update(it) },
        markDirty = { markDirty() },
        notifySelectionHandled = { assetGen.completeConfirmation() }
    )

    init {
        // 初始化一些 AI 指令模板
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
}

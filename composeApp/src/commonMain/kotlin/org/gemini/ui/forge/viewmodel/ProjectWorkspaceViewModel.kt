package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 统一工作区 ViewModel。
 * 组合了布局编辑、资产生成、资产管理、历史记录以及快捷键核心逻辑。
 * 所有的具体实现逻辑均已剥离至对应的 Delegate 委托类中。
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

    // --- 状态更新辅助 ---
    fun updateState(transform: (ProjectWorkspaceState) -> ProjectWorkspaceState) {
        _state.update(transform)
    }

    // --- 核心逻辑委托实例化 (严格按照依赖顺序) ---

    /** 1. 历史管理 (底层基础，供其它所有修改状态的委托使用) */
    val historyManager = HistoryManagerDelegate(
        getState = { _state.value },
        updateState = { _state.update(it) },
        markDirty = { markDirty() }
    )

    /** 2. 资产生成 */
    val assetGen = AssetGenerationDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        getState = { _state.value },
        updateState = { _state.update(it) },
        notifySelectionHandled = { /* 内部逻辑已通过 AssetManager 同步 */ }
    )

    /** 3. 布局编辑 */
    val layoutEditor = LayoutEditorDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        cloudAssetManager = cloudAssetManager,
        getState = { _state.value },
        updateState = { _state.update(it) },
        markDirty = { markDirty() },
        saveSnapshot = { historyManager.saveSnapshot(it) },
        undo = { historyManager.undo() },
        redo = { historyManager.redo() },
        onRequestRename = { viewModelScope.launch { _requestRenameEvent.emit(Unit) } }
    )

    /** 4. 资产管理 */
    val assetManager = AssetManagerDelegate(
        scope = viewModelScope,
        templateRepo = templateRepo,
        getState = { _state.value },
        updateState = { _state.update(it) },
        markDirty = { markDirty() },
        saveSnapshot = { historyManager.saveSnapshot(it) },
        notifySelectionHandled = { assetGen.completeConfirmation() }
    )

    /** 5. 快捷键管理 (依赖布局和历史委托) */
    val shortcutManager = ShortcutManagerDelegate(
        layoutEditor = layoutEditor,
        historyManager = historyManager,
        onSaveRequest = { viewModelScope.launch { _requestSaveEvent.emit(Unit) } }
    )

    // --- 外部事件流 ---

    private val _requestRenameEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRenameEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _requestRenameEvent.asSharedFlow()

    private val _requestSaveEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestSaveEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _requestSaveEvent.asSharedFlow()

    // --- 生命周期与基础控制 ---

    init {
        // 加载初始 AI 提示词模板
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

    /** 重新加载项目数据 */
    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        _state.update {
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null,
                globalStyle = newProject.globalStyle,
                referenceImageUri = newProject.styleReferenceUri,
                undoStack = emptyList(), 
                redoStack = emptyList()
            )
        }
    }

    /** 切换语言偏好 */
    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    /** 切换当前编辑页面 */
    fun switchPage(pageId: String) {
        _state.update { 
            it.copy(
                selectedPageId = pageId,
                selectedBlockId = null,
                editingGroupId = null
            ) 
        }
    }

    /** 更新页面画布尺寸 */
    fun updatePageSize(width: Float, height: Float) {
        val pageId = _state.value.selectedPageId ?: return
        historyManager.saveSnapshot("修改页面尺寸")
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { 
                if (it.id == pageId) it.copy(width = width, height = height) else it 
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 更新背景颜色 */
    fun updateStageBackgroundColor(color: String) {
        _state.update { it.copy(stageBackgroundColor = color) }
    }

    /** 更新块坐标 (直接更新，不产生历史快照，通常用于拖拽过程中的实时预览) */
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

    /** 修改模块类型 */
    fun updateBlockType(blockId: String, type: org.gemini.ui.forge.model.ui.UIBlockType) {
        historyManager.saveSnapshot("修改模块类型: $type")
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

    /** 处理模块点击选中 */
    fun onBlockClicked(blockId: String?) {
        _state.update {
            it.copy(
                selectedBlockId = if (blockId == null) null else if (it.selectedBlockId == blockId) null else blockId
            )
        }
    }

    /** 处理模块双击进入组编辑 */
    fun onBlockDoubleClicked(blockId: String) {
        _state.update {
            val newGroupId = if (it.editingGroupId == blockId) null else blockId
            it.copy(
                editingGroupId = newGroupId,
                selectedBlockId = if (newGroupId != null) null else it.selectedBlockId 
            )
        }
    }

    // --- 按钮多态管理交互封装 (仅作为 UI 控制入口，具体资产操作委派给 assetManager) ---

    fun openButtonGenDialog() {
        val block = state.value.selectedBlock
        if (block?.type != UIBlockType.BUTTON) return
        val props = block.properties as? BlockProperties.ButtonProperties
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
            historyManager.saveSnapshot("保存按钮多态资源")
            val existingProps = block.properties as? BlockProperties.ButtonProperties
                ?: BlockProperties.ButtonProperties()
            
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

    // --- 内部递归辅助 ---

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
}

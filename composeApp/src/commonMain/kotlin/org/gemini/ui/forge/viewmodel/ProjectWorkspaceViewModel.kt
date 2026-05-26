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
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.findParentBlockId
import org.gemini.ui.forge.utils.updateBlockInList
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.viewmodel.delegate.AssetGenerationDelegate
import org.gemini.ui.forge.viewmodel.delegate.AssetManagerDelegate
import org.gemini.ui.forge.viewmodel.delegate.HistoryManagerDelegate
import org.gemini.ui.forge.viewmodel.delegate.LayoutEditorDelegate
import org.gemini.ui.forge.viewmodel.delegate.ShortcutManagerDelegate

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
        // 加载初始 AI 提示词模板与工作区持久化配置
        viewModelScope.launch {
            val updateInstruction = aiService.promptManager.getPrompt("refine_instruction_update")
            val newInstruction = aiService.promptManager.getPrompt("refine_instruction_new")
            val wsConfig = templateRepo.loadWorkspaceConfig(initialProjectName)

            _state.update {
                it.copy(
                    defaultRefineInstructionUpdate = updateInstruction,
                    defaultRefineInstructionNew = newInstruction,
                    collapsedSections = wsConfig?.collapsedSections ?: emptyMap(),
                    isVisualMode = wsConfig?.isVisualMode ?: false,
                    isHideOutlines = wsConfig?.isHideOutlines ?: false,
                    referenceMode = wsConfig?.referenceMode ?: org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
                    referenceOpacity = wsConfig?.referenceOpacity ?: 0.4f,
                    resourceConfigPath = wsConfig?.resourceConfigPath
                )
            }
        }
    }

    /** 重新加载项目数据 */
    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        
        // 如果物理层级结构（页面、模块）完全一致，仅非物理的配置或元数据路径发生变化，
        // 则执行静默内存更新，保留用户的撤销/重做历史和当前选中状态。
        if (_state.value.project.pages == newProject.pages &&
            _state.value.project.globalStyle == newProject.globalStyle &&
            _state.value.project.styleReferenceUri == newProject.styleReferenceUri
        ) {
            _state.update { it.copy(project = newProject) }
            return
        }

        viewModelScope.launch {
            val wsConfig = templateRepo.loadWorkspaceConfig(_state.value.projectName)
            _state.update {
                it.copy(
                    project = newProject,
                    selectedPageId = newProject.pages.firstOrNull()?.id,
                    selectedBlockId = null,
                    editingGroupId = null,
                    globalStyle = newProject.globalStyle,
                    referenceImageUri = newProject.styleReferenceUri,
                    undoStack = emptyList(), 
                    redoStack = emptyList(),
                    collapsedSections = wsConfig?.collapsedSections ?: emptyMap(),
                    isVisualMode = wsConfig?.isVisualMode ?: false,
                    isHideOutlines = wsConfig?.isHideOutlines ?: false,
                    referenceMode = wsConfig?.referenceMode ?: org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
                    referenceOpacity = wsConfig?.referenceOpacity ?: 0.4f,
                    resourceConfigPath = wsConfig?.resourceConfigPath
                )
            }
        }
    }

    /** 触发工作区配置持久化 */
    private fun saveWorkspaceConfig() {
        val currentState = _state.value
        val config = org.gemini.ui.forge.model.app.WorkspaceConfig(
            collapsedSections = currentState.collapsedSections,
            isVisualMode = currentState.isVisualMode,
            isHideOutlines = currentState.isHideOutlines,
            referenceMode = currentState.referenceMode,
            referenceOpacity = currentState.referenceOpacity,
            resourceConfigPath = currentState.resourceConfigPath
        )
        viewModelScope.launch {
            templateRepo.saveWorkspaceConfig(currentState.projectName, config)
        }
    }

    /** 切换折叠状态 */
    fun toggleSectionCollapsed(blockId: String?, title: String, collapsed: Boolean) {
        val targetId = blockId ?: "global"
        _state.update { 
            val currentMap = it.collapsedSections.toMutableMap()
            val currentSet = currentMap[targetId]?.toMutableSet() ?: mutableSetOf()
            if (collapsed) {
                currentSet.add(title)
            } else {
                currentSet.remove(title)
            }
            if (currentSet.isEmpty()) {
                currentMap.remove(targetId)
            } else {
                currentMap[targetId] = currentSet
            }
            it.copy(collapsedSections = currentMap)
        }
        saveWorkspaceConfig()
    }

    /** 切换视觉模式 */
    fun toggleVisualMode() {
        _state.update { it.copy(isVisualMode = !it.isVisualMode) }
        saveWorkspaceConfig()
    }

    /** 切换隐藏描边 */
    fun toggleHideOutlines() {
        _state.update { it.copy(isHideOutlines = !it.isHideOutlines) }
        saveWorkspaceConfig()
    }

    /** 切换参考图模式 */
    fun updateReferenceMode(mode: org.gemini.ui.forge.model.app.ReferenceDisplayMode) {
        _state.update { it.copy(referenceMode = mode) }
        saveWorkspaceConfig()
    }

    /** 更新参考图透明度 */
    fun updateReferenceOpacity(opacity: Float) {
        _state.update { it.copy(referenceOpacity = opacity) }
        saveWorkspaceConfig()
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
                    page.copy(blocks = page.blocks.updateBlockInList(blockId) { 
                        it.copy(bounds = SerialRect(left, top, right, bottom))
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 修改模块类型 */
    fun updateBlockType(blockId: String, type: UIBlockType) {
        historyManager.saveSnapshot("修改模块类型: $type")
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) {
                    page.copy(blocks = page.blocks.updateBlockInList(blockId) { it.copy(type = type) })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 处理模块点击选中 */
    fun onBlockClicked(blockId: String?, isMultiSelect: Boolean = false) {
        _state.update { currentState ->
            if (blockId == null) {
                currentState.copy(selectedBlockId = null, selectedBlockIds = emptySet())
            } else if (isMultiSelect) {
                val currentSet = currentState.selectedBlockIds
                val newSet = if (currentSet.contains(blockId)) {
                    currentSet - blockId
                } else {
                    currentSet + blockId
                }
                currentState.copy(
                    selectedBlockIds = newSet,
                    selectedBlockId = newSet.firstOrNull()
                )
            } else {
                val alreadySelected = currentState.selectedBlockIds.contains(blockId) && currentState.selectedBlockIds.size == 1
                val newId = if (alreadySelected) null else blockId
                val newSet = if (newId != null) setOf(newId) else emptySet()
                currentState.copy(
                    selectedBlockId = newId,
                    selectedBlockIds = newSet
                )
            }
        }
    }



    /** 退出当前组编辑模式（返回父组，并清除选中） */
    fun exitGroupEdit() {
        _state.update { currentState ->
            val currentPage = currentState.currentPage
            val parentId = if (currentPage != null && currentState.editingGroupId != null) {
                currentPage.blocks.findParentBlockId(currentState.editingGroupId)
            } else {
                null
            }
            currentState.copy(
                editingGroupId = parentId,
                selectedBlockId = null,
                selectedBlockIds = emptySet()
            )
        }
    }

    /** 处理模块双击进入或退出组编辑 */
    fun onBlockDoubleClicked(blockId: String) {
        _state.update { currentState ->
            if (currentState.editingGroupId == blockId) {
                // 如果双击的是当前正在编辑的组，则退回父编辑组
                val currentPage = currentState.currentPage
                val parentId = if (currentPage != null) {
                    currentPage.blocks.findParentBlockId(blockId)
                } else {
                    null
                }
                currentState.copy(
                    editingGroupId = parentId,
                    selectedBlockId = null,
                    selectedBlockIds = emptySet()
                )
            } else {
                // 如果双击的是其它组，进入该组，并清除模块选中状态
                currentState.copy(
                    editingGroupId = blockId,
                    selectedBlockId = null,
                    selectedBlockIds = emptySet()
                )
            }
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

    /** 更新当前项目的资源配置路径 */
    fun updateResourceConfigPath(path: String?) {
        _state.update { currentState ->
            currentState.copy(
                resourceConfigPath = path,
                resourceConfigRefreshTrigger = org.gemini.ui.forge.getCurrentTimeMillis()
            )
        }
        saveWorkspaceConfig()
    }

    /** 手动触发资源命名规范配置文件的重新读取与刷新 */
    fun refreshResourceConfig() {
        _state.update { currentState ->
            currentState.copy(
                resourceConfigRefreshTrigger = org.gemini.ui.forge.getCurrentTimeMillis()
            )
        }
    }

    /** 更新特定模块的资源绑定规范路径 */
    fun updateBlockResourceBinding(blockId: String, bindingPath: List<String>) {
        historyManager.saveSnapshot("更改资源绑定")
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) {
                    page.copy(blocks = page.blocks.updateBlockInList(blockId) { 
                        it.copy(resourceBindingPath = bindingPath)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    // --- 交互对话框及状态控制 ---

    /** 显示区域重塑对话框 */
    fun showVisualRefine(blockId: String?) {
        updateState { it.copy(showVisualRefine = true, refineTargetId = blockId) }
    }

    /** 隐藏区域重塑对话框 */
    fun hideVisualRefine() {
        updateState { it.copy(showVisualRefine = false, refineTargetId = null) }
    }

    /** 显示参考区域裁剪对话框 */
    fun showReferenceArea(blockId: String) {
        updateState { it.copy(showReferenceArea = true, referenceAreaTargetId = blockId) }
    }

    /** 隐藏参考区域裁剪对话框 */
    fun hideReferenceArea() {
        updateState { it.copy(showReferenceArea = false, referenceAreaTargetId = null) }
    }

    /** 异步加载历史生成图片并显示历史对话框 */
    fun showHistoricalDialog(blockId: String) {
        viewModelScope.launch {
            val images = assetManager.loadHistoricalImages(blockId)
            updateState { it.copy(showHistoricalDialog = true, historicalImages = images, historicalTargetBlockId = blockId) }
        }
    }

    /** 隐藏历史对话框 */
    fun hideHistoricalDialog() {
        updateState { it.copy(showHistoricalDialog = false, historicalImages = emptyList(), historicalTargetBlockId = null) }
    }

    /** 显示删除确认对话框 */
    fun showDeleteConfirmation(blockId: String) {
        updateState { it.copy(showDeleteBlockConfirmation = true, pendingDeleteBlockId = blockId) }
    }

    /** 隐藏删除确认对话框 */
    fun hideDeleteConfirmation() {
        updateState { it.copy(showDeleteBlockConfirmation = false, pendingDeleteBlockId = null) }
    }
}

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
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.findBlockById
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
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.AppLogger

/**
 * 统一工作区 ViewModel。
 * 组合了布局编辑、资产生成、资产管理、历史记录以及快捷键核心逻辑。
 * 所有的具体实现逻辑均已剥离至对应的 Delegate 委托类中。
 */
class ProjectWorkspaceViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    val templateRepo: TemplateRepository,
    val cloudAssetManager: CloudAssetManager,
    val aiService: AIGenerationService,
    private val onDirtyChanged: (Boolean) -> Unit = {}
) : ViewModel() {

    val storage get() = templateRepo.fileStorage

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
        updateState = { updateState(it) },
        markDirty = { markDirty() }
    )

    /** 2. 资产生成 */
    val assetGen = AssetGenerationDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        getState = { _state.value },
        updateState = { updateState(it) },
        notifySelectionHandled = { /* 内部逻辑已通过 AssetManager 同步 */ }
    )

    /** 3. 布局编辑 */
    val layoutEditor = LayoutEditorDelegate(
        scope = viewModelScope,
        aiService = aiService,
        templateRepo = templateRepo,
        cloudAssetManager = cloudAssetManager,
        getState = { _state.value },
        updateState = { updateState(it) },
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
        updateState = { updateState(it) },
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
            val currentProject = _state.value.project
            val initialRefUri = currentProject.styleReferenceUri
                ?: currentProject.referenceImages.firstOrNull()
                ?: currentProject.pages.firstOrNull()?.sourceImageUri

            _state.update {
                it.copy(
                    defaultRefineInstructionUpdate = updateInstruction,
                    defaultRefineInstructionNew = newInstruction,
                    collapsedSections = wsConfig?.collapsedSections ?: emptyMap(),
                    isVisualMode = wsConfig?.isVisualMode ?: false,
                    isHideOutlines = wsConfig?.isHideOutlines ?: false,
                    referenceMode = wsConfig?.referenceMode ?: if (initialRefUri != null) org.gemini.ui.forge.model.app.ReferenceDisplayMode.OVERLAY else org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
                    referenceOpacity = wsConfig?.referenceOpacity ?: 0.4f,
                    referenceImageUri = initialRefUri,
                    resourceConfigPath = wsConfig?.resourceConfigPath
                )
            }
            // ★ 内存级物理存在性清洗：若参考图文件物理不存在则在内存中置空，绝不主动写磁盘
            cleanMissingReferenceImagesInMemory()
        }
    }

    /**
     * 内存安全校验与清洗：检查所有页面的 blocks 的 referenceImage 物理文件是否存在，
     * 若文件不存在则仅在内存中置为 null，绝不主动调用 saveTemplate 写磁盘。
     */
    private suspend fun cleanMissingReferenceImagesInMemory() {
        try {
            var hasCleaned = false
            val currentProj = _state.value.project
            val updatedPages = currentProj.pages.map { page ->
                var pageModified = false
                suspend fun cleanBlock(block: UIBlock): UIBlock {
                    val ref = block.referenceImage
                    val validRef = if (ref != null && ref.relativePath.isNotBlank()) {
                        if (isFileExists(ref.getAbsolutePath())) ref else null
                    } else null

                    if (validRef != ref) {
                        pageModified = true
                        hasCleaned = true
                    }
                    val cleanedChildren = block.children.map { cleanBlock(it) }
                    return block.copy(referenceImage = validRef, children = cleanedChildren)
                }

                val cleanedBlocks = page.blocks.map { cleanBlock(it) }
                if (pageModified) page.copy(blocks = cleanedBlocks) else page
            }

            val boundPages = updatedPages.map { page ->
                page.copy(blocks = page.blocks.bindParents())
            }
            _state.update { it.copy(project = it.project.copy(pages = boundPages)) }
            if (hasCleaned) {
                AppLogger.i("ProjectWorkspaceVM", "已完成内存级参考图物理校验：已清理不存在的失效 referenceImage 引用（未主动写入磁盘）")
            }
        } catch (e: Exception) {
            AppLogger.w("ProjectWorkspaceVM", "内存级参考图物理校验异常", e)
        }
    }

    /** 重新加载项目数据 */
    fun reload(newProject: ProjectState) {
        val effectiveRefUri = newProject.styleReferenceUri
            ?: newProject.referenceImages.firstOrNull()
            ?: newProject.pages.firstOrNull()?.sourceImageUri

        newProject.pages.forEach { it.blocks.bindParents() }

        viewModelScope.launch {
            val wsConfig = templateRepo.loadWorkspaceConfig(_state.value.projectName)
            _state.update { current ->
                val targetPageId = newProject.pages.find { it.id == current.selectedPageId }?.id
                    ?: newProject.pages.firstOrNull()?.id
                val keepGroupId = if (current.editingGroupId != null && newProject.pages.any { p -> p.blocks.findBlockById(
                        current.editingGroupId
                    ) != null }) current.editingGroupId else null
                current.copy(
                    project = newProject,
                    selectedPageId = targetPageId,
                    selectedBlockId = if (newProject.pages.any { p -> p.blocks.any { b -> b.id == current.selectedBlockId } }) current.selectedBlockId else null,
                    editingGroupId = keepGroupId,
                    globalStyle = newProject.globalStyle,
                    referenceImageUri = effectiveRefUri,
                    collapsedSections = wsConfig?.collapsedSections ?: current.collapsedSections,
                    isVisualMode = wsConfig?.isVisualMode ?: current.isVisualMode,
                    isHideOutlines = wsConfig?.isHideOutlines ?: current.isHideOutlines,
                    referenceMode = wsConfig?.referenceMode ?: if (effectiveRefUri != null) org.gemini.ui.forge.model.app.ReferenceDisplayMode.OVERLAY else org.gemini.ui.forge.model.app.ReferenceDisplayMode.HIDDEN,
                    referenceOpacity = wsConfig?.referenceOpacity ?: current.referenceOpacity,
                    resourceConfigPath = wsConfig?.resourceConfigPath ?: current.resourceConfigPath
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

    /** 切换骨架网格与辅助线（二合一合并操作：开启时显示色块与边框，隐藏时进入100%纯净预览） */
    fun toggleWireframe() {
        val currentOn = !(_state.value.isVisualMode && _state.value.isHideOutlines)
        val nextOn = !currentOn
        _state.update {
            it.copy(
                isVisualMode = !nextOn,
                isHideOutlines = !nextOn
            )
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

    /** 切换所有从参考图切割显示的资源切片隐藏/显示 */
    fun toggleHideReferenceSlices() {
        _state.update { it.copy(isHideReferenceSlices = !it.isHideReferenceSlices) }
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
            val currentPage = currentState.currentPage
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) {
                    page.copy(blocks = page.blocks.updateBlockInList(blockId) { block ->
                        if (type == UIBlockType.BACKGROUND && block.parent == null && currentPage != null) {
                            // ★ 当根模块切换为背景类型时，自动将尺寸贴合为全屏画布大小
                            block.copy(
                                type = type,
                                bounds = SerialRect(0f, 0f, currentPage.width, currentPage.height),
                                cropRect = block.cropRect ?: SerialRect(0f, 0f, currentPage.width, currentPage.height)
                            )
                        } else {
                            block.copy(type = type)
                        }
                    })
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

    /** 处理模块双击：有子图层进入组编辑；子图层则自动切换进入直接父组编辑并高亮选中该子图层 */
    fun onBlockDoubleClicked(blockId: String) {
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val block = currentPage.blocks.findBlockById(blockId) ?: return@update currentState

            if (currentState.editingGroupId == blockId) {
                // 如果双击的是当前正在编辑的组本身，退回上一级父编辑组
                val parentId = currentPage.blocks.findParentBlockId(blockId)
                currentState.copy(
                    editingGroupId = parentId,
                    selectedBlockId = blockId,
                    selectedBlockIds = setOf(blockId)
                )
            } else if (block.children.isNotEmpty()) {
                // 如果双击的是包含子模块的容器，直接进入该容器的组编辑模式
                currentState.copy(
                    editingGroupId = blockId,
                    selectedBlockId = null,
                    selectedBlockIds = emptySet()
                )
            } else {
                // 如果双击的是一个子图层（自身无子节点），自动切入其直接父模块的编辑模式，并选中此子图层
                val parentId = currentPage.blocks.findParentBlockId(blockId)
                currentState.copy(
                    editingGroupId = parentId,
                    selectedBlockId = blockId,
                    selectedBlockIds = setOf(blockId)
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

    /** 针对历史生成记录中的某张图片，调用本地 Python/Rembg 引擎执行去背景并生成透明 PNG */
    fun removeBackgroundForHistoricalImage(targetBlockId: String, file: TemplateFile) {
        viewModelScope.launch {
            try {
                updateState { it.copy(isProcessingHistoricalBg = true) }
                org.gemini.ui.forge.utils.Toast.show("正在启动本地 AI 抠图引擎...", org.gemini.ui.forge.ui.component.ToastType.INFO)
                
                val bytes = file.readBytes() ?: throw Exception("无法读取原图数据")
                val noBgBytes = aiService.removeBackgroundLocal(bytes) ?: throw Exception("本地抠图未返回有效图像数据")
                
                val savedFile = templateRepo.saveBlockResource(
                    templateName = _state.value.projectName,
                    blockId = targetBlockId,
                    fileNamePrefix = "nobg",
                    bytes = noBgBytes,
                    isPng = true
                )
                
                val refreshedImages = assetManager.loadHistoricalImages(targetBlockId)
                updateState { it.copy(historicalImages = refreshedImages, isProcessingHistoricalBg = false) }
                org.gemini.ui.forge.utils.Toast.show("本地去背景成功！已生成透明 PNG 资产", org.gemini.ui.forge.ui.component.ToastType.SUCCESS)
            } catch (e: Exception) {
                updateState { it.copy(isProcessingHistoricalBg = false) }
                org.gemini.ui.forge.utils.AppLogger.e("ProjectWorkspaceVM", "历史资产去背景失败", e)
                org.gemini.ui.forge.utils.Toast.show("去背景失败: ${e.message}", org.gemini.ui.forge.ui.component.ToastType.ERROR)
            }
        }
    }

    /**
     * 智能吸附校准图元几何尺寸与物理坐标 (支持单模块、多选批量与全量页面图元)
     *
     * @param targetBlockIds 目标图元 ID 集合，为 null 时代表校正当前页面的全部图元
     * @param alsoCropAndBindReference 是否同步从原图物理裁切出切片并绑定为参考图 (默认 false，仅纠偏坐标大小)
     */
    fun calibrateBlocks(
        targetBlockIds: Set<String>? = null,
        alsoCropAndBindReference: Boolean = false,
        engineMode: org.gemini.ui.forge.service.detection.DetectionEngineMode? = null
    ) {
        val currentPage = state.value.currentPage ?: return
        val pageRefFile = currentPage.sourceImageUri
        val refPath = pageRefFile?.getAbsolutePath()
        if (refPath.isNullOrBlank()) {
            org.gemini.ui.forge.utils.Toast.show("当前页面未绑定设计参考原图，无法执行边缘吸附校准", org.gemini.ui.forge.ui.component.ToastType.ERROR)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val refBytes = org.gemini.ui.forge.utils.readLocalFileBytes(refPath)
            if (refBytes == null || refBytes.isEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    org.gemini.ui.forge.utils.Toast.show("未能读取页面参考原图文件: $refPath", org.gemini.ui.forge.ui.component.ToastType.ERROR)
                }
                return@launch
            }

            // ★ 核心铁律：强制先执行 bindParents()，打通所有嵌套子图元的父级引用链条，确保 toAbsoluteBounds() 绝对正确！
            val boundBlocks = currentPage.blocks.bindParents()
            val boundPage = currentPage.copy(blocks = boundBlocks)

            val pageW = boundPage.width
            val pageH = boundPage.height
            val alignMode = engineMode ?: state.value.activeAlignmentMode
            val aligner = org.gemini.ui.forge.service.detection.DetectionEngineRegistry.getAligner(alignMode)
            var calibratedCount = 0
            var unchangedCount = 0
            var croppedCount = 0

            // 自动展开目标集合：若选中的是复合组，自动将其所有深层子孙节点全部展开纳入待校准集合！
            val effectiveTargetIds: Set<String>? = if (targetBlockIds != null) {
                val expanded = mutableSetOf<String>()
                fun collectDescendants(b: UIBlock) {
                    expanded.add(b.id)
                    b.children.forEach { collectDescendants(it) }
                }
                boundBlocks.forEach { root ->
                    fun scan(b: UIBlock) {
                        if (targetBlockIds.contains(b.id)) {
                            collectDescendants(b)
                        } else {
                            b.children.forEach { scan(it) }
                        }
                    }
                    scan(root)
                }
                expanded
            } else null

            suspend fun calibrateRecursive(block: UIBlock): UIBlock {
                // 1. 先自底向上递归校准所有子节点
                val newChildren = block.children.map { calibrateRecursive(it) }
                val blockWithCalibratedChildren = block.copy(children = newChildren)

                val shouldCalibrate = effectiveTargetIds == null || effectiveTargetIds.contains(block.id)
                if (!shouldCalibrate) {
                    return blockWithCalibratedChildren
                }

                // 2. 复合组容器模式：若包含子组件，不再作为单体盲目边缘吸附，而是直接触发容器自适应贴合与原点归零！
                if (blockWithCalibratedChildren.children.isNotEmpty()) {
                    val normalizedGroup = org.gemini.ui.forge.utils.UIBlockLayoutNormalizer.normalizeContainerAndChildren(blockWithCalibratedChildren)
                    calibratedCount++
                    org.gemini.ui.forge.utils.AppLogger.i("Calibrate", "🧩 复合模块组【${block.id}】完成子组件深层物理吸附与容器原点贴合归零 (代数守恒)")
                    return normalizedGroup
                }

                // 3. 叶子图元：执行精准物理边缘微观吸附
                val absBounds = blockWithCalibratedChildren.toAbsoluteBounds()
                val snapRes = aligner.align(
                    imageBytes = refBytes,
                    logicalBounds = absBounds,
                    blockType = blockWithCalibratedChildren.type,
                    canvasWidth = pageW,
                    canvasHeight = pageH
                )

                val newLocalBounds = if (snapRes != null) {
                    val local = blockWithCalibratedChildren.toLocalBounds(snapRes.logicalRect)
                    val isChanged = local != blockWithCalibratedChildren.bounds
                    if (isChanged) {
                        calibratedCount++
                        org.gemini.ui.forge.utils.AppLogger.i(
                            "Calibrate",
                            "📐 校准图元 [${block.id}]: 原局部[${block.bounds.left.toInt()}, ${block.bounds.top.toInt()}, ${block.bounds.width.toInt()}x${block.bounds.height.toInt()}] -> 新局部[${local.left.toInt()}, ${local.top.toInt()}, ${local.width.toInt()}x${local.height.toInt()}] (物理偏移: ΔX=${snapRes.deltaX.toInt()}px, ΔY=${snapRes.deltaY.toInt()}px, 尺寸变化: ΔW=${snapRes.deltaW.toInt()}px, ΔH=${snapRes.deltaH.toInt()}px)"
                        )
                    } else {
                        unchangedCount++
                        org.gemini.ui.forge.utils.AppLogger.d("Calibrate", "图元 [${block.id}] 已吻合物理边缘")
                    }
                    local
                } else {
                    unchangedCount++
                    blockWithCalibratedChildren.bounds
                }

                val newRefImage = if (alsoCropAndBindReference) {
                    val cropBytes = org.gemini.ui.forge.utils.SmartEdgeSnapper.cropSnappedComponent(
                        imageBytes = refBytes,
                        logicalBounds = absBounds,
                        canvasWidth = pageW,
                        canvasHeight = pageH
                    )
                    if (cropBytes != null) {
                        croppedCount++
                        templateRepo.saveBlockResource(
                            templateName = state.value.projectName,
                            blockId = block.id,
                            fileNamePrefix = "ref_snap",
                            bytes = cropBytes,
                            isPng = true
                        )
                    } else {
                        block.referenceImage
                    }
                } else {
                    block.referenceImage
                }

                val updatedCropRect = snapRes?.logicalRect ?: blockWithCalibratedChildren.cropRect ?: blockWithCalibratedChildren.toAbsoluteBounds()
                return blockWithCalibratedChildren.copy(
                    bounds = newLocalBounds,
                    cropRect = updatedCropRect,
                    referenceImage = newRefImage
                )
            }

            val newBlocks = boundPage.blocks.map { calibrateRecursive(it) }.bindParents()
            val updatedPage = boundPage.copy(blocks = newBlocks)

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateState { s ->
                    val newPages = s.project.pages.map { p ->
                        if (p.id == updatedPage.id) updatedPage else p
                    }
                    s.copy(project = s.project.copy(pages = newPages))
                }
                markDirty()
                val summaryMsg = if (alsoCropAndBindReference) {
                    "已校准 $calibratedCount 个图元坐标 (已吻合 $unchangedCount 个)，并同步切片更新了 $croppedCount 个参考图"
                } else {
                    "已成功校验并校准 $calibratedCount 个图元物理范围与坐标 (已吻合 $unchangedCount 个，未切图)"
                }
                org.gemini.ui.forge.utils.Toast.show(summaryMsg, org.gemini.ui.forge.ui.component.ToastType.SUCCESS)
            }
        }
    }

    fun calibrateSelectedBlock(
        alsoCropAndBindReference: Boolean = false,
        engineMode: org.gemini.ui.forge.service.detection.DetectionEngineMode? = null
    ) {
        val currentId = state.value.selectedBlockId ?: return
        calibrateBlocks(setOf(currentId), alsoCropAndBindReference, engineMode)
    }

    fun calibrateMultiSelectedBlocks(
        alsoCropAndBindReference: Boolean = false,
        engineMode: org.gemini.ui.forge.service.detection.DetectionEngineMode? = null
    ) {
        val ids = state.value.selectedBlockIds
        if (ids.isEmpty()) return
        calibrateBlocks(ids, alsoCropAndBindReference, engineMode)
    }

    fun calibrateAllBlocks(
        alsoCropAndBindReference: Boolean = false,
        engineMode: org.gemini.ui.forge.service.detection.DetectionEngineMode? = null
    ) {
        calibrateBlocks(null, alsoCropAndBindReference, engineMode)
    }

    /** 切换当前的对齐引擎模式 (微观吸附 / 传统CV / 端侧AI) */
    fun setActiveAlignmentMode(mode: org.gemini.ui.forge.service.detection.DetectionEngineMode) {
        updateState { it.copy(activeAlignmentMode = mode) }
    }

    /** 切换纯工程物理对齐模式 (为 true 时自适应隐藏提示词等 AI 概念) */
    fun togglePureEngineeringMode() {
        updateState { it.copy(isPureEngineeringMode = !it.isPureEngineeringMode) }
    }

    /** 切换工作区中间渲染区域的视图显示模式 (画布舞台 vs JSON 源码) */
    fun setWorkspaceViewMode(mode: org.gemini.ui.forge.state.WorkspaceViewMode) {
        updateState { it.copy(workspaceViewMode = mode) }
    }
}

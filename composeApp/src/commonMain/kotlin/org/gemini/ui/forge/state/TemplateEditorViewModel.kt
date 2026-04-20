package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.utils.*
import kotlin.collections.ArrayDeque

/**
 * 布局编辑器的专属 ViewModel。
 * 负责管理当前正在编辑的模板项目状态、处理 UI 块的增删改查、撤销重做逻辑，以及与 AI 服务的交互。
 * 生命周期：该 ViewModel 的生命周期随 TemplateEditorScreen 绑定。当退出该 Screen 时，ViewModel 将被销毁，自动释放网络请求、撤销栈等资源。
 */
class TemplateEditorViewModel(
    initialProject: ProjectState,
    initialProjectName: String,
    initialLang: PromptLanguage,
    private val templateRepo: TemplateRepository,
    private val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService
) : ViewModel() {

    // 内部可变状态流，包含当前编辑的页面、块等信息
    private val _state = MutableStateFlow(
        TemplateEditorState(
            project = initialProject,
            projectName = initialProjectName,
            selectedPageId = initialProject.pages.firstOrNull()?.id,
            currentLang = initialLang
        )
    )

    /**
     * 对外暴露的不可变状态流，供 Compose 监听
     */
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    fun switchLang(lang: PromptLanguage) {
        _state.update { it.copy(currentLang = lang) }
    }

    // 追踪当前的 AI 生成任务，以便在需要时进行取消
    private var currentGenJob: kotlinx.coroutines.Job? = null

    // 历史操作栈，用于实现撤销(Undo)与重做(Redo)功能。最大保留50步记录。
    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

    init {
        // 初始化时异步拉取默认的 AI 重塑提示词
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

    /** 强制从外部重载项目状态（解决 ViewModel 缓存导致旧状态残留的问题） */
    fun reload(newProject: ProjectState) {
        if (_state.value.project == newProject) return
        _state.update { 
            it.copy(
                project = newProject,
                selectedPageId = newProject.pages.firstOrNull()?.id,
                selectedBlockId = null,
                editingGroupId = null
            ) 
        }
        undoStack.clear()
        redoStack.clear()
    }

    // ==========================================
    // 撤销与重做逻辑 (Undo / Redo)
    // ==========================================

    /**
     * 记录当前状态快照。
     * 在每次可能修改项目结构的操作（如移动、添加、删除、重命名块）之前调用。
     */
    fun saveSnapshot() {
        val currentProject = _state.value.project
        // 避免记录重复的状态
        if (undoStack.lastOrNull() != currentProject) {
            undoStack.addLast(currentProject)
            // 限制栈最大深度为 50，防止内存泄漏
            if (undoStack.size > 50) undoStack.removeFirst()
            // 产生新操作时，清空重做栈
            redoStack.clear()
        }
    }

    /**
     * 执行撤销操作：回退到上一个状态。
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _state.value.project
            redoStack.addLast(currentState) // 将当前状态压入重做栈
            val previousState = undoStack.removeLast()
            _state.update { it.copy(project = previousState) }
        }
    }

    /**
     * 执行重做操作：恢复撤销的状态。
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _state.value.project
            undoStack.addLast(currentState) // 将当前状态重新压入撤销栈
            val nextState = redoStack.removeLast()
            _state.update { it.copy(project = nextState) }
        }
    }

    // ==========================================
    // 页面与组件块交互逻辑 (Page & Block Operations)
    // ==========================================

    /** 选择指定的页面，同时清空选中的块和组编辑状态 */
    fun onPageSelected(pageId: String) =
        _state.update { it.copy(selectedPageId = pageId, selectedBlockId = null, editingGroupId = null) }

    /** 选中指定的 UI 块。如果再次点击已选中的块，则取消选中 */
    fun onBlockClicked(blockId: String?) {
        _state.update { currentState ->
            val newSelectedId = if (blockId == null) null else if (currentState.selectedBlockId == blockId) null else blockId
            
            // 打印模块详细信息日志
            if (newSelectedId != null) {
                val block = currentState.project.pages.flatMap { it.blocks }.let { findBlockById(it, newSelectedId) }
                if (block != null) {
                    AppLogger.d("TemplateEditorViewModel", "👆 选中模块: ID=[${block.id}], 类型=${block.type}, 坐标=(L:${block.bounds.left}, T:${block.bounds.top}, W:${block.bounds.width}, H:${block.bounds.height}), 隐藏=${!block.isVisible}")
                    
                    val uri = block.currentImageUri
                    if (!uri.isNullOrBlank()) {
                        viewModelScope.launch {
                            try {
                                val bitmap = uri.decodeBase64ToBitmap()
                                if (bitmap != null) {
                                    val scaleX = block.bounds.width / bitmap.width
                                    val scaleY = block.bounds.height / bitmap.height
                                    val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                                    val targetRatio = block.bounds.width / block.bounds.height
                                    AppLogger.d("TemplateEditorViewModel", "🖼️ 图片追踪: 原生像素=${bitmap.width}x${bitmap.height} (比例 $imageRatio)")
                                    AppLogger.d("TemplateEditorViewModel", "🖼️ 容器追踪: 目标容器=${block.bounds.width}x${block.bounds.height} (比例 $targetRatio)")
                                    AppLogger.d("TemplateEditorViewModel", "📏 拉伸比例: X轴缩放=$scaleX, Y轴缩放=$scaleY")
                                }
                            } catch (e: Exception) {
                                AppLogger.e("TemplateEditorViewModel", "图片解析追踪异常", e)
                            }
                        }
                    }
                }
            } else {
                AppLogger.d("TemplateEditorViewModel", "👆 取消选中模块 (点击空白处或反选)")
            }
            
            currentState.copy(selectedBlockId = newSelectedId)
        }
    }

    /** 
     * 双击事件：如果是容器类型的块（拥有子节点），双击则进入该组的局部编辑模式。
     */
    fun onBlockDoubleClicked(blockId: String) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) }
        if (block != null && block.children.isNotEmpty()) {
            _state.update { it.copy(editingGroupId = blockId, selectedBlockId = null) }
        }
    }

    /** 退出当前的组局部编辑模式 */
    fun exitGroupEditMode() {
        _state.update { it.copy(editingGroupId = null) }
    }

    /** 修改块的唯一标识符 (ID) */
    fun renameBlock(oldId: String, newId: String) {
        if (oldId == newId || newId.isBlank()) return
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, oldId) { block -> block.copy(id = newId) })
                } else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                selectedBlockId = if (currentState.selectedBlockId == oldId) newId else currentState.selectedBlockId,
                editingGroupId = if (currentState.editingGroupId == oldId) newId else currentState.editingGroupId
            )
        }
    }

    /** 更新块的物理坐标和尺寸 */
    fun updateBlockBounds(blockId: String, left: Float, top: Float, right: Float, bottom: Float) {
        val pageId = _state.value.selectedPageId ?: return
        AppLogger.d("TemplateEditorViewModel", "📏 更新模块 [$blockId] 边界 -> L:$left, T:$top, R:$right, B:$bottom | 实际宽高: W=${right - left}, H=${bottom - top}")
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

    /** 更改指定块的组件类型 (例如从 IMAGE 改为 BUTTON) */
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

    /**
     * 在当前页面（或当前组编辑的容器内）添加一个新的组件块。
     */
    fun addBlock(type: UIBlockType) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val newBlockId = "block_${getCurrentTimeMillis()}"
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId
            val width = 400f;
            val height = 300f
            // 默认放在画布或组容器的中心
            var left = (currentPage.width - width) / 2f
            var top = (currentPage.height - height) / 2f

            if (editingGroupId != null) {
                findBlockById(currentPage.blocks, editingGroupId)?.let { group ->
                    left = (group.bounds.width - width) / 2f
                    top = (group.bounds.height - height) / 2f
                }
            }
            val newBlock = UIBlock(newBlockId, type, SerialRect(left, top, left + width, top + height))
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    // 如果处于组编辑模式，将新块添加至组的 children 中
                    if (editingGroupId != null) {
                        page.copy(
                            blocks = updateBlockInList(
                                page.blocks,
                                editingGroupId
                            ) { group -> group.copy(children = group.children + newBlock) })
                    } else page.copy(blocks = page.blocks + newBlock)
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = newBlockId)
        }
    }

    /** 手动添加一个自定义图层块 */
    fun addCustomBlock(id: String, type: UIBlockType, w: Float, h: Float) {
        saveSnapshot()
        val pageId = _state.value.selectedPageId ?: return
        val finalId = if (id.isBlank()) "block_${getCurrentTimeMillis()}" else id
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val editingGroupId = currentState.editingGroupId

            var left = (currentPage.width - w) / 2f
            var top = (currentPage.height - h) / 2f
            if (editingGroupId != null) {
                findBlockById(currentPage.blocks, editingGroupId)?.let { group ->
                    left = (group.bounds.width - w) / 2f
                    top = (group.bounds.height - h) / 2f
                }
            }

            val newBlock = UIBlock(finalId, type, SerialRect(left, top, left + w, top + h))

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    if (editingGroupId != null) {
                        page.copy(blocks = updateBlockInList(page.blocks, editingGroupId) { group -> group.copy(children = group.children + newBlock) })
                    } else {
                        page.copy(blocks = page.blocks + newBlock)
                    }
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages), selectedBlockId = finalId)
        }
    }

    /** 切换指定块的可见性 */
    fun toggleBlockVisibility(blockId: String, isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(isVisible = isVisible)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    /** 切换当前页面所有块的可见性 */
    fun toggleAllBlocksVisibility(isVisible: Boolean) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            fun updateVis(list: List<UIBlock>): List<UIBlock> = list.map { it.copy(isVisible = isVisible, children = updateVis(it.children)) }

            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateVis(page.blocks))
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    /** 删除指定的块，同时级联删除它的所有子节点 */
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

    /** 处理画布拖拽：按偏移量相对移动块的位置 */
    fun moveBlockBy(blockId: String, dx: Float, dy: Float) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        block.copy(
                            bounds = block.bounds.copy(
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

    /** 
     * 在图层树面板中移动块，处理拖拽改变节点层级（成为子节点或兄弟节点）。
     */
    fun moveBlock(draggedBlockId: String, targetId: String?, dropPosition: DropPosition = DropPosition.INSIDE) {
        if (draggedBlockId == targetId) return
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val currentPage = currentState.currentPage ?: return@update currentState
            val draggedBlock = findBlockById(currentPage.blocks, draggedBlockId) ?: return@update currentState
            // 防止陷入循环引用：不能将块移动到自己的子孙节点内
            if (targetId != null && isDescendantOfBlock(targetId, draggedBlock)) return@update currentState

            // 计算被拖拽块的绝对坐标，确保移动层级后视觉位置尽量不变
            val draggedAbsBounds = getAbsoluteBounds(currentPage.blocks, draggedBlockId) ?: draggedBlock.bounds
            val newBlocks = removeBlockRecursive(currentPage.blocks, draggedBlockId)

            // 确定最终插入的父节点
            val actualParentId =
                if (targetId == null) null else if (dropPosition == DropPosition.INSIDE) targetId else getParentIdOf(
                    currentPage.blocks,
                    targetId
                )
            val targetAbsBounds = if (actualParentId != null) getAbsoluteBounds(newBlocks, actualParentId) else null

            // 转换新的相对坐标
            val newRelativeBounds = if (targetAbsBounds != null) SerialRect(
                left = draggedAbsBounds.left - targetAbsBounds.left,
                top = draggedAbsBounds.top - targetAbsBounds.top,
                right = (draggedAbsBounds.left - targetAbsBounds.left) + draggedAbsBounds.width,
                bottom = (draggedAbsBounds.top - targetAbsBounds.top) + draggedAbsBounds.height
            ) else draggedAbsBounds
            val updatedDraggedBlock = draggedBlock.copy(bounds = newRelativeBounds)

            val resultBlocks = if (targetId == null) newBlocks + updatedDraggedBlock
            else if (dropPosition == DropPosition.INSIDE) updateBlockInList(
                newBlocks,
                targetId
            ) { it.copy(children = it.children + updatedDraggedBlock) }
            else insertBlockSibling(newBlocks, targetId, updatedDraggedBlock, dropPosition)

            saveSnapshot()
            val updatedPages =
                currentState.project.pages.map { if (it.id == pageId) it.copy(blocks = resultBlocks) else it }
            currentState.copy(
                project = currentState.project.copy(
                    pages = updatedPages,
                    createdAt = getCurrentTimeMillis()
                )
            )
        }
    }

    // ==========================================
    // AI 重塑与生成逻辑 (AI Refine & Optimization)
    // ==========================================

    /**
     * 区域重塑功能。利用原图的指定区域坐标向 AI 发送重绘请求。
     * @param blockId 指定要重塑的具体块的 ID；若为 null，则表示自定义绘制区域提取
     */
    fun onRefineArea(
        blockId: String?,
        bounds: SerialRect,
        userInstruction: String,
        apiKey: String,
        onComplete: (Boolean) -> Unit
    ) {
        val currentState = _state.value
        val currentPage = currentState.currentPage
        val originalImage = currentPage?.sourceImageUri ?: return
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            try {
                _state.update { it.copy(isGenerating = true, showAITaskDialog = true, generationLogs = emptyList()) }
                val logger = { msg: String -> addGenLog(msg) }
                // 1. 根据坐标和原图尺寸进行裁剪
                val croppedBytes = cropImage(
                    imageSource = originalImage,
                    bounds = bounds,
                    logicalWidth = currentPage.width,
                    logicalHeight = currentPage.height
                ) ?: throw Exception("裁剪失败")
                val originalBytes = readLocalFileBytes(originalImage) ?: throw Exception("无法读取原图")
                val fingerprint = originalBytes.calculateMd5()

                // 2. 检查云端资产库是否已缓存原图
                var originalFileUri =
                    cloudAssetManager.assets.value.find { it.displayName?.contains(fingerprint) == true && it.state == "ACTIVE" }?.uri
                        ?: ""
                if (originalFileUri.isBlank()) {
                    originalFileUri = cloudAssetManager.getOrUploadFile(
                        originalImage.substringAfterLast("/"),
                        originalBytes,
                        getMimeType(originalImage)
                    ) { _, status -> logger("[$status]") } ?: ""
                }

                // 3. 将当前 JSON 状态传递给 AI，执行重塑
                val currentJson = Json.encodeToString(ProjectState.serializer(), currentState.project)
                val updatedProject = aiService.refineAreaForTemplate(
                    originalImageUri = originalFileUri,
                    croppedBytes = croppedBytes,
                    currentJson = currentJson,
                    userInstruction = userInstruction,
                    apiKey = apiKey,
                    onLog = logger
                )

                _state.update { it.copy(project = updatedProject) }
                templateRepo.saveTemplate(currentState.projectName, updatedProject)
                onComplete(true)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    addGenLog("❌ 错误: ${e.message}"); onComplete(false)
                }
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    /**
     * 对用户提示词进行 AI 自动优化与润色。
     */
    fun optimizePrompt(blockId: String, apiKey: String, currentLang: PromptLanguage) {
        val block = _state.value.project.pages.flatMap { it.blocks }.let { findBlockById(it, blockId) } ?: return
        val textToOptimize = if (currentLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
        if (textToOptimize.isBlank()) return
        currentGenJob?.cancel()
        currentGenJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationLogs = emptyList(), showAITaskDialog = true) }
            try {
                // 根据当前语言获取相应的系统指令设定
                val systemInstruction = if (currentLang == PromptLanguage.EN) aiService.promptManager.getPrompt("optimize_instruction_en") else aiService.promptManager.getPrompt("optimize_instruction_zh")
                addGenLog(">>> 正在使用 AI 优化提示词 (${currentLang.displayName})...")
                val optimized = aiService.optimizePrompt(systemInstruction + textToOptimize, apiKey, 3)
                addGenLog(">>> 优化完成！")
                onUserPromptChanged(blockId, optimized, currentLang)
            } catch (e: Exception) {
                addGenLog(">>> 优化失败: ${e.message} <<<")
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    /** 响应用户在属性面板手动编辑提示词 */
    fun onUserPromptChanged(blockId: String, newPrompt: String, lang: PromptLanguage) {
        val pageId = _state.value.selectedPageId ?: return
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) {
                    page.copy(blocks = updateBlockInList(page.blocks, blockId) { block ->
                        if (lang == PromptLanguage.EN) block.copy(userPromptEn = newPrompt) else block.copy(userPromptZh = newPrompt)
                    })
                } else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    /** 更新当前页面的尺寸属性 */
    fun updatePageSize(newWidth: Float, newHeight: Float) {
        val pageId = _state.value.selectedPageId ?: return
        saveSnapshot()
        _state.update { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(width = newWidth, height = newHeight) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
    }

    /** 更新舞台的临时背景颜色 */
    fun updateStageBackgroundColor(colorHex: String) {
        _state.update { it.copy(stageBackgroundColor = colorHex) }
    }

    // ==========================================
    // 内部辅助方法 (Internal Helpers)
    // ==========================================

    private fun addGenLog(msg: String) {
        _state.update {
            it.copy(generationLogs = it.generationLogs + msg, showAITaskDialog = true)
        }
    }

    fun closeAITaskDialog() {
        _state.update {
            it.copy(showAITaskDialog = false, generationLogs = emptyList())
        }
    }

    fun cancelAITask() {
        currentGenJob?.cancel();
        currentGenJob = null;
        _state.update {
            it.copy(isGenerating = false)
        }
    }

    /** 深度优先递归查找对应 ID 的块 */
    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block;
            findBlockById(block.children, id)
        }
        return null
    }

    /** 递归遍历结构，对指定 ID 的节点执行转换函数 */
    private fun updateBlockInList(
        blocks: List<UIBlock>,
        blockId: String,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { block ->
            if (block.id == blockId) transform(block)
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children)
                    block.copy(children = newChildren)
                else block
            }
        }
    }

    /** 递归过滤删除指定的块 */
    private fun removeBlockRecursive(blocks: List<UIBlock>, idToRemove: String): List<UIBlock> {
        return blocks.filterNot { it.id == idToRemove }
            .map {
                it.copy(children = removeBlockRecursive(it.children, idToRemove))
            }
    }

    /** 计算多级嵌套节点在画布上的绝对坐标 */
    private fun getAbsoluteBounds(
        blocks: List<UIBlock>,
        targetId: String,
        currentOffsetX: Float = 0f,
        currentOffsetY: Float = 0f
    ): SerialRect? {
        for (block in blocks) {
            val absL = currentOffsetX + block.bounds.left;
            val absT = currentOffsetY + block.bounds.top
            if (block.id == targetId)
                return SerialRect(
                    absL,
                    absT,
                    absL + block.bounds.width,
                    absT + block.bounds.height
                )
            getAbsoluteBounds(block.children, targetId, absL, absT)
        }
        return null
    }

    /** 判断一个块是否是另一个块的子节点（防循环引用） */
    private fun isDescendantOfBlock(targetId: String, currentBlock: UIBlock): Boolean =
        currentBlock.id == targetId || currentBlock.children.any { isDescendantOfBlock(targetId, it) }

    /** 获取节点的父节点 ID */
    private fun getParentIdOf(blocks: List<UIBlock>, targetId: String, currentParentId: String? = null): String? {
        for (block in blocks) {
            if (block.id == targetId) return currentParentId
            getParentIdOf(block.children, targetId, block.id)
        }
        return null
    }

    /** 作为兄弟节点插入到指定目标节点之前或之后 */
    private fun insertBlockSibling(
        blocks: List<UIBlock>,
        targetId: String,
        blockToInsert: UIBlock,
        position: DropPosition
    ): List<UIBlock> {
        val index = blocks.indexOfFirst { it.id == targetId }
        if (index != -1) {
            val result = blocks.toMutableList()
            if (position == DropPosition.BEFORE)
                result.add(index, blockToInsert)
            else result.add(
                index + 1,
                blockToInsert
            )
            return result
        }
        return blocks.map { b ->
            val newChildren = insertBlockSibling(b.children, targetId, blockToInsert, position)
            if (newChildren !== b.children) b.copy(children = newChildren) else b
        }
    }
}

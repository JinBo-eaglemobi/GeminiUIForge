package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.DragRemoveLastChildContext
import org.gemini.ui.forge.ui.uistudio.UIStudioHistoryEntry
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.calculateBlockAbsoluteBounds
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.findParentBlockId
import org.gemini.ui.forge.utils.updateBlockInList
import kotlin.random.Random

/**
 * 画布编辑领域委托：承载图元树递归工具（internal 供视口域注入复用）、
 * 图元选择与层级门禁纠偏、锁定/可见性/成组、撤销重做历史快照栈、
 * 剪贴板（复制/剪切/粘贴/克隆/微调）、图元几何变换与层级重排（置顶置底/上下移层）、
 * 父容器嵌套移动（含末子组件移出确认与容器解散）与图元重命名等画布核心编辑操作。
 */
class CanvasEditorDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
) {
    /** 状态桥接属性：使平移方法体内 `state = state.copy(...)` 零改动生效 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /**
     * 递归遍历并更新模块树中的目标图元。
     */
    internal fun updateBlockInTreeRecursively(
        blocks: List<UIStudioBlock>,
        targetId: String,
        transform: (UIStudioBlock) -> UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else if (b.children.isNotEmpty()) {
                b.copy(children = updateBlockInTreeRecursively(b.children, targetId, transform))
            } else {
                b
            }
        }
    }

    /**
     * 递归遍历整棵模块树并统一变换所有图元。
     */
    internal fun mapAllBlocksRecursively(
        blocks: List<UIStudioBlock>,
        transform: (UIStudioBlock) -> UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            val updated = transform(b)
            if (updated.children.isNotEmpty()) {
                updated.copy(children = mapAllBlocksRecursively(updated.children, transform))
            } else {
                updated
            }
        }
    }

    /**
     * 切换指定组件/图层的锁定状态（递归支持容器内的子图元）。
     */
    fun toggleBlockLock(blockId: String, repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(currentV.blocks, blockId) { b ->
            val newLock = !b.isLocked
            // 若为容器，级联锁定全部子项
            if (b.children.isNotEmpty()) {
                b.copy(isLocked = newLock, children = mapAllBlocksRecursively(b.children) { it.copy(isLocked = newLock) })
            } else {
                b.copy(isLocked = newLock)
            }
        }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
    }

    /**
     * 一键锁定/解锁当前视图的所有组件（全层级递归）：
     * 若存在任一未锁定的组件，则将全部组件锁定；
     * 若所有组件均已锁定，则全部解锁。
     */
    fun toggleAllBlocksLocked(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyUnlocked(list: List<UIStudioBlock>): Boolean = list.any { !it.isLocked || hasAnyUnlocked(it.children) }
        val targetLock = hasAnyUnlocked(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isLocked = targetLock) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetLock) "已锁定当前视图全部组件" else "已解锁当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 一键显示/隐藏当前视图的所有组件（全层级递归）：
     * 若存在任一隐藏的组件，则将全部组件设为显示；
     * 若所有组件均已显示，则全部隐藏。
     */
    fun toggleAllBlocksVisibility(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyHidden(list: List<UIStudioBlock>): Boolean = list.any { !it.isVisible || hasAnyHidden(it.children) }
        val targetVisible = hasAnyHidden(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isVisible = targetVisible) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetVisible) "已显示当前视图全部组件" else "已隐藏当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 快捷发起将两个组件合并至新建纯父容器的确认弹窗。
     */
    fun groupBlocksIntoContainer(draggedId: String, targetId: String) {
        state = state.copy(pendingCreateContainer = draggedId to targetId)
    }

    /**
     * 选中或取消选中指定图元。
     * 若处于全局主视图模式且选中的是父容器内部子组件，根据层级门禁规则强制纠偏选中其根父组件。
     *
     * @param blockId 目标图元 ID，传入 null 表示点击空白处取消选中
     */
    fun selectBlock(blockId: String?) {
        if (blockId == null) {
            state = state.copy(
                selectedBlockId = null,
                selectedAssetHash = state.selectedAssetHash
            )
            return
        }
        val currentV = state.currentView
        val effectiveId = if (state.activeContainerId == null && currentV != null) {
            val rootParent = currentV.blocks.find { root ->
                root.id == blockId || root.children.any { c -> c.id == blockId || c.children.any { cc -> cc.id == blockId } }
            }
            rootParent?.id ?: blockId
        } else {
            blockId
        }
        state = state.copy(
            selectedBlockId = effectiveId,
            selectedAssetHash = null
        )
    }

    /** 执行深拷贝以避免快照与内存状态的引用污染 */
    private fun deepCopyProject(project: ProjectState): ProjectState {
        val copiedPages = project.studioPages.map { page ->
            page.copy(blocks = deepCopyBlocks(page.blocks))
        }
        return project.copy(studioPages = copiedPages)
    }

    private fun deepCopyBlocks(blocks: List<UIStudioBlock>): List<UIStudioBlock> {
        return blocks.map { block ->
            block.copy(
                bounds = block.bounds.copy(),
                cropRect = block.cropRect?.copy(),
                properties = block.properties,
                children = deepCopyBlocks(block.children)
            )
        }
    }

    /**
     * 保存当前项目状态至撤销快照栈 (最大深度 50)。
     */
    fun saveSnapshot(label: String = "操作") {
        val proj = state.project ?: return
        val snapshot = deepCopyProject(proj)
        val entry = UIStudioHistoryEntry(label = label, project = snapshot)
        val newUndo = (state.undoStack + entry).let { if (it.size > 50) it.drop(1) else it }
        state = state.copy(undoStack = newUndo, redoStack = emptyList())
    }

    /**
     * 撤销上一步操作 (Ctrl+Z / Cmd+Z)。
     */
    fun undo() {
        if (state.undoStack.isEmpty()) {
            Toast.show("暂无可撤销的历史记录", ToastType.INFO)
            return
        }
        val currentProj = state.project ?: return
        val currentSnapshot = deepCopyProject(currentProj)
        val lastEntry = state.undoStack.last()
        val newUndo = state.undoStack.dropLast(1)
        val newRedo = state.redoStack + UIStudioHistoryEntry(label = "撤销", project = currentSnapshot)
        state = state.copy(
            project = deepCopyProject(lastEntry.project),
            undoStack = newUndo,
            redoStack = newRedo,
            isDirty = true
        )
        Toast.show("已撤销: ${lastEntry.label}", ToastType.INFO)
    }

    /**
     * 重做下一步操作 (Ctrl+Y / Ctrl+Shift+Z / Cmd+Shift+Z)。
     */
    fun redo() {
        if (state.redoStack.isEmpty()) {
            Toast.show("已是最新操作，无可重做记录", ToastType.INFO)
            return
        }
        val currentProj = state.project ?: return
        val currentSnapshot = deepCopyProject(currentProj)
        val nextEntry = state.redoStack.last()
        val newRedo = state.redoStack.dropLast(1)
        val newUndo = state.undoStack + UIStudioHistoryEntry(label = "重做", project = currentSnapshot)
        state = state.copy(
            project = deepCopyProject(nextEntry.project),
            undoStack = newUndo,
            redoStack = newRedo,
            isDirty = true
        )
        Toast.show("已重做: ${nextEntry.label}", ToastType.INFO)
    }

    fun copySelectedBlock() {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要复制的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = (view.blocks.findBlockById(blockId) as? UIStudioBlock) ?: return
        state = state.copy(clipboardBlock = target)
        Toast.show("已复制组件: ${target.id}", ToastType.SUCCESS)
    }

    fun cutSelectedBlock(repo: TemplateRepository = TemplateRepository()) {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要剪切的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = (view.blocks.findBlockById(blockId) as? UIStudioBlock) ?: return
        saveSnapshot("剪切组件")
        state = state.copy(clipboardBlock = target)
        deleteSelectedBlock(blockId, repo)
        Toast.show("已剪切组件: ${target.id}", ToastType.SUCCESS)
    }

    fun pasteBlock(repo: TemplateRepository = TemplateRepository()) {
        val copied = state.clipboardBlock ?: run {
            Toast.show("剪贴板中暂无组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        saveSnapshot("粘贴组件")
        val timestamp = getCurrentTimeMillis()
        val newId = "block_${timestamp % 100000}_${(100..999).random()}"
        val offsetDistance = 20f
        val newBounds = SerialRect(
            copied.bounds.left + offsetDistance,
            copied.bounds.top + offsetDistance,
            copied.bounds.right + offsetDistance,
            copied.bounds.bottom + offsetDistance
        )
        val newBlock = copied.copy(
            id = newId,
            bounds = newBounds
        )
        val updatedBlocks = (view.blocks + newBlock).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = newBlock.id)
        Toast.show("已粘贴组件: ${newBlock.id}", ToastType.SUCCESS)
    }

    fun duplicateSelectedBlock(repo: TemplateRepository = TemplateRepository()) {
        val blockId = state.selectedBlockId ?: run {
            Toast.show("请先选择要克隆的组件", ToastType.INFO)
            return
        }
        val view = state.currentView ?: return
        val target = (view.blocks.findBlockById(blockId) as? UIStudioBlock) ?: return
        saveSnapshot("克隆组件")
        val timestamp = getCurrentTimeMillis()
        val newId = "block_${timestamp % 100000}_${(100..999).random()}"
        val offsetDistance = 20f
        val newBounds = SerialRect(
            target.bounds.left + offsetDistance,
            target.bounds.top + offsetDistance,
            target.bounds.right + offsetDistance,
            target.bounds.bottom + offsetDistance
        )
        val newBlock = target.copy(
            id = newId,
            bounds = newBounds
        )
        val updatedBlocks = (view.blocks + newBlock).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = newBlock.id)
        Toast.show("已克隆组件: ${newBlock.id}", ToastType.SUCCESS)
    }

    fun nudgeSelectedBlock(dx: Float, dy: Float) {
        val blockId = state.selectedBlockId ?: return
        saveSnapshot("微调组件")
        moveBlockDelta(blockId, dx, dy)
    }

    fun startRenameView(view: UIStudioPage) { state = state.copy(renamingView = view) }
    fun cancelRenameView() { state = state.copy(renamingView = null) }
    fun commitRenameView(viewId: String, newName: String, repo: TemplateRepository = TemplateRepository()) {
        if (newName.isBlank()) return
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == viewId) it.copy(nameStr = newName.trim()) else it
        } ?: return
        val updatedProject = state.project?.copy(studioPages = updatedPages) ?: return
        state = state.copy(project = updatedProject, renamingView = null, isDirty = true)
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("视图已重命名为: $newName", ToastType.SUCCESS)
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, width: Float, height: Float, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(bounds = SerialRect(left, top, left + width, top + height)) else it
        }
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    /**
     * 实时平移单个图元（高频拖拽中毫秒级更新内存坐标，递归支持子图元）。
     * 若处于全局主视图模式，子组件严禁独立位移，强制收拢至其根父容器整体平移。
     */
    fun moveBlockDelta(blockId: String, dx: Float, dy: Float) {
        val view = state.currentView ?: return
        val effectiveId = if (state.activeContainerId == null) {
            val rootParent = view.blocks.find { root ->
                root.id == blockId || root.children.any { c -> c.id == blockId || c.children.any { cc -> cc.id == blockId } }
            }
            rootParent?.id ?: blockId
        } else {
            blockId
        }
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, effectiveId) { block ->
            val b = block.bounds
            val w = b.right - b.left
            val h = b.bottom - b.top
            val newLeft = b.left + dx
            val newTop = b.top + dy
            block.copy(bounds = SerialRect(newLeft, newTop, newLeft + w, newTop + h))
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    /**
     * 图元拖拽释放后标记未保存状态（仅更新内存，不写磁盘）。
     */
    fun commitBlockMove(repo: TemplateRepository = TemplateRepository()) {
        state = state.copy(isDirty = true)
    }

    fun updateBlockType(blockId: String, newType: UIBlockType, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(type = newType)
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    fun updateBlockPureContainer(blockId: String, isPure: Boolean, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(isPureContainer = isPure) else it
        }
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    fun deleteSelectedBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        saveSnapshot("删除组件")
        val updatedBlocks = removeBlockFromTree(view.blocks, blockId).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = null)
        Toast.show("已删除组件", ToastType.INFO)
    }

    /**
     * 递归从模块树中安全移除指定 ID 的图元对象。
     */
    private fun removeBlockFromTree(list: List<UIStudioBlock>, id: String): List<UIStudioBlock> {
        return list.filterNot { it.id == id }.map { block ->
            if (block.children.any { it.id == id }) {
                block.copy(children = block.children.filterNot { it.id == id })
            } else {
                block.copy(children = removeBlockFromTree(block.children, id))
            }
        }
    }

    /**
     * 移动图层层级（支持 BEFORE / AFTER 同级排序及 INSIDE 父子容器嵌套约束）。
     */
    fun moveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        if (draggedId == targetId) return
        val draggedBlock = (view.blocks.findBlockById(draggedId) as? UIStudioBlock) ?: return

        val oldParentId = view.blocks.findParentBlockId(draggedId)
        val oldParent = oldParentId?.let { view.blocks.findBlockById(it) as? UIStudioBlock }
        val targetParentId = if (targetId != null) view.blocks.findParentBlockId(targetId) else null
        val isMovingOutOfParent = oldParentId != null && (
            (position == DropPosition.INSIDE && targetId != oldParentId) ||
                (position != DropPosition.INSIDE && targetParentId != oldParentId) ||
                targetId == null
            )

        // 移出最后一个子组件时强校验与弹窗拦截
        if (isMovingOutOfParent && oldParent != null && oldParent.children.size <= 1) {
            state = state.copy(
                pendingDragRemoveLastChild = DragRemoveLastChildContext(
                    draggedId = draggedId,
                    oldParentId = oldParent.id,
                    targetId = targetId,
                    position = position
                )
            )
            return
        }

        executeMoveHierarchyBlock(
            draggedId = draggedId,
            targetId = targetId,
            position = position,
            repo = repo,
            dissolveContainerId = null
        )
    }

    /**
     * 用户确认移除最后一个子组件操作：可选择保留或解散父容器。
     */
    fun confirmDragRemoveLastChild(keepContainer: Boolean, repo: TemplateRepository) {
        val ctx = state.pendingDragRemoveLastChild ?: return
        state = state.copy(pendingDragRemoveLastChild = null)
        executeMoveHierarchyBlock(
            draggedId = ctx.draggedId,
            targetId = ctx.targetId,
            position = ctx.position,
            repo = repo,
            dissolveContainerId = if (keepContainer) null else ctx.oldParentId
        )
    }

    /**
     * 取消最后一个子组件的移出操作，恢复原始层级状态。
     */
    fun cancelDragRemoveLastChild() {
        state = state.copy(pendingDragRemoveLastChild = null)
    }

    private fun executeMoveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository,
        dissolveContainerId: String? = null
    ) {
        val view = state.currentView ?: return
        val draggedBlock = (view.blocks.findBlockById(draggedId) as? UIStudioBlock) ?: return

        // 目标为 null：移至列表末尾（容器视口下移至当前激活容器末尾，整页视口下移至根列表末尾）
        if (targetId == null) {
            val activeContainer = state.activeContainerBlock
            if (state.activeContainerId != null && activeContainer != null) {
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val containerAbsBounds = view.blocks.calculateBlockAbsoluteBounds(activeContainer.id) ?: activeContainer.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - containerAbsBounds.left,
                    top = draggedAbsBounds.top - containerAbsBounds.top,
                    right = draggedAbsBounds.right - containerAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - containerAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                val updatedDragged = draggedBlock.copy(bounds = localBounds)
                val newBlocks = updateBlockInTreeRecursively(cleanedBlocks, activeContainer.id) { c ->
                    val newChildren = c.children.filterNot { it.id == draggedId } + updatedDragged
                    c.copy(children = newChildren)
                }.bindParents()
                val finalBlocks = if (dissolveContainerId != null) {
                    removeBlockFromTree(newBlocks, dissolveContainerId).bindParents()
                } else newBlocks
                updateViewBlocksAndSave(view.id, finalBlocks, repo)
                return
            }

            val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
            val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            var newBlocks = (cleanedBlocks + updatedDragged).bindParents()
            if (dissolveContainerId != null) {
                newBlocks = removeBlockFromTree(newBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, newBlocks, repo)
            return
        }

        val targetBlock = (view.blocks.findBlockById(targetId) as? UIStudioBlock) ?: return
        val targetParentId = view.blocks.findParentBlockId(targetId)

        if (position == DropPosition.INSIDE) {
            // 移入目标成组逻辑判定：
            // 1. 若目标本身就是纯容器
            if (targetBlock.isPureContainer || targetBlock.type == UIBlockType.CONTAINER) {
                val targetAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: targetBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - targetAbsBounds.left,
                    top = draggedAbsBounds.top - targetAbsBounds.top,
                    right = draggedAbsBounds.right - targetAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - targetAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                val updatedTarget = targetBlock.copy(children = targetBlock.children + draggedBlock.copy(bounds = localBounds))
                var resultBlocks = cleanedBlocks.updateBlockInList(targetId) { updatedTarget }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入容器: ${targetBlock.id}", ToastType.SUCCESS)
                return
            }

            // 2. 若目标所处已有父容器
            if (targetParentId != null) {
                val parentBlock = (view.blocks.findBlockById(targetParentId) as? UIStudioBlock) ?: return
                val parentAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - parentAbsBounds.left,
                    top = draggedAbsBounds.top - parentAbsBounds.top,
                    right = draggedAbsBounds.right - parentAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - parentAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                    p.copy(children = p.children + draggedBlock.copy(bounds = localBounds))
                }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入同级容器", ToastType.SUCCESS)
                return
            }

            // 3. 目标为独立根图层且无父容器：触发二次确认弹窗
            state = state.copy(pendingCreateContainer = draggedId to targetId)
            return
        }

        // BEFORE 或 AFTER：同级相邻排序
        val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
        val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)

        if (targetParentId != null) {
            val parentBlock = (cleanedBlocks.findBlockById(targetParentId) as? UIStudioBlock) ?: return
            val parentAbsBounds = cleanedBlocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
            val localBounds = SerialRect(
                left = draggedAbsBounds.left - parentAbsBounds.left,
                top = draggedAbsBounds.top - parentAbsBounds.top,
                right = draggedAbsBounds.right - parentAbsBounds.left,
                bottom = draggedAbsBounds.bottom - parentAbsBounds.top
            )
            val updatedDragged = draggedBlock.copy(bounds = localBounds)
            var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                val list = p.children.toMutableList()
                val targetIndex = list.indexOfFirst { it.id == targetId }
                val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
                list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
                p.copy(children = list)
            }.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        } else {
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            val list = cleanedBlocks.toMutableList()
            val targetIndex = list.indexOfFirst { it.id == targetId }
            val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
            list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
            var resultBlocks = list.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        }
    }

    /**
     * 用户确认创建父容器约束。
     */
    fun confirmCreateContainer(draggedId: String, targetId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val blockA = (view.blocks.findBlockById(draggedId) as? UIStudioBlock) ?: return
        val blockB = (view.blocks.findBlockById(targetId) as? UIStudioBlock) ?: return

        val boundsA = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: blockA.bounds
        val boundsB = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: blockB.bounds

        val unionLeft = kotlin.math.min(boundsA.left, boundsB.left)
        val unionTop = kotlin.math.min(boundsA.top, boundsB.top)
        val unionRight = kotlin.math.max(boundsA.right, boundsB.right)
        val unionBottom = kotlin.math.max(boundsA.bottom, boundsB.bottom)
        val unionRect = SerialRect(unionLeft, unionTop, unionRight, unionBottom)

        val localA = blockA.copy(bounds = SerialRect(boundsA.left - unionLeft, boundsA.top - unionTop, boundsA.right - unionLeft, boundsA.bottom - unionTop))
        val localB = blockB.copy(bounds = SerialRect(boundsB.left - unionLeft, boundsB.top - unionTop, boundsB.right - unionLeft, boundsB.bottom - unionTop))

        val containerId = "Container_${getCurrentTimeMillis() % 10000}"
        val container = UIStudioBlock(
            id = containerId,
            type = UIBlockType.CONTAINER,
            isPureContainer = true,
            bounds = unionRect,
            children = listOf(localB, localA)
        )

        val remaining = removeBlockFromTree(removeBlockFromTree(view.blocks, draggedId), targetId)
        val targetIdx = view.blocks.indexOfFirst { it.id == targetId }
        val mutable = remaining.toMutableList()
        val insertIdx = if (targetIdx >= 0) targetIdx.coerceAtMost(mutable.size) else mutable.size
        mutable.add(insertIdx, container)
        val resultBlocks = mutable.bindParents()

        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        state = state.copy(pendingCreateContainer = null, selectedBlockId = containerId)
        Toast.show("成功创建父约束容器: $containerId", ToastType.SUCCESS)
    }

    fun cancelCreateContainer() {
        state = state.copy(pendingCreateContainer = null)
    }

    /**
     * 请求将组件移出父容器。若为容器内最后一个组件，触发确认提示。
     */
    fun requestRemoveBlockFromParent(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId) ?: return
        val parent = (view.blocks.findBlockById(parentId) as? UIStudioBlock) ?: return
        if (parent.children.size <= 1) {
            state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, false))
        } else {
            performRemoveBlockFromParent(parentId, blockId, false, repo)
        }
    }

    /**
     * 请求删除组件。若属于容器且为最后一个子组件，触发确认提示。
     */
    fun requestDeleteBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId)
        if (parentId != null) {
            val parent = view.blocks.findBlockById(parentId) as? UIStudioBlock
            if (parent != null && parent.children.size <= 1) {
                state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, true))
                return
            }
        }
        deleteSelectedBlock(blockId, repo)
    }

    fun confirmRemoveLastChildAction(repo: TemplateRepository) {
        val action = state.pendingRemoveLastChildAction ?: return
        val (containerId, childId, isDelete) = action
        performRemoveBlockFromParent(containerId, childId, isDelete, repo)
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    fun cancelRemoveLastChildAction() {
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    private fun performRemoveBlockFromParent(
        containerId: String,
        childId: String,
        isDelete: Boolean,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        val child = (view.blocks.findBlockById(childId) as? UIStudioBlock) ?: return
        val childAbsBounds = view.blocks.calculateBlockAbsoluteBounds(childId) ?: child.bounds
        val parent = (view.blocks.findBlockById(containerId) as? UIStudioBlock) ?: return

        val parentIndexInRoot = view.blocks.indexOfFirst { it.id == containerId }
        val remainingInContainer = parent.children.filterNot { it.id == childId }

        var newBlocks = removeBlockFromTree(view.blocks, childId)
        if (remainingInContainer.isEmpty()) {
            newBlocks = removeBlockFromTree(newBlocks, containerId)
        }

        if (!isDelete) {
            val mutable = newBlocks.toMutableList()
            val targetInsertIndex = if (parentIndexInRoot >= 0) {
                if (remainingInContainer.isNotEmpty()) {
                    val currentParentIdx = mutable.indexOfFirst { it.id == containerId }
                    if (currentParentIdx >= 0) currentParentIdx + 1 else mutable.size
                } else {
                    parentIndexInRoot.coerceIn(0, mutable.size)
                }
            } else {
                mutable.size
            }
            mutable.add(targetInsertIndex, child.copy(bounds = childAbsBounds))
            newBlocks = mutable
        }

        val resultBlocks = newBlocks.bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        val actionText = if (isDelete) "组件已删除" else "组件已移出父容器"
        val containerText = if (remainingInContainer.isEmpty()) "，原父容器已自动解散清理" else ""
        Toast.show("$actionText$containerText", ToastType.SUCCESS)
    }

    /**
     * 解散父容器：将其内部所有子图层平移释放至根列表，并销毁该纯容器。
     */
    fun ungroupContainer(containerId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val container = (view.blocks.findBlockById(containerId) as? UIStudioBlock) ?: return
        val containerAbsBounds = view.blocks.calculateBlockAbsoluteBounds(containerId) ?: container.bounds
        val releasedChildren = container.children.map { child ->
            val absLeft = containerAbsBounds.left + child.bounds.left
            val absTop = containerAbsBounds.top + child.bounds.top
            child.copy(bounds = SerialRect(absLeft, absTop, absLeft + child.bounds.width, absTop + child.bounds.height))
        }
        val withoutContainer = removeBlockFromTree(view.blocks, containerId)
        val resultBlocks = (withoutContainer + releasedChildren).bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        Toast.show("已解散父容器: ${container.id}", ToastType.SUCCESS)
    }

    /** 将指定视图的图元树整体替换并标记脏状态（internal 供宿主素材挂载等跨域流程复用） */
    internal fun updateViewBlocksAndSave(viewId: String, newBlocks: List<UIStudioBlock>, repo: TemplateRepository = TemplateRepository()) {
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == viewId) it.copy(blocks = newBlocks) else it
        } ?: return
        state = state.copy(
            project = state.project?.copy(studioPages = updatedPages),
            isDirty = true
        )
    }

    /**
     * 切换指定组件的可见性状态 (显示/隐藏，递归支持容器内的子图元)。
     */
    fun toggleBlockVisibility(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) { b ->
            val newVisible = !b.isVisible
            if (b.children.isNotEmpty()) {
                b.copy(isVisible = newVisible, children = mapAllBlocksRecursively(b.children) { it.copy(isVisible = newVisible) })
            } else {
                b.copy(isVisible = newVisible)
            }
        }.bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
    }

    /** 兄弟列表内重排动作枚举 */
    private enum class SiblingReorderAction { MOVE_UP, MOVE_DOWN, TO_FRONT, TO_BACK }

    /**
     * 在图元树中递归定位 blockId 所在的兄弟列表，并在该列表内执行重排。
     * 返回重建后的新树；返回 null 表示已在边界无需移动（或未找到）。
     * 纯平移语义：仅调整列表顺序，绝不改动任何图元 bounds 与宽高。
     */
    private fun rerouteWithinSiblings(
        blocks: List<UIStudioBlock>,
        blockId: String,
        action: SiblingReorderAction
    ): List<UIStudioBlock>? {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index >= 0) {
            val mutable = blocks.toMutableList()
            when (action) {
                SiblingReorderAction.MOVE_UP ->
                    if (index == mutable.lastIndex) return null else {
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                    }
                SiblingReorderAction.MOVE_DOWN ->
                    if (index == 0) return null else {
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                    }
                SiblingReorderAction.TO_FRONT -> {
                    if (index == mutable.lastIndex) return null
                    val item = mutable.removeAt(index)
                    mutable.add(item)
                }
                SiblingReorderAction.TO_BACK -> {
                    if (index == 0) return null
                    val item = mutable.removeAt(index)
                    mutable.add(0, item)
                }
            }
            return mutable
        }
        // 递归深入各容器的 children 子树
        for (i in blocks.indices) {
            val child = blocks[i]
            if (child.children.isEmpty()) continue
            val newChildren = rerouteWithinSiblings(child.children, blockId, action) ?: continue
            return blocks.toMutableList().also { it[i] = child.copy(children = newChildren) }
        }
        return null
    }

    /**
     * 将指定组件置于顶层（移至渲染列表尾部）。
     */
    fun bringBlockToFront(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.TO_FRONT) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
        Toast.show("已将组件置于顶层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件置于底层（移至渲染列表头部）。
     */
    fun sendBlockToBack(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.TO_BACK) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
        Toast.show("已将组件置于底层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件上移一层。
     */
    fun moveBlockUp(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.MOVE_UP) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
    }

    /**
     * 将指定组件下移一层。
     */
    fun moveBlockDown(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val newBlocks = rerouteWithinSiblings(view.blocks, blockId, SiblingReorderAction.MOVE_DOWN) ?: return
        updateViewBlocksAndSave(view.id, newBlocks.bindParents(), repo)
    }

    /**
     * 重命名组件（递归支持子组件）。
     */
    fun renameBlock(blockId: String, newName: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val target = (view.blocks.findBlockById(blockId) as? UIStudioBlock) ?: return
        val trimmed = newName.trim().ifBlank { target.id }
        if (trimmed == blockId) {
            state = state.copy(renamingBlockId = null)
            return
        }
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(id = trimmed)
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(
            project = state.project?.copy(studioPages = updatedPages),
            selectedBlockId = if (state.selectedBlockId == blockId) trimmed else state.selectedBlockId,
            renamingBlockId = null,
            isDirty = true
        )
        Toast.show("组件已重命名为: $trimmed", ToastType.SUCCESS)
    }

    fun startRenameBlock(blockId: String) {
        state = state.copy(renamingBlockId = blockId)
    }

    fun cancelRenameBlock() {
        state = state.copy(renamingBlockId = null)
    }
}

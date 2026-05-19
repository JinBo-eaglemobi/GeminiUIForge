package org.gemini.ui.forge.viewmodel

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.history.HistoryEntry
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 历史记录与版本控制逻辑委托。
 * 负责管理撤销/重做栈、快照保存、历史点跳转以及历史面板的可见性。
 */
class HistoryManagerDelegate(
    private val getState: () -> ProjectWorkspaceState,
    private val updateState: ((ProjectWorkspaceState) -> ProjectWorkspaceState) -> Unit,
    private val markDirty: () -> Unit
) {

    /** 执行深拷贝以避免状态引用污染 */
    private fun deepCopyProject(project: ProjectState): ProjectState {
        val copiedPages = project.pages.map { page ->
            page.copy(blocks = deepCopyBlocks(page.blocks))
        }
        return project.copy(pages = copiedPages)
    }

    private fun deepCopyBlocks(blocks: List<org.gemini.ui.forge.model.ui.UIBlock>): List<org.gemini.ui.forge.model.ui.UIBlock> {
        return blocks.map { block ->
            block.copy(
                bounds = block.bounds.copy(),
                cropRect = block.cropRect?.copy(),
                ninePatchConfig = block.ninePatchConfig.copy(),
                properties = block.properties,
                children = deepCopyBlocks(block.children)
            )
        }
    }

    /** 保存当前状态到撤销栈 */
    fun saveSnapshot(label: String = "操作") {
        org.gemini.ui.forge.utils.AppLogger.d("HistoryManager", "保存快照: $label")
        org.gemini.ui.forge.utils.AppLogger.showStatus("已保存: $label")
        val currentState = getState()
        val entry = HistoryEntry(
            id = "hist_${getCurrentTimeMillis()}_${(0..999).random()}",
            label = label,
            timestamp = getCurrentTimeMillis(),
            projectState = deepCopyProject(currentState.project)
        )
        
        updateState { s ->
            val newUndo = s.undoStack.toMutableList().apply { 
                add(entry)
                if (size > 50) removeAt(0)
            }
            s.copy(
                undoStack = newUndo, 
                redoStack = emptyList(),
                statusMessage = "已保存: $label"
            )
        }
    }

    /** 执行撤销操作 */
    fun undo() {
        val s = getState()
        if (s.undoStack.isEmpty()) return
        
        val poppedUndo = s.undoStack.last()
        val newUndo = s.undoStack.dropLast(1)
        
        val currentAsRedo = HistoryEntry(
            id = poppedUndo.id,
            label = poppedUndo.label,
            timestamp = poppedUndo.timestamp,
            projectState = deepCopyProject(s.project)
        )
        val newRedo = s.redoStack + currentAsRedo
        
        org.gemini.ui.forge.utils.AppLogger.d("HistoryManager", "撤销回退: ${poppedUndo.label}")
        org.gemini.ui.forge.utils.AppLogger.showStatus("已撤销: ${poppedUndo.label}")
        
        updateState { it.copy(
            project = deepCopyProject(poppedUndo.projectState),
            undoStack = newUndo,
            redoStack = newRedo,
            statusMessage = "已撤销: ${poppedUndo.label}"
        ) }
        markDirty()
    }

    /** 执行重做操作 */
    fun redo() {
        val s = getState()
        if (s.redoStack.isEmpty()) return
        
        val poppedRedo = s.redoStack.last()
        val newRedo = s.redoStack.dropLast(1)
        
        val currentAsUndo = HistoryEntry(
            id = poppedRedo.id,
            label = poppedRedo.label,
            timestamp = poppedRedo.timestamp,
            projectState = deepCopyProject(s.project)
        )
        val newUndo = s.undoStack + currentAsUndo
        
        org.gemini.ui.forge.utils.AppLogger.d("HistoryManager", "重做前进: ${poppedRedo.label}")
        org.gemini.ui.forge.utils.AppLogger.showStatus("已重做: ${poppedRedo.label}")
        
        updateState { it.copy(
            project = deepCopyProject(poppedRedo.projectState),
            undoStack = newUndo,
            redoStack = newRedo,
            statusMessage = "已重做: ${poppedRedo.label}"
        ) }
        markDirty()
    }

    /** 跳转到指定的历史点 */
    fun jumpToHistory(entryId: String) {
        org.gemini.ui.forge.utils.AppLogger.d("HistoryManager", "尝试跳转到记录: $entryId")
        val s = getState()
        
        if (s.undoStack.any { it.id == entryId }) {
            while (getState().undoStack.isNotEmpty()) {
                val lastId = getState().undoStack.last().id
                undo()
                if (lastId == entryId) break
            }
        } else if (s.redoStack.any { it.id == entryId }) {
            while (getState().redoStack.isNotEmpty()) {
                val lastId = getState().redoStack.last().id
                redo()
                if (lastId == entryId) break
            }
        }
    }

    /** 彻底重置所有操作，回到最初加载的状态 */
    fun clearAllHistoryAndReset() {
        val s = getState()
        val firstState = s.undoStack.firstOrNull()?.projectState ?: s.project
        updateState { it.copy(
            project = deepCopyProject(firstState),
            undoStack = emptyList(),
            redoStack = emptyList(),
            statusMessage = "已清空操作历史"
        ) }
        markDirty()
    }

    /** 切换历史记录面板显示状态 */
    fun toggleHistoryPanel(show: Boolean) {
        updateState { it.copy(showHistoryPanel = show) }
    }
}

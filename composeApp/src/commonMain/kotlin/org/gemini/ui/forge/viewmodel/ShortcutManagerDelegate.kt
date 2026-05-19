package org.gemini.ui.forge.viewmodel

import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.utils.AppLogger

/**
 * 快捷键逻辑委托。
 * 负责分发和调度所有的快捷键动作到对应的处理器。
 */
class ShortcutManagerDelegate(
    private val layoutEditor: LayoutEditorDelegate,
    private val historyManager: HistoryManagerDelegate,
    private val onSaveRequest: () -> Unit
) {

    /**
     * 处理来自 UI 层的快捷键动作。
     * 将指令路由至布局编辑器或其他业务逻辑块。
     */
    fun handleAction(action: ShortcutAction) {
        AppLogger.d("ShortcutManager", "⌨️ 路由快捷键动作: ${action.name}")
        
        when (action) {
            ShortcutAction.UNDO -> historyManager.undo()
            ShortcutAction.REDO -> historyManager.redo()
            ShortcutAction.SAVE -> onSaveRequest()
            ShortcutAction.COPY -> layoutEditor.copy()
            ShortcutAction.PASTE -> layoutEditor.paste()
            ShortcutAction.CUT -> layoutEditor.cut()
            ShortcutAction.RENAME -> layoutEditor.triggerRename()
            ShortcutAction.DELETE -> layoutEditor.deleteSelectedBlock()
        }
    }
}

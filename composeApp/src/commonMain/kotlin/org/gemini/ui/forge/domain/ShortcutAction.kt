package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 快捷键动作枚举
 */
@Serializable
enum class ShortcutAction(val label: String, val defaultKey: String) {
    UNDO("撤销", "Ctrl+Z"),
    REDO("重做", "Ctrl+Y"),
    SAVE("保存项目", "Ctrl+S"),
    RENAME("重命名图层", "F2"),
    DELETE("删除图层", "Delete")
}

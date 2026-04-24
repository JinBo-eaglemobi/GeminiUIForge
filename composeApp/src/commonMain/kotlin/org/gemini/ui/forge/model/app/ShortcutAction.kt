package org.gemini.ui.forge.model.app
import kotlinx.serialization.Serializable

/**
 * 快捷键动作枚举
 */
@Serializable
enum class ShortcutAction(val label: String, val defaultKey: String) {
    UNDO("撤销", "Ctrl+Z"),
    REDO("重做", "Ctrl+Y"),
    SAVE("保存项目", "Ctrl+S"),
    COPY("复制图层", "Ctrl+C"),
    PASTE("粘贴图层", "Ctrl+V"),
    CUT("剪切图层", "Ctrl+X"),
    RENAME("重命名图层", "F2"),
    DELETE("删除图层", "Delete")
}

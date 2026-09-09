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
    DELETE("删除图层", "Delete"),
    MOVE_UP("向上移动", "Up"),
    MOVE_UP_FAST("向上快速移动", "Shift+Up"),
    MOVE_DOWN("向下移动", "Down"),
    MOVE_DOWN_FAST("向下快速移动", "Shift+Down"),
    MOVE_LEFT("向左移动", "Left"),
    MOVE_LEFT_FAST("向左快速移动", "Shift+Left"),
    MOVE_RIGHT("向右移动", "Right"),
    MOVE_RIGHT_FAST("向右快速移动", "Shift+Right")
}

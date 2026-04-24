package org.gemini.ui.forge.utils

import androidx.compose.ui.input.key.*

/**
 * 快捷键工具类，用于判断按键事件是否匹配快捷键配置
 */
object ShortcutUtils {

    /**
     * 判断按键事件是否匹配给定的快捷键组合字符串 (如 "Ctrl+S", "Delete")
     */
    fun isMatch(event: KeyEvent, shortcut: String): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val parts = shortcut.split("+").map { it.trim().lowercase() }
        val hasCtrl = parts.contains("ctrl")
        val hasAlt = parts.contains("alt")
        val hasShift = parts.contains("shift")
        val keyPart = parts.last()

        // 兼容 Mac 的 Cmd 键 (Meta)
        val isCtrl = event.isCtrlPressed || event.isMetaPressed
        val isAlt = event.isAltPressed
        val isShift = event.isShiftPressed

        if (isCtrl != hasCtrl) return false
        if (isAlt != hasAlt) return false
        if (isShift != hasShift) return false

        val isKeyMatch = when (keyPart) {
            "s" -> event.key == Key.S
            "c" -> event.key == Key.C
            "v" -> event.key == Key.V
            "x" -> event.key == Key.X
            "z" -> event.key == Key.Z
            "y" -> event.key == Key.Y
            "f2" -> event.key == Key.F2
            "delete" -> event.key == Key.Delete || event.key == Key.Backspace // Mac 上经常用 Backspace 充当 Delete
            else -> false
        }

        if (isKeyMatch) {
            AppLogger.d("ShortcutUtils", "✅ 快捷键匹配成功: $shortcut")
        }

        return isKeyMatch
    }
}

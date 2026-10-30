package org.gemini.ui.forge.extend

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

/**
 * 监听 Enter 与小键盘 Enter 键的确认扩展修饰符
 *
 * 用于单行文本输入框、表单字段或弹窗按键交互，在按下 Enter/NumPadEnter 时触发确认回调。
 * 消费该事件阻止其向外层冒泡或插入非预期换行。
 */
fun Modifier.onEnterConfirm(
    enabled: Boolean = true,
    onConfirm: () -> Unit
): Modifier = if (enabled) {
    onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown &&
            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
            !keyEvent.isCtrlPressed && !keyEvent.isAltPressed && !keyEvent.isShiftPressed && !keyEvent.isMetaPressed
        ) {
            onConfirm()
            true
        } else {
            false
        }
    }
} else this

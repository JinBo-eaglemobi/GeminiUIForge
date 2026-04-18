package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 封装的全局输入框组件：
 * 1. 支持在通过键盘 (Tab) 获得焦点时自动全选内容。
 * 2. 如果是通过鼠标/触摸点击获得焦点，则保持系统原生行为（光标停留在点击处），避免出现先全选再跳光标的闪烁问题。
 */
@Composable
fun SelectAllOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = MaterialTheme.shapes.small,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1
) {
    // 维护带光标和选择状态的 TextFieldValue
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    var isFocused by remember { mutableStateOf(false) }
    var isPointerDown by remember { mutableStateOf(false) }

    // 外部 value 发生变化时，同步更新内部状态
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != value) {
                onValueChange(newValue.text)
            }
        },
        modifier = modifier
            // 在事件分发的最早阶段捕获鼠标/触摸按下状态
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> isPointerDown = true
                            PointerEventType.Release -> isPointerDown = false
                        }
                    }
                }
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !isFocused) {
                    // 仅当不是通过鼠标/触摸点击时（即通过 Tab 键盘导航获得焦点），才执行全选
                    if (!isPointerDown) {
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length)
                        )
                    }
                }
                isFocused = focusState.isFocused
            },
        label = label,
        keyboardOptions = keyboardOptions,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyMedium,
        readOnly = readOnly,
        enabled = enabled,
        maxLines = maxLines,
        minLines = minLines,
        singleLine = maxLines == 1
    )
}

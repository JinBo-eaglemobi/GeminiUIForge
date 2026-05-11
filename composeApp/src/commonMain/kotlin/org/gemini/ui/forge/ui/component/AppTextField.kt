package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues

/**
 * 封装的全局输入框组件：
 * 1. 支持在通过键盘 (Tab) 获得焦点时自动全选内容。
 * 2. 如果是通过鼠标/触摸点击获得焦点，则保持系统原生行为（光标停留在点击处），避免出现先全选再跳光标的闪烁问题。
 * 3. 自适应高密度模式：如果没有被外部 Modifier 锁定高度，则在高密度下自动压缩内边距和高度，实现紧凑 UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAllOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = MaterialTheme.shapes.small,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    singleLine: Boolean = maxLines == 1,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
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

    // 判断是否在紧凑模式下
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp

    // 通过 Layout Modifier 拦截约束
    val baseModifier = modifier.layout { measurable, constraints ->
        // 判断外部是否指定了固定的高度约束 (例如调用了 Modifier.height(50.dp))
        val isFixedHeight = constraints.hasBoundedHeight && constraints.minHeight == constraints.maxHeight

        // 如果处于紧凑模式且是单行，并且外部 *没有* 强制固定高度，我们才将其压缩为 36.dp
        val resolvedConstraints = if (isCompact && singleLine && !isFixedHeight) {
            val compactHeightPx = 36.dp.roundToPx()
            constraints.copy(minHeight = compactHeightPx, maxHeight = compactHeightPx)
        } else {
            constraints
        }

        val placeable = measurable.measure(resolvedConstraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()

    // 设置 BasicTextField 的文本颜色与光标颜色
    val mergedTextStyle = textStyle.merge(androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface))
    val cursorColor = MaterialTheme.colorScheme.primary

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != value) {
                onValueChange(newValue.text)
            }
        },
        modifier = baseModifier
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
                    // 仅当不是通过鼠标/触摸点击时，才执行全选
                    if (!isPointerDown) {
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length)
                        )
                    }
                }
                isFocused = focusState.isFocused
            },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(cursorColor),
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        decorationBox = @Composable { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = textFieldValue.text,
                visualTransformation = visualTransformation,
                innerTextField = innerTextField,
                placeholder = placeholder,
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                enabled = enabled,
                isError = false,
                interactionSource = interactionSource,
                colors = colors,
                // 根据模式动态压缩 Padding 解决内边距过大的问题
                contentPadding = if (isCompact && singleLine) {
                    PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                } else {
                    OutlinedTextFieldDefaults.contentPadding()
                },
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape
                    )
                }
            )
        }
    )
}

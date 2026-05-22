package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.ResizeHorizontalIcon
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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.PaddingValues
import org.gemini.ui.forge.getCurrentTimeMillis

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

/**
 * 带有数值校验与空值处理的数字输入框组件：
 * 1. 当用户清空输入框时，内部自动回传 "0" 以确保数值逻辑不崩溃，但 UI 层可暂存空状态以便于修改。
 * 2. 如果输入框丢失了焦点，并且当前处于空值或无效状态，则默认恢复到之前有效的值。
 * 3. 对整数形如 12.0 进行显示精简为 12。
 * 4. 支持滚轮加减与左右拖拽加减数值。
 */
@Composable
fun NumberOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    isFloat: Boolean = true,
    enabled: Boolean = true
) {
    var localText by remember { mutableStateOf(normalizeNumberString(value, isFloat)) }
    var focusInitialValue by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isFocused) {
            localText = normalizeNumberString(value, isFloat)
        }
    }

    val focusRequester = remember { FocusRequester() }
    var accumulatedDrag by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        SelectAllOutlinedTextField(
            value = localText,
            onValueChange = { input ->
                if (input.isEmpty()) {
                    localText = input
                    onValueChange("0")
                } else if (input == "-" || input == "." || input == "-.") {
                    localText = input
                    onValueChange("0")
                } else {
                    val isValid = if (isFloat) input.toFloatOrNull() != null else input.toIntOrNull() != null
                    if (isValid || (input.endsWith(".") && input.count { it == '.' } == 1)) {
                        localText = input
                        if (isValid) {
                            onValueChange(input)
                        }
                    }
                }
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        isFocused = true
                        focusInitialValue = localText
                    } else {
                        isFocused = false
                        val isValid = if (isFloat) localText.toFloatOrNull() != null else localText.toIntOrNull() != null
                        if (!isValid) {
                            val fallback = normalizeNumberString(focusInitialValue, isFloat)
                            localText = fallback
                            onValueChange(fallback)
                        } else {
                            val normalized = normalizeNumberString(localText, isFloat)
                            localText = normalized
                            onValueChange(normalized)
                        }
                    }
                }
                .then(
                    if (isFocused && enabled) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Scroll) {
                                        event.changes.firstOrNull()?.let { change ->
                                            val scrollDelta = change.scrollDelta.y
                                            if (scrollDelta != 0f) {
                                                change.consume()
                                                val step = if (scrollDelta > 0) -1 else 1 // 滚轮向后（下）是减，向前（上）是加
                                                val currentVal = localText.toFloatOrNull() ?: 0f
                                                val newVal = currentVal + step
                                                val formatted = normalizeNumberString(newVal.toString(), isFloat)
                                                localText = formatted
                                                onValueChange(formatted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            label = label,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled
        )

        // 当没有焦点且启用时，覆盖一个透明层来拦截 Hover 和 Drag
        if (!isFocused && enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerHoverIcon(ResizeHorizontalIcon)
                    .pointerInput(isFloat, enabled) {
                        awaitEachGesture {
                            // 1. 等待按下事件，并立即消费以防止底层文本框瞬间获焦
                            val down = awaitFirstDown()
                            down.consume()

                            val startTime = getCurrentTimeMillis()
                            val startPosition = down.position
                            var isDragging = false
                            var currentPointerId = down.id
                            accumulatedDrag = 0f

                            // 2. 循环处理直到指针释放或手势结束
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == currentPointerId } ?: break

                                if (change.pressed) {
                                    val currentPosition = change.position
                                    val dragDistanceX = currentPosition.x - startPosition.x
                                    val totalDistance = kotlin.math.abs(dragDistanceX)

                                    if (!isDragging) {
                                        // 判定拖拽的阈值为 8 像素
                                        if (totalDistance > 8f) {
                                            isDragging = true
                                        }
                                    }

                                    if (isDragging) {
                                        // 处于拖拽状态，消费当前事件，计算位移并累加
                                        change.consume()
                                        val dragDelta = change.position.x - change.previousPosition.x
                                        accumulatedDrag += dragDelta

                                        val threshold = 5f // 每移动 5 像素对数值执行一次增减
                                        if (kotlin.math.abs(accumulatedDrag) >= threshold) {
                                            val steps = (accumulatedDrag / threshold).toInt()
                                            accumulatedDrag -= steps * threshold

                                            val currentVal = localText.toFloatOrNull() ?: 0f
                                            val newVal = currentVal + steps
                                            val formatted = normalizeNumberString(newVal.toString(), isFloat)
                                            localText = formatted
                                            onValueChange(formatted)
                                        }
                                    }
                                } else {
                                    // 3. 指针松开
                                    change.consume()

                                    val duration = getCurrentTimeMillis() - startTime
                                    val finalDistance = (change.position - startPosition).getDistance()

                                    // 如果用户没有进行有效的滑动拖拽，且属于正常的轻点（时间小于 300ms 或距离小于 8 像素），则让输入框获得焦点
                                    if (!isDragging && duration < 300L && finalDistance < 8f) {
                                        focusRequester.requestFocus()
                                    }
                                    break
                                }
                            }
                        }
                    }
            )
        }
    }
}

private fun normalizeNumberString(input: String, isFloat: Boolean): String {
    if (isFloat) {
        val f = input.toFloatOrNull() ?: return input
        val i = f.toInt()
        return if (f == i.toFloat()) i.toString() else f.toString()
    } else {
        val i = input.toIntOrNull() ?: return input
        return i.toString()
    }
}

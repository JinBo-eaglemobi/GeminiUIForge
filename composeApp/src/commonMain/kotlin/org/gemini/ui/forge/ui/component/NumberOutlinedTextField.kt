package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.getCurrentTimeMillis

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
    var isDraggingState by remember { mutableStateOf(false) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(value) {
        if (!isFocused && !isDraggingState) {
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
                            currentOnValueChange(fallback)
                        } else {
                            val normalized = normalizeNumberString(localText, isFloat)
                            localText = normalized
                            currentOnValueChange(normalized)
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
                                                currentOnValueChange(formatted)
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
                                            isDraggingState = true
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
                                            currentOnValueChange(formatted)
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
                                    
                                    if (isDragging) {
                                        focusInitialValue = localText
                                    }
                                    isDraggingState = false
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

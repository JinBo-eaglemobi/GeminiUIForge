package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 全局 Tooltip 状态管理器
 */
class GlobalTooltipState {
    var text by mutableStateOf<String?>(null)
    var pointerPosition by mutableStateOf(Offset.Zero)
    var isVisible by mutableStateOf(false)
    private var displayJobCount = 0

    /**
     * 显示提示
     * @param text 提示文案
     * @param position 鼠标指针在窗口中的位置
     */
    fun show(text: String, position: Offset) {
        this.text = text
        this.pointerPosition = position
        this.isVisible = true
        displayJobCount++
    }

    /**
     * 更新鼠标位置
     */
    fun updatePosition(position: Offset) {
        if (isVisible) {
            this.pointerPosition = position
        }
    }

    /**
     * 隐藏提示
     */
    fun hide() {
        displayJobCount--
        if (displayJobCount <= 0) {
            isVisible = false
            displayJobCount = 0
            text = null // 清理文案，防止下次显示时闪烁旧内容
        }
    }
}

/**
 * 提供全局 Tooltip 状态的 CompositionLocal
 */
val LocalGlobalTooltip = compositionLocalOf { GlobalTooltipState() }

/**
 * 万能 Tooltip Modifier 扩展。
 * 只需在任何 Composable 的 Modifier 链中调用 .tip("文案") 即可实现 PC 端悬浮提示。
 *
 * @param text 提示文案，为 null 时不启用。
 */
fun Modifier.tip(text: String?): Modifier = composed {
    if (text.isNullOrBlank()) return@composed this
    
    val tooltipState = LocalGlobalTooltip.current
    var componentPosition by remember { mutableStateOf(Offset.Zero) }

    this.onGloballyPositioned {
        componentPosition = it.positionInWindow()
    }.pointerInput(text) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val currentPointer = event.changes.firstOrNull()?.position ?: Offset.Zero
                // 计算鼠标相对于窗口的绝对坐标
                val absolutePointer = componentPosition + currentPointer

                when (event.type) {
                    PointerEventType.Enter -> {
                        // 鼠标进入组件范围
                        tooltipState.show(text, absolutePointer)
                    }
                    PointerEventType.Move -> {
                        // 鼠标在组件内移动，更新位置，让提示框跟随
                        tooltipState.updatePosition(absolutePointer)
                    }
                    PointerEventType.Exit -> {
                        // 鼠标离开组件范围
                        tooltipState.hide()
                    }
                    PointerEventType.Press -> {
                        // 鼠标点击组件时，通常也应该隐藏提示
                        tooltipState.hide()
                    }
                }
            }
        }
    }
}

/**
 * 全局 Tooltip 宿主组件。
 * 建议挂载在 App 根节点的 Box 中，确保层级在最上方。
 */
@Composable
fun GlobalTooltipHost() {
    val state = LocalGlobalTooltip.current
    if (state.isVisible && !state.text.isNullOrBlank()) {
        Popup(
            // 在鼠标指针的右下方添加安全偏移显示，避免遮挡光标引发闪烁
            offset = IntOffset(state.pointerPosition.x.toInt() + 15, state.pointerPosition.y.toInt() + 15),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                color = Color(0xFF323232),
                shape = RoundedCornerShape(4.dp),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = state.text!!,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

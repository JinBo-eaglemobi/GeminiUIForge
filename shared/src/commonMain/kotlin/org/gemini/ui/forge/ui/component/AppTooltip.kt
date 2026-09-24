package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
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
    private var currentOwnerId by mutableStateOf<Any?>(null)

    /**
     * 显示提示
     * @param ownerId 触发显示的持有者唯一令牌
     * @param text 提示文案
     * @param position 鼠标指针在窗口中的位置
     */
    fun show(ownerId: Any, text: String, position: Offset) {
        this.currentOwnerId = ownerId
        this.text = text
        this.pointerPosition = position
        this.isVisible = true
    }

    /**
     * 更新鼠标位置
     * @param ownerId 触发更新的持有者唯一令牌
     */
    fun updatePosition(ownerId: Any, position: Offset) {
        if (isVisible && currentOwnerId == ownerId) {
            this.pointerPosition = position
        }
    }

    /**
     * 隐藏提示
     * @param ownerId 触发隐藏的持有者凭证；为 null 时强制全局隐藏
     */
    fun hide(ownerId: Any? = null) {
        if (ownerId == null || currentOwnerId == ownerId) {
            isVisible = false
            currentOwnerId = null
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
    val ownerId = remember { Any() }
    val currentText by rememberUpdatedState(text)
    var componentPosition by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(ownerId) {
        onDispose {
            tooltipState.hide(ownerId)
        }
    }

    this.onGloballyPositioned {
        componentPosition = it.positionInWindow()
    }.pointerInput(ownerId) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val currentPointer = event.changes.firstOrNull()?.position ?: Offset.Zero
                    // 计算鼠标相对于窗口的绝对坐标
                    val absolutePointer = componentPosition + currentPointer

                    when (event.type) {
                        PointerEventType.Enter -> {
                            tooltipState.show(ownerId, currentText, absolutePointer)
                        }
                        PointerEventType.Move -> {
                            tooltipState.updatePosition(ownerId, absolutePointer)
                        }
                        PointerEventType.Exit, PointerEventType.Press -> {
                            tooltipState.hide(ownerId)
                        }
                    }
                }
            }
        } finally {
            tooltipState.hide(ownerId)
        }
    }
}

/**
 * 全局 Tooltip 宿主组件。
 * 挂载在 App 根节点 Box 中，内置视口边界碰撞与动态上下/左右智能翻转算法，绝对杜绝遮挡按钮或超出屏幕。
 */
@Composable
fun GlobalTooltipHost() {
    val state = LocalGlobalTooltip.current
    if (state.isVisible && !state.text.isNullOrBlank()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowWidthPx = constraints.maxWidth.toFloat()
            val windowHeightPx = constraints.maxHeight.toFloat()

            var tooltipWidthPx by remember(state.text) { mutableStateOf(0f) }
            var tooltipHeightPx by remember(state.text) { mutableStateOf(0f) }

            val pointerX = state.pointerPosition.x
            val pointerY = state.pointerPosition.y

            // 预估尺寸（尚未测量时使用安全预估）
            val estimatedHeight = if (tooltipHeightPx > 0f) tooltipHeightPx else 28f
            val estimatedWidth = if (tooltipWidthPx > 0f) tooltipWidthPx else 140f

            // 1. 垂直 Y 轴自适应翻转定位：
            // 当鼠标靠近窗口下边缘，且下方不足以容纳 Tooltip 时，翻转至上方显示，绝不遮挡底部状态栏按钮
            val isNearBottom = pointerY + estimatedHeight + 36f > windowHeightPx
            val targetY = if (isNearBottom) {
                (pointerY - estimatedHeight - 12f).coerceAtLeast(8f)
            } else {
                (pointerY + 16f).coerceAtMost(windowHeightPx - estimatedHeight - 8f)
            }

            // 2. 水平 X 轴自适应内缩定位：
            // 当鼠标靠近窗口右边缘时，自动向左收缩对齐，防止超出可视视口
            val isNearRight = pointerX + estimatedWidth + 24f > windowWidthPx
            val targetX = if (isNearRight) {
                (windowWidthPx - estimatedWidth - 12f).coerceAtLeast(8f)
            } else {
                (pointerX + 14f).coerceAtLeast(8f)
            }

            Popup(
                offset = IntOffset(targetX.toInt(), targetY.toInt()),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF4D4D4D)),
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier.onGloballyPositioned {
                        tooltipWidthPx = it.size.width.toFloat()
                        tooltipHeightPx = it.size.height.toFloat()
                    }
                ) {
                    Text(
                        text = state.text!!,
                        color = Color(0xFFF0F0F0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

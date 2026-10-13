package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ResizeHorizontalIcon

/**
 * 纵向拖拽分割线（遵循 Compose 官方 Modifier.draggable 生产级封装）。
 * 支持鼠标拖动调节左右两侧面板的宽度。
 *
 * 核心特性：
 * 1. 采用官方 Modifier.draggable + rememberDraggableState 规范封装；
 * 2. 配置 startDragImmediately = true 确保零启动死区与即刻指针连续捕获；
 * 3. 拖拽回调采用 rememberUpdatedState 安全收拢，手势绝不因宿主重组而中断；
 * 4. 拖拽结束/取消时通过 onDragStopped 100% 确保释放高亮状态，彻底根除死锁缺陷。
 */
@Composable
fun VerticalSplitter(
    onDrag: (Float) -> Unit = {},
    onDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val draggableState = rememberDraggableState { deltaPx ->
        currentOnDrag(deltaPx)
    }

    Box(
        modifier = modifier
            .width(5.dp)
            .fillMaxHeight()
            .pointerHoverIcon(ResizeHorizontalIcon)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                startDragImmediately = true,
                onDragStarted = { isDragging = true },
                onDragStopped = {
                    isDragging = false
                    currentOnDragEnd?.invoke()
                }
            )
            .background(
                if (isDragging) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
    )
}

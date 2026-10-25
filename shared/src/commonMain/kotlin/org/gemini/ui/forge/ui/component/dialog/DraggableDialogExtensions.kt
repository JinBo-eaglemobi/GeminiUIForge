package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset

/**
 * 作用于弹窗根容器的修饰符。
 * 动态应用位移 offset，并自动捕获弹窗自身物理尺寸以供给夹具 (Clamping) 算法。
 */
fun Modifier.draggableDialogContainer(
    state: DraggableDialogState
): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.dialogSize = coordinates.size
    }
    .offset { state.offset }

/**
 * 作用于标题栏（或其他可拖拽抓手区域）的交互修饰符。
 * 支持鼠标悬浮小手手型、按住平滑拖拽移动，以及双击瞬时复位居中。
 */
fun Modifier.draggableDialogHandle(
    state: DraggableDialogState,
    onDoubleClick: () -> Unit = { state.reset() }
): Modifier = this
    .pointerHoverIcon(PointerIcon.Hand)
    .pointerInput(state) {
        detectTapGestures(
            onDoubleTap = {
                onDoubleClick()
            }
        )
    }
    .pointerInput(state) {
        detectDragGestures(
            onDragStart = {
                state.isDragging = true
            },
            onDragEnd = {
                state.isDragging = false
            },
            onDragCancel = {
                state.isDragging = false
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDragDelta(dragAmount)
            }
        )
    }

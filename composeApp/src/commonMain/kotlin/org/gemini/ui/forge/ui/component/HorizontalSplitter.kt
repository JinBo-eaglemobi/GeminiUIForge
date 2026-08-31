package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * 水平分割线。
 * 支持垂直拖拽以调整下方/上方组件的高度。
 */
@Composable
fun HorizontalSplitter(onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .height(4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerHoverIcon(org.gemini.ui.forge.ResizeVerticalIcon)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { onDrag(it) }
            )
    )
}

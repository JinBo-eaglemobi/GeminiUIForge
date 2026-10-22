package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.ui.Modifier

actual fun Modifier.onFileDrop(
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit
): Modifier = this

@androidx.compose.runtime.Composable
actual fun AttachWindowDropTarget(
    onDragHoverChanged: (Boolean) -> Unit,
    onFilesDropped: (List<String>) -> Unit
) {
    // Android 端无需 AWT 窗口级挂载
}

@androidx.compose.runtime.Composable
actual fun FileDropZone(
    modifier: Modifier,
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.onFileDrop(onDragStateChanged, onFilesDropped)
    ) {
        content()
    }
}

package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.ui.Modifier

actual fun Modifier.onFileDrop(
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit
): Modifier = this

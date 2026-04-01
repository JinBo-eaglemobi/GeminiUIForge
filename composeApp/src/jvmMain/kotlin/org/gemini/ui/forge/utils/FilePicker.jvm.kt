package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        // Run dialog picking on a background to avoid blocking the main UI event loop
        Thread {
            val dialog = FileDialog(null as Frame?, "Select Images", FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                val paths = files.map { it.absolutePath }
                // Call result callback
                onResult(paths)
            }
        }.start()
    }
}

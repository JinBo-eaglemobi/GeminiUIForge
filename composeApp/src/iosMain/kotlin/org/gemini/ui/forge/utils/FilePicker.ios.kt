package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        // Stub for iOS - file picker typically uses UIViewControllerRepresentable which is complex for a quick fix
        onResult(emptyList())
    }
}

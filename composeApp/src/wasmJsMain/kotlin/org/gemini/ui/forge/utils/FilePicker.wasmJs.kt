package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        // Stub for JS/WASM
        onResult(emptyList())
    }
}

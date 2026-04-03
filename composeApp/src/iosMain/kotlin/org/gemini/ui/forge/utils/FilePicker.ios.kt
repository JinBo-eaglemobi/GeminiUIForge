package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        // Stub for iOS
        onResult(emptyList())
    }
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    return {
        // Stub for iOS directory picker
        onResult(null)
    }
}

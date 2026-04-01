package org.gemini.ui.forge.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            onResult(uris.map { it.toString() })
        }
    }
    return {
        // Only pick images
        launcher.launch("image/*")
    }
}

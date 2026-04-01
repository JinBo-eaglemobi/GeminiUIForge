package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

/**
 * A cross-platform composable that remembers an image picker launcher.
 * Invoking the returned function launches the platform-specific file picker dialog.
 */
@Composable
expect fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit

package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

/**
 * A cross-platform composable that remembers an image picker launcher.
 * Invoking the returned function launches the platform-specific file picker dialog.
 */
@Composable
expect fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit

/**
 * A cross-platform composable that remembers a directory picker launcher.
 * Invoking the returned function launches the platform-specific folder picker dialog.
 * @param title The localized title for the dialog (used on platforms that support it, like JVM).
 */
@Composable
expect fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit

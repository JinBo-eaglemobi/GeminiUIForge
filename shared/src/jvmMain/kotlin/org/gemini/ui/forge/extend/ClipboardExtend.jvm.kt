package org.gemini.ui.forge.extend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun String.toClipEntry(): ClipEntry {
    return ClipEntry(StringSelection(this))
}
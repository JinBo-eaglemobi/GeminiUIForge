package org.gemini.ui.forge.extend

import androidx.compose.ui.platform.ClipEntry

actual fun String.toClipEntry(): ClipEntry {
    return ClipEntry.withPlainText(this)
}

actual suspend fun readClipboardImageBytes(): ByteArray? = null
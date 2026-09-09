package org.gemini.ui.forge.extend

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun String.toClipEntry(): ClipEntry {
    return ClipEntry(ClipData.newPlainText(null, this))
}
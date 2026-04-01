package org.gemini.ui.forge.utils

import java.io.File

actual fun readLocalFileBytes(filePath: String): ByteArray? {
    return try {
        val file = File(filePath)
        if (file.exists()) file.readBytes() else null
    } catch (e: Exception) {
        null
    }
}

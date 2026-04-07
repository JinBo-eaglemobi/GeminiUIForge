package org.gemini.ui.forge.utils

import java.io.File
import java.security.MessageDigest

actual suspend fun readLocalFileBytes(filePath: String): ByteArray? {
    return try {
        val file = File(filePath)
        if (file.exists()) file.readBytes() else null
    } catch (e: Exception) {
        null
    }
}

actual suspend fun isFileExists(filePath: String): Boolean {
    return try {
        File(filePath).exists()
    } catch (e: Exception) {
        false
    }
}

actual fun ByteArray.calculateMd5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}

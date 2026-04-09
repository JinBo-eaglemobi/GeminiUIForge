package org.gemini.ui.forge.utils

import java.io.File
import java.security.MessageDigest

actual fun Throwable.getPlatformStackTrace(): String {
    return android.util.Log.getStackTraceString(this)
}

actual suspend fun getLocalFileLastModified(filePath: String): Long {
    return File(filePath).lastModified()
}

actual suspend fun deleteLocalFile(filePath: String): Boolean {
    return File(filePath).delete()
}

actual suspend fun listFilesInLocalDirectory(dirPath: String): List<String> {
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
}

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

actual suspend fun appendToLocalFile(filePath: String, content: String): Boolean {
    return try {
        File(filePath).appendText(content)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun ByteArray.calculateMd5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}

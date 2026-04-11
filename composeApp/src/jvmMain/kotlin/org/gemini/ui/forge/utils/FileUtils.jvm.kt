package org.gemini.ui.forge.utils

import java.io.File
import java.security.MessageDigest

actual fun Throwable.getPlatformStackTrace(): String {
    return this.stackTraceToString()
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

actual suspend fun executeSystemCommand(
    command: String,
    args: List<String>,
    onLog: (String) -> Unit
): Boolean {
    val fullCmd = listOf(command) + args
    AppLogger.i("SystemCommand", "[RAW COMMAND EXEC] ${fullCmd.joinToString(" ")}")
    return try {
        val process = ProcessBuilder(fullCmd)
            .redirectErrorStream(true)
            .start()
        
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { onLog(it) }
        }
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            onLog("Process exited with non-zero code: $exitCode")
        }
        exitCode == 0
    } catch (e: Exception) {
        onLog("Failed to execute command: ${e.message}")
        false
    }
}

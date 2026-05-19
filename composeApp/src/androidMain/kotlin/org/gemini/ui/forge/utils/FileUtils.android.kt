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

actual suspend fun executeSystemCommand(
    command: String,
    args: List<String>,
    onLog: (String) -> Unit
): Boolean {
    // Android 通常不通过这种方式执行外部脚本，返回 false
    onLog("System command execution is not supported on Android.")
    return false
}

actual suspend fun calculateFileHash(filePath: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return@withContext null
        
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        val digest = md.digest()
        digest.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun copyLocalFile(sourcePath: String, destPath: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.isFile) return@withContext false
        
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, 8192)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}



actual suspend fun readLocalFileTail(filePath: String, maxLines: Int): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val file = java.io.File(filePath) // simplify for android
        if (!file.exists()) return@withContext null
        
        java.io.RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            if (fileLength == 0L) return@withContext ""
            
            var pos = fileLength - 1
            var lines = 0
            
            while (pos >= 0) {
                raf.seek(pos)
                val c = raf.readByte().toInt().toChar()
                if (c == '\n') {
                    lines++
                    if (lines == maxLines) {
                        pos++
                        break
                    }
                }
                pos--
            }
            if (pos < 0) pos = 0
            raf.seek(pos)
            val bytes = ByteArray((fileLength - pos).toInt())
            raf.readFully(bytes)
            return@withContext String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun streamLocalFileLines(filePath: String, onChunk: (List<String>) -> Unit) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) return@withContext
        file.useLines { lines ->
            val chunk = mutableListOf<String>()
            for (line in lines) {
                chunk.add(line)
                if (chunk.size >= 3000) {
                    val currentChunk = chunk.toList()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onChunk(currentChunk) }
                    chunk.clear()
                    kotlinx.coroutines.yield()
                }
            }
            if (chunk.isNotEmpty()) {
                val currentChunk = chunk.toList()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onChunk(currentChunk) }
            }
        }
    } catch (e: Exception) {
        org.gemini.ui.forge.utils.AppLogger.e("FileUtils", "流式读取失败: ${e.message}", e)
    }
}

package org.gemini.ui.forge.utils

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 辅助：如果路径是相对的，则解析为绝对路径 */
private fun resolve(path: String): String {
    if (path.startsWith("http") || path.startsWith("data:")) return path
    val file = File(path)
    if (file.isAbsolute) return path
    // 相对路径，通过与 LocalFileStorage 一致的规则解析
    val dataDir = File(System.getProperty("user.home"), ".geminiuiforge")
    return File(dataDir, path).absolutePath
}

actual fun Throwable.getPlatformStackTrace(): String {
    return this.stackTraceToString()
}

actual suspend fun getLocalFileLastModified(filePath: String): Long = withContext(Dispatchers.IO) {
    return@withContext File(resolve(filePath)).lastModified()
}

actual suspend fun deleteLocalFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext File(resolve(filePath)).delete()
}

actual suspend fun listFilesInLocalDirectory(dirPath: String): List<String> = withContext(Dispatchers.IO) {
    val dir = File(resolve(dirPath))
    if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
    return@withContext dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
}

actual suspend fun readLocalFileBytes(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    return@withContext try {
        val file = File(resolve(filePath))
        if (file.exists()) file.readBytes() else null
    } catch (e: Exception) {
        null
    }
}

actual suspend fun isFileExists(filePath: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        File(resolve(filePath)).exists()
    } catch (e: Exception) {
        false
    }
}

actual suspend fun appendToLocalFile(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        File(resolve(filePath)).appendText(content)
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
): Boolean = withContext(Dispatchers.IO) {
    val fullCmd = listOf(command) + args
    AppLogger.i("SystemCommand", "[RAW COMMAND EXEC] ${fullCmd.joinToString(" ")}")
    return@withContext try {
        val process = ProcessBuilder(fullCmd)
            .redirectErrorStream(true)
            .start()
        
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { 
                // 确保在主线程上调用 UI 回调
                withContext(Dispatchers.Main) { onLog(it) }
            }
        }
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            withContext(Dispatchers.Main) { onLog("Process exited with non-zero code: $exitCode") }
        }
        exitCode == 0
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onLog("Failed to execute command: ${e.message}") }
        false
    }
}

actual suspend fun calculateFileHash(filePath: String): String? = withContext(Dispatchers.IO) {
    try {
        val file = File(resolve(filePath))
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
        AppLogger.e("FileUtils", "计算文件哈希失败: ${e.message}")
        null
    }
}

actual suspend fun copyLocalFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val sourceFile = File(resolve(sourcePath))
        if (!sourceFile.exists() || !sourceFile.isFile) return@withContext false
        
        val destFile = File(resolve(destPath))
        destFile.parentFile?.mkdirs()
        
        // 流式复制，避免 OOM
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, 8192)
            }
        }
        true
    } catch (e: Exception) {
        AppLogger.e("FileUtils", "文件复制失败: ${e.message}")
        false
    }
}


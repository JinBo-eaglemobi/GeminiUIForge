package org.gemini.ui.forge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual typealias PlatformPath = java.io.File

actual fun resolvePlatformPath(absolutePath: String): PlatformPath {
    return File(absolutePath)
}

actual suspend fun copyToInternal(sourcePath: String, targetPath: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val source = File(sourcePath).toPath()
        val target = File(targetPath).toPath()
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

actual suspend fun isFileExistsInternal(absPath: String): Boolean = withContext(Dispatchers.IO) {
    File(absPath).exists()
}

actual suspend fun readBytesInternal(absPath: String): ByteArray? = withContext(Dispatchers.IO) {
    val file = File(absPath)
    if (file.exists()) file.readBytes() else null
}

actual suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
    try {
        val file = File(absPath)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray> = flow {
    val file = File(absPath)
    if (!file.exists()) return@flow
    
    file.inputStream().use { input ->
        val buffer = ByteArray(chunkSize)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            if (bytesRead == chunkSize) {
                emit(buffer.copyOf())
            } else {
                emit(buffer.copyOfRange(0, bytesRead))
            }
        }
    }
}

actual suspend fun createParentDirsInternal(absPath: String): Boolean = withContext(Dispatchers.IO) {
    File(absPath).parentFile?.mkdirs() ?: false
}

actual suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
    val file = File(absPath)
    if (recursive) {
        file.deleteRecursively()
    } else {
        file.delete()
    }
}

package org.gemini.ui.forge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

actual typealias PlatformPath = java.io.File

actual fun resolvePlatformPath(absolutePath: String): PlatformPath {
    // Android (API < 26) 可能不支持 java.nio.file.Path，返回 File 对象作为替代
    return File(absolutePath)
}

actual suspend fun copyToInternal(sourcePath: String, targetPath: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val source = File(sourcePath)
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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
            emit(if (bytesRead == chunkSize) buffer.copyOf() else buffer.copyOfRange(0, bytesRead))
        }
    }
}

actual suspend fun createParentDirsInternal(absPath: String): Boolean = withContext(Dispatchers.IO) {
    File(absPath).parentFile?.mkdirs() ?: false
}

actual suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
    val file = File(absPath)
    if (recursive) file.deleteRecursively() else file.delete()
}

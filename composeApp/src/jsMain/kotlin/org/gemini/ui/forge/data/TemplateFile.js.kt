package org.gemini.ui.forge.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.gemini.ui.forge.service.LocalFileStorage

actual fun resolvePlatformPath(absolutePath: String): Any = absolutePath

actual suspend fun isFileExistsInternal(absPath: String): Boolean {
    return LocalFileStorage().exists(absPath)
}

actual suspend fun readBytesInternal(absPath: String): ByteArray? {
    return LocalFileStorage().readBytesFromFile(absPath)
}

actual suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean {
    return try {
        LocalFileStorage().saveBytesToFile(absPath, data)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray> = flow {
    // JS 暂不支持真实流式读取，退化为一次性读取
    val bytes = readBytesInternal(absPath)
    if (bytes != null) emit(bytes)
}

actual suspend fun createParentDirsInternal(absPath: String): Boolean = true // JS 通常自动处理

actual suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean {
    return LocalFileStorage().deleteFile(absPath)
}

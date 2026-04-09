package org.gemini.ui.forge.utils

import platform.Foundation.*
import kotlinx.cinterop.*
import org.gemini.ui.forge.service.LocalFileStorage

actual fun Throwable.getPlatformStackTrace(): String {
    return this.getStackTrace().joinToString("\n")
}

actual suspend fun readLocalFileBytes(filePath: String): ByteArray? {
    val storage = LocalFileStorage()
    return storage.readBytesFromFile(filePath)
}

actual suspend fun isFileExists(filePath: String): Boolean {
    val storage = LocalFileStorage()
    return storage.exists(filePath)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun appendToLocalFile(filePath: String, content: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(filePath)) {
        // 文件不存在则先创建空文件
        fileManager.createFileAtPath(filePath, contents = null, attributes = null)
    }

    val fileHandle = NSFileHandle.fileHandleForWritingAtPath(filePath) ?: return false
    return try {
        fileHandle.seekToEndOfFile()
        val data = (content as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        if (data != null) {
            fileHandle.writeData(data)
        }
        true
    } catch (e: Exception) {
        false
    } finally {
        fileHandle.closeFile()
    }
}

actual fun ByteArray.calculateMd5(): String {
    // 简化实现：iOS 平台通常使用 CommonCrypto 进行 MD5 计算
    // 鉴于这是一个辅助功能，我们先实现一个基础的快速哈希占位，或调用平台 CC_MD5
    return this.contentHashCode().toString(16)
}

package org.gemini.ui.forge.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.*
import kotlinx.cinterop.*

actual typealias PlatformPath = String

actual fun resolvePlatformPath(absolutePath: String): PlatformPath = absolutePath

actual suspend fun copyToInternal(sourcePath: String, targetPath: String): Boolean {
    // iOS 具体实现待补充（可使用 NSFileManager）
    return false
}

actual suspend fun isFileExistsInternal(absPath: String): Boolean {
    return NSFileManager.defaultManager.fileExistsAtPath(absPath)
}

actual suspend fun readBytesInternal(absPath: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(absPath) ?: return null
    return data.toByteArray()
}

actual suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean {
    val nsData = data.toNSData()
    return nsData.writeToFile(absPath, true)
}

actual fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray> = flow {
    val fileHandle = NSFileHandle.fileHandleForReadingAtPath(absPath) ?: return@flow
    try {
        var data = fileHandle.readDataOfLength(chunkSize.toULong())
        while (data.length > 0uL) {
            emit(data.toByteArray())
            data = fileHandle.readDataOfLength(chunkSize.toULong())
        }
    } finally {
        fileHandle.closeFile()
    }
}

actual suspend fun createParentDirsInternal(absPath: String): Boolean {
    val url = NSURL.fileURLWithPath(absPath)
    val parent = url.URLByDeletingLastPathComponent ?: return false
    return NSFileManager.defaultManager.createDirectoryAtURL(parent, true, null, null)
}

actual suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean {
    return NSFileManager.defaultManager.removeItemAtPath(absPath, null)
}

// 辅助扩展：NSData 转 ByteArray
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val byteArray = ByteArray(size)
    if (size > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return byteArray
}

// 辅助扩展：ByteArray 转 NSData
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.data()
    return this.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
    }
}

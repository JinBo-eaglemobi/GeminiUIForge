package org.gemini.ui.forge.utils

import platform.Foundation.*
import kotlinx.cinterop.*
import org.gemini.ui.forge.service.LocalFileStorage
import kotlin.experimental.ExperimentalNativeApi
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

@OptIn(ExperimentalNativeApi::class)
actual fun Throwable.getPlatformStackTrace(): String {
    return this.getStackTrace().joinToString("\n")
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getLocalFileLastModified(filePath: String): Long {
    val fileManager = NSFileManager.defaultManager
    val attributes = fileManager.attributesOfItemAtPath(filePath, error = null)
    val date = attributes?.get(NSFileModificationDate) as? NSDate
    return (date?.timeIntervalSince1970 ?: 0.0).toLong() * 1000L
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun deleteLocalFile(filePath: String): Boolean {
    return NSFileManager.defaultManager.removeItemAtPath(filePath, error = null)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun listFilesInLocalDirectory(dirPath: String): List<String> {
    val fileManager = NSFileManager.defaultManager
    val contents = fileManager.contentsOfDirectoryAtPath(dirPath, error = null) as? List<String>
    return contents?.map { "$dirPath/$it" } ?: emptyList()
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

/**
 * 使用 iOS 官方的 CommonCrypto 库计算 MD5 哈希值
 */
@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.calculateMd5(): String {
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    this.usePinned { pinnedData ->
        digest.usePinned { pinnedDigest ->
            CC_MD5(pinnedData.addressOf(0), this.size.toUInt(), pinnedDigest.addressOf(0).reinterpret())
        }
    }
    return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

actual suspend fun executeSystemCommand(
    command: String,
    args: List<String>,
    onLog: (String) -> Unit
): Boolean {
    onLog("System command execution is not supported on iOS.")
    return false
}

private infix fun Int.add(other: Int): Int = this + other

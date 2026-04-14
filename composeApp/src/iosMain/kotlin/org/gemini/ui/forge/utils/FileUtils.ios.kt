package org.gemini.ui.forge.utils

import platform.Foundation.*
import kotlinx.cinterop.*
import org.gemini.ui.forge.service.LocalFileStorage
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.sin

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
 * 手动实现 MD5 以保持跨平台一致性，避免对平台 Crypto 库的过度依赖
 */
actual fun ByteArray.calculateMd5(): String {
    val x = IntArray(this.size + 9 shr 6 add 1 shl 4)
    for (i in this.indices) {
        x[i shr 2] = x[i shr 2] or (this[i].toInt() and 0xFF shl (i and 3 shl 3))
    }
    x[this.size shr 2] = x[this.size shr 2] or (0x80 shl (this.size and 3 shl 3))
    x[x.size - 2] = this.size shl 3
    
    var a = 0x67452301
    var b = -0x10325477
    var c = -0x67452302
    var d = 0x10325476
    
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )
    
    val k = IntArray(64) { i -> ((kotlin.math.abs(sin(i + 1.0)) * 4294967296.0).toLong() and 0xFFFFFFFFL).toInt() }
    
    for (i in 0 until x.size step 16) {
        val oldA = a
        val oldB = b
        val oldC = c
        val oldD = d
        
        for (j in 0 until 64) {
            var f: Int
            var g: Int
            if (j < 16) {
                f = (b and c) or (b.inv() and d)
                g = j
            } else if (j < 32) {
                f = (d and b) or (d.inv() and c)
                g = (5 * j + 1) % 16
            } else if (j < 48) {
                f = b xor c xor d
                g = (3 * j + 5) % 16
            } else {
                f = c xor (b or d.inv())
                g = (7 * j) % 16
            }
            val temp = d
            d = c
            c = b
            b = b + rotateLeft(a + f + k[j] + x[i + g], s[j])
            a = temp
        }
        a += oldA
        b += oldB
        c += oldC
        d += oldD
    }
    
    return toHexString(a) + toHexString(b) + toHexString(c) + toHexString(d)
}

private fun rotateLeft(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

private fun toHexString(n: Int): String {
    var s = ""
    for (i in 0 until 4) {
        val v = (n shr (i * 8)) and 0xFF
        s += v.toString(16).padStart(2, '0')
    }
    return s
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

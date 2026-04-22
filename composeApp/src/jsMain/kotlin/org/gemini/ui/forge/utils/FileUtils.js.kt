package org.gemini.ui.forge.utils

import org.gemini.ui.forge.service.LocalFileStorage
import kotlin.math.sin

actual fun Throwable.getPlatformStackTrace(): String {
    return this.message ?: "Unknown JS Error"
}

actual suspend fun getLocalFileLastModified(filePath: String): Long {
    // Web OPFS does not easily expose last modified via handles yet in pure JS without File interface wrapping.
    // Return current time as a placeholder.
    return org.gemini.ui.forge.getCurrentTimeMillis()
}

actual suspend fun deleteLocalFile(filePath: String): Boolean {
    val storage = LocalFileStorage()
    return storage.deleteFile(filePath)
}

actual suspend fun listFilesInLocalDirectory(dirPath: String): List<String> {
    val storage = LocalFileStorage()
    return storage.listFilesRecursive(dirPath)
}

actual suspend fun readLocalFileBytes(filePath: String): ByteArray? {
    if (filePath.startsWith("data:image")) {
        return try {
            val base64Data = filePath.substringAfter("base64,")
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(base64Data)
        } catch (e: Exception) {
            null
        }
    }
    val storage = LocalFileStorage()
    return storage.readBytesFromFile(filePath)
}

actual suspend fun isFileExists(filePath: String): Boolean {
    val storage = LocalFileStorage()
    return storage.exists(filePath)
}

actual suspend fun appendToLocalFile(filePath: String, content: String): Boolean {
    return try {
        val storage = LocalFileStorage()
        val oldContent = storage.readFromFile(filePath) ?: ""
        storage.saveToFile(filePath, oldContent + content)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * 手动实现 MD5 以保持在 JS 环境下的同步调用
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
    onLog("System command execution is not supported on Browser/JS.")
    return false
}

actual suspend fun calculateFileHash(filePath: String): String? {
    // Web OPFS 环境暂且直接读取后做 MD5 替代
    val bytes = readLocalFileBytes(filePath) ?: return null
    return bytes.calculateMd5()
}

actual suspend fun copyLocalFile(sourcePath: String, destPath: String): Boolean {
    val storage = LocalFileStorage()
    val bytes = storage.readBytesFromFile(sourcePath) ?: return false
    storage.saveBytesToFile(destPath, bytes)
    return true
}

private infix fun Int.add(other: Int): Int = this + other

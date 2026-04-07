package org.gemini.ui.forge.utils

import org.gemini.ui.forge.service.LocalFileStorage

actual suspend fun readLocalFileBytes(filePath: String): ByteArray? {
    val storage = LocalFileStorage()
    return storage.readBytesFromFile(filePath)
}

actual suspend fun isFileExists(filePath: String): Boolean {
    val storage = LocalFileStorage()
    return storage.exists(filePath)
}

actual fun ByteArray.calculateMd5(): String {
    // 在 JS 环境下计算哈希，通常由于其异步性质，同步接口较为复杂
    // 我们暂时采用简单的字符串哈希或通过 TextEncoder 处理。
    // 为了满足 expect 签名，我们先实现一个基础的快速哈希
    var hash = 0
    if (this.isEmpty()) return "0"
    for (i in 0 until this.size) {
        val char = this[i].toInt()
        hash = ((hash shl 5) - hash) + char
        hash = hash or 0 // Convert to 32bit integer
    }
    return hash.toString(16)
}

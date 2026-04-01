package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 跨平台将字节数组转换为 ImageBitmap
 */
expect fun ByteArray.toImageBitmap(): ImageBitmap

/**
 * 处理各种图片源：Base64 字符串、HTTP 链接或本地物理文件路径。
 */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun String.decodeBase64ToBitmap(): ImageBitmap? {
    return try {
        if (this.startsWith("data:image")) {
            val pureBase64 = if (this.contains(",")) this.substringAfter(",") else this
            val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)
            bytes.toImageBitmap()
        } else if (this.startsWith("http")) {
            // Http 链接由于涉及到异步网络加载，不应在这里同步返回 ImageBitmap，交由 AsyncImage 处理
            null 
        } else {
            // 是一个本地绝对路径
            val bytes = readLocalFileBytes(this)
            bytes?.toImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.gemini.ui.forge.model.ui.SerialRect

/**
 * 跨平台将字节数组转换为 ImageBitmap
 */
expect fun ByteArray.toImageBitmap(): ImageBitmap

/**
 * 跨平台根据坐标区域裁剪图片，并可选进行强制等比缩放。
 */
expect suspend fun cropImage(
    imageSource: String, 
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean = false,
    forceWidth: Int? = null,
    forceHeight: Int? = null
): ByteArray?

/**
 * 一键去除图片四周的透明空白区域
 */
expect suspend fun trimTransparency(imageSource: String): ByteArray?

/**
 * 跨平台获取图片尺寸
 */
expect suspend fun getImageSize(uri: String): Pair<Int, Int>?

/**
 * 处理各种图片源：Base64 字符串、HTTP 链接或本地物理文件路径。
 */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
suspend fun String.decodeBase64ToBitmap(): ImageBitmap? {
    return try {
        if (this.startsWith("data:image")) {
            val pureBase64 = if (this.contains(",")) this.substringAfter(",") else this
            val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)
            bytes.toImageBitmap()
        } else if (this.startsWith("http")) {
            null 
        } else {
            val bytes = readLocalFileBytes(this)
            bytes?.toImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

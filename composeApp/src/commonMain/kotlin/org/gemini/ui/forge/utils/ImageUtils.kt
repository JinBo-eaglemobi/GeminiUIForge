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
 * 扫描图片非透明区域的物理包围盒，返回 (left, top, right, bottom)
 */
expect suspend fun getNonTransparentBounds(imageSource: String): SerialRect?

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
            AppLogger.d("ImageUtils", "🖼️ 已从 Base64 解码图片 (${bytes.size / 1024} KB)")
            bytes.toImageBitmap()
        } else if (this.startsWith("http")) {
            AppLogger.d("ImageUtils", "🌐 正在跳过 HTTP 链接的同步解码: $this")
            null 
        } else {
            val bytes = readLocalFileBytes(this)
            if (bytes != null) {
                AppLogger.d("ImageUtils", "📁 已从本地文件加载图片: $this (${bytes.size / 1024} KB)")
                bytes.toImageBitmap()
            } else {
                AppLogger.e("ImageUtils", "❌ 无法读取本地图片文件: $this")
                null
            }
        }
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 图片解码失败", e)
        null
    }
}

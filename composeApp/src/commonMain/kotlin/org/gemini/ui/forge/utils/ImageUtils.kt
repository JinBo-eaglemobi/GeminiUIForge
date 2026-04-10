package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.gemini.ui.forge.model.ui.SerialRect

/**
 * 跨平台将字节数组转换为 ImageBitmap
 */
expect fun ByteArray.toImageBitmap(): ImageBitmap

/**
 * 跨平台根据坐标区域裁剪图片。
 * @param imageSource 图片源（本地路径、Base64 或 URL）
 * @param bounds 裁剪区域（基于逻辑画布的绝对坐标）
 * @param logicalWidth 逻辑画布的总宽度
 * @param logicalHeight 逻辑画布的总高度
 * @return 裁剪后的图片字节数组，如果失败则返回 null
 */
expect suspend fun cropImage(
    imageSource: String, 
    bounds: org.gemini.ui.forge.model.ui.SerialRect,
    logicalWidth: Float,
    logicalHeight: Float
): ByteArray?

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
            // Http 链接由 AsyncImage 处理
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

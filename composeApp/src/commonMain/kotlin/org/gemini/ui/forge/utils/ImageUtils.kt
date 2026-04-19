package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.gemini.ui.forge.model.ui.SerialRect
import org.jetbrains.skia.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 处理各种图片源：Base64 字符串、HTTP 链接或本地物理文件路径。
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun String.decodeBase64ToBitmap(): ImageBitmap? {
    return try {
        if (this.startsWith("data:image")) {
            val pureBase64 = if (this.contains(",")) this.substringAfter(",") else this
            val bytes = Base64.decode(pureBase64)
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

/**
 * 跨平台将字节数组转换为 ImageBitmap (基于 Skia)
 */
fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}

/**
 * 获取图片原始像素尺寸 (基于 Skia)
 */
suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    val bytes = readLocalFileBytes(uri) ?: return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        Pair(image.width, image.height)
    } catch (e: Exception) {
        null
    }
}

/**
 * 跨平台图像裁剪与缩放逻辑 (基于 Skia)
 */
suspend fun cropImage(
    imageSource: String,
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean = true,
    forceWidth: Int? = null,
    forceHeight: Int? = null
): ByteArray? {
    val bytes = readLocalFileBytes(imageSource) ?: return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        
        val scaleX = image.width.toFloat() / logicalWidth
        val scaleY = image.height.toFloat() / logicalHeight
        
        val srcL = bounds.left * scaleX
        val srcT = bounds.top * scaleY
        val srcW = bounds.width * scaleX
        val srcH = bounds.height * scaleY
        
        val outputWidth = forceWidth ?: srcW.toInt().coerceAtLeast(1)
        val outputHeight = forceHeight ?: srcH.toInt().coerceAtLeast(1)
        
        // 使用 Surface 重新绘制以实现裁剪与缩放
        val surface = Surface.makeRasterN32Premul(outputWidth, outputHeight)
        val canvas = surface.canvas
        
        val srcRect = Rect.makeXYWH(srcL, srcT, srcW, srcH)
        val dstRect = Rect.makeWH(outputWidth.toFloat(), outputHeight.toFloat())
        
        // drawImageRect 使用高质量插值算法解决缩小导致的模糊问题
        val paint = Paint().apply { 
            isAntiAlias = true
        }
        canvas.drawImageRect(image, srcRect, dstRect, SamplingMode.MITCHELL, paint, true)
        
        val finalImage = surface.makeImageSnapshot()
        val format = if (isPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
        finalImage.encodeToData(format, 100)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 跨平台裁剪失败", e)
        null
    }
}

/**
 * 跨平台透明度边界检测 (基于 Skia)
 */
suspend fun getNonTransparentBounds(imageSource: String): SerialRect? {
    val bytes = readLocalFileBytes(imageSource) ?: return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        val width = image.width
        val height = image.height
        
        // 利用 Bitmap 提取像素
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height, true)
        val canvas = Canvas(bitmap)
        canvas.drawImage(image, 0f, 0f)
        
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var found = false
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = bitmap.getColor(x, y)
                // 获取 Alpha 字节 (Skia getColor 的 ColorInt 中，Alpha 位于最高 8 位)
                val alpha = (color ushr 24) and 0xFF
                if (alpha > 8) { 
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    found = true
                }
            }
        }
        
        if (found) {
            SerialRect(minX.toFloat(), minY.toFloat(), (maxX - minX + 1).toFloat(), (maxY - minY + 1).toFloat())
        } else null
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 边界检测异常", e)
        null
    }
}

/**
 * 自动切除图片四周的透明留白 (基于 Skia)
 */
suspend fun trimTransparency(imageSource: String): ByteArray? {
    val bounds = getNonTransparentBounds(imageSource) ?: return null
    val size = getImageSize(imageSource) ?: return null
    return cropImage(imageSource, bounds, size.first.toFloat(), size.second.toFloat(), isPng = true)
}

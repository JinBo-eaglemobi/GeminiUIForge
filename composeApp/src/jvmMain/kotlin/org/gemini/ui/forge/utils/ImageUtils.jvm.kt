package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.ui.SerialRect

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val image = ImageIO.read(ByteArrayInputStream(this))
    return image.toComposeImageBitmap()
}

actual suspend fun cropImage(
    imageSource: String, 
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean,
    forceWidth: Int?,
    forceHeight: Int?
): ByteArray? = withContext(Dispatchers.IO) {
    return@withContext try {
        val fullBytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return@withContext null

        val original: BufferedImage = ImageIO.read(ByteArrayInputStream(fullBytes)) ?: return@withContext null

        val scaleX = original.width.toFloat() / logicalWidth
        val scaleY = original.height.toFloat() / logicalHeight

        val left = (bounds.left * scaleX).toInt().coerceIn(0, original.width - 1)
        val top = (bounds.top * scaleY).toInt().coerceIn(0, original.height - 1)
        var width = (bounds.width * scaleX).toInt().coerceAtLeast(1)
        var height = (bounds.height * scaleY).toInt().coerceAtLeast(1)

        if (left + width > original.width) width = original.width - left
        if (top + height > original.height) height = original.height - top

        var resultImage = original.getSubimage(left, top, width, height)

        if (forceWidth != null && forceHeight != null) {
            // 使用高质量的多步平滑缩放
            val type = if (isPng) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            val scaled = BufferedImage(forceWidth, forceHeight, type)
            val g2d = scaled.createGraphics()
            
            // 关键优化：针对缩小操作使用更好的插值算法
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // 使用底层高质量缩放
            val tmp = resultImage.getScaledInstance(forceWidth, forceHeight, Image.SCALE_SMOOTH)
            g2d.drawImage(tmp, 0, 0, null)
            g2d.dispose()
            resultImage = scaled
        }

        val out = ByteArrayOutputStream()
        val format = if (isPng) "png" else "jpg"
        ImageIO.write(resultImage, format, out)
        out.toByteArray()
    } catch (e: Exception) {
        org.gemini.ui.forge.utils.AppLogger.e("ImageUtils", "JVM 裁剪缩放失败", e)
        null
    }
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return@withContext null

        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
        val width = img.width
        val height = img.height
        
        var minX = width; var minY = height; var maxX = -1; var maxY = -1

        // 像素级扫描寻找非透明边界
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (img.getRGB(x, y) shr 24) and 0xff
                if (alpha > 5) { // 阈值，过滤掉极其微弱的杂色
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) return@withContext bytes // 全透明图片返回原样

        val cropped = img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
        val out = ByteArrayOutputStream()
        ImageIO.write(cropped, "png", out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

actual suspend fun getNonTransparentBounds(imageSource: String): SerialRect? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return@withContext null

        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
        val width = img.width
        val height = img.height

        var minX = width; var minY = height; var maxX = -1; var maxY = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (img.getRGB(x, y) shr 24) and 0xff
                if (alpha > 5) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) return@withContext null

        SerialRect(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
    } catch (e: Exception) {
        null
    }
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = if (uri.startsWith("data:image")) {
            val pureBase64 = if (uri.contains(",")) uri.substringAfter(",") else uri
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(uri)
        } ?: return@withContext null
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
        Pair(image.width, image.height)
    } catch (e: Exception) { null }
}

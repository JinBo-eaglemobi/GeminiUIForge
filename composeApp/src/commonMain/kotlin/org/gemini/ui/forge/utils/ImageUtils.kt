package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
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
        val srcW = (bounds.width * scaleX).coerceAtLeast(1f)
        val srcH = (bounds.height * scaleY).coerceAtLeast(1f)
        
        val targetWidth = (forceWidth ?: srcW.toInt()).toFloat().coerceAtLeast(1f)
        val targetHeight = (forceHeight ?: srcH.toInt()).toFloat().coerceAtLeast(1f)
        
        val paint = Paint().apply { isAntiAlias = true }
        val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

        // 1. 先进行裁剪，得到原始比例的位图
        val cropSurface = Surface.makeRasterN32Premul(srcW.toInt(), srcH.toInt())
        cropSurface.canvas.drawImageRect(
            image, 
            Rect.makeXYWH(srcL, srcT, srcW, srcH), 
            Rect.makeWH(srcW, srcH), 
            filter, paint, true
        )
        var currentImage = cropSurface.makeImageSnapshot()
        var currentWidth = srcW
        var currentHeight = srcH

        // 2. 分步缩放逻辑 (Step-wise scaling)
        var stepCount = 0
        AppLogger.d("ImageUtils", "📐 开始缩放处理: [${srcW.toInt()}x${srcH.toInt()}] -> [${targetWidth.toInt()}x${targetHeight.toInt()}]")
        
        while (true) {
            val ratioW = targetWidth / currentWidth
            val ratioH = targetHeight / currentHeight
            
            // 如果缩放比例在 [0.5, 2.0] 之间，则可以进行最后一步缩放
            if (ratioW >= 0.5f && ratioW <= 2.0f && ratioH >= 0.5f && ratioH <= 2.0f) {
                break
            }
            
            stepCount++
            // 计算单步缩放目标，单步比例限制在 0.5 (缩小) 或 2.0 (放大)
            val nextStepWidth = when {
                ratioW < 0.5f -> currentWidth * 0.5f
                ratioW > 2.0f -> currentWidth * 2.0f
                else -> targetWidth
            }
            val nextStepHeight = when {
                ratioH < 0.5f -> currentHeight * 0.5f
                ratioH > 2.0f -> currentHeight * 2.0f
                else -> targetHeight
            }
            
            val stepW = nextStepWidth.toInt().coerceAtLeast(1)
            val stepH = nextStepHeight.toInt().coerceAtLeast(1)
            
            AppLogger.d("ImageUtils", "⏳ 缩放步骤 #$stepCount: 比例 [W:${(ratioW * 100).toInt()}%, H:${(ratioH * 100).toInt()}%] -> 尺寸 [${stepW}x${stepH}]")
            
            val stepSurface = Surface.makeRasterN32Premul(stepW, stepH)
            stepSurface.canvas.drawImageRect(
                currentImage,
                Rect.makeWH(currentWidth, currentHeight),
                Rect.makeWH(stepW.toFloat(), stepH.toFloat()),
                filter, paint, true
            )
            currentImage = stepSurface.makeImageSnapshot()
            currentWidth = stepW.toFloat()
            currentHeight = stepH.toFloat()
        }
        if (stepCount > 0) {
            AppLogger.d("ImageUtils", "✅ 分步缩放完成，共执行 $stepCount 步中间转换")
        }

        // 3. 最终缩放至目标尺寸并导出
        val finalSurface = Surface.makeRasterN32Premul(targetWidth.toInt(), targetHeight.toInt())
        finalSurface.canvas.drawImageRect(
            currentImage,
            Rect.makeWH(currentWidth, currentHeight),
            Rect.makeWH(targetWidth, targetHeight),
            filter, paint, true
        )
        
        val finalImage = finalSurface.makeImageSnapshot()
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

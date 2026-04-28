package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.gemini.ui.forge.data.TemplateFile
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

/** 扩展：支持直接从 TemplateFile 解码 */
suspend fun TemplateFile.decodeToBitmap(): ImageBitmap? = this.getAbsolutePath().decodeBase64ToBitmap()

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

/** 扩展：获取 TemplateFile 图片尺寸 */
suspend fun TemplateFile.getImageSize(): Pair<Int, Int>? = getImageSize(this.getAbsolutePath())

/**
 * 核心功能：将当前的拉伸/九宫格效果固化导出为一张新的位图字节流。
 */
suspend fun bakeNinePatchImage(
    sourcePath: String,
    targetWidth: Int,
    targetHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    resizeMode: org.gemini.ui.forge.model.ui.ImageResizeMode,
    ninePatchConfig: org.gemini.ui.forge.model.ui.NinePatchConfig
): ByteArray? {
    val bytes = readLocalFileBytes(sourcePath) ?: return null
    return try {
        val srcImage = Image.makeFromEncoded(bytes)
        val srcW = srcImage.width
        val srcH = srcImage.height
        
        val paint = Paint().apply { isAntiAlias = true }
        val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

        val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
        val canvas = surface.canvas
        
        // 计算内容在画布上的居中偏移
        val cX = (targetWidth - contentWidth) / 2f
        val cY = (targetHeight - contentHeight) / 2f
        
        // 限定绘制范围在内容区域内（解决等比铺满可能超出内容区的问题）
        canvas.clipRect(Rect.makeXYWH(cX, cY, contentWidth.toFloat(), contentHeight.toFloat()))

        when (resizeMode) {
            org.gemini.ui.forge.model.ui.ImageResizeMode.STRETCH -> {
                canvas.drawImageRect(srcImage, Rect.makeWH(srcW.toFloat(), srcH.toFloat()), Rect.makeXYWH(cX, cY, contentWidth.toFloat(), contentHeight.toFloat()), filter, paint, true)
            }
            org.gemini.ui.forge.model.ui.ImageResizeMode.FIT_WITH_PADDING -> {
                val scale = minOf(contentWidth.toFloat() / srcW, contentHeight.toFloat() / srcH)
                val dw = srcW * scale
                val dh = srcH * scale
                val dx = cX + (contentWidth - dw) / 2f
                val dy = cY + (contentHeight - dh) / 2f
                canvas.drawImageRect(srcImage, Rect.makeWH(srcW.toFloat(), srcH.toFloat()), Rect.makeXYWH(dx, dy, dw, dh), filter, paint, true)
            }
            org.gemini.ui.forge.model.ui.ImageResizeMode.CROP_TO_FILL -> {
                val scale = maxOf(contentWidth.toFloat() / srcW, contentHeight.toFloat() / srcH)
                val dw = srcW * scale
                val dh = srcH * scale
                val dx = cX + (contentWidth - dw) / 2f
                val dy = cY + (contentHeight - dh) / 2f
                canvas.drawImageRect(srcImage, Rect.makeWH(srcW.toFloat(), srcH.toFloat()), Rect.makeXYWH(dx, dy, dw, dh), filter, paint, true)
            }
            org.gemini.ui.forge.model.ui.ImageResizeMode.NINE_PATCH -> {
                val l = ninePatchConfig.left.toFloat()
                val t = ninePatchConfig.top.toFloat()
                val r = ninePatchConfig.right.toFloat()
                val b = ninePatchConfig.bottom.toFloat()

                val dw = contentWidth.toFloat()
                val dh = contentHeight.toFloat()

                fun drawPart(sx: Float, sy: Float, sw: Float, sh: Float, dx: Float, dy: Float, dwPart: Float, dhPart: Float) {
                    if (sw <= 0 || sh <= 0 || dwPart <= 0 || dhPart <= 0) return
                    canvas.drawImageRect(srcImage, Rect.makeXYWH(sx, sy, sw, sh), Rect.makeXYWH(cX + dx, cY + dy, dwPart, dhPart), filter, paint, true)
                }

                drawPart(0f, 0f, l, t, 0f, 0f, l, t)
                drawPart(l, 0f, srcW - l - r, t, l, 0f, dw - l - r, t)
                drawPart(srcW - r, 0f, r, t, dw - r, 0f, r, t)
                drawPart(0f, t, l, srcH - t - b, 0f, t, l, dh - t - b)
                drawPart(l, t, srcW - l - r, srcH - t - b, l, t, dw - l - r, dh - t - b)
                drawPart(srcW - r, t, r, srcH - t - b, dw - r, t, r, dh - t - b)
                drawPart(0f, srcH - b, l, b, 0f, dh - b, l, b)
                drawPart(l, srcH - b, srcW - l - r, b, l, dh - b, dw - l - r, b)
                drawPart(srcW - r, srcH - b, r, b, dw - r, dh - b, r, b)
            }
        }

        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 烘焙图片失败: ${e.message}")
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

        var stepCount = 0
        while (true) {
            val ratioW = targetWidth / currentWidth
            val ratioH = targetHeight / currentHeight
            if (ratioW >= 0.5f && ratioW <= 2.0f && ratioH >= 0.5f && ratioH <= 2.0f) break
            
            stepCount++
            val nextStepWidth = if (ratioW < 0.5f) currentWidth * 0.5f else if (ratioW > 2.0f) currentWidth * 2.0f else targetWidth
            val nextStepHeight = if (ratioH < 0.5f) currentHeight * 0.5f else if (ratioH > 2.0f) currentHeight * 2.0f else targetHeight
            
            val stepW = nextStepWidth.toInt().coerceAtLeast(1)
            val stepH = nextStepHeight.toInt().coerceAtLeast(1)
            
            val stepSurface = Surface.makeRasterN32Premul(stepW, stepH)
            stepSurface.canvas.drawImageRect(currentImage, Rect.makeWH(currentWidth, currentHeight), Rect.makeWH(stepW.toFloat(), stepH.toFloat()), filter, paint, true)
            currentImage = stepSurface.makeImageSnapshot()
            currentWidth = stepW.toFloat()
            currentHeight = stepH.toFloat()
        }

        val finalSurface = Surface.makeRasterN32Premul(targetWidth.toInt(), targetHeight.toInt())
        finalSurface.canvas.drawImageRect(currentImage, Rect.makeWH(currentWidth, currentHeight), Rect.makeWH(targetWidth, targetHeight), filter, paint, true)
        
        val format = if (isPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
        finalSurface.makeImageSnapshot().encodeToData(format, 100)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 跨平台裁剪失败", e)
        null
    }
}

/** 扩展：从 TemplateFile 裁剪图片 */
suspend fun TemplateFile.crop(
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean = true,
    forceWidth: Int? = null,
    forceHeight: Int? = null
): ByteArray? = cropImage(this.getAbsolutePath(), bounds, logicalWidth, logicalHeight, isPng, forceWidth, forceHeight)

/**
 * 跨平台透明度边界检测 (基于 Skia)
 */
suspend fun getNonTransparentBounds(imageSource: String): SerialRect? {
    val bytes = readLocalFileBytes(imageSource) ?: return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        val width = image.width
        val height = image.height
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height, true)
        val canvas = Canvas(bitmap)
        canvas.drawImage(image, 0f, 0f)
        
        var minX = width; var minY = height; var maxX = 0; var maxY = 0; var found = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = bitmap.getColor(x, y)
                val alpha = (color ushr 24) and 0xFF
                if (alpha > 8) { 
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                    found = true
                }
            }
        }
        if (found) SerialRect(minX.toFloat(), minY.toFloat(), (maxX - minX + 1).toFloat(), (maxY - minY + 1).toFloat()) else null
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

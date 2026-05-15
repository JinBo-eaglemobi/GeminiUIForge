package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.jetbrains.skia.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * ImageUtils.kt
 * 
 * 图像处理工具类，提供基于 Skia 引擎的跨平台图像解码、裁剪、缩放、九宫格烘焙及透明度检测等核心功能。
 * 适用于 Compose Multiplatform 项目中的 commonMain 模块。
 */

/**
 * 将字符串（支持 Base64、HTTP 链接或本地物理文件路径）解码为 [ImageBitmap]。
 * 
 * @receiver 图像源字符串。
 *           - 若以 "data:image" 开头，则视为 Base64 字符串解码。
 *           - 若以 "http" 开头，目前会跳过同步解码并返回 null。
 *           - 否则视为本地物理文件路径。
 * @return 解码后的 [ImageBitmap]，若解码失败或不支持则返回 null。
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
 * [TemplateFile] 的扩展方法：直接将模板文件内容解码为 [ImageBitmap]。
 * 
 * @return 解码后的 [ImageBitmap]，失败返回 null。
 */
suspend fun TemplateFile.decodeToBitmap(): ImageBitmap? = this.getAbsolutePath().decodeBase64ToBitmap()

/**
 * 将字节数组（ByteArray）转换为 Compose [ImageBitmap]。
 * 内部基于 Skia 的 [Image.makeFromEncoded] 实现。
 * 
 * @receiver 包含图像数据的字节数组（如 PNG/JPEG 编码数据）。
 * @return 转换后的 [ImageBitmap]。
 */
fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}

/**
 * 获取指定路径或 URI 对应图片的原始像素尺寸。
 * 
 * @param uri 图像文件路径。
 * @return 包含 (宽度, 高度) 的 [Pair]，若读取失败则返回 null。
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
 * [TemplateFile] 的扩展方法：获取模板文件的图片像素尺寸。
 * 
 * @return 包含 (宽度, 高度) 的 [Pair]，失败返回 null。
 */
suspend fun TemplateFile.getImageSize(): Pair<Int, Int>? = getImageSize(this.getAbsolutePath())

/**
 * 烘焙九宫格（Nine-patch）图像。
 * 根据指定的缩放模式和九宫格配置，将原始图像渲染到目标尺寸的画布上，并导出为位图字节流。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @param targetWidth 生成的目标位图总宽度（像素）。
 * @param targetHeight 生成的目标位图总高度（像素）。
 * @param contentWidth 实际图像内容在画布中占据的宽度。
 * @param contentHeight 实际图像内容在画布中占据的高度。
 * @param resizeMode 缩放模式（拉伸、等比适应、等比填充、九宫格）。
 * @param ninePatchConfig 九宫格切片配置（左、上、右、下边距）。
 * @return 烘焙后的 PNG 格式字节数组，失败返回 null。
 */
suspend fun bakeNinePatchImage(
    imageBytes: ByteArray,
    targetWidth: Int,
    targetHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    resizeMode: org.gemini.ui.forge.model.ui.ImageResizeMode,
    ninePatchConfig: org.gemini.ui.forge.model.ui.NinePatchConfig
): ByteArray? {
    return try {
        val srcImage = Image.makeFromEncoded(imageBytes)
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
 * 烘焙九宫格（Nine-patch）图像（从路径读取源文件）。
 * 
 * @param sourcePath 原始图像的本地物理路径。
 * @param targetWidth 生成的目标位图总宽度（像素）。
 * @param targetHeight 生成的目标位图总高度（像素）。
 * @param contentWidth 实际图像内容在画布中占据的宽度。
 * @param contentHeight 实际图像内容在画布中占据的高度。
 * @param resizeMode 缩放模式。
 * @param ninePatchConfig 九宫格切片配置。
 * @return 烘焙后的 PNG 格式字节数组，失败返回 null。
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
    return bakeNinePatchImage(bytes, targetWidth, targetHeight, contentWidth, contentHeight, resizeMode, ninePatchConfig)
}

/**
 * 跨平台图像裁剪与缩放逻辑（基于 Skia）。使用字节数组作为输入源。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @param bounds 裁剪区域（相对于 logicalWidth/Height 的比例坐标）。
 * @param originalWidth 输入坐标对应的逻辑画布宽度。
 * @param originalHeight 输入坐标对应的逻辑画布高度。
 * @param isPng 是否导出为 PNG 格式（false 为 JPEG）。
 * @param forceWidth 强制指定导出的图像宽度（像素），为空则使用裁剪区域的原始大小。
 * @param forceHeight 强制指定导出的图像高度（像素），为空则使用裁剪区域的原始大小。
 * @return 裁剪并缩放后的图像字节数组，失败返回 null。
 */
fun cropImage(
    imageBytes: ByteArray,
    bounds: SerialRect,
    originalWidth: Float,
    originalHeight: Float,
    isPng: Boolean = true,
    forceWidth: Int? = null,
    forceHeight: Int? = null
): ByteArray? {
    return try {
        val image = Image.makeFromEncoded(imageBytes)
        val scaleX = image.width.toFloat() / originalWidth
        val scaleY = image.height.toFloat() / originalHeight
        
        val srcL = bounds.left * scaleX
        val srcT = bounds.top * scaleY
        val srcW = (bounds.width * scaleX).coerceAtLeast(1f)
        val srcH = (bounds.height * scaleY).coerceAtLeast(1f)

        // 1. 将 Image 转换为 Bitmap (零拷贝/完美继承属性)
        val srcBitmap = Bitmap.makeFromImage(image)

        // 2. 使用 extractSubset 进行裁剪
        val croppedBitmap = Bitmap()
        val extractSuccess = srcBitmap.extractSubset(
            croppedBitmap, 
            IRect.makeXYWH(
                srcL.toInt().coerceAtMost(image.width - 1),
                srcT.toInt().coerceAtMost(image.height - 1),
                srcW.toInt().coerceAtMost(image.width - srcL.toInt()),
                srcH.toInt().coerceAtMost(image.height - srcT.toInt())
            )
        )

        if (!extractSuccess) {
            AppLogger.e("ImageUtils", "❌ Bitmap.extractSubset 裁剪失败")
            return null
        }

        val format = if (isPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
        val targetWidth = (forceWidth ?: srcW.toInt()).toFloat().coerceAtLeast(1f)
        val targetHeight = (forceHeight ?: srcH.toInt()).toFloat().coerceAtLeast(1f)

        // 如果不需要缩放，直接将裁剪后的 Bitmap 编码返回
        if (srcW == targetWidth && srcH == targetHeight) {
            val finalImage = Image.makeFromBitmap(croppedBitmap)
            return finalImage.encodeToData(format, 100)?.bytes
        }

        // 3. 一次性直接缩放至目标尺寸
        val currentImage = Image.makeFromBitmap(croppedBitmap)
        val finalSurface = Surface.makeRasterN32Premul(targetWidth.toInt(), targetHeight.toInt())
        val paint = Paint().apply { isAntiAlias = true }
        val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

        finalSurface.canvas.drawImageRect(
            currentImage, 
            Rect.makeWH(srcW, srcH), 
            Rect.makeWH(targetWidth, targetHeight), 
            filter, paint, true
        )
        
        finalSurface.makeImageSnapshot().encodeToData(format, 100)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 跨平台裁剪失败 (从字节)", e)
        null
    }
}

/**
 * 跨平台图像裁剪与缩放逻辑（从文件路径读取）。
 * 
 * @param imageSource 原始图像路径。
 * @param bounds 裁剪区域。
 * @param logicalWidth 逻辑宽度。
 * @param logicalHeight 逻辑高度。
 * @param isPng 是否为 PNG。
 * @param forceWidth 强制宽度。
 * @param forceHeight 强制高度。
 * @return 裁剪后的字节数组。
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
    return cropImage(bytes, bounds, logicalWidth, logicalHeight, isPng, forceWidth, forceHeight)
}

/** 
 * [TemplateFile] 的扩展方法：裁剪图片。
 * 
 * @param bounds 裁剪区域。
 * @param logicalWidth 逻辑宽度。
 * @param logicalHeight 逻辑高度。
 * @param isPng 是否为 PNG。
 * @param forceWidth 强制宽度。
 * @param forceHeight 强制高度。
 * @return 裁剪后的字节数组。
 */
suspend fun TemplateFile.crop(
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean = true,
    forceWidth: Int? = null,
    forceHeight: Int? = null
): ByteArray? = cropImage(this.getAbsolutePath(), bounds, logicalWidth, logicalHeight, isPng, forceWidth, forceHeight)

/**
 * 检测图片中非透明部分的边界区域（Bounding Box）。
 * 使用 alpha 通道阈值 (>8) 进行判定。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @return 包含非透明区域坐标的 [SerialRect]，若全透明或处理失败则返回 null。
 */
fun getNonTransparentBounds(imageBytes: ByteArray): SerialRect? {
    return try {
        val image = Image.makeFromEncoded(imageBytes)
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
        if (found) SerialRect(minX.toFloat(), minY.toFloat(), maxX.toFloat() + 1f, maxY.toFloat() + 1f) else null
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 字节数组边界检测异常", e)
        null
    }
}

/**
 * 检测图片中非透明部分的边界区域（从文件路径读取）。
 * 
 * @param imageSource 图像文件路径。
 * @return 非透明区域的 [SerialRect]。
 */
suspend fun getNonTransparentBounds(imageSource: String): SerialRect? {
    val bytes = readLocalFileBytes(imageSource) ?: return null
    return getNonTransparentBounds(bytes)
}

/**
 * 自动切除图片四周的透明留白（Trim Transparency）。
 * 内部首先检测非透明边界，然后根据原始尺寸进行裁剪导出。
 * 
 * @param imageSource 原始图像路径。
 * @return 切除白边后的 PNG 格式字节数组，若无非透明像素则返回 null。
 */
suspend fun trimTransparency(imageSource: String): ByteArray? {
    val bounds = getNonTransparentBounds(imageSource) ?: return null
    val size = getImageSize(imageSource) ?: return null
    return cropImage(imageSource, bounds, size.first.toFloat(), size.second.toFloat(), isPng = true)
}

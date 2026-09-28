package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.ImageScaleConfig
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
 * 跨平台通用图像字节获取函数：无缝兼容 HTTP/HTTPS 网络资源、Base64 Data URI、本地绝对物理路径
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun fetchImageBytes(source: String): ByteArray? {
    return try {
        when {
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
                val resp: HttpResponse = org.gemini.ui.forge.data.remote.NetworkClient.shared.get(source)
                if (resp.status.value == 200) {
                    resp.readRawBytes()
                } else null
            }
            source.startsWith("data:image", ignoreCase = true) -> {
                val b64 = if (source.contains(",")) source.substringAfter(",") else source
                Base64.decode(b64)
            }
            else -> readLocalFileBytes(source)
        }
    } catch (e: Exception) {
        AppLogger.w("ImageUtils", "获取图像字节失败 [$source]: ${e.message}")
        null
    }
}

/**
 * 将字符串（支持 Base64、HTTP/HTTPS 网络链接或本地物理文件路径）解码为 [ImageBitmap]。
 * 
 * @receiver 图像源字符串。
 *           - 若以 "data:image" 开头，则视为 Base64 字符串解码。
 *           - 若以 "http://" 或 "https://" 开头，则发起网络请求下载并解码。
 *           - 否则视为本地物理文件路径。
 * @return 解码后的 [ImageBitmap]，若解码失败或不支持则返回 null。
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun String.decodeBase64ToBitmap(): ImageBitmap? {
    return try {
        val bytes = fetchImageBytes(this)
        if (bytes != null) {
            bytes.toImageBitmap()
        } else {
            AppLogger.e("ImageUtils", "❌ 无法获取图片资源: $this")
            null
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
    val bytes = if (uri.startsWith("data:image")) {
        val b64 = if (uri.contains(",")) uri.substringAfter(",") else uri
        try {
            Base64.decode(b64)
        } catch (_: Exception) {
            null
        }
    } else {
        readLocalFileBytes(uri)
    } ?: return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        Pair(image.width, image.height)
    } catch (_: Exception) {
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
fun bakeNinePatchImage(
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
        
        // 可选的缩放模式测试：
        // val filter = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)      // 1. 邻近采样 (Nearest Neighbor) - 性能最高，边缘锐利但可能有锯齿
        // val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)       // 2. 双线性过滤 (Bilinear) - 平滑，性能均衡
        val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)     // 3. 三线性过滤 (Trilinear) - 默认推荐，缩放效果好
        // val filter = FilterCubic(1/3f, 1/3f)                               // 4. 双三次过滤 (Mitchell-Netravali) - 锐度与平滑的平衡
        // val filter = FilterCubic(0f, 0.5f)                                 // 5. 双三次过滤 (Catmull-Rom) - 较锐利，适合高质量缩放

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
 * 将图像按指定的物理目标宽高进行高质量重采样缩放并输出 PNG 字节。
 * 用于生图校验后按当前缩放大小的实际宽高物理落盘保存图片。
 *
 * @param imageBytes 原始图像字节
 * @param targetWidth 目标物理宽度（像素）
 * @param targetHeight 目标物理高度（像素）
 * @return 缩放后的 PNG 字节数组，失败返回 null。
 */
fun rescaleImageBytes(
    imageBytes: ByteArray,
    targetWidth: Int,
    targetHeight: Int
): ByteArray? {
    if (targetWidth <= 0 || targetHeight <= 0) return null
    return try {
        val srcImage = Image.makeFromEncoded(imageBytes)
        val srcW = srcImage.width
        val srcH = srcImage.height
        if (srcW == targetWidth && srcH == targetHeight) return imageBytes

        val paint = Paint().apply { isAntiAlias = true }
        val filter = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
        val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
        val canvas = surface.canvas
        canvas.drawImageRect(
            srcImage,
            Rect.makeWH(srcW.toFloat(), srcH.toFloat()),
            Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
            filter,
            paint,
            true
        )
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 缩放图像失败: ${e.message}")
        null
    }
}

/**
 * 将图像裁剪为仅保留非透明主体（Tight Crop，去除多余空白透明边距）。
 *
 * @param imageBytes 原始 PNG 字节数组。
 * @param alphaThreshold 不透明度判定阈值（0~255）。
 * @param padding 额外保留的安全边距（像素）。
 * @return 紧凑裁剪后的 PNG 字节数组，若无可裁剪区域则返回原图。
 */
suspend fun cropToOpaqueBounds(
    imageBytes: ByteArray,
    alphaThreshold: Int = 20,
    padding: Int = 2
): ByteArray {
    if (imageBytes.isEmpty()) return imageBytes
    return try {
        val opaqueBox = detectOpaqueBoundingBox(imageBytes, alphaThreshold) ?: return imageBytes
        val srcImage = Image.makeFromEncoded(imageBytes)
        val imgW = srcImage.width.toFloat()
        val imgH = srcImage.height.toFloat()

        val padL = (opaqueBox.left - padding).coerceAtLeast(0f)
        val padT = (opaqueBox.top - padding).coerceAtLeast(0f)
        val padR = (opaqueBox.right + padding).coerceAtMost(imgW)
        val padB = (opaqueBox.bottom + padding).coerceAtMost(imgH)
        val cropRect = SerialRect(padL, padT, padR, padB)

        cropImage(
            imageBytes = imageBytes,
            bounds = cropRect,
            originalWidth = imgW,
            originalHeight = imgH,
            isPng = true
        ) ?: imageBytes
    } catch (e: Exception) {
        AppLogger.w("ImageUtils", "紧凑裁剪透明边缘异常，回退原图: ${e.message}")
        imageBytes
    }
}

/**
 * 从原图中提取指定边界的子图（仅裁剪，不涉及缩放）。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @param bounds 裁剪区域（相对于 originalWidth/Height 的比例坐标）。
 * @param originalWidth 输入坐标对应的逻辑画布宽度。
 * @param originalHeight 输入坐标对应的逻辑画布高度。
 * @return 仅经过裁剪的原始尺寸子图的 [Image] 对象，失败返回 null。
 */
fun extractImageSubsetToImage(
    imageBytes: ByteArray,
    bounds: SerialRect,
    originalWidth: Float,
    originalHeight: Float
): Image? {
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
        AppLogger.d("ImageUtils", "✅ 成功截取子图: 截取尺寸 = ${croppedBitmap.width}x${croppedBitmap.height}")

        Image.makeFromBitmap(croppedBitmap)
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 跨平台裁剪提取子图失败", e)
        null
    }
}

/**
 * 从原图中提取指定边界的子图（仅裁剪，不涉及缩放），返回字节数组。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @param bounds 裁剪区域（相对于 originalWidth/Height 的比例坐标）。
 * @param originalWidth 输入坐标对应的逻辑画布宽度。
 * @param originalHeight 输入坐标对应的逻辑画布高度。
 * @param isPng 是否导出为 PNG 格式（false 为 JPEG）。
 * @return 仅经过裁剪的原始尺寸子图的字节数组，失败返回 null。
 */
fun extractImageSubset(
    imageBytes: ByteArray,
    bounds: SerialRect,
    originalWidth: Float,
    originalHeight: Float,
    isPng: Boolean = true
): ByteArray? {
    val image = extractImageSubsetToImage(imageBytes, bounds, originalWidth, originalHeight) ?: return null
    val format = if (isPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
    return image.encodeToData(format, 100)?.bytes
}

/**
 * 将图像缩放至目标尺寸 (Image 重载版)。
 *
 * @param image 原始 Skia Image 对象。
 * @param targetWidth 强制指定导出的图像宽度（像素）。
 * @param targetHeight 强制指定导出的图像高度（像素）。
 * @param isPng 是否导出为 PNG 格式。
 * @return 缩放后的图像字节数组，失败返回 null。
 */
fun scaleImage(
    image: Image,
    targetWidth: Int,
    targetHeight: Int,
    isPng: Boolean = true
): ByteArray? {
    return try {
        val format = if (isPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG

        if (image.width == targetWidth && image.height == targetHeight) {
            AppLogger.d("ImageUtils", "⏭️ 尺寸一致，跳过缩放阶段，直接导出")
            return image.encodeToData(format, 100)?.bytes
        }

        AppLogger.d("ImageUtils", "🔍 执行阶梯式高质量缩放: ${image.width}x${image.height} -> ${targetWidth}x${targetHeight}")

        // 1. 使用变量持有当前处理的图像源
        var currentImage = image
        var currentWidth = image.width
        var currentHeight = image.height

        // 2. 阶梯下采样循环：每次最多缩小一半，直到逼近目标尺寸的 2 倍
        // 针对 392 -> 20，路径为: 392 -> 196 -> 98 -> 49 -> 20
        while (currentWidth > targetWidth * 2 && currentHeight > targetHeight * 2) {
            val nextWidth = currentWidth / 2
            val nextHeight = currentHeight / 2

            val stepSurface = Surface.makeRasterN32Premul(nextWidth, nextHeight)
            // 阶梯缩放使用 LINEAR (Bilinear) 即可，配合 50% 缩放能完美融合像素
            // 可选的缩放模式测试：
            // val stepSampling = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)      // 1. 邻近采样 (Nearest Neighbor) - 性能最高，边缘锐利但可能有锯齿
//         val stepSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)       // 2. 双线性过滤 (Bilinear) - 平滑，性能均衡
            val stepSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)     // 3. 三线性过滤 (Trilinear) - 默认推荐，缩放效果好
//         val stepSampling = CubicResampler(1/3f, 1/3f)                               // 4. 双三次过滤 (Mitchell-Netravali) - 锐度与平滑的平衡
//         val stepSampling = CubicResampler(0f, 0.5f)                                 // 5. 双三次过滤 (Catmull-Rom) - 较锐利，适合高质量缩放
//         val stepSampling = CubicResampler(0f, 0.75f)                                 // 5. 双三次过滤 (Catmull-Rom) - 较锐利，适合高质量缩放

            stepSurface.canvas.drawImageRect(
                currentImage,
                Rect.makeWH(currentWidth.toFloat(), currentHeight.toFloat()),
                Rect.makeWH(nextWidth.toFloat(), nextHeight.toFloat()),
                stepSampling, null, true
            )

            val stepImage = stepSurface.makeImageSnapshot()
            // 如果不是初始传入的 image，则需要释放中间生成的临时 Image 内存
            if (currentImage != image) {
                currentImage.close()
            }

            currentImage = stepImage
            currentWidth = nextWidth
            currentHeight = nextHeight
        }

        // 3. 最后一棒：从最接近的中间尺寸（如 49x49）缩放到最终尺寸（20x20）
        val finalSurface = Surface.makeRasterN32Premul(targetWidth, targetHeight)

        // 此时两尺寸非常接近，使用双三次插值（Mitchell-Netravali 1/3, 1/3）可以获得极佳的图标质感
        // 如果想要边缘更硬一点，可以用 CubicResampler(0f, 0.5f)
        val finalSampling = CubicResampler(1/3f, 1/3f)

        finalSurface.canvas.drawImageRect(
            currentImage,
            Rect.makeWH(currentWidth.toFloat(), currentHeight.toFloat()),
            Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
            finalSampling, null, true
        )

        // 4. 释放最后一次的中间图内存
        if (currentImage != image) {
            currentImage.close()
        }

        finalSurface.makeImageSnapshot().encodeToData(format, 100)?.bytes
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 缩放图像失败", e)
        null
    }
}

/**
 * 将图像缩放至目标尺寸。
 *
 * @param imageBytes 原始图像的字节数组。
 * @param targetWidth 强制指定导出的图像宽度（像素）。
 * @param targetHeight 强制指定导出的图像高度（像素）。
 * @param isPng 是否导出为 PNG 格式。
 * @return 缩放后的图像字节数组，失败返回 null。
 */
fun scaleImage(
    imageBytes: ByteArray,
    targetWidth: Int,
    targetHeight: Int,
    isPng: Boolean = true
): ByteArray? {
    return try {
        val image = Image.makeFromEncoded(imageBytes)
        scaleImage(image, targetWidth, targetHeight, isPng)
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "❌ 缩放图像解码失败", e)
        null
    }
}

/**
 * 跨平台图像裁剪与缩放逻辑（基于 Skia）。使用字节数组作为输入源。
 * 此方法为向后兼容封装，内部将调用 extractImageSubset 与 scaleImage。
 * 
 * @param imageBytes 原始图像的字节数组。
 * @param bounds 裁剪区域（相对于 originalWidth/Height 的比例坐标）。
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
    val subsetBytes = extractImageSubset(imageBytes, bounds, originalWidth, originalHeight, isPng) ?: return null
    if (forceWidth == null && forceHeight == null) return subsetBytes
    
    // 如果只需要裁剪，这里尺寸交由 subset 自己决定
    return scaleImage(subsetBytes, forceWidth ?: 1, forceHeight ?: 1, isPng)
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

/** 上行图片转码格式策略 */
enum class TranscodeFormat { OFF, WEBP, JPEG }

/** 上行图片转码压缩策略实体 */
data class ImageTranscodePolicy(
    val format: TranscodeFormat = TranscodeFormat.WEBP,
    val maxDimension: Int = 1536, // 等比缩放最大边长（768=最省token档 / 1536=均衡高质档）
    val quality: Int = 82         // 有损压缩质量
)

/** 转码压缩输出结果 */
data class TranscodeResult(
    val bytes: ByteArray,
    val mimeType: String
)

/**
 * 统一上行图片转码压缩管道（发往 Gemini 大模型前调用）。
 *
 * 核心机制：
 * 1. 若策略为 OFF 或数据异常，安全透传原图；
 * 2. 若超出 maxDimension，执行高品质等比等宽等高下采样缩限，大幅降低 Gemini 图片 token 计算与 Base64 体积；
 * 3. 优先编码为高压缩比 WebP（带透明通道支持）；若平台编码失败自动优雅降级为 JPEG。
 */
suspend fun transcodeForUpload(
    imageBytes: ByteArray,
    policy: ImageTranscodePolicy = ImageTranscodePolicy()
): TranscodeResult {
    if (policy.format == TranscodeFormat.OFF || imageBytes.isEmpty()) {
        val mime = if (imageBytes.size > 8 && imageBytes[0] == 0x89.toByte() && imageBytes[1] == 0x50.toByte()) "image/png" else "image/jpeg"
        return TranscodeResult(imageBytes, mime)
    }

    return try {
        val srcImage = Image.makeFromEncoded(imageBytes)
        val origW = srcImage.width
        val origH = srcImage.height

        // 计算等比下采样尺寸
        val maxSide = maxOf(origW, origH)
        val scale = if (maxSide > policy.maxDimension) policy.maxDimension.toFloat() / maxSide.toFloat() else 1.0f

        val targetW = (origW * scale).toInt().coerceAtLeast(1)
        val targetH = (origH * scale).toInt().coerceAtLeast(1)

        val finalSurface = Surface.makeRasterN32Premul(targetW, targetH)
        val canvas = finalSurface.canvas
        val paint = Paint().apply { isAntiAlias = true }
        val sampling = SamplingMode.DEFAULT

        canvas.drawImageRect(
            srcImage,
            Rect.makeWH(origW.toFloat(), origH.toFloat()),
            Rect.makeWH(targetW.toFloat(), targetH.toFloat()),
            paint
        )

        val snapshot = finalSurface.makeImageSnapshot()

        // 尝试 WebP 编码；若失败降级为 JPEG
        val webpData = if (policy.format == TranscodeFormat.WEBP) {
            try {
                snapshot.encodeToData(EncodedImageFormat.WEBP, policy.quality)
            } catch (_: Throwable) { null }
        } else null

        if (webpData != null && webpData.bytes.isNotEmpty()) {
            TranscodeResult(webpData.bytes, "image/webp")
        } else {
            val jpegData = snapshot.encodeToData(EncodedImageFormat.JPEG, policy.quality)
            if (jpegData != null && jpegData.bytes.isNotEmpty()) {
                TranscodeResult(jpegData.bytes, "image/jpeg")
            } else {
                TranscodeResult(imageBytes, "image/png")
            }
        }
    } catch (e: Exception) {
        AppLogger.w("ImageUtils", "上行图片转码异常，回退原图: ${e.message}")
        TranscodeResult(imageBytes, "image/png")
    }
}

/**
 * 通用压缩编码结果载体（字节 + MIME 类型 + 文件扩展名三位一体）。
 * 与 [TranscodeResult] 的区别：面向本地磁盘缓存与通用分发场景，额外携带文件扩展名。
 */
data class CompactImage(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String
)

/**
 * 通用有损压缩转换中枢（全项目统一入口，MCP 截图 / 报告导出等场景共用）。
 *
 * 核心策略：WEBP(quality) 优先 → JPEG(quality) 降级 → 原始 PNG 兜底，
 * 任何一级编码失败均优雅降级，绝不让调用方因编码问题中断业务。
 *
 * @param sourceBytes 原始图片字节（通常为体积较大的 PNG 截图）
 * @param quality 压缩质量（0~100），默认 88（体积与画质均衡点）
 */
suspend fun compressToCompactImage(sourceBytes: ByteArray, quality: Int = 88): CompactImage {
    if (sourceBytes.isEmpty()) return CompactImage(sourceBytes, "image/png", "png")
    return try {
        val srcImage = Image.makeFromEncoded(sourceBytes)
        compressImageInternal(srcImage, quality, fallbackBytes = sourceBytes)
    } catch (e: Exception) {
        AppLogger.w("ImageUtils", "通用图片压缩异常，回退原图: ${e.message}")
        CompactImage(sourceBytes, "image/png", "png")
    }
}

/**
 * 已持有 Skia [Image] 的调用方专用重载，直接在像面层面编码，避免多一次 PNG 中转。
 */
suspend fun compressToCompactImage(image: Image, quality: Int = 88): CompactImage =
    compressImageInternal(image, quality, fallbackBytes = null)

/**
 * 私有共用编码管线：WEBP → JPEG → PNG 三级降级。
 */
private fun compressImageInternal(image: Image, quality: Int, fallbackBytes: ByteArray?): CompactImage {
    // 第一优先：WEBP（高压缩比，控制返回数据体积的主力格式）
    val webpData = try {
        image.encodeToData(EncodedImageFormat.WEBP, quality)
    } catch (_: Throwable) {
        null
    }
    if (webpData != null && webpData.bytes.isNotEmpty()) {
        return CompactImage(webpData.bytes, "image/webp", "webp")
    }

    // 第一降级：JPEG（截图类场景无透明通道需求）
    val jpegData = try {
        image.encodeToData(EncodedImageFormat.JPEG, quality)
    } catch (_: Throwable) {
        null
    }
    if (jpegData != null && jpegData.bytes.isNotEmpty()) {
        return CompactImage(jpegData.bytes, "image/jpeg", "jpg")
    }

    // 最终兜底：优先透传原始字节，其次无损 PNG 重编码
    val pngBytes = fallbackBytes ?: try {
        image.encodeToData(EncodedImageFormat.PNG)?.bytes
    } catch (_: Throwable) {
        null
    }
    return CompactImage(pngBytes ?: ByteArray(0), "image/png", "png")
}

/**
 * 基于 Alpha 通道扫描图片的核心不透明内容外接矩形 (Opaque Bounding Box)
 *
 * @param imageBytes 原始图片字节数据
 * @param alphaThreshold Alpha 阈值 (0..255)，默认 20，过滤外发光边缘的微弱噪点与透明留白
 * @return 核心不透明区域在原图中的像素包围盒 [SerialRect]，全透明或失败时返回 null
 */
suspend fun detectOpaqueBoundingBox(
    imageBytes: ByteArray,
    alphaThreshold: Int = 20
): SerialRect? {
    if (imageBytes.isEmpty()) return null
    return try {
        val skiaImage = Image.makeFromEncoded(imageBytes)
        val w = skiaImage.width
        val h = skiaImage.height
        if (w <= 0 || h <= 0) return null

        val bitmap = Bitmap().apply { allocN32Pixels(w, h) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(skiaImage, 0f, 0f)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        // 像素点采样步长：大图适度跳步提速（<= 1200px 逐像素，> 1200px 步长 2）
        val step = if (w > 1200 || h > 1200) 2 else 1

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val color = bitmap.getColor(x, y)
                val alpha = Color.getA(color)
                if (alpha >= alphaThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX >= minX && maxY >= minY) {
            val left = minX.coerceIn(0, w - 1).toFloat()
            val top = minY.coerceIn(0, h - 1).toFloat()
            val right = (maxX + step).coerceIn(1, w).toFloat()
            val bottom = (maxY + step).coerceIn(1, h).toFloat()
            SerialRect(left, top, right, bottom)
        } else {
            null
        }
    } catch (e: Exception) {
        AppLogger.w("ImageUtils", "扫描不透明内容包围盒异常: ${e.message}")
        null
    }
}

/**
 * 根据图片不透明主体包围盒与模块目标尺寸，计算自适应对齐缩放配置
 *
 * @param opaqueBox 不透明主体包围盒 [l, t, r, b]
 * @param imgWidth 原始图片像素总宽
 * @param imgHeight 原始图片像素总高
 * @param targetWidth 模块目标逻辑宽度 (block.bounds.width)
 * @param targetHeight 模块目标逻辑高度 (block.bounds.height)
 * @param lockAspectRatio 是否锁定等比缩放，默认 true
 * @return 计算出的 [ImageScaleConfig]
 */
fun calculateOpaqueScaleConfig(
    opaqueBox: SerialRect,
    imgWidth: Float,
    imgHeight: Float,
    targetWidth: Float,
    targetHeight: Float,
    lockAspectRatio: Boolean = true
): ImageScaleConfig {
    val opaqueW = opaqueBox.width.coerceAtLeast(1f)
    val opaqueH = opaqueBox.height.coerceAtLeast(1f)

    val rawScaleX = targetWidth / opaqueW
    val rawScaleY = targetHeight / opaqueH

    val (scaleX, scaleY) = if (lockAspectRatio) {
        val s = minOf(rawScaleX, rawScaleY)
        Pair(s, s)
    } else {
        Pair(rawScaleX, rawScaleY)
    }

    // 居中对齐余量
    val extraX = (targetWidth - opaqueW * scaleX) / 2f
    val extraY = (targetHeight - opaqueH * scaleY) / 2f

    val offsetX = extraX - opaqueBox.left * scaleX
    val offsetY = extraY - opaqueBox.top * scaleY

    return ImageScaleConfig(
        scaleX = scaleX,
        scaleY = scaleY,
        offsetX = offsetX,
        offsetY = offsetY,
        lockAspectRatio = lockAspectRatio,
        enabled = true,
        opaqueBounds = opaqueBox
    )
}

/**
 * 通用一键计算不透明内容主体缩放配置
 */
suspend fun calculateOpaqueContentScaleConfig(
    imageBytes: ByteArray,
    targetWidth: Float,
    targetHeight: Float,
    lockAspectRatio: Boolean = true,
    alphaThreshold: Int = 20
): ImageScaleConfig? {
    if (imageBytes.isEmpty() || targetWidth <= 0f || targetHeight <= 0f) return null
    val skiaImage = try { Image.makeFromEncoded(imageBytes) } catch (e: Exception) { null } ?: return null
    val opaqueBox = detectOpaqueBoundingBox(imageBytes, alphaThreshold) ?: return null
    return calculateOpaqueScaleConfig(
        opaqueBox = opaqueBox,
        imgWidth = skiaImage.width.toFloat(),
        imgHeight = skiaImage.height.toFloat(),
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        lockAspectRatio = lockAspectRatio
    )
}

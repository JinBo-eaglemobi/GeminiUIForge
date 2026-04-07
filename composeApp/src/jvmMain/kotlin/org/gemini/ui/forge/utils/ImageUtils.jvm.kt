package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual suspend fun cropImage(imageSource: String, bounds: org.gemini.ui.forge.domain.SerialRect): ByteArray? {
    return try {
        val fullBytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return null

        val original: BufferedImage = ImageIO.read(ByteArrayInputStream(fullBytes)) ?: return null

        // 关键修复：逻辑坐标映射到物理像素
        // 假设原始分析是基于标准的 1080p (如果是长图则是 1080x高度)
        // 我们利用图片的真实宽度作为基准来推导缩放比例
        val logicalWidth = 1080f
        val scale = original.width.toFloat() / logicalWidth

        val left = (bounds.left * scale).toInt().coerceIn(0, original.width - 1)
        val top = (bounds.top * scale).toInt().coerceIn(0, original.height - 1)
        var width = (bounds.width * scale).toInt().coerceAtLeast(1)
        var height = (bounds.height * scale).toInt().coerceAtLeast(1)

        // 确保不超出物理边界
        if (left + width > original.width) width = original.width - left
        if (top + height > original.height) height = original.height - top

        val cropped = original.getSubimage(left, top, width, height)

        val out = ByteArrayOutputStream()
        ImageIO.write(cropped, "jpg", out)
        out.toByteArray()
    } catch (e: Exception) {
        org.gemini.ui.forge.utils.AppLogger.e("ImageUtils", "JVM 裁剪失败: ${e.message}", e)
        null
    }
}

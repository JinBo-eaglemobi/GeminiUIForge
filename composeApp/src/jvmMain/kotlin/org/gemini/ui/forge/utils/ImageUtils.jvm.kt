package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.gemini.ui.forge.model.ui.SerialRect

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual suspend fun cropImage(
    imageSource: String, 
    bounds: org.gemini.ui.forge.model.ui.SerialRect,
    logicalWidth: Float,
    logicalHeight: Float
): ByteArray? {
    return try {
        val fullBytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return null

        val original: BufferedImage = ImageIO.read(ByteArrayInputStream(fullBytes)) ?: return null

        // 关键修复：基于传入的逻辑画布尺寸，分别计算横纵缩放比
        val scaleX = original.width.toFloat() / logicalWidth
        val scaleY = original.height.toFloat() / logicalHeight

        val left = (bounds.left * scaleX).toInt().coerceIn(0, original.width - 1)
        val top = (bounds.top * scaleY).toInt().coerceIn(0, original.height - 1)
        var width = (bounds.width * scaleX).toInt().coerceAtLeast(1)
        var height = (bounds.height * scaleY).toInt().coerceAtLeast(1)
        
        AppLogger.d("ImageUtils", """
            [JVM Crop Debug]
            - Physical Size: ${original.width} x ${original.height}
            - Logical Canvas: $logicalWidth x $logicalHeight
            - Scale: X=$scaleX, Y=$scaleY
            - Target Logical Rect: L=${bounds.left}, T=${bounds.top}, W=${bounds.width}, H=${bounds.height}
            - Mapped Physical Rect: L=$left, T=$top, W=$width, H=$height
        """.trimIndent())

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

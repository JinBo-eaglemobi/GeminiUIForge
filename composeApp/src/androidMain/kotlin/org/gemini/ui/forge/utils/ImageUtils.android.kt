package org.gemini.ui.forge.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size)
    return bitmap.asImageBitmap()
}

actual suspend fun cropImage(
    imageSource: String, 
    bounds: org.gemini.ui.forge.domain.SerialRect,
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

        val original = BitmapFactory.decodeByteArray(fullBytes, 0, fullBytes.size) ?: return null
        
        // 关键修复：计算逻辑坐标到物理像素的缩放比
        val scaleX = original.width.toFloat() / logicalWidth
        val scaleY = original.height.toFloat() / logicalHeight

        val left = (bounds.left * scaleX).toInt().coerceIn(0, original.width - 1)
        val top = (bounds.top * scaleY).toInt().coerceIn(0, original.height - 1)
        var width = (bounds.width * scaleX).toInt().coerceAtLeast(1)
        var height = (bounds.height * scaleY).toInt().coerceAtLeast(1)

        AppLogger.d("ImageUtils", """
            [Android Crop Debug]
            - Physical Size: ${original.width} x ${original.height}
            - Logical Canvas: $logicalWidth x $logicalHeight
            - Scale: X=$scaleX, Y=$scaleY
            - Target Logical Rect: L=${bounds.left}, T=${bounds.top}, W=${bounds.width}, H=${bounds.height}
            - Mapped Physical Rect: L=$left, T=$top, W=$width, H=$height
        """.trimIndent())

        // 越界防护
        if (left + width > original.width) width = original.width - left
        if (top + height > original.height) height = original.height - top

        val croppedBitmap = Bitmap.createBitmap(original, left, top, width, height)
        
        val out = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    } catch (e: Exception) {
        org.gemini.ui.forge.utils.AppLogger.e("ImageUtils", "Android 裁剪失败: ${e.message}", e)
        null
    }
}

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

actual suspend fun cropImage(imageSource: String, bounds: org.gemini.ui.forge.domain.SerialRect): ByteArray? {
    return try {
        // 1. 读取原始图片数据
        val fullBytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return null

        // 2. 解码并裁剪
        val original = BitmapFactory.decodeByteArray(fullBytes, 0, fullBytes.size) ?: return null
        
        // 计算裁剪坐标（确保不越界）
        val left = bounds.left.toInt().coerceIn(0, original.width)
        val top = bounds.top.toInt().coerceIn(0, original.height)
        val width = (bounds.right - bounds.left).toInt().coerceIn(1, original.width - left)
        val height = (bounds.bottom - bounds.top).toInt().coerceIn(1, original.height - top)

        val croppedBitmap = Bitmap.createBitmap(original, left, top, width, height)
        
        // 3. 压缩并返回
        val out = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

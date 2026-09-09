package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

actual fun Image.toComposeImageBitmap(): ImageBitmap {
    // 将 Skia Image 转换为 Skia Bitmap
    val bitmap = Bitmap.makeFromImage(this)
    // 将 Skia Bitmap 转换为 Android Bitmap (通过获取像素数据)
    val androidBitmap = android.graphics.Bitmap.createBitmap(
        this.width,
        this.height,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val pixels = bitmap.readPixels(bitmap.imageInfo, (this.width * 4), 0, 0)
    if (pixels != null) {
        androidBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
    }
    return androidBitmap.asImageBitmap()
}

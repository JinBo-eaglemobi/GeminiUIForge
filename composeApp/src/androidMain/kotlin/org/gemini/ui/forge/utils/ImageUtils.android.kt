package org.gemini.ui.forge.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import org.gemini.ui.forge.model.ui.SerialRect

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return BitmapFactory.decodeByteArray(this, 0, this.size).asImageBitmap()
}

actual suspend fun cropImage(
    imageSource: String, 
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean,
    forceWidth: Int?,
    forceHeight: Int?
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
        
        val scaleX = original.width.toFloat() / logicalWidth
        val scaleY = original.height.toFloat() / logicalHeight

        val left = (bounds.left * scaleX).toInt().coerceIn(0, original.width - 1)
        val top = (bounds.top * scaleY).toInt().coerceIn(0, original.height - 1)
        var width = (bounds.width * scaleX).toInt().coerceAtLeast(1)
        var height = (bounds.height * scaleY).toInt().coerceAtLeast(1)

        if (left + width > original.width) width = original.width - left
        if (top + height > original.height) height = original.height - top

        var croppedBitmap = Bitmap.createBitmap(original, left, top, width, height)

        if (forceWidth != null && forceHeight != null) {
            croppedBitmap = Bitmap.createScaledBitmap(croppedBitmap, forceWidth, forceHeight, true)
        }
        
        val out = ByteArrayOutputStream()
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        croppedBitmap.compress(format, 90, out)
        out.toByteArray()
    } catch (e: Exception) {
        org.gemini.ui.forge.utils.AppLogger.e("ImageUtils", "Android 裁剪缩放失败", e)
        null
    }
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? {
    return try {
        val bytes = if (imageSource.startsWith("data:image")) {
            val pureBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(imageSource)
        } ?: return null

        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val width = original.width
        val height = original.height
        
        var left = width; var top = height; var right = -1; var bottom = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (((original.getPixel(x, y) shr 24) and 0xff) > 5) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        if (right < left || bottom < top) return bytes

        val cropped = Bitmap.createBitmap(original, left, top, right - left + 1, bottom - top + 1)
        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    return try {
        val bytes = if (uri.startsWith("data:image")) {
            val pureBase64 = if (uri.contains(",")) uri.substringAfter(",") else uri
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.Default.decode(pureBase64)
        } else {
            readLocalFileBytes(uri)
        } ?: return null
        
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        Pair(options.outWidth, options.outHeight)
    } catch (e: Exception) { null }
}

actual suspend fun getNonTransparentBounds(imageSource: String): SerialRect? = null

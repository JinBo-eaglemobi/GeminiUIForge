package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.gemini.ui.forge.model.ui.SerialRect
import org.w3c.dom.url.URL

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}

private suspend fun loadImage(imageSource: String): HTMLImageElement {
    val deferred = CompletableDeferred<HTMLImageElement>()
    val img = document.createElement("img") as HTMLImageElement
    img.crossOrigin = "anonymous"
    img.onload = { deferred.complete(img) }
    img.onerror = { _, _, _, _, _ -> deferred.completeExceptionally(Exception("Failed to load image: $imageSource")) }
    img.src = imageSource
    return deferred.await()
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    return try {
        val img = loadImage(uri)
        Pair(img.naturalWidth, img.naturalHeight)
    } catch (e: Exception) {
        null
    }
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
        val img = loadImage(imageSource)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        
        val scaleX = img.naturalWidth.toDouble() / logicalWidth
        val scaleY = img.naturalHeight.toDouble() / logicalHeight
        
        val sx = bounds.left * scaleX
        val sy = bounds.top * scaleY
        val sw = bounds.width * scaleX
        val sh = bounds.height * scaleY
        
        val targetWidth = forceWidth ?: bounds.width.toInt()
        val targetHeight = forceHeight ?: bounds.height.toInt()
        
        canvas.width = targetWidth
        canvas.height = targetHeight
        
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        ctx.drawImage(img, sx, sy, sw, sh, 0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble())
        
        val dataUrl = canvas.toDataURL(if (isPng) "image/png" else "image/jpeg")
        val base64 = dataUrl.split(",")[1]
        
        // Base64 to ByteArray
        val binaryString = window.atob(base64)
        val bytes = ByteArray(binaryString.length)
        for (i in binaryString.indices) {
            bytes[i] = binaryString[i].code.toByte()
        }
        bytes
    } catch (e: Exception) {
        null
    }
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? {
    val bounds = getNonTransparentBounds(imageSource) ?: return null
    val img = loadImage(imageSource)
    return cropImage(imageSource, bounds, img.naturalWidth.toFloat(), img.naturalHeight.toFloat(), true, null, null)
}

actual suspend fun getNonTransparentBounds(imageSource: String): SerialRect? {
    return try {
        val img = loadImage(imageSource)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = img.naturalWidth
        canvas.height = img.naturalHeight
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        ctx.drawImage(img, 0.0, 0.0)
        
        val imageData = ctx.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        val data = imageData.data
        val width = canvas.width
        val height = canvas.height
        
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var found = false
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x) * 4 + 3
                val alpha = data.asDynamic()[index].unsafeCast<Int>()
                if (alpha > 0) {
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
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

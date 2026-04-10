package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import kotlinx.browser.document
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
        val deferred = CompletableDeferred<HTMLImageElement>()
        val img = document.createElement("img") as HTMLImageElement
        
        img.onload = { deferred.complete(img) }
        img.onerror = { _, _, _, _, _ -> deferred.completeExceptionally(Exception("Image load failed")) }
        
        img.src = imageSource
        
        val original = deferred.await()
        
        // 动态计算缩放比
        val scaleX = original.width.toFloat() / logicalWidth
        val scaleY = original.height.toFloat() / logicalHeight
        
        val left = (bounds.left * scaleX).toDouble()
        val top = (bounds.top * scaleY).toDouble()
        val width = (bounds.width * scaleX).toDouble()
        val height = (bounds.height * scaleY).toDouble()

        AppLogger.d("ImageUtils", """
            [JS Crop Debug]
            - Physical Size: ${original.width} x ${original.height}
            - Logical Canvas: $logicalWidth x $logicalHeight
            - Scale: X=$scaleX, Y=$scaleY
            - Target Logical Rect: L=${bounds.left}, T=${bounds.top}, W=${bounds.width}, H=${bounds.height}
            - Mapped Physical Rect: L=$left, T=$top, W=$width, H=$height
        """.trimIndent())

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = if (width > 0) width.toInt() else 1
        canvas.height = if (height > 0) height.toInt() else 1
        
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        ctx.drawImage(original, left, top, width, height, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        
        val blobDeferred = CompletableDeferred<ByteArray?>()
        canvas.toBlob({ blob ->
            if (blob == null) {
                blobDeferred.complete(null)
            } else {
                val reader = org.w3c.files.FileReader()
                reader.onload = { event ->
                    val arrayBuffer = event.target.asDynamic().result as org.khronos.webgl.ArrayBuffer
                    val uint8Array = org.khronos.webgl.Uint8Array(arrayBuffer)
                    val bytes = ByteArray(uint8Array.length) { i -> 
                        uint8Array.asDynamic()[i].unsafeCast<Byte>()
                    }
                    blobDeferred.complete(bytes)
                }
                reader.readAsArrayBuffer(blob)
            }
        }, "image/jpeg", 0.9)
        
        blobDeferred.await()
    } catch (e: Exception) {
        AppLogger.e("ImageUtils", "JS 裁剪异常: ${e.message}")
        null
    }
}

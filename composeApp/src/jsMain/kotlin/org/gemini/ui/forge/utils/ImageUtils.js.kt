package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import kotlinx.browser.document

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual suspend fun cropImage(imageSource: String, bounds: org.gemini.ui.forge.domain.SerialRect): ByteArray? {
    return try {
        val deferred = CompletableDeferred<HTMLImageElement>()
        val img = document.createElement("img") as HTMLImageElement
        
        img.onload = { deferred.complete(img) }
        img.onerror = { _, _, _, _, _ -> deferred.completeExceptionally(Exception("Image load failed")) }
        
        img.src = imageSource
        
        val original = deferred.await()
        
        // 比例换算 (基于 1080 逻辑宽)
        val scale = original.width.toFloat() / 1080f
        
        val left = (bounds.left * scale).toDouble()
        val top = (bounds.top * scale).toDouble()
        val width = (bounds.width * scale).toDouble()
        val height = (bounds.height * scale).toDouble()

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

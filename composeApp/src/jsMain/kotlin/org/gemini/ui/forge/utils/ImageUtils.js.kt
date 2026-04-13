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
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean,
    forceWidth: Int?,
    forceHeight: Int?
): ByteArray? {
    // 占位实现
    return null
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? {
    return null
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    return try {
        val deferred = CompletableDeferred<HTMLImageElement>()
        val img = document.createElement("img") as HTMLImageElement
        img.onload = { deferred.complete(img) }
        img.onerror = { _, _, _, _, _ -> deferred.completeExceptionally(Exception("Image load failed")) }
        img.src = uri
        val loaded = deferred.await()
        Pair(loaded.width, loaded.height)
    } catch (e: Exception) {
        null
    }
}

actual suspend fun getNonTransparentBounds(imageSource: String): SerialRect? = null

package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.gemini.ui.forge.model.ui.SerialRect

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    throw UnsupportedOperationException("toImageBitmap is not implemented on iOS")
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
    return null
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? {
    return null
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    return null
}

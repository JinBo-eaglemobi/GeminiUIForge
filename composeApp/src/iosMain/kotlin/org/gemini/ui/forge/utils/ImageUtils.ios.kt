package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    throw UnsupportedOperationException("toImageBitmap is not implemented on iOS")
}

actual suspend fun cropImage(
    imageSource: String, 
    bounds: org.gemini.ui.forge.model.ui.SerialRect,
    logicalWidth: Float,
    logicalHeight: Float
): ByteArray? {
    return null
}

actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    return null
}
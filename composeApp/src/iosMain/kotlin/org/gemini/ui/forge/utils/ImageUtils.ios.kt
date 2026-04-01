package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    throw UnsupportedOperationException("toImageBitmap is not implemented on iOS")
}
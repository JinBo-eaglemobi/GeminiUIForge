package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun Image.toComposeImageBitmap(): ImageBitmap = this.toComposeImageBitmap()

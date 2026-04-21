package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.Image

expect fun Image.toComposeImageBitmap(): ImageBitmap

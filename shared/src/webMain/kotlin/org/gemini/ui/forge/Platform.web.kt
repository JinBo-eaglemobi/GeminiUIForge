package org.gemini.ui.forge

import androidx.compose.ui.input.pointer.PointerIcon

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default

actual fun getProcessorCount(): Int = kotlinx.browser.window.navigator.hardwareConcurrency.toInt()


actual val userHomePath: String = "opfs://"

actual val runDir: String
    get() = userHomePath
actual val appDir: String
    get() = userHomePath

actual fun captureActiveScreenShot(): ByteArray? = null
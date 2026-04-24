package org.gemini.ui.forge

import androidx.compose.ui.input.pointer.PointerIcon
import org.gemini.ui.forge.utils.AppLogger
import java.io.File

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"

    override fun openInBrowser(url: String) {
        AppLogger.d("AndroidPlatform", "Attempting to open URL: $url")
    }

    override fun applyUpdateAndRestart(tempFilePath: String) {
        val file = File(tempFilePath)
        if (file.exists()) {
            AppLogger.d("AndroidPlatform", "Apply update from: $tempFilePath")
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default

actual fun getProcessorCount(): Int = Runtime.getRuntime().availableProcessors()

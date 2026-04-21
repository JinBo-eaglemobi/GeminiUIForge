package org.gemini.ui.forge

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.input.pointer.PointerIcon
import java.io.File
import org.gemini.ui.forge.utils.AppLogger

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

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default

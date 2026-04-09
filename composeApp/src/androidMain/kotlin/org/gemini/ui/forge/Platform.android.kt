package org.gemini.ui.forge

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.input.pointer.PointerIcon

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default
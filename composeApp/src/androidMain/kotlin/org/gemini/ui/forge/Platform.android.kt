package org.gemini.ui.forge

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun Long.formatTimestamp(format: String): String {
    val date = Date(this)
    val formatter = SimpleDateFormat(format, Locale.getDefault())
    return formatter.format(date)
}
package org.gemini.ui.forge

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun Long.formatTimestamp(format: String): String {
    val date = Date(this)
    val formatter = SimpleDateFormat(format, Locale.getDefault())
    return formatter.format(date)
}
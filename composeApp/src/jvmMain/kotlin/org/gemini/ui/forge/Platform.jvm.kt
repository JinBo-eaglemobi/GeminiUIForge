package org.gemini.ui.forge

import java.text.SimpleDateFormat
import java.util.Date
import java.awt.Cursor
import androidx.compose.ui.input.pointer.PointerIcon

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
actual val ResizeVerticalIcon: PointerIcon = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
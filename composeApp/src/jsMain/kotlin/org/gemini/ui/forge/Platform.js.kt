package org.gemini.ui.forge
import androidx.compose.ui.input.pointer.PointerIcon

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
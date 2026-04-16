package org.gemini.ui.forge
import androidx.compose.ui.input.pointer.PointerIcon

class JsPlatform : Platform {
    override val name: String = "Web with Kotlin/JS"
    override fun openInBrowser(url: String) {
        kotlinx.browser.window.open(url)
    }
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default
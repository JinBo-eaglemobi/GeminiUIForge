package org.gemini.ui.forge
import androidx.compose.ui.input.pointer.PointerIcon

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
package org.gemini.ui.forge
import androidx.compose.ui.input.pointer.PointerIcon

class JsPlatform : Platform {
    override val name: String = "Web with Kotlin/JS"
    override fun openInBrowser(url: String) {
        kotlinx.browser.window.open(url)
    }

    override fun openInFileExplorer(path: String) {
        // Not supported in browser
        println("Open in file explorer not supported in JS: $path")
    }

    override fun applyUpdateAndRestart(tempFilePath: String) {
        // JS 平台不支持本地静默更新
        println("Update not supported on JS platform: $tempFilePath")
    }
}

actual fun getPlatform(): Platform = JsPlatform()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default

actual fun getProcessorCount(): Int = kotlinx.browser.window.navigator.hardwareConcurrency.toInt()

actual val userHomePath: String = "opfs://"
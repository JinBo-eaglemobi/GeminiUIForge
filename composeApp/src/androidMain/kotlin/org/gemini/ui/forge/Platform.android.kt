package org.gemini.ui.forge

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.input.pointer.PointerIcon
import org.gemini.ui.forge.utils.AppLogger
import java.io.File

@SuppressLint("StaticFieldLeak")
lateinit var androidContext: Context
    private set

/**
 * 供 Android App 入口初始化全局上下文。
 * 使用 applicationContext 避免内存泄漏。
 */
fun initAndroidContext(context: Context) {
    androidContext = context.applicationContext
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"

    override fun openInBrowser(url: String) {
        AppLogger.d("AndroidPlatform", "Attempting to open URL: $url")
    }

    override fun openInFileExplorer(path: String) {
        AppLogger.d("AndroidPlatform", "Attempting to open path: $path")
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

actual val userHomePath: String
    get() = androidContext.filesDir.absolutePath

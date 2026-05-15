package org.gemini.ui.forge.utils

import android.util.Log
import org.gemini.ui.forge.androidContext

val androidLogDir by lazy {
    // 初始化 Android 端的日志持久化路径 (Internal Storage)
    androidContext.filesDir.resolve("logs").apply { if (!exists()) mkdirs() }

}

actual fun printToConsole(level: String, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        "DEBUG" -> Log.d(tag, message, throwable)
        "INFO" -> Log.i(tag, message, throwable)
        "ERROR" -> Log.e(tag, message, throwable)
        else -> Log.d(tag, message, throwable)
    }
}

actual fun getPlatformLogDirectory(): String {
    return androidLogDir.absolutePath
}

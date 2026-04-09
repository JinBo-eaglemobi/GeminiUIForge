package org.gemini.ui.forge.utils

import android.util.Log

private var androidLogDir: String = ""

/**
 * Android 平台专用的初始化函数，需在 MainActivity 中调用
 */
fun initAndroidLogConfig(dir: String) {
    androidLogDir = dir
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
    return androidLogDir
}

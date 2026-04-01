package org.gemini.ui.forge.utils

import android.util.Log

actual object AppLogger {
    actual fun d(tag: String, message: String) { Log.d(tag, message) }
    actual fun i(tag: String, message: String) { Log.i(tag, message) }
    actual fun e(tag: String, message: String, throwable: Throwable?) { Log.e(tag, message, throwable) }
}

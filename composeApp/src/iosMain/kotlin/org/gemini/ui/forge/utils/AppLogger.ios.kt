package org.gemini.ui.forge.utils

actual object AppLogger {
    actual fun d(tag: String, message: String) { println("[DEBUG] $tag - $message") }
    actual fun i(tag: String, message: String) { println("[INFO] $tag - $message") }
    actual fun e(tag: String, message: String, throwable: Throwable?) { 
        println("[ERROR] $tag - $message") 
        throwable?.printStackTrace()
    }
}

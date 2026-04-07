package org.gemini.ui.forge.utils

actual object AppLogger {
    actual fun d(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            console.log("[$tag] $message", throwable)
        } else {
            console.log("[$tag] $message")
        }
    }

    actual fun i(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            console.info("[$tag] $message", throwable)
        } else {
            console.info("[$tag] $message")
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            console.error("[$tag] $message", throwable)
        } else {
            console.error("[$tag] $message")
        }
    }
}

package org.gemini.ui.forge.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object AppLogger {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val time = LocalDateTime.now().format(formatter)
        val thread = Thread.currentThread().name
        val out = if (level == "ERROR") System.err else System.out
        
        out.println("[$time] [$level] [$thread] $tag - $message")
        throwable?.printStackTrace(out)
    }

    actual fun d(tag: String, message: String, throwable: Throwable?) {
        log("DEBUG", tag, message, throwable)
    }

    actual fun i(tag: String, message: String, throwable: Throwable?) {
        log("INFO", tag, message, throwable)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        log("ERROR", tag, message, throwable)
    }
}

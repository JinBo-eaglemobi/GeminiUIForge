package org.gemini.ui.forge.utils

import java.io.File

actual fun printToConsole(level: String, tag: String, message: String, throwable: Throwable?) {
    val out = if (level == "ERROR") System.err else System.out
    val thread = Thread.currentThread().name
    out.println("[$level] [$thread] $tag - $message")
    throwable?.printStackTrace(out)
}

actual fun getPlatformLogDirectory(): String {
    val logDir = File(System.getProperty("user.home"), ".geminiuiforge/logs")
    if (!logDir.exists()) {
        logDir.mkdirs()
    }
    return logDir.absolutePath
}

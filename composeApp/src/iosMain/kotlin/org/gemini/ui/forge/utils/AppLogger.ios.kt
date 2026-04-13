package org.gemini.ui.forge.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

actual fun printToConsole(level: String, tag: String, message: String, throwable: Throwable?) {
    val prefix = when(level) {
        "DEBUG" -> "🔍"
        "INFO" -> "ℹ️"
        "ERROR" -> "❌"
        else -> "📝"
    }
    println("$prefix [$tag] $message")
    throwable?.let {
        println("💥 Exception: ${it.message}")
        it.cause?.let { cause -> println("⛓️ Caused by: ${cause.message}") }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformLogDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val documentsDirectory = paths.firstOrNull() as? String ?: return ""
    val logDir = "$documentsDirectory/logs"
    
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(logDir)) {
        fileManager.createDirectoryAtPath(logDir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    
    return logDir
}

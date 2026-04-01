package org.gemini.ui.forge.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

actual object AppLogger {
    private val logFile = File(System.getProperty("user.home"), ".geminiuiforge/logs/app.log")

    private fun writeLog(level: String, tag: String, message: String, throwable: Throwable?) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())
        val logLine = "[$time] [$level] $tag - $message\n"
        
        // 控制台输出
        if (level == "ERROR") {
            System.err.print(logLine)
        } else {
            print(logLine)
        }

        // 本地文件持久化
        try {
            if (logFile.parentFile?.exists() == false) {
                logFile.parentFile?.mkdirs()
            }
            logFile.appendText(logLine)
            if (throwable != null) {
                logFile.appendText(throwable.stackTraceToString() + "\n")
            }
        } catch (e: Exception) {
            // 静默失败，避免日志系统崩溃导致应用闪退
        }
    }

    actual fun d(tag: String, message: String) = writeLog("DEBUG", tag, message, null)
    actual fun i(tag: String, message: String) = writeLog("INFO", tag, message, null)
    actual fun e(tag: String, message: String, throwable: Throwable?) = writeLog("ERROR", tag, message, throwable)
}

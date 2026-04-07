package org.gemini.ui.forge.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object AppLogger {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    
    // 日志目录：用户家目录下的 .geminiuiforge/logs
    private val logDir = File(System.getProperty("user.home"), ".geminiuiforge/logs").apply {
        if (!exists()) mkdirs()
    }
    
    private val fullLogFile = File(logDir, "app_full.log")
    private val errorLogFile = File(logDir, "app_error.log")

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val time = LocalDateTime.now().format(formatter)
        val thread = Thread.currentThread().name
        val out = if (level == "ERROR") System.err else System.out
        
        val consoleLine = "[$time] [$level] [$thread] $tag - $message"
        out.println(consoleLine)
        
        // 构建持久化内容
        val fileLine = StringBuilder().apply {
            append(consoleLine)
            if (throwable != null) {
                append("\n")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
            append("\n")
        }.toString()

        // 异步或同步写入文件 (此处使用同步以确保记录完整)
        writeToLogFiles(level, fileLine)
        
        if (throwable != null && level != "ERROR") {
            throwable.printStackTrace(out)
        }
    }

    private fun writeToLogFiles(level: String, content: String) {
        try {
            // 1. 所有日志写入全量日志文件 (Append 模式)
            fullLogFile.appendText(content)
            
            // 2. 错误日志额外写入错误日志文件
            if (level == "ERROR") {
                errorLogFile.appendText(content)
            }
        } catch (e: Exception) {
            System.err.println("无法写入日志文件: ${e.message}")
        }
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

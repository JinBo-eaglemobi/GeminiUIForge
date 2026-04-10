package org.gemini.ui.forge.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.gemini.ui.forge.model.app.LogEvent

/**
 * 提取各平台控制台的标准输出（如 Logcat、console.log）
 */
expect fun printToConsole(level: String, tag: String, message: String, throwable: Throwable?)

/**
 * 全平台统一的异步高性能日志系统
 */
object AppLogger {
    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)
    private val loggerScope = CoroutineScope(Dispatchers.Default)
    
    init {
        loggerScope.launch {
            val fullLogBuffer = StringBuilder()
            val errorLogBuffer = StringBuilder()
            var lastFlushTime = org.gemini.ui.forge.getCurrentTimeMillis()
            
            while (true) {
                val event = withTimeoutOrNull(1000L) {
                    logChannel.receive()
                }

                if (event != null) {
                    val formatted = formatForFile(event)
                    fullLogBuffer.append(formatted).append("\n")
                    if (event.level == "ERROR") {
                        errorLogBuffer.append(formatted).append("\n")
                    }
                }

                val now = org.gemini.ui.forge.getCurrentTimeMillis()
                if (fullLogBuffer.length > 8000 || (now - lastFlushTime >= 1000 && fullLogBuffer.isNotEmpty())) {
                    flushToDisk(fullLogBuffer, errorLogBuffer)
                    lastFlushTime = now
                }
            }
        }
    }

    /**
     * 将 LogEvent 转换为包含完整时间戳、级别、标签、消息及全量堆栈的字符串
     */
    private fun formatForFile(event: LogEvent): String {
        val timeStr = org.gemini.ui.forge.formatTimestamp(event.timestamp)
        return buildString {
            append("[$timeStr] [${event.level}] ${event.tag} - ${event.message}")
            event.throwable?.let {
                append("\n--- STACK TRACE START ---\n")
                append(it.getPlatformStackTrace())
                append("\n--- STACK TRACE END ---")
            }
        }
    }

    private suspend fun flushToDisk(fullBuf: StringBuilder, errorBuf: StringBuilder) {
        val logDir = getPlatformLogDirectory()
        if (logDir.isNotEmpty()) {
            if (fullBuf.isNotEmpty()) {
                appendToLocalFile("$logDir/app_full.log", fullBuf.toString())
                fullBuf.clear()
            }
            if (errorBuf.isNotEmpty()) {
                appendToLocalFile("$logDir/app_error.log", errorBuf.toString())
                errorBuf.clear()
            }
        } else {
            fullBuf.clear(); errorBuf.clear()
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        // 1. 同步控制台打印
        printToConsole(level, tag, message, throwable)

        // 2. 异步投递原始对象至持久化队列
        logChannel.trySend(LogEvent(level, tag, message, throwable))
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) = log("DEBUG", tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = log("INFO", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)
}

expect fun getPlatformLogDirectory(): String

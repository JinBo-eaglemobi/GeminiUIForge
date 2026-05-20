package org.gemini.ui.forge.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.gemini.ui.forge.event.LogEvent
import kotlin.time.Duration.Companion.milliseconds

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
    private var justStarted = true
    
    private val _memoryLogs = MutableStateFlow<List<LogEvent>>(emptyList())
    val memoryLogs = _memoryLogs.asStateFlow()

    private val _statusMessage = MutableStateFlow("就绪")
    val statusMessage = _statusMessage.asStateFlow()
    
    private val _showLogViewer = MutableStateFlow(false)
    val showLogViewer = _showLogViewer.asStateFlow()

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun toggleLogViewer(show: Boolean) {
        _showLogViewer.value = show
    }

    init {
        loggerScope.launch {
            val debugLogBuffer = StringBuilder() // 全部日志
            val infoLogBuffer = StringBuilder()  // INFO, WARN 和 ERROR
            val errorLogBuffer = StringBuilder() // 仅 ERROR
            var lastFlushTime = org.gemini.ui.forge.getCurrentTimeMillis()
            
            while (true) {
                val event = withTimeoutOrNull(500L.milliseconds) {
                    logChannel.receive()
                }

                if (event != null) {
                    val formatted = formatForFile(event)
                    // 所有日志进 debug.log
                    debugLogBuffer.append(formatted).append("\n")
                    
                    if (event.level == "INFO" || event.level == "WARN" || event.level == "ERROR") {
                        infoLogBuffer.append(formatted).append("\n")
                    }
                    if (event.level == "ERROR") {
                        errorLogBuffer.append(formatted).append("\n")
                    }
                }

                val now = org.gemini.ui.forge.getCurrentTimeMillis()
                // 当缓存足够大或者时间超过 1 秒时刷入磁盘
                if (debugLogBuffer.length > 5000 || (now - lastFlushTime >= 1000 && debugLogBuffer.isNotEmpty())) {
                    flushToDisk(debugLogBuffer, infoLogBuffer, errorLogBuffer)
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

    private suspend fun flushToDisk(debugBuf: StringBuilder, infoBuf: StringBuilder, errorBuf: StringBuilder) {
        val logDir = getPlatformLogDirectory()
        if (logDir.isNotEmpty()) {
            if (debugBuf.isNotEmpty()) {
                val fileName = "app_debug.log"
                checkAndRoll(logDir, fileName)
                appendToLocalFile("$logDir/$fileName", debugBuf.toString())
                debugBuf.clear()
            }
            if (infoBuf.isNotEmpty()) {
                val fileName = "app_info.log"
                checkAndRoll(logDir, fileName)
                appendToLocalFile("$logDir/$fileName", infoBuf.toString())
                infoBuf.clear()
            }
            if (errorBuf.isNotEmpty()) {
                val fileName = "app_error.log"
                checkAndRoll(logDir, fileName)
                appendToLocalFile("$logDir/$fileName", errorBuf.toString())
                errorBuf.clear()
            }
            // 所有检查完后，标记为非首次启动
            justStarted = false
        } else {
            debugBuf.clear(); infoBuf.clear(); errorBuf.clear()
        }
    }

    private suspend fun checkAndRoll(logDir: String, fileName: String) {
        val filePath = "$logDir/$fileName"
        if (!isFileExists(filePath)) return

        val fileSize = getLocalFileSize(filePath)
        val lastModified = getLocalFileLastModified(filePath)
        val todayStr = org.gemini.ui.forge.getCurrentDate()
        val lastModifiedDateStr = org.gemini.ui.forge.formatTimestamp(lastModified, "yyyy-MM-dd")

        // 滚动条件：文件超过5M，或者最后修改日期不是今天，或者程序刚刚启动
        val needsRoll = fileSize > 5 * 1024 * 1024 || 
                        lastModifiedDateStr != todayStr ||
                        justStarted

        if (needsRoll) {
            val baseName = fileName.substringBeforeLast(".")
            val extension = fileName.substringAfterLast(".", "log")
            val dateSuffix = org.gemini.ui.forge.formatTimestamp(lastModified, "yyyy_MM_dd")
            
            // 查找下一个可用的索引
            var index = 1
            var rollPath = "$logDir/${baseName}-${dateSuffix}_${index}.${extension}"
            while (isFileExists(rollPath)) {
                index++
                rollPath = "$logDir/${baseName}-${dateSuffix}_${index}.${extension}"
            }
            
            renameLocalFile(filePath, rollPath)
            
            // 清理旧日志
            cleanupOldLogs(logDir, baseName, extension)
        }
    }

    private suspend fun cleanupOldLogs(logDir: String, baseFileName: String, extension: String) {
        val prefix = "$baseFileName-"
        val allFiles = listFilesInLocalDirectory(logDir)
        
        // 过滤出该类型的滚动日志文件
        val rolledFiles = allFiles.filter { 
            val fileName = it.replace("\\", "/").substringAfterLast("/")
            fileName.startsWith(prefix) && fileName.endsWith(".$extension")
        }.map { it to getLocalFileLastModified(it) }
        .sortedByDescending { it.second } // 按时间倒序

        // 超过20个则删除最早的
        if (rolledFiles.size > 20) {
            for (i in 20 until rolledFiles.size) {
                deleteLocalFile(rolledFiles[i].first)
            }
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
    fun w(tag: String, message: String, throwable: Throwable? = null) = log("WARN", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)
}

expect fun getPlatformLogDirectory(): String

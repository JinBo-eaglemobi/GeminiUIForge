package org.gemini.ui.forge.model.app
/**
 * 日志载体实体：携带原始信息以供后续在各平台进行全量堆栈序列化
 */
data class LogEvent(
    val level: String, 
    val tag: String, 
    val message: String, 
    val throwable: Throwable?,
    val timestamp: Long = org.gemini.ui.forge.getCurrentTimeMillis()
)

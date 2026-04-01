package org.gemini.ui.forge.utils

/**
 * 跨平台日志工具类，支持在 JVM 端持久化保存到本地文件。
 */
expect object AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

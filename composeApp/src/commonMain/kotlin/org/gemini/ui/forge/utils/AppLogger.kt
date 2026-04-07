package org.gemini.ui.forge.utils

/**
 * 跨平台日志工具类抽象定义
 * 统一管理应用在各平台（Android, JVM, iOS, JS）下的日志输出行为。
 */
expect object AppLogger {
    /** 输出调试级别日志 */
    fun d(tag: String, message: String, throwable: Throwable? = null)
    
    /** 输出信息级别日志 */
    fun i(tag: String, message: String, throwable: Throwable? = null)
    
    /** 输出错误级别日志 */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

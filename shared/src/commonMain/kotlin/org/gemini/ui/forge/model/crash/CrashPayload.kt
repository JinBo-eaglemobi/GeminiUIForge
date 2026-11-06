package org.gemini.ui.forge.model.crash

/**
 * 全局未捕获异常事件载荷实体。
 *
 * @param title 错误简要标题（例如 "界面布局越界异常" 或 "未捕获的致命错误"）
 * @param message 错误消息正文
 * @param stackTrace 完整堆栈回溯跟踪字符串
 * @param threadName 发生异常的线程名（例如 "AWT-EventQueue-0"）
 * @param timestamp 发生时间戳毫秒
 */
data class CrashPayload(
    val title: String,
    val message: String,
    val stackTrace: String,
    val threadName: String = "Main",
    val timestamp: Long = 0L
)

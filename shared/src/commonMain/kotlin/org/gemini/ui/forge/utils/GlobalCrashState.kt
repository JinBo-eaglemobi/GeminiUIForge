package org.gemini.ui.forge.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.crash.CrashPayload

/**
 * 全局未捕获异常响应式状态中枢（单例）。
 *
 * 核心人机工程学与取证设计：
 * 拦截底层 OS/AWT 默认的阻塞式系统 Error 对话框，将其收拢并打包为 [CrashPayload]；
 * 由顶层 Compose 渲染层优雅弹出应用自建的 [AppCrashDialog]，
 * 从而确保 MCP 截图工具能够 100% 捕获包含完整报错内容、位置与堆栈的现场视觉画面。
 */
object GlobalCrashState {

    private val _currentCrash = MutableStateFlow<CrashPayload?>(null)

    /** 当前处于待处置状态的崩溃载荷（为 null 时表示系统健康运行） */
    val currentCrash: StateFlow<CrashPayload?> = _currentCrash.asStateFlow()

    /**
     * 捕获并登记全局未处理的严重异常。
     *
     * @param throwable 捕获到的异常或错误实体
     * @param threadName 发生异常的线程名称
     * @param customTitle 可选的自定义报错标题
     */
    fun capture(
        throwable: Throwable,
        threadName: String = "Main",
        customTitle: String? = null
    ) {
        val title = customTitle ?: when {
            throwable is IllegalArgumentException && throwable.message?.contains("out of range") == true -> "界面尺寸测量越界异常"
            throwable::class.simpleName?.contains("OutOfMemory") == true -> "内存溢出崩溃 (OOM)"
            throwable is NullPointerException -> "空指针引用异常"
            else -> "应用程序未处理异常"
        }

        val stackTrace = throwable.stackTraceToString()
        val payload = CrashPayload(
            title = title,
            message = throwable.message ?: throwable::class.simpleName ?: "发生未知严重错误",
            stackTrace = stackTrace,
            threadName = threadName,
            timestamp = getCurrentTimeMillis()
        )

        AppLogger.e("GlobalCrashState", "🚨 拦截到致命异常并唤起应用内取证弹窗: ${payload.title} - ${payload.message}", throwable)
        _currentCrash.value = payload
    }

    /**
     * 关闭/忽略当前崩溃弹窗（仅供高级恢复或忽略使用）。
     */
    fun dismiss() {
        _currentCrash.value = null
    }
}

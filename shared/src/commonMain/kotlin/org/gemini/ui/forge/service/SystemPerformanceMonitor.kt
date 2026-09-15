package org.gemini.ui.forge.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.model.SystemMetricsSnapshot

/**
 * 全局系统与进程性能监控管理中枢 (单例)
 * 负责 1.5 秒低开销轮询采集性能指标并向 UI 响应式广播
 */
object SystemPerformanceMonitor {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _metrics = MutableStateFlow<SystemMetricsSnapshot?>(null)
    /** 实时指标流 */
    val metrics: StateFlow<SystemMetricsSnapshot?> = _metrics.asStateFlow()

    private var pollJob: Job? = null
    var pollIntervalMs: Long = 1500L // 1.5 秒采样周期

    /**
     * 启动实时性能指标轮询
     */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                try {
                    _metrics.value = captureSystemMetrics()
                } catch (_: Throwable) {
                    // 异常防御，保障轮询协程不中断
                }
                delay(pollIntervalMs)
            }
        }
    }

    /**
     * 暂停/停止性能指标轮询
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * 手动触发垃圾回收清理，并在 200ms 后立即主动拉取一帧最新数据
     */
    fun triggerGc() {
        triggerSystemGc()
        scope.launch {
            delay(200)
            try {
                _metrics.value = captureSystemMetrics()
            } catch (_: Throwable) {}
        }
    }
}

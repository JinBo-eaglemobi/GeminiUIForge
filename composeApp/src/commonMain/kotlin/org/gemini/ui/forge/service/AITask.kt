package org.gemini.ui.forge.service

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.formatTimestamp

/**
 * 任务状态枚举
 * 用于指示当前 AI 任务的生命周期阶段
 */
enum class AITaskStatus {
    /** 初始状态，任务尚未启动 */
    IDLE, 
    /** 任务正在执行中 */
    RUNNING, 
    /** 任务已成功执行完毕 */
    SUCCESS, 
    /** 任务执行过程中发生异常 */
    ERROR, 
    /** 任务被用户或系统主动取消 */
    CANCELLED
}

/**
 * 统一的 AI 任务包装器
 * 
 * 这是一个泛型类，用于封装长耗时的 AI 后台操作（如：图片分析、生成、背景去除等）。
 * 它将 Kotlin Coroutines 与 Compose 状态管理结合，为 UI 层提供了一个易于观察的状态流。
 * 
 * 核心能力：
 * 1. **状态流管理**：通过 StateFlow 提供当前的执行状态 (Status)、进度 (Progress)、错误信息 (Error) 和最终结果 (Result)。
 * 2. **日志收集**：内置 Compose 友好的 [SnapshotStateList]，UI 可直接绑定展示实时任务日志。
 * 3. **任务控制**：支持任务的启动 [execute] 与随时中断 [cancel]。
 * 
 * @param T 任务成功完成后返回的结果类型
 * @property taskName 任务的显示名称，将用于日志输出的前缀和标识
 * @property scope 执行此任务的协程作用域（通常是 ViewModel 的 viewModelScope 或 Screen 的 coroutineScope）
 */
class AITask<T>(
    private val taskName: String,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(AITaskStatus.IDLE)
    /** 观测当前任务状态 */
    val status = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    /** 观测当前任务进度，范围为 0.0f 到 1.0f */
    val progress = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** 观测任务失败时的错误信息，若无错误则为 null */
    val error = _error.asStateFlow()

    private val _result = MutableStateFlow<T?>(null)
    /** 观测任务成功完成后的最终结果数据 */
    val result = _result.asStateFlow()

    /** 
     * 实时任务日志列表 
     * 使用 Compose 的 `mutableStateListOf` 实现，当添加新日志时，绑定此列表的 UI 会自动重组。
     */
    val logs = mutableStateListOf<String>()

    private var job: Job? = null

    /**
     * 启动并执行任务
     * 
     * 将传入的挂起代码块 [block] 提交到绑定的协程作用域中执行。
     * 若当前已有同名任务正在运行，会先将其取消。
     * 在代码块内可以通过 `this` 调用 [log] 和 [updateProgress] 方法来更新 UI。
     * 
     * @param block 包含具体业务逻辑的挂起函数，需返回类型为 [T] 的结果
     * @return 返回当前任务实例以支持链式调用
     */
    fun execute(block: suspend AITask<T>.() -> T): AITask<T> {
        job?.cancel()
        job = scope.launch {
            try {
                _status.value = AITaskStatus.RUNNING
                _error.value = null
                _progress.value = 0f
                log("🚀 启动任务: $taskName")
                
                val resultData = block()
                
                _result.value = resultData
                _status.value = AITaskStatus.SUCCESS
                log("✅ 任务完成: $taskName")
            } catch (e: CancellationException) {
                _status.value = AITaskStatus.CANCELLED
                log("⏹️ 任务已取消")
            } catch (e: Exception) {
                _status.value = AITaskStatus.ERROR
                _error.value = e.message
                log("❌ 任务失败: ${e.message}")
                AppLogger.e("AITask", "Task $taskName failed", e)
            }
        }
        return this
    }

    /**
     * 主动取消正在执行的任务
     * 会终止内部的协程，并将状态置为 [AITaskStatus.CANCELLED]。
     */
    fun cancel() {
        job?.cancel()
        _status.value = AITaskStatus.CANCELLED
    }

    /**
     * 记录一条带时间戳的任务日志
     * 
     * 日志将被添加到可观察的 [logs] 列表中供 UI 展示，同时写入底层的 [AppLogger]。
     * 
     * @param message 日志文本内容
     */
    fun log(message: String) {
        val timestamp = formatTimestamp(getCurrentTimeMillis())
        val formattedMsg = "[$timestamp] $message"
        logs.add(formattedMsg)
        AppLogger.i("AITask", "[$taskName] $message")
    }

    /**
     * 更新任务的进度条值
     * 
     * @param value 进度值，自动限制在 0.0f 到 1.0f 之间
     */
    fun updateProgress(value: Float) {
        _progress.value = value.coerceIn(0f, 1f)
    }
}

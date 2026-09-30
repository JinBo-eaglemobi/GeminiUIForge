package org.gemini.ui.forge.service.mcp

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.utils.AppLogger

/**
 * 异步作业执行生命周期状态
 */
@Serializable
enum class McpJobState {
    /** 任务已进入队列，等待调度 */
    PENDING,
    /** 任务正在执行中 */
    RUNNING,
    /** 任务已成功完成，结果就绪 */
    COMPLETED,
    /** 任务执行抛出异常或失败 */
    FAILED,
    /** 任务已被客户端或系统取消 */
    CANCELLED
}

/**
 * 异步作业当前状态快照（供轮询接口结构化返回）
 */
@Serializable
data class McpJobStatusSnapshot(
    val jobId: String,
    val toolName: String,
    val state: McpJobState,
    val progress: Float,
    val stageMessage: String?,
    val createdAt: Long,
    val durationMs: Long,
    val error: String? = null
) {
    /** 格式化生成 JSON 摘要 */
    fun toJson(): JsonObject = buildJsonObject {
        put("jobId", jobId)
        put("toolName", toolName)
        put("state", state.name)
        put("progress", (progress * 100).toInt())
        put("stageMessage", stageMessage ?: "")
        put("createdAt", createdAt)
        put("durationMs", durationMs)
        if (error != null) {
            put("error", error)
        }
    }
}

/**
 * 异步长任务生命周期与结果注册中心（单例）
 *
 * 核心机制：
 * 1. 异步作业模式（Asynchronous Job Pattern）：防止客户端 60 秒硬超时；
 * 2. 服务端自适应长轮询（Long Polling）：支持通过 CompletableDeferred 优雅挂起并在任务完成时瞬时唤醒；
 * 3. 三级可配置架构：入参优先 > 全局配置兜底 > 物理硬边界防呆；
 * 4. 自动保留与过期清理，防内存泄漏。
 */
object McpJobManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 默认长轮询等待时长（秒），出厂默认 25 秒 */
    @Volatile
    var defaultWaitSeconds: Int = 25

    /** 最大安全长轮询等待时长（秒），出厂物理硬上限 45 秒（严密防御客户端 60s 硬超时） */
    @Volatile
    var maxWaitSeconds: Int = 45

    /** 历史作业保留时长（毫秒），默认 30 分钟 */
    @Volatile
    var jobRetentionMs: Long = 30 * 60 * 1000L

    private var jobSequence = 0L

    private class JobHandle(
        val jobId: String,
        val toolName: String,
        val createdAt: Long,
        @Volatile var state: McpJobState = McpJobState.PENDING,
        @Volatile var progress: Float = 0f,
        @Volatile var stageMessage: String? = "任务已提交...",
        @Volatile var result: McpToolResult? = null,
        @Volatile var error: String? = null,
        val signal: CompletableDeferred<Unit> = CompletableDeferred()
    ) {
        var coroutineJob: Job? = null

        fun toSnapshot(): McpJobStatusSnapshot = McpJobStatusSnapshot(
            jobId = jobId,
            toolName = toolName,
            state = state,
            progress = progress,
            stageMessage = stageMessage,
            createdAt = createdAt,
            durationMs = getCurrentTimeMillis() - createdAt,
            error = error
        )
    }

    private val jobs = mutableMapOf<String, JobHandle>()
    private val mutex = Mutex()

    /**
     * 更新系统配置
     */
    fun updateConfig(config: McpConfig) {
        defaultWaitSeconds = config.defaultJobWaitSeconds
        maxWaitSeconds = config.maxJobWaitSeconds
        jobRetentionMs = config.jobRetentionMinutes * 60 * 1000L
        AppLogger.i("McpJobManager", "配置已更新: defaultWait=${defaultWaitSeconds}s, maxWait=${maxWaitSeconds}s")
    }

    /**
     * 提交一个后台耗时异步作业
     *
     * @param toolName 工具名称
     * @param block 业务具体执行体
     * @return 分配的唯一 jobId
     */
    suspend fun submitJob(
        toolName: String,
        block: suspend (onProgress: (progress: Float, message: String?) -> Unit) -> McpToolResult
    ): String {
        cleanupExpiredJobs()

        val now = getCurrentTimeMillis()
        val id = mutex.withLock {
            jobSequence++
            "job_${toolName}_${now}_$jobSequence"
        }

        val handle = JobHandle(
            jobId = id,
            toolName = toolName,
            createdAt = now
        )

        mutex.withLock {
            jobs[id] = handle
        }

        handle.coroutineJob = scope.launch {
            try {
                handle.state = McpJobState.RUNNING
                handle.stageMessage = "任务开始执行..."
                AppLogger.i("McpJobManager", "[$id] 作业启动: $toolName")

                val res = block { p, msg ->
                    handle.progress = p.coerceIn(0f, 1f)
                    if (!msg.isNullOrBlank()) {
                        handle.stageMessage = msg
                    }
                    AppLogger.d("McpJobManager", "[$id] 进度: ${(handle.progress * 100).toInt()}% - ${handle.stageMessage}")
                }

                handle.result = res
                if (res.isError) {
                    handle.state = McpJobState.FAILED
                    val errText = res.content.filterIsInstance<McpContent.Text>().firstOrNull()?.text
                    handle.error = errText ?: "执行失败"
                    handle.stageMessage = "任务执行失败: ${handle.error}"
                } else {
                    handle.state = McpJobState.COMPLETED
                    handle.progress = 1.0f
                    handle.stageMessage = "任务执行成功"
                }
                AppLogger.i("McpJobManager", "[$id] 作业完成: state=${handle.state}")
            } catch (e: Throwable) {
                handle.state = McpJobState.FAILED
                handle.error = e.message ?: "未知异常"
                handle.stageMessage = "任务异常中断: ${handle.error}"
                AppLogger.e("McpJobManager", "[$id] 作业执行异常: ${e.message}", e)
            } finally {
                // 瞬时唤醒所有长轮询等待者
                handle.signal.complete(Unit)
            }
        }

        return id
    }

    /**
     * 长轮询等待作业完成或超时
     *
     * @param jobId 作业唯一标识
     * @param requestedWaitSeconds 调用端显式请求的等待秒数（三级可配置模型：入参 > 系统配置 > 安全上限）
     * @return 最新的作业快照与产物包装对，若作业不存在返回 null
     */
    suspend fun awaitJob(
        jobId: String,
        requestedWaitSeconds: Int? = null
    ): Pair<McpJobStatusSnapshot, McpToolResult?>? {
        val handle = mutex.withLock { jobs[jobId] } ?: return null

        // 落实三级可配置架构解析
        val targetWaitSeconds = (requestedWaitSeconds ?: defaultWaitSeconds).coerceIn(0, maxWaitSeconds)
        val waitMs = targetWaitSeconds * 1000L

        // 如果作业尚未处于终态，且请求了有效等待时间，则优雅挂起
        if (waitMs > 0 && handle.state != McpJobState.COMPLETED &&
            handle.state != McpJobState.FAILED && handle.state != McpJobState.CANCELLED
        ) {
            withTimeoutOrNull<Unit>(waitMs) {
                handle.signal.await()
            }
        }

        return Pair(handle.toSnapshot(), handle.result)
    }

    /**
     * 显式取消某个运行中的作业
     */
    suspend fun cancelJob(jobId: String): Boolean {
        val handle = mutex.withLock { jobs[jobId] } ?: return false
        if (handle.state == McpJobState.COMPLETED || handle.state == McpJobState.FAILED || handle.state == McpJobState.CANCELLED) {
            return false
        }
        handle.coroutineJob?.cancel()
        handle.state = McpJobState.CANCELLED
        handle.stageMessage = "任务已被手动取消"
        handle.signal.complete(Unit)
        AppLogger.i("McpJobManager", "[$jobId] 作业已被手动取消")
        return true
    }

    /**
     * 自动清理过期作业记录
     */
    private suspend fun cleanupExpiredJobs() {
        val now = getCurrentTimeMillis()
        mutex.withLock {
            val iterator = jobs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val handle = entry.value
                val isFinished = handle.state == McpJobState.COMPLETED ||
                        handle.state == McpJobState.FAILED ||
                        handle.state == McpJobState.CANCELLED
                if (isFinished && (now - handle.createdAt > jobRetentionMs)) {
                    iterator.remove()
                    AppLogger.d("McpJobManager", "已清理过期作业: ${entry.key}")
                }
            }
        }
    }
}

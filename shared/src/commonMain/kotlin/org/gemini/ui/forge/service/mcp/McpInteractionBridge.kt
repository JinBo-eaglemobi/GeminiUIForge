package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.gemini.ui.forge.getCurrentTimeMillis
import kotlin.concurrent.Volatile

/**
 * MCP 请求 UI 交互的事件对象层级
 */
sealed interface McpInteractionRequest {
    val id: Long

    /**
     * 二元确认请求：向用户展示操作确认弹窗并挂起等待用户决定
     */
    data class Confirmation(
        override val id: Long,
        val title: String,
        val message: String,
        val confirmText: String,
        val dismissText: String,
        val isDestructive: Boolean,
        val timeoutMs: Long
    ) : McpInteractionRequest

    /**
     * 多候选抉择请求：向用户展示选项列表并挂起等待用户单选
     */
    data class Choice(
        override val id: Long,
        val title: String,
        val message: String,
        val options: List<String>,
        val allowCancel: Boolean,
        val timeoutMs: Long
    ) : McpInteractionRequest

    /**
     * 单向通知请求：向用户触发全局 Toast 通知（无需挂起等待）
     */
    data class Notification(
        override val id: Long,
        val message: String,
        val type: String,
        val durationMillis: Long
    ) : McpInteractionRequest
}

/**
 * 确认交互的返回结果封装
 */
data class ConfirmationResult(
    val approved: Boolean,
    val reason: String // "user_confirmed", "user_rejected", "timeout", "ui_unreachable"
)

/**
 * 抉择交互的返回结果封装
 */
data class ChoiceResult(
    val selectedIndex: Int?, // 选中的选项索引 (0-based)，若取消或未选则为 null
    val selectedOption: String?,
    val reason: String // "user_selected", "user_cancelled", "timeout", "ui_unreachable"
)

/**
 * MCP 后台工具与 Compose 前台界面之间的双向请求-响应交互桥梁 (单例)
 *
 * 核心机制：
 * 1. 工具协程调用挂起函数 (requestConfirmation / requestChoice)；
 * 2. 桥梁生成全局自增请求 ID 与 CompletableDeferred，并将请求 emit 到 SharedFlow；
 * 3. 前台 UI 宿主 (McpInteractionHost) 订阅 SharedFlow 并渲染弹窗；
 * 4. 用户点击后，UI 宿主调用 completeConfirmation / completeChoice 解锁协程；
 * 5. 若 UI 宿主未运行或用户超时，自动安全降级并兜底拒绝。
 */
object McpInteractionBridge {
    private val _requests = MutableSharedFlow<McpInteractionRequest>(extraBufferCapacity = 32)
    val requests: SharedFlow<McpInteractionRequest> = _requests.asSharedFlow()

    private val mapMutex = Mutex()
    private val confirmationDeferreds = LinkedHashMap<Long, CompletableDeferred<Boolean>>()
    private val choiceDeferreds = LinkedHashMap<Long, CompletableDeferred<Int?>>()

    @Volatile
    private var isHostActive: Boolean = false

    /**
     * 标记 UI 宿主是否在线处于活跃监听状态
     */
    fun setHostActive(active: Boolean) {
        isHostActive = active
    }

    /**
     * 检查当前 UI 宿主是否在线就绪
     */
    fun isUiReachable(): Boolean = isHostActive

    /**
     * 发起用户确认请求并挂起等待结果
     */
    suspend fun requestConfirmation(
        title: String,
        message: String,
        confirmText: String = "允许执行",
        dismissText: String = "拒绝",
        isDestructive: Boolean = false,
        timeoutMs: Long = 240_000L
    ): ConfirmationResult {
        if (!isHostActive) {
            return ConfirmationResult(approved = false, reason = "ui_unreachable")
        }

        val reqId = getCurrentTimeMillis()
        val deferred = CompletableDeferred<Boolean>()
        mapMutex.withLock {
            confirmationDeferreds[reqId] = deferred
        }

        val request = McpInteractionRequest.Confirmation(
            id = reqId,
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            isDestructive = isDestructive,
            timeoutMs = timeoutMs
        )

        val emitted = _requests.tryEmit(request)
        if (!emitted) {
            mapMutex.withLock {
                confirmationDeferreds.remove(reqId)
            }
            return ConfirmationResult(approved = false, reason = "ui_unreachable")
        }

        return try {
            val approved = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
            if (approved == null) {
                ConfirmationResult(approved = false, reason = "timeout")
            } else if (approved) {
                ConfirmationResult(approved = true, reason = "user_confirmed")
            } else {
                ConfirmationResult(approved = false, reason = "user_rejected")
            }
        } finally {
            mapMutex.withLock {
                confirmationDeferreds.remove(reqId)
            }
        }
    }

    /**
     * 发起多候选抉择请求并挂起等待用户选择
     */
    suspend fun requestChoice(
        title: String,
        message: String,
        options: List<String>,
        allowCancel: Boolean = true,
        timeoutMs: Long = 240_000L
    ): ChoiceResult {
        if (!isHostActive) {
            return ChoiceResult(selectedIndex = null, selectedOption = null, reason = "ui_unreachable")
        }

        val reqId = getCurrentTimeMillis()
        val deferred = CompletableDeferred<Int?>()
        mapMutex.withLock {
            choiceDeferreds[reqId] = deferred
        }

        val request = McpInteractionRequest.Choice(
            id = reqId,
            title = title,
            message = message,
            options = options,
            allowCancel = allowCancel,
            timeoutMs = timeoutMs
        )

        val emitted = _requests.tryEmit(request)
        if (!emitted) {
            mapMutex.withLock {
                choiceDeferreds.remove(reqId)
            }
            return ChoiceResult(selectedIndex = null, selectedOption = null, reason = "ui_unreachable")
        }

        return try {
            val selected = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
            if (selected == null && !deferred.isCompleted) {
                ChoiceResult(selectedIndex = null, selectedOption = null, reason = "timeout")
            } else if (selected != null && selected in options.indices) {
                ChoiceResult(selectedIndex = selected, selectedOption = options[selected], reason = "user_selected")
            } else {
                ChoiceResult(selectedIndex = null, selectedOption = null, reason = "user_cancelled")
            }
        } finally {
            mapMutex.withLock {
                choiceDeferreds.remove(reqId)
            }
        }
    }

    /**
     * 发送单向用户通知 (即发即忘，不阻塞)
     */
    fun sendNotification(
        message: String,
        type: String = "INFO",
        durationMillis: Long = 3000L
    ): Boolean {
        val request = McpInteractionRequest.Notification(
            id = getCurrentTimeMillis(),
            message = message,
            type = type,
            durationMillis = durationMillis
        )
        return _requests.tryEmit(request)
    }

    /**
     * 前台 UI 宿主提交确认结果
     */
    suspend fun completeConfirmation(requestId: Long, approved: Boolean): Boolean {
        val deferred = mapMutex.withLock {
            confirmationDeferreds.remove(requestId)
        } ?: return false
        return deferred.complete(approved)
    }

    /**
     * 前台 UI 宿主提交选项结果
     */
    suspend fun completeChoice(requestId: Long, selectedIndex: Int?): Boolean {
        val deferred = mapMutex.withLock {
            choiceDeferreds.remove(requestId)
        } ?: return false
        return deferred.complete(selectedIndex)
    }
}

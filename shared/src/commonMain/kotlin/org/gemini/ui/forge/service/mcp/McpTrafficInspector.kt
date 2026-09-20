package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.userHomePath
import org.gemini.ui.forge.utils.appendToLocalFile

/**
 * MCP 实时连接节点追踪与双向通信报文探针中枢 (全局单例)
 *
 * 核心能力：
 * 1. 静默记录客户端连接与心跳，绝不刷屏污染流水；
 * 2. 内存队列：按单个客户端 Session 独立隔离保存多达 1000 条完整记录；
 * 3. 磁盘持久化：每条实质性通信报文异步追加写入本地日志文件；
 * 4. 支持手动断开客户端并显式标记状态。
 */
object McpTrafficInspector {

    /** 单个连接在内存中保留的完整报文上限 */
    const val MAX_LOGS_PER_SESSION = 1000

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val logFilePath: String = "$userHomePath/.geminiuiforge/logs/mcp_traffic.log"

    private val _logs = MutableStateFlow<List<McpTrafficRecord>>(emptyList())
    /** 全局双向报文流 (最新条目在前) */
    val logs: StateFlow<List<McpTrafficRecord>> = _logs.asStateFlow()

    private val _nodes = MutableStateFlow<List<McpClientNode>>(emptyList())
    /** 当前已感知的外部客户端节点列表 (含在线与已断开) */
    val nodes: StateFlow<List<McpClientNode>> = _nodes.asStateFlow()

    // 按会话隔离的报文缓冲映射 (SessionId -> 专属报文列表)
    private val sessionLogsState = MutableStateFlow<Map<String, List<McpTrafficRecord>>>(emptyMap())

    /**
     * 记录或更新客户端心跳 (静默保活，绝不写入报文流水，防止高频刷屏)
     *
     * @param sessionId 会话唯一 ID
     * @param userAgent 客户端发送的 User-Agent
     * @param ip 客户端来源 IP
     * @return 识别出的友好客户端名称
     */
    fun recordClientHeartbeat(sessionId: String, userAgent: String, ip: String = "127.0.0.1"): String {
        val now = getCurrentTimeMillis()
        val clientName = resolveClientName(userAgent, sessionId)

        _nodes.update { currentNodes ->
            val existing = currentNodes.find { it.id == sessionId }
            if (existing != null) {
                currentNodes.map { node ->
                    if (node.id == sessionId) {
                        node.copy(
                            lastActiveAt = now,
                            requestCount = node.requestCount + 1,
                            isActive = true,
                            disconnectedAt = null
                        )
                    } else node
                }
            } else {
                val newNode = McpClientNode(
                    id = sessionId,
                    clientName = clientName,
                    userAgent = userAgent,
                    ip = ip,
                    firstConnectedAt = now,
                    lastActiveAt = now,
                    requestCount = 1,
                    isActive = true,
                    disconnectedAt = null
                )
                listOf(newNode) + currentNodes
            }
        }
        return clientName
    }

    /**
     * 兼容接口
     */
    fun recordClientNode(sessionId: String, userAgent: String, ip: String = "127.0.0.1"): String =
        recordClientHeartbeat(sessionId, userAgent, ip)

    /**
     * 记录实质性入站请求 (Client -> Server，仅在有具体 RPC 或工具调用时入库并刷盘)
     */
    fun recordInbound(
        methodOrTool: String,
        summary: String,
        payloadJson: String,
        sessionId: String? = null,
        clientName: String? = null,
        type: McpTrafficType = McpTrafficType.TOOL_CALL
    ): String {
        val recordId = "req_${getCurrentTimeMillis()}_${(1000..9999).random()}"
        val record = McpTrafficRecord(
            id = recordId,
            timestamp = getCurrentTimeMillis(),
            direction = McpTrafficDirection.INBOUND,
            type = type,
            methodOrTool = methodOrTool,
            summary = summary,
            sessionId = sessionId,
            clientName = clientName,
            durationMs = null,
            payloadJson = payloadJson,
            isError = false
        )
        appendRecord(record)
        return recordId
    }

    /**
     * 记录实质性出站响应 (Server -> Client，仅在有具体处理回包时入库并刷盘)
     */
    fun recordOutbound(
        methodOrTool: String,
        summary: String,
        payloadJson: String,
        durationMs: Long? = null,
        isError: Boolean = false,
        sessionId: String? = null,
        clientName: String? = null,
        type: McpTrafficType = McpTrafficType.TOOL_RESULT
    ) {
        val recordId = "resp_${getCurrentTimeMillis()}_${(1000..9999).random()}"
        val record = McpTrafficRecord(
            id = recordId,
            timestamp = getCurrentTimeMillis(),
            direction = McpTrafficDirection.OUTBOUND,
            type = type,
            methodOrTool = methodOrTool,
            summary = summary,
            sessionId = sessionId,
            clientName = clientName,
            durationMs = durationMs,
            payloadJson = payloadJson,
            isError = isError
        )
        appendRecord(record)
    }

    private fun appendRecord(record: McpTrafficRecord) {
        // 1. 内存：按 SessionId 隔离维护专属 1000 条队列 (使用原子 StateFlow.update 确保跨平台并发安全)
        val sid = record.sessionId ?: "global"
        sessionLogsState.update { map ->
            val list = map[sid] ?: emptyList()
            val newList = (listOf(record) + list).let {
                if (it.size > MAX_LOGS_PER_SESSION) it.take(MAX_LOGS_PER_SESSION) else it
            }
            map + (sid to newList)
        }

        // 2. 全局视图更新
        _logs.update { current ->
            val updated = listOf(record) + current
            if (updated.size > 2000) {
                updated.take(2000)
            } else {
                updated
            }
        }

        // 3. 磁盘异步持久化刷盘 (追加写入本地日志文件)
        scope.launch {
            try {
                val timeStr = record.timestamp.toString()
                val line = "[$timeStr] [${record.direction}] [${record.methodOrTool}] [${record.clientName ?: "Unknown"}#${record.sessionId ?: "none"}] " +
                        "${record.summary} (耗时: ${record.durationMs ?: 0}ms, Error: ${record.isError})\nPayload: ${record.payloadJson}\n\n"
                appendToLocalFile(logFilePath, line)
            } catch (_: Throwable) {
                // 刷盘异常防御，保障业务线程不中断
            }
        }
    }

    /**
     * 手动断开指定的外部客户端连接会话
     */
    fun disconnectNode(nodeId: String): Boolean {
        val now = getCurrentTimeMillis()
        var disconnectedClientName: String? = null

        _nodes.update { currentNodes ->
            currentNodes.map { node ->
                if (node.id == nodeId) {
                    disconnectedClientName = node.clientName
                    node.copy(
                        isActive = false,
                        disconnectedAt = now
                    )
                } else node
            }
        }

        if (disconnectedClientName != null) {
            recordOutbound(
                methodOrTool = "disconnect",
                summary = "管理员已手动断开客户端连接 [$disconnectedClientName]",
                payloadJson = """{"status": "disconnected", "nodeId": "$nodeId", "clientName": "$disconnectedClientName", "disconnectedAt": $now}""",
                sessionId = nodeId,
                clientName = disconnectedClientName,
                type = McpTrafficType.ERROR,
                isError = false
            )
            return true
        }
        return false
    }

    /**
     * 清空通信日志 (同时清空内存中的单会话缓冲)
     */
    fun clearLogs() {
        sessionLogsState.value = emptyMap()
        _logs.value = emptyList()
    }

    /**
     * 清空节点列表
     */
    fun clearNodes() {
        _nodes.value = emptyList()
    }

    /**
     * 智能识别 User-Agent 对应的友好客户端生态名称，并附带可区分的短标识
     */
    fun resolveClientName(userAgent: String, sessionId: String = ""): String {
        val uaLower = userAgent.lowercase()
        val baseName = when {
            uaLower.contains("opencode") -> "OpenCode"
            uaLower.contains("claude-code") || uaLower.contains("claudecode") -> "Claude Code"
            uaLower.contains("cursor") -> "Cursor"
            uaLower.contains("gemini") -> "Gemini CLI"
            uaLower.contains("claude") -> "Claude Desktop"
            uaLower.contains("curl") || uaLower.contains("postman") -> "HTTP 调试器"
            uaLower.startsWith("node") -> "Node.js Client"
            userAgent.isBlank() -> "MCP Client"
            else -> userAgent.take(16)
        }
        val shortId = if (sessionId.isNotBlank()) {
            "#" + sessionId.takeLast(4)
        } else ""
        return if (shortId.isNotBlank()) "$baseName $shortId" else baseName
    }
}

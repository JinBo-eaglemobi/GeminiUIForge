package org.gemini.ui.forge.service.mcp

import kotlinx.serialization.Serializable

/**
 * 通信流量流向
 */
enum class McpTrafficDirection {
    /** 客户端 -> 服务端 (请求/调用) */
    INBOUND,
    /** 服务端 -> 客户端 (响应/结果) */
    OUTBOUND
}

/**
 * 通信流量类型
 */
enum class McpTrafficType {
    /** MCP 工具调用 (tools/call) */
    TOOL_CALL,
    /** MCP 工具执行结果返回 */
    TOOL_RESULT,
    /** 初始化/握手协议 (initialize / ping) */
    HANDSHAKE,
    /** 工具列表查询 (tools/list) */
    LIST_TOOLS,
    /** 通信异常或校验失败 */
    ERROR
}

/**
 * 连接到本 MCP 服务的外部客户端节点信息
 */
@Serializable
data class McpClientNode(
    /** 会话唯一 ID (如 Mcp-Session-Id 或基于 IP/UA 生成的指纹) */
    val id: String,
    /** 识别的友好客户端名称 (如 "OpenCode", "Cursor", "Claude Code", "Gemini CLI", "未知客户端") */
    val clientName: String,
    /** 原始 User-Agent */
    val userAgent: String = "",
    /** 客户端 IP 地址 */
    val ip: String = "127.0.0.1",
    /** 首次连入时间戳 */
    val firstConnectedAt: Long,
    /** 最后活跃心跳时间戳 */
    val lastActiveAt: Long,
    /** 累计收发请求次数 */
    val requestCount: Int = 1,
    /** 是否当前在线活跃 (30 秒内有心跳则视为活跃) */
    val isActive: Boolean = true,
    /** 手动断开或离线的时间戳 (若已断开) */
    val disconnectedAt: Long? = null
)

/**
 * 实时通信数据日志条目 (用于调试探针)
 */
@Serializable
data class McpTrafficRecord(
    /** 记录唯一 ID */
    val id: String,
    /** 触发时间戳 */
    val timestamp: Long,
    /** 通信流向 (入站/出站) */
    val direction: McpTrafficDirection,
    /** 通信类型 */
    val type: McpTrafficType,
    /** 方法名或工具名 (如 "create_template", "tools/list", "initialize") */
    val methodOrTool: String,
    /** 人类可读的单行摘要说明 */
    val summary: String,
    /** 关联的会话 ID */
    val sessionId: String? = null,
    /** 关联的客户端名称 */
    val clientName: String? = null,
    /** 执行耗时 (毫秒) */
    val durationMs: Long? = null,
    /** 格式化后的完整 JSON 报文或入参/回包文本 */
    val payloadJson: String = "",
    /** 是否属于错误响应 */
    val isError: Boolean = false
)

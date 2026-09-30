package org.gemini.ui.forge.service.mcp

import kotlinx.serialization.Serializable

/**
 * MCP 服务配置实体
 *
 * @param enabled 是否开启服务
 * @param port 本地监听端口（默认 18330）
 * @param host 绑定主机地址（默认 127.0.0.1）
 * @param token 可选的鉴权令牌，为 null 或空白时不鉴权
 */
@Serializable
data class McpConfig(
    val enabled: Boolean = false,
    val port: Int = 18330,
    val host: String = "127.0.0.1",
    val token: String? = null,
    val followNavigation: Boolean = false,
    val defaultJobWaitSeconds: Int = 25,
    val maxJobWaitSeconds: Int = 45,
    val jobRetentionMinutes: Long = 30L
)

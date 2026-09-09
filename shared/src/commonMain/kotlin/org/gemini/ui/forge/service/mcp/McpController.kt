package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.StateFlow

/**
 * 跨平台 MCP 服务生命周期与状态调度控制器
 */
expect object McpController {
    val isRunning: StateFlow<Boolean>
    val serverUrl: StateFlow<String?>
    val currentPort: StateFlow<Int>
    fun start(host: String = "127.0.0.1", port: Int = 18330): Boolean
    fun stop()
}

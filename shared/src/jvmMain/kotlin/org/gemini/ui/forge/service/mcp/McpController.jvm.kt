package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.StateFlow

actual object McpController {
    actual val isRunning: StateFlow<Boolean> = McpServerManager.isRunning
    actual val serverUrl: StateFlow<String?> = McpServerManager.serverUrl
    actual val currentPort: StateFlow<Int> = McpServerManager.currentPort

    actual fun start(host: String, port: Int, token: String?): Boolean =
        McpServerManager.start(host, port, token)

    actual fun stop() {
        McpServerManager.stop()
    }
}

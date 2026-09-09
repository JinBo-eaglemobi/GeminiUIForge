package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.StateFlow

actual object McpController {
    actual val isRunning: StateFlow<Boolean> = McpServerManager.isRunning
    actual val serverUrl: StateFlow<String?> = McpServerManager.serverUrl
    actual val currentPort: StateFlow<Int> = McpServerManager.currentPort

    actual fun start(host: String, port: Int): Boolean =
        McpServerManager.start(host, port)

    actual fun stop() {
        McpServerManager.stop()
    }
}

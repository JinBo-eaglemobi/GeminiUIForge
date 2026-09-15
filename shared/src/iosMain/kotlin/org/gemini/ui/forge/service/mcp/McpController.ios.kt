package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual object McpController {
    actual val isRunning: StateFlow<Boolean> = MutableStateFlow(false)
    actual val serverUrl: StateFlow<String?> = MutableStateFlow(null)
    actual val currentPort: StateFlow<Int> = MutableStateFlow(18330)

    actual fun start(host: String, port: Int, token: String?): Boolean = false

    actual fun stop() {}
}

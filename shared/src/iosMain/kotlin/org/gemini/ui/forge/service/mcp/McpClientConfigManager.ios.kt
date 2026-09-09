package org.gemini.ui.forge.service.mcp

actual object McpClientConfigManager {
    actual fun getSupportedClientsStatus(serverUrl: String): List<ClientAppConfigStatus> = emptyList()
    actual fun toggleClientConfig(clientType: McpClientType, enable: Boolean, serverUrl: String): Boolean = false
}

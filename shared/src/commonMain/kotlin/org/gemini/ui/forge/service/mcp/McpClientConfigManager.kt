package org.gemini.ui.forge.service.mcp

/**
 * 跨平台 AI 客户端配置文件检测与修改管理器
 */
expect object McpClientConfigManager {
    /**
     * 扫描并返回全部受支持 AI 客户端的本地配置状态
     *
     * @param serverUrl 当前应用的 MCP 服务访问地址（如 http://127.0.0.1:18330/mcp）
     */
    fun getSupportedClientsStatus(serverUrl: String): List<ClientAppConfigStatus>

    /**
     * 针对指定客户端类型，安全切换 MCP 服务的配置状态（开启写入/关闭移除）
     *
     * @param clientType 目标客户端类型
     * @param enable true 为注入配置，false 为移除配置
     * @param serverUrl 当前应用的 MCP 服务访问地址
     * @return 操作是否成功
     */
    fun toggleClientConfig(clientType: McpClientType, enable: Boolean, serverUrl: String): Boolean
}

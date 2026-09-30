package org.gemini.ui.forge.service.mcp

/**
 * 支持的外部 AI 客户端类型枚举
 */
enum class McpClientType(
    val displayName: String,
    val tip: String,
    val supportsDisabledConfig: Boolean = false,
    val disabledSyntaxTip: String? = null
) {
    OPEN_CODE("OpenCode", "支持 ~/.config/opencode/opencode.jsonc / .json", supportsDisabledConfig = true, disabledSyntaxTip = "\"enabled\": false"),
    CLAUDE_CODE("Claude Code", "支持 ~/.claude.json"),
    CLAUDE_DESKTOP("Claude Desktop", "支持 Claude Desktop 官方桌面端客户端配置"),
    GEMINI_CLI("Gemini CLI", "支持 ~/.gemini/settings.json"),
    CURSOR("Cursor", "支持 ~/.cursor/mcp.json", supportsDisabledConfig = true, disabledSyntaxTip = "\"disabled\": true")
}

/**
 * 客户端配置文件的探测与接入状态模型
 *
 * @property clientType 目标客户端类型
 * @property name 客户端显示名称
 * @property configPath 配置文件宿主机物理绝对路径
 * @property isFileExists 物理配置文件是否存在
 * @property isConfigured 是否已写入并接入了本程序的 MCP 服务
 * @property configuredUrl 配置文件中记录的 MCP 服务 URL
 * @property isServiceDisabled 是否在配置中显式标记禁用了该 MCP 服务
 */
data class ClientAppConfigStatus(
    val clientType: McpClientType,
    val name: String,
    val configPath: String,
    val isFileExists: Boolean,
    val isConfigured: Boolean,
    val configuredUrl: String?,
    val isServiceDisabled: Boolean = false
)

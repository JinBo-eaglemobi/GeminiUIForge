package org.gemini.ui.forge.service.mcp

import kotlinx.serialization.json.JsonObject

/**
 * MCP 工具定义抽象契约。
 *
 * 业务层工具仅依赖此纯 Kotlin 接口，与底层 MCP SDK 具体类型完全解耦，
 * 避免未来 SDK 升级带来破坏性接口变更。
 */
interface McpToolDefinition {
    /** 工具唯一标识符（符合 snake_case，如 list_templates） */
    val name: String

    /** 工具功能描述（供 AI 模型理解意图与参数上下文） */
    val description: String

    /** 输入参数的 JSON Schema 定义 */
    val inputSchema: JsonObject

    /**
     * 执行具体业务调用
     *
     * @param arguments AI 客户端传入的结构化实参
     * @return 返回给 AI 的纯文本或 JSON 格式结果
     */
    suspend fun execute(arguments: JsonObject): String
}

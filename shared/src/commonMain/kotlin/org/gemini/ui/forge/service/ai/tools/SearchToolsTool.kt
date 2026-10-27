package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.UniversalAiToolRegistry
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 动态搜索与发现系统所有支持的工具（Search Tools）
 *
 * 赋予大模型自主大脑能力，按意图与关键词查询系统中具备的底层工具及入参模式。
 */
class SearchToolsTool : McpToolDefinition {
    override val name: String = "search_tools"
    override val description: String =
        "根据关键词或业务场景（如 'canvas', 'view', 'layout', 'asset', 'image', 'system'）搜索系统中可用工具的名称、功能描述与参数规范。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "要搜索的工具名称或功能关键词，例如 'layout', 'canvas', 'asset', 'image'")
            })
        })
        put("required", buildJsonArray {
            add("query")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: ""
        if (query.isEmpty()) {
            return McpToolResult.error("搜索关键词 query 不能为空")
        }

        val allTools = UniversalAiToolRegistry.getAllAvailableTools()
        val matched = allTools.filter { tool ->
            tool.name.lowercase().contains(query) ||
                    tool.description.lowercase().contains(query)
        }

        val resultJson = buildJsonObject {
            put("query", query)
            put("matched_count", matched.size)
            put("tools", buildJsonArray {
                matched.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters_summary", tool.inputSchema.toString())
                    })
                }
            })
        }

        return McpToolResult.success(resultJson.toString())
    }
}

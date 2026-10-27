package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.UniversalAiToolRegistry
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 列出当前可用工具概览元工具
 */
class ListToolsTool : McpToolDefinition {
    override val name: String = "list_tools"
    override val description: String = "列出当前系统中可用的细节工具列表及其功能概览。AI 可以通过该工具探索系统具备哪些功能与接口。"
    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put("description", "可选的工具分类筛选（如 uistudio, canvas, asset, image 等），为空则列出全部核心可用工具")
            }
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val categoryFilter = arguments["category"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()

        val allTools = UniversalAiToolRegistry.getAllAvailableTools()
        val filtered = allTools.filter { tool ->
            if (categoryFilter.isNullOrBlank()) {
                true
            } else {
                tool.name.lowercase().contains(categoryFilter) ||
                        tool.description.lowercase().contains(categoryFilter)
            }
        }

        val resultArray = buildJsonArray {
            filtered.forEach { tool ->
                addJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                }
            }
        }

        val response = buildJsonObject {
            put("total", resultArray.size)
            put("tools", resultArray)
        }

        return McpToolResult.success(response.toString())
    }
}

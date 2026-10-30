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
    override val description: String =
        "列出当前系统中可用的工具列表及其功能概览。支持按业务分类 (category) 定向筛选特定领域的工具集。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "可选：按工具业务大类筛选。支持枚举：'asset'（素材资源与生图）、'view'（界面排版与图元操作）、'template'（工程项目与模板）、'discovery'（探索发现元工具）、'runtime'（运行态与实机视口）、'interaction'（用户交互与问询），为空则列出全部核心可用工具。"
                )
            }
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val categoryInput = arguments["category"]?.jsonPrimitive?.contentOrNull
        val normalizedCategory = ToolCategoryDictionary.normalizeCategory(categoryInput)

        val allTools = UniversalAiToolRegistry.getAllAvailableTools()
        val filtered = allTools.filter { tool ->
            if (normalizedCategory.isNullOrBlank()) {
                true
            } else {
                ToolCategoryDictionary.getToolCategory(tool.name) == normalizedCategory
            }
        }

        val resultArray = buildJsonArray {
            filtered.forEach { tool ->
                addJsonObject {
                    put("name", tool.name)
                    put("category", ToolCategoryDictionary.getToolCategory(tool.name))
                    put("description", tool.description)
                }
            }
        }

        val response = buildJsonObject {
            if (normalizedCategory != null) {
                put("category", normalizedCategory)
            }
            put("total", resultArray.size)
            put("tools", resultArray)
        }

        return McpToolResult.success(response.toString())
    }
}

package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.UniversalAiToolRegistry
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 动态搜索与发现系统所有支持的工具（Search Tools）
 *
 * 赋予大模型自主大脑能力：
 * 1. 支持按分类 (category) 快速定向筛选（如 'asset' 素材资源类、'view' 界面排版类）；
 * 2. 支持多关键词并发检索 (queries: array)，系统合并去重一次性返回，杜绝多次反复调用。
 */
class SearchToolsTool : McpToolDefinition {
    override val name: String = "search_tools"
    override val description: String =
        "根据关键词列表或业务分类搜索系统中可用工具的名称、功能描述与参数规范。支持同时传入多个查询词 (queries) 或按类别 (category) 一键获取特定领域工具。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("category", buildJsonObject {
                put("type", "string")
                put(
                    "description",
                    "可选：按工具业务大类定向过滤。支持枚举：'asset'（素材资源与生图）、'view'（界面排版与图元操作）、'template'（工程项目与模板）、'discovery'（探索发现元工具）、'runtime'（运行态与实机视口）、'interaction'（用户交互与问询）"
                )
            })
            put("queries", buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put(
                    "description",
                    "可选：同时搜索多个功能关键词或业务意图列表（例如 ['asset', 'view']），系统合并去重一次性返回所有匹配工具，无需多次分批调用。"
                )
            })
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "可选：单个搜索关键词，亦支持使用逗号或空格分隔多个关键词进行批量匹配。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val categoryInput = arguments["category"]?.jsonPrimitive?.contentOrNull
        val normalizedCategory = ToolCategoryDictionary.normalizeCategory(categoryInput)

        // 提取并聚合所有搜索词
        val keywordSet = mutableSetOf<String>()

        // 1. 从 queries 数组提取
        arguments["queries"]?.jsonArray?.forEach { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let {
                keywordSet.add(it)
            }
        }

        // 2. 从 query 字符串提取（支持逗号、空格分词）
        arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let { rawQuery ->
            rawQuery.split(',', '，', ';', '；', ' ').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                keywordSet.add(it)
            }
        }

        // 门禁：若既没有传入任何关键词，也没有传入 category，给出明确指引
        if (keywordSet.isEmpty() && normalizedCategory == null) {
            return McpToolResult.error("请至少提供搜索关键词 (query / queries) 或指定工具业务分类 (category: asset/view/template/discovery/runtime/interaction)")
        }

        val allTools = UniversalAiToolRegistry.getAllAvailableTools()

        // 执行过滤
        val matched = allTools.filter { tool ->
            val toolCat = ToolCategoryDictionary.getToolCategory(tool.name)

            // 分类匹配条件
            val matchesCategory = if (normalizedCategory != null) {
                toolCat == normalizedCategory
            } else {
                true
            }

            // 关键词匹配条件（匹配名称、描述或所属分类）
            val matchesKeywords = if (keywordSet.isNotEmpty()) {
                keywordSet.any { kw ->
                    tool.name.lowercase().contains(kw) ||
                            tool.description.lowercase().contains(kw) ||
                            toolCat.contains(kw)
                }
            } else {
                true // 若仅指定分类未指定关键词，则放行该分类下所有工具
            }

            matchesCategory && matchesKeywords
        }

        val resultJson = buildJsonObject {
            if (normalizedCategory != null) {
                put("filteredCategory", normalizedCategory)
            }
            if (keywordSet.isNotEmpty()) {
                put("searchedQueries", buildJsonArray { keywordSet.forEach { add(it) } })
            }
            put("matchedCount", matched.size)
            put("tools", buildJsonArray {
                matched.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("category", ToolCategoryDictionary.getToolCategory(tool.name))
                        put("description", tool.description)
                        put("parametersSummary", tool.inputSchema.toString())
                    })
                }
            })
        }

        return McpToolResult.success(resultJson.toString())
    }
}

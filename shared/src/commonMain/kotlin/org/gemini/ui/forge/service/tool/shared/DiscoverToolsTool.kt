package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolHub
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 双通道唯一的全量被动发现与工具检索元工具 (discover_tools)。
 *
 * 彻底合并并废弃原有的 [search_tools] 与 [list_tools]，作为唯一被动发现入口：
 * 1. 开箱即用：未传参数时列出系统中全量注册工具概览；
 * 2. 类别定向：支持按 [category] 一键筛选特定业务域（如 'asset', 'view', 'template' 等）；
 * 3. 关键词检索：支持单词 [query] 或列表 [queries] 进行并发模糊匹配；
 * 4. 紧凑精简：仅下发名称、分类、描述与简短参数名摘要，杜绝冗长 JsonSchema 浪费 Token。
 */
class DiscoverToolsTool : ToolDefinition {

    override val name: String = "discover_tools"

    override val description: String =
        "动态发现和检索当前系统中所有注册可用的业务工具列表。支持按业务分类 (category) 筛选，或按功能关键词 (queries/query) 搜索，返回工具名称、分类、功能说明与入参摘要。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
                    "可选：按工具业务大类筛选。支持枚举：'asset'（素材资源与生图）、'view'（界面排版与图元操作）、'template'（工程项目与模板）、'discovery'（探索发现元工具）、'runtime'（运行态与实机视口）、'interaction'（用户交互与问询）。"
                )
            }
            putJsonObject("queries") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put(
                    "description",
                    "可选：同时搜索多个功能关键词或业务意图（如 ['asset', 'template']），系统合并去重一次性返回。"
                )
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "可选：单个搜索关键词，支持逗号或空格分隔。")
            }
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val categoryInput = arguments["category"]?.jsonPrimitive?.contentOrNull
        val normalizedCategory = ToolCategoryDictionary.normalizeCategory(categoryInput)

        // 提取并聚合所有搜索词
        val keywordSet = mutableSetOf<String>()
        arguments["queries"]?.jsonArray?.forEach { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let {
                keywordSet.add(it)
            }
        }
        arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let { rawQuery ->
            rawQuery.split(',', '，', ';', '；', ' ').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                keywordSet.add(it)
            }
        }

        val allTools = ToolHub.getAllTools()

        val matchedTools = allTools.filter { tool ->
            val toolCat = ToolCategoryDictionary.getToolCategory(tool.name)

            val matchCategory = normalizedCategory.isNullOrBlank() || toolCat == normalizedCategory
            if (!matchCategory) return@filter false

            if (keywordSet.isEmpty()) {
                true
            } else {
                val nameLower = tool.name.lowercase()
                val descLower = tool.description.lowercase()
                keywordSet.any { kw ->
                    nameLower.contains(kw) || descLower.contains(kw) || toolCat.contains(kw)
                }
            }
        }

        val resultArray = buildJsonArray {
            matchedTools.forEach { tool ->
                val propKeys = tool.inputSchema["properties"]?.jsonObject?.keys ?: emptySet()
                addJsonObject {
                    put("name", tool.name)
                    put("category", ToolCategoryDictionary.getToolCategory(tool.name))
                    put("description", tool.description)
                    put("parametersSummary", propKeys.joinToString(", "))
                }
            }
        }

        val response = buildJsonObject {
            if (normalizedCategory != null) {
                put("category", normalizedCategory)
            }
            if (keywordSet.isNotEmpty()) {
                put("keywords", buildJsonArray { keywordSet.forEach { add(it) } })
            }
            put("total", resultArray.size)
            put("tools", resultArray)
        }

        return ToolResult.success(response.toString())
    }
}

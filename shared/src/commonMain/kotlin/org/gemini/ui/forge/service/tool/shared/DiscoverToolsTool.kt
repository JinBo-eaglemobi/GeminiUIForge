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
                putJsonArray("enum") {
                    add("asset")
                    add("view")
                    add("template")
                    add("runtime")
                    add("interaction")
                    add("discovery")
                }
                put(
                    "description",
                    "可选：按工具业务大类精准筛选。支持枚举：'asset'（图像生成/重绘、AI抠图去背、图集切片与素材库资产操作）、'view'（图元增删改查、层级调整、坐标与8pt网格计算、界面排版布局与标注图渲染）、'template'（工程与模板创建/查询/删除、图集流水线导入、设计参考图分析逆向）、'runtime'（桌面窗口实时截图、系统运行状态、长任务异步轮询、Compose语义树与离屏渲染）、'interaction'（用户问询抉择弹窗、Toast通知、拟人化UI节点点击与文本输入）、'discovery'（技能规范与文档查阅、网络抓取、系统时间探测）。"
                )
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "可选：主推精准搜索关键词（如 'button', 'screenshot', 'color' 等），支持逗号或空格分隔。留空且无 category 时将列出全部可用工具。")
            }
            putJsonObject("queries") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put(
                    "description",
                    "可选：高级检索数组，同时搜索多个功能关键词或业务意图（如 ['asset', 'template']），系统合并去重一次性返回。"
                )
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

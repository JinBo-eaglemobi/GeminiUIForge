package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 搜索已启用专业技能元工具
 *
 * 核心特性：
 * 1. 【安全红线门禁】未启用的技能（Disabled）绝对不允许被搜索或列出；
 * 2. 支持多关键词并发检索 (queries: array)，一次性返回所有匹配的专业技能，杜绝多次反复调用。
 */
class SearchSkillsTool : McpToolDefinition {
    override val name: String = "search_skills"
    override val description: String =
        "根据关键词列表或业务设计场景搜索系统中已启用的专业技能与执行规范。支持传入单个词 (query) 或多关键词列表 (queries) 批量匹配。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("queries") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put(
                    "description",
                    "可选：同时搜索多个技能关键词或场景列表（例如 ['layout', 'slot', 'ui']），系统合并去重一次性返回所有匹配的技能规范。"
                )
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "可选：单个搜索关键词，亦支持使用逗号或空格分隔多个关键词。")
            }
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val keywordSet = mutableSetOf<String>()

        // 1. 从 queries 数组提取
        arguments["queries"]?.jsonArray?.forEach { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let {
                keywordSet.add(it)
            }
        }

        // 2. 从 query 字符串提取
        arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let { rawQuery ->
            rawQuery.split(',', '，', ';', '；', ' ').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                keywordSet.add(it)
            }
        }

        if (keywordSet.isEmpty()) {
            return McpToolResult.error("请至少提供一个技能搜索关键词 (query 或 queries)")
        }

        // 仅获取已启用的技能，严格物理隔离未启用的技能
        val enabledSkills = UiStudioSkillManager.getEnabledSkills()

        val matched = enabledSkills.filter { skill ->
            keywordSet.any { kw ->
                skill.id.lowercase().contains(kw) ||
                        skill.name.lowercase().contains(kw) ||
                        skill.description.lowercase().contains(kw) ||
                        skill.effectiveTriggers.any { it.lowercase().contains(kw) }
            }
        }

        val resultArray = buildJsonArray {
            matched.forEach { skill ->
                addJsonObject {
                    put("skillId", skill.id)
                    put("name", skill.name)
                    put("description", skill.description)
                    put("triggers", buildJsonArray { skill.effectiveTriggers.forEach { add(it) } })
                }
            }
        }

        val response = buildJsonObject {
            put("searchedQueries", buildJsonArray { keywordSet.forEach { add(it) } })
            put("totalMatched", resultArray.size)
            put("skills", resultArray)
            if (resultArray.isEmpty()) {
                put(
                    "message",
                    "未找到匹配的已启用技能（未启用技能已被系统安全隔离）。你可以直接基于自身的意图理解和通用工具完成任务。"
                )
            }
        }

        return McpToolResult.success(response.toString())
    }
}

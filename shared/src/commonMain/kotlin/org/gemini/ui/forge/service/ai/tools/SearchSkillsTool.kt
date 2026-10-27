package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 搜索已启用技能元工具
 * 【安全红线门禁】未启用的技能（Disabled）绝对不允许被搜索或列出
 */
class SearchSkillsTool : McpToolDefinition {
    override val name: String = "search_skills"
    override val description: String = "根据关键字或设计需求场景，搜索系统中当前已启用的专业技能与执行规范（未启用的技能不可见）。"
    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "要搜索的技能关键词、业务场景或规范名称（如 布局, 切片, spec_to_view, UI规范 等）")
            }
        }
        putJsonArray("required") {
            add("query")
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()

        // 仅获取已启用的技能，严格物理隔离未启用的技能
        val enabledSkills = UiStudioSkillManager.getEnabledSkills()

        val matched = enabledSkills.filter { skill ->
            skill.id.lowercase().contains(query) ||
                    skill.name.lowercase().contains(query) ||
                    skill.description.lowercase().contains(query) ||
                    skill.effectiveTriggers.any { it.lowercase().contains(query) }
        }

        val resultArray = buildJsonArray {
            matched.forEach { skill ->
                addJsonObject {
                    put("id", skill.id)
                    put("name", skill.name)
                    put("description", skill.description)
                    put("triggers", buildJsonArray { skill.effectiveTriggers.forEach { add(it) } })
                }
            }
        }

        val response = buildJsonObject {
            put("query", query)
            put("totalMatched", resultArray.size)
            put("skills", resultArray)
            if (resultArray.isEmpty()) {
                put("message", "未找到与 '$query' 相关的已启用技能（未启用技能已被系统安全隔离）。你可以直接基于自身的意图理解和通用工具完成任务。")
            }
        }

        return McpToolResult.success(response.toString())
    }
}

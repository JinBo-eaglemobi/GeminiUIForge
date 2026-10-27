package org.gemini.ui.forge.service.ai.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.UiStudioSkillManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 读取已启用技能规范元工具
 * 【安全红线门禁】未启用的技能（Disabled）严禁读取其执行规范
 */
class ReadSkillSpecTool : McpToolDefinition {
    override val name: String = "read_skill_spec"
    override val description: String = "读取指定技能的完整执行规范、提示词指导原则与标准操作流程。仅允许读取当前已启用的技能。"
    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("skill_id") {
                put("type", "string")
                put("description", "要读取的技能唯一标识 ID（如 spec_to_view）")
            }
        }
        putJsonArray("required") {
            add("skill_id")
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val skillId = arguments["skill_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        if (skillId.isBlank()) {
            return McpToolResult.error("参数 'skill_id' 不能为空")
        }

        // 校验技能是否已启用
        if (!UiStudioSkillManager.isSkillEnabled(skillId)) {
            val response = buildJsonObject {
                put("status", "DENIED")
                put("skillId", skillId)
                put("message", "技能 '$skillId' 当前处于停用状态或不存在，系统已安全隔离其规范。请直接基于你的自主意图推断和工作区核心工具执行操作。")
            }
            return McpToolResult.success(response.toString())
        }

        val skill = UiStudioSkillManager.getEnabledSkills().firstOrNull { it.id == skillId }
        if (skill == null) {
            return McpToolResult.error("未找到已启用的技能: $skillId")
        }

        val response = buildJsonObject {
            put("status", "SUCCESS")
            put("skillId", skill.id)
            put("name", skill.name)
            put("description", skill.description)
            put("instructions", skill.effectiveInstructions)
            put("recommendedTools", buildJsonArray { skill.effectiveTools.forEach { add(it) } })
        }

        return McpToolResult.success(response.toString())
    }
}

package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.geometry.GeometricHealthValidator
import org.gemini.ui.forge.utils.geometry.GeometricIssueLevel

/**
 * 设置或解除大厅模板工程的 AI 生成中锁定状态工具。
 * 用于在后台批量生成或校准图元期间保护工程完整性，防止用户误触大厅卡片进入未准备就绪的工作区。
 */
class SetProjectGeneratingStatusTool : McpToolDefinition {
    override val name: String = "set_project_generating_status"
    override val description: String = "设置或解除大厅模板工程的 AI 反向生成中锁定状态。isGenerating 为 true 时在大厅卡片展示呼吸动效并拦截点击；为 false 时恢复正常可访问状态。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "目标模板工程名称")
            }
            putJsonObject("isGenerating") {
                put("type", "boolean")
                put("description", "是否处于生成中锁定状态（true 为锁定禁止进入，false 为解锁恢复正常）")
            }
            putJsonObject("status") {
                put("type", "string")
                put("description", "可选：卡片上显示的生成状态提示文案（缺省为系统默认提示）")
            }
            putJsonObject("force") {
                put("type", "boolean")
                put("description", "可选：是否跳过几何健康度门禁强制解除锁定，默认 false")
            }
        }
        put("required", buildJsonArray {
            add("projectName")
            add("isGenerating")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val isGenerating = arguments["isGenerating"]?.jsonPrimitive?.booleanOrNull
            ?: return McpToolResult.error("参数 'isGenerating' 不能为空")
        val status = arguments["status"]?.jsonPrimitive?.contentOrNull
        val force = arguments["force"]?.jsonPrimitive?.booleanOrNull ?: false

        if (isGenerating) {
            val statusMsg = status ?: "正在由 AI 逆向生成图元工程..."
            UiRoadmapRegistry.markProjectGenerating(projectName, statusMsg)
        } else {
            if (!force) {
                val template = TemplateRepository().getTemplateByName(projectName)
                val page = template?.pages?.firstOrNull()
                if (page != null) {
                    val issues = GeometricHealthValidator.validate(page.blocks.bindParents(), page.width, page.height)
                    val blockers = issues.filter { it.level == GeometricIssueLevel.BLOCKER }
                    if (blockers.isNotEmpty()) {
                        return McpToolResult.error(
                            "⚠️ 拒绝解除锁定！项目 '$projectName' 存在 ${blockers.size} 处阻断级几何缺陷：\n" +
                                blockers.joinToString("\n") { "• ${it.description}" } +
                                "\n👉 必须先调用 update_block / delete_block / add_block 完成坐标纠偏，方可解除锁定！（如确需强制解锁请传入 force=true）"
                        )
                    }
                }
            }
            UiRoadmapRegistry.unmarkProjectGenerating(projectName)
        }

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectName)
            put("isGenerating", isGenerating)
            put("currentGeneratingCount", UiRoadmapRegistry.generatingProjects.value.size)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

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
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry

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

        if (isGenerating) {
            val statusMsg = status ?: "正在由 AI 逆向生成图元工程..."
            UiRoadmapRegistry.markProjectGenerating(projectName, statusMsg)
        } else {
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

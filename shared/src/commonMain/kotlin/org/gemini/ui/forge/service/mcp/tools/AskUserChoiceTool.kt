package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpInteractionBridge
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 用户多选一抉择弹窗工具：向用户展示多个方案选项并等待选择
 */
class AskUserChoiceTool : McpToolDefinition {

    override val name: String = "ask_user_choice"

    override val description: String =
        "向真实用户展示单选抉择弹窗并挂起等待用户做出选择。当 AI 面临多个可行方案、生成风格或设计分支时，可主动询问用户意向。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 300_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("title", buildJsonObject {
                put("type", "string")
                put("description", "抉择弹窗标题，如：请选择新图元的布局方向")
            })
            put("message", buildJsonObject {
                put("type", "string")
                put("description", "引导说明或背景描述")
            })
            put("options", buildJsonObject {
                put("type", "array")
                put("description", "供用户选择的方案或文案列表（最少2项，最多8项）")
                put("items", buildJsonObject { put("type", "string") })
            })
            put("allowCancel", buildJsonObject {
                put("type", "boolean")
                put("description", "是否允许用户放弃/取消选择，默认 true")
            })
            put("timeoutSeconds", buildJsonObject {
                put("type", "integer")
                put("description", "等待用户选择的超时秒数，默认 240 秒")
            })
        })
        put("required", buildJsonArray {
            add("title")
            add("options")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'title' 不能为空")
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull ?: ""
        val optionsArray = arguments["options"]?.jsonArray
            ?: return McpToolResult.error("参数 'options' 不能为空且必须为数组")
        val options = optionsArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        if (options.size < 2) {
            return McpToolResult.error("选项列表至少需要提供 2 个候选方案")
        }
        val allowCancel = arguments["allowCancel"]?.jsonPrimitive?.booleanOrNull ?: true
        val timeoutSeconds = arguments["timeoutSeconds"]?.jsonPrimitive?.intOrNull ?: 240
        val timeoutMs = (timeoutSeconds.coerceIn(5, 300)) * 1000L

        onProgress?.invoke(0.2f, "正在向用户屏幕推送选项弹窗...")
        val result = McpInteractionBridge.requestChoice(
            title = title,
            message = message,
            options = options,
            allowCancel = allowCancel,
            timeoutMs = timeoutMs
        )

        val responseJson = buildJsonObject {
            put("selectedIndex", result.selectedIndex)
            put("selectedOption", result.selectedOption)
            put("reason", result.reason)
        }

        return McpToolResult.success(responseJson.toString())
    }
}

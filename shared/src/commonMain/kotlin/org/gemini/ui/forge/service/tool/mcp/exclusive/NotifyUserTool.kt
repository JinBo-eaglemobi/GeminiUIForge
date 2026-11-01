package org.gemini.ui.forge.service.tool.mcp.exclusive

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpInteractionBridge

/**
 * 用户消息通知工具：向用户桌面应用推送即时轻量 Toast 提示
 */
class NotifyUserTool : ToolDefinition {

    override val name: String = "notify_user"

    override val description: String =
        "向用户桌面应用推送即时 Toast 提示消息。适用于操作完成提醒、长耗时任务进度里程碑报告或轻量状态告知，即发即忘，不阻塞执行。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 10_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("message", buildJsonObject {
                put("type", "string")
                put("description", "要展示给用户的通知文本内容")
            })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "通知类型：INFO (默认), SUCCESS, WARNING, ERROR")
                put("enum", buildJsonArray {
                    add("INFO")
                    add("SUCCESS")
                    add("WARNING")
                    add("ERROR")
                })
            })
            put("durationMillis", buildJsonObject {
                put("type", "integer")
                put("description", "气泡展示时长（毫秒），默认 3000")
            })
        })
        put("required", buildJsonArray {
            add("message")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'message' 不能为空")
        val type = arguments["type"]?.jsonPrimitive?.contentOrNull ?: "INFO"
        val durationMillis = arguments["durationMillis"]?.jsonPrimitive?.longOrNull ?: 3000L

        val delivered = McpInteractionBridge.sendNotification(
            message = message,
            type = type,
            durationMillis = durationMillis
        )

        val responseJson = buildJsonObject {
            put("delivered", delivered)
            put("uiReachable", McpInteractionBridge.isUiReachable())
            put("message", message)
        }

        return ToolResult.success(responseJson.toString())
    }
}


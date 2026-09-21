package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpInteractionBridge
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 询问确认弹窗工具：向真实用户弹出确认弹窗并挂起等待人工决策
 */
class AskUserConfirmationTool : McpToolDefinition {

    override val name: String = "ask_user_confirmation"

    override val description: String =
        "向真实用户弹出确认弹窗并挂起等待人工批复。适用于删除模板、重置配置、批量生图等重大或破坏性操作前的最终人工授权门禁。超时或 UI 不可达时一律返回拒绝。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 300_000L // 最多等待 5 分钟

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("title", buildJsonObject {
                put("type", "string")
                put("description", "弹窗标题，如：确认删除模块 btn_spin")
            })
            put("message", buildJsonObject {
                put("type", "string")
                put("description", "对操作意图及潜在影响的详细说明")
            })
            put("isDestructive", buildJsonObject {
                put("type", "boolean")
                put("description", "是否为破坏性操作（如删除、覆盖），为 true 时确认按钮呈警示红色，默认 false")
            })
            put("confirmText", buildJsonObject {
                put("type", "string")
                put("description", "确认按钮的自定义显示文案，默认为'允许执行'")
            })
            put("dismissText", buildJsonObject {
                put("type", "string")
                put("description", "取消按钮的自定义显示文案，默认为'拒绝'")
            })
            put("timeoutSeconds", buildJsonObject {
                put("type", "integer")
                put("description", "等待用户响应的超时秒数，默认 240 秒")
            })
        })
        put("required", buildJsonArray {
            add("title")
            add("message")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'title' 不能为空")
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'message' 不能为空")
        val isDestructive = arguments["isDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
        val confirmText = arguments["confirmText"]?.jsonPrimitive?.contentOrNull ?: "允许执行"
        val dismissText = arguments["dismissText"]?.jsonPrimitive?.contentOrNull ?: "拒绝"
        val timeoutSeconds = arguments["timeoutSeconds"]?.jsonPrimitive?.intOrNull ?: 240
        val timeoutMs = (timeoutSeconds.coerceIn(5, 300)) * 1000L

        onProgress?.invoke(0.2f, "正在向用户屏幕推送确认弹窗...")
        val result = McpInteractionBridge.requestConfirmation(
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            isDestructive = isDestructive,
            timeoutMs = timeoutMs
        )

        val responseJson = buildJsonObject {
            put("approved", result.approved)
            put("reason", result.reason)
            put("details", when (result.reason) {
                "user_confirmed" -> "用户在界面上明确点击了确认授权"
                "user_rejected" -> "用户在界面上主动点击了拒绝或关闭"
                "timeout" -> "等待用户授权超时（默认判定为拒绝）"
                "ui_unreachable" -> "应用程序前台窗口未就绪或未处于可交互状态"
                else -> result.reason
            })
        }

        return if (result.approved) {
            McpToolResult.success(responseJson.toString())
        } else {
            // 返回正常 JSON，但 approved 为 false
            McpToolResult.text(responseJson.toString(), isError = false)
        }
    }
}

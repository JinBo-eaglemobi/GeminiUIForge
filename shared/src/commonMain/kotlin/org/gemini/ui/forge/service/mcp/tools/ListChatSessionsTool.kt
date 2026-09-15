package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.SessionCacheManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 历史会话清单查询工具
 */
class ListChatSessionsTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "list_chat_sessions"

    override val description: String =
        "查询 AI 视觉工作室在指定图元 (scopeId) 下保存的历史会话列表，获取会话 ID、标题、更新时间与消息条数。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("scopeId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元 blockId 或功能域标识，如 'btn_spin'")
            })
        })
        put("required", buildJsonArray { add("scopeId") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val scopeId = arguments["scopeId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'scopeId' 不能为空")

        onProgress?.invoke(0.3f, "正在检索会话归档...")
        val sessionManager = SessionCacheManager(storage)
        val sessions = sessionManager.listSessions(scopeId)

        onProgress?.invoke(0.8f, "正在组装会话摘要 (${sessions.size} 条)...")
        val jsonArray = buildJsonArray {
            sessions.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id)
                    put("title", s.title)
                    put("scopeId", s.scopeId)
                    put("messageCount", s.messages.size)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                })
            }
        }

        onProgress?.invoke(1.0f, "会话列表查询就绪")
        return McpToolResult.text(jsonArray.toString())
    }
}

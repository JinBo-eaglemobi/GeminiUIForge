package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 原始网络通信报文归档查询工具
 */
class GetTrafficRecordTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "get_traffic_record"

    override val description: String =
        "按图元 scopeId 与会话 sessionId 查询真实底层的原始 HTTP 请求与响应报文 (Raw Traffic Archive)，支持按序号精确过滤。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("scopeId", buildJsonObject {
                put("type", "string")
                put("description", "图元 ID 或功能域标识")
            })
            put("sessionId", buildJsonObject {
                put("type", "string")
                put("description", "目标会话 ID")
            })
            put("seq", buildJsonObject {
                put("type", "integer")
                put("description", "可选：报文自增序号，传入时仅查询特定序号的请求与响应")
            })
        })
        put("required", buildJsonArray {
            add("scopeId")
            add("sessionId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val scopeId = arguments["scopeId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'scopeId' 不能为空")
        val sessionId = arguments["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'sessionId' 不能为空")
        val targetSeq = arguments["seq"]?.jsonPrimitive?.intOrNull

        onProgress?.invoke(0.3f, "正在从本地磁盘分流读取通信报文...")
        val trafficStore = SessionTrafficStore(storage)
        val records = trafficStore.listRecords(scopeId, sessionId)

        val filtered = if (targetSeq != null) {
            records.filter { it.seq == targetSeq }
        } else {
            records
        }

        onProgress?.invoke(0.8f, "正在序列化报文数据 (${filtered.size} 条)...")
        val jsonArray = buildJsonArray {
            filtered.forEach { r ->
                add(looseJson.encodeToJsonElement(r))
            }
        }

        onProgress?.invoke(1.0f, "通信报文查询就绪")
        return McpToolResult.text(jsonArray.toString())
    }
}

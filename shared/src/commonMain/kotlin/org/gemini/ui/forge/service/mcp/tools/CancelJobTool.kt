package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.mcp.McpJobManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 取消后台异步长任务工具
 */
class CancelJobTool : McpToolDefinition {

    override val name: String = "cancel_job"

    override val description: String =
        "显式取消一个正在执行的后台异步长任务（如大模型识别、批量生图等）。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("jobId", buildJsonObject {
                put("type", "string")
                put("description", "要取消的目标作业标识 jobId")
            })
        })
        put("required", buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("jobId"))
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val jobId = arguments["jobId"]?.jsonPrimitive?.content
            ?: return McpToolResult.error("缺少必需参数: jobId")

        val cancelled = McpJobManager.cancelJob(jobId)
        return if (cancelled) {
            McpToolResult.success("作业 [$jobId] 已成功发送取消信号")
        } else {
            McpToolResult.error("作业 [$jobId] 不存在或已处于完成/终止状态，无法取消")
        }
    }
}

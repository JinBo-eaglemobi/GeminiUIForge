package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.mcp.McpJobManager

/**
 * 取消后台异步长任务工具
 */
class CancelJobTool : ToolDefinition {

    override val name: String = "cancel_job"

    override val description: String =
        "显式取消一个正在执行的后台异步长任务（如大模型识别、批量生图等）。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
    ): ToolResult {
        val jobId = arguments["jobId"]?.jsonPrimitive?.content
            ?: return ToolResult.error("缺少必需参数: jobId")

        val cancelled = McpJobManager.cancelJob(jobId)
        return if (cancelled) {
            ToolResult.success("作业 [$jobId] 已成功发送取消信号")
        } else {
            ToolResult.error("作业 [$jobId] 不存在或已处于完成/终止状态，无法取消")
        }
    }
}


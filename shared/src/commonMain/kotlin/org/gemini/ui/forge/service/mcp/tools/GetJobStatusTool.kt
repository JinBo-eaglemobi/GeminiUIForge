package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.mcp.McpContent
import org.gemini.ui.forge.service.mcp.McpJobManager
import org.gemini.ui.forge.service.mcp.McpJobState
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 异步作业状态与长轮询查询工具
 */
class GetJobStatusTool : McpToolDefinition {

    override val name: String = "get_job_status"

    override val description: String =
        "查询后台异步长任务的执行进度与产物。支持服务端自适应长轮询（Long Polling）：当任务仍在执行时挂起等待至多 waitSeconds 秒（可配置，缺省读取系统默认值 25s，安全上限 45s），任务提前完成时立即返回，彻底免疫客户端超时并大幅节省 Token。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("jobId", buildJsonObject {
                put("type", "string")
                put("description", "提交异步任务时返回的唯一作业标识 jobId")
            })
            put("waitSeconds", buildJsonObject {
                put("type", "integer")
                put("description", "可选：服务端长轮询等待秒数（0 表示即刻返回不等待；缺省时自动读取系统配置默认值 25s，最大安全上限 45s）")
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

        val waitSeconds = arguments["waitSeconds"]?.jsonPrimitive?.intOrNull

        val pair = McpJobManager.awaitJob(jobId, waitSeconds)
            ?: return McpToolResult.error("未找到指定的作业或该作业记录已过期: $jobId")

        val (snapshot, originalResult) = pair

        // 如果任务已成功完成且有产物，将状态摘要与多模态产物合并输出
        return if (snapshot.state == McpJobState.COMPLETED && originalResult != null) {
            val statusSummaryText = "【异步作业已完成】${snapshot.jobId} (耗时: ${snapshot.durationMs}ms)"
            val combinedContents = buildList {
                add(McpContent.Text(statusSummaryText))
                addAll(originalResult.content)
            }
            McpToolResult(combinedContents, isError = false)
        } else if (snapshot.state == McpJobState.FAILED) {
            McpToolResult.error("异步作业执行失败 [${snapshot.jobId}]: ${snapshot.error ?: snapshot.stageMessage}")
        } else if (snapshot.state == McpJobState.CANCELLED) {
            McpToolResult.error("异步作业已被取消 [${snapshot.jobId}]")
        } else {
            // 仍在运行中（PENDING / RUNNING）
            val jsonText = org.gemini.ui.forge.utils.looseJson.encodeToString(
                JsonObject.serializer(),
                snapshot.toJson()
            )
            McpToolResult.success(jsonText)
        }
    }
}

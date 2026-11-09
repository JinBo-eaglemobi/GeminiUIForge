package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.job.ImageJobManager
import org.gemini.ui.forge.service.job.ImageJobStatus
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 异步生图状态与产物查询工具 (Query Image Job Tool)
 *
 * 供 AI 智能体与 MCP 客户端通过 jobId 实时轮询异步生图进度、耗时与最终图像产物。
 */
class QueryImageJobTool : ToolDefinition {

    override val name: String = "query_image_job"

    override val description: String =
        "查询指定异步生图任务（jobId）的实时状态与生成产物。当生图仍在进行中时返回当前进度与耗时；当生图成功完成时，直接返回 ToolResult.Image 图像产物与规格信息；若任务失败返回具体错误原因。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        destructiveHint = false,
        openWorldHint = false
    )

    override val timeoutMs: Long = 10_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("jobId", buildJsonObject {
                put("type", "string")
                put("description", "要查询的异步生图任务唯一标识（由 generate_image 工具返回，形如 'img_job_1794187939_123456'）")
            })
        })
        put("required", buildJsonArray {
            add("jobId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val jobId = arguments["jobId"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'jobId' 不能为空")

        val record = ImageJobManager.getJob(jobId)
            ?: return ToolResult.error("未找到指定的生图任务 [jobId: $jobId]，可能任务已过期或未成功提交")

        return when (record.status) {
            ImageJobStatus.SUBMITTED, ImageJobStatus.RUNNING -> {
                val runningJson = buildJsonObject {
                    put("status", "RUNNING")
                    put("jobId", record.jobId)
                    put("progress", record.progress)
                    put("elapsedSeconds", record.elapsedSeconds)
                    put("statusMessage", record.statusMessage)
                    put("message", "AI 生图正在生成中（${record.statusMessage}），已耗时 ${record.elapsedSeconds} 秒。请稍后继续调用 query_image_job 轮询，未确认完成前禁止执行后续依赖任务。")
                }
                ToolResult.text(runningJson.toString())
            }

            ImageJobStatus.SUCCESS -> {
                val base64 = record.base64Data ?: ""
                val mime = record.mimeType ?: "image/png"
                val resultMessage = buildString {
                    append("🎉 AI 独立文生图生成成功！\n")
                    append("🆔 任务ID: ${record.jobId}\n")
                    append("🎨 提示词: ${record.prompt}\n")
                    append("📐 画面规格: ${record.width.toInt()}x${record.height.toInt()}\n")
                    append("⏱️ 总耗时: ${record.elapsedSeconds} 秒\n")
                    if (record.savedRelativePath != null) {
                        append("📁 素材库归档: `${record.savedRelativePath}`\n")
                    } else {
                        append("💾 内存模式: 图片数据已完整持久化于会话日志中，可通过操作栏按需存入素材库。")
                    }
                }

                ToolResult.image(
                    base64Data = base64,
                    mimeType = mime,
                    message = resultMessage
                )
            }

            ImageJobStatus.FAILED -> {
                ToolResult.error("生图任务执行失败 [jobId: ${record.jobId}]: ${record.errorMessage ?: "未知错误"}")
            }
        }
    }
}

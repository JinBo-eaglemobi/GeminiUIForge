package org.gemini.ui.forge.service.tool.ai.exclusive

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 系统实时时钟与运行环境工具
 */
class GetCurrentTimeTool : ToolDefinition {

    override val name: String = "get_current_time"

    override val description: String =
        "获取宿主机系统的精确当前时间（年月日时分秒、星期几、时区）及操作系统平台环境信息。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val now = kotlin.time.Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val local = now.toLocalDateTime(timeZone)

        val weekdayZh = when (local.dayOfWeek.name) {
            "MONDAY" -> "星期一"
            "TUESDAY" -> "星期二"
            "WEDNESDAY" -> "星期三"
            "THURSDAY" -> "星期四"
            "FRIDAY" -> "星期五"
            "SATURDAY" -> "星期六"
            else -> "星期日"
        }

        val formattedTime = "${local.year}-${local.month.number.toString().padStart(2, '0')}-${local.day.toString().padStart(2, '0')} " +
                "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"

        val resultJson = buildJsonObject {
            put("current_time", formattedTime)
            put("weekday", weekdayZh)
            put("timezone", timeZone.id)
            put("timestamp_millis", now.toEpochMilliseconds())
            put("platform", "Compose Multiplatform Desktop (JVM)")
        }

        return ToolResult.success(resultJson.toString())
    }
}

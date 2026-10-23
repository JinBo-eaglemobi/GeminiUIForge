package org.gemini.ui.forge.service.ai.tools

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import kotlin.math.round

/**
 * 1. 系统实时时钟与运行环境工具
 */
class GetCurrentTimeTool : McpToolDefinition {
    override val name: String = "get_current_time"
    override val description: String =
        "获取宿主机系统的精确当前时间（年月日时分秒、星期几、时区）及操作系统平台环境信息。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
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
    ): McpToolResult {
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

        return McpToolResult.success(resultJson.toString())
    }
}

/**
 * 2. 高精度 UI 人机工程学几何计算器
 */
class CalculateGeometryTool : McpToolDefinition {
    override val name: String = "calculate_geometry"
    override val description: String =
        "执行严格的 8pt 空间网格对齐、嵌套同心圆角曲率半径计算 (R_outer = R_inner + Padding)、纵横比外切/内嵌等纯数学几何计算。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("operation", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("8pt_snap")
                    add("concentric_radius")
                    add("aspect_ratio_fit")
                })
                put("description", "运算类型：8pt_snap (对齐到最近的8倍数), concentric_radius (同心嵌套圆角曲率计算), aspect_ratio_fit (纵横比外切/内嵌计算)")
            })
            put("parameters", buildJsonObject {
                put("type", "object")
                put("description", "运算参数字典")
            })
        })
        put("required", buildJsonArray { add("operation"); add("parameters") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val op = arguments["operation"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("缺少必填参数 operation")
        val params = arguments["parameters"]?.jsonObject ?: buildJsonObject {}

        return when (op) {
            "concentric_radius" -> {
                val innerR = params["inner_radius"]?.jsonPrimitive?.floatOrNull ?: 8f
                val padding = params["padding"]?.jsonPrimitive?.floatOrNull ?: 8f
                val outerR = innerR + padding
                val res = buildJsonObject {
                    put("outer_radius", outerR)
                    put("inner_radius", innerR)
                    put("padding", padding)
                    put("formula", "$outerR = $innerR + $padding")
                }
                McpToolResult.success(res.toString())
            }
            "8pt_snap" -> {
                val value = params["value"]?.jsonPrimitive?.floatOrNull ?: 0f
                val snapped = round(value / 8f) * 8f
                val res = buildJsonObject {
                    put("original", value)
                    put("snapped_8pt", snapped)
                }
                McpToolResult.success(res.toString())
            }
            "aspect_ratio_fit" -> {
                val targetW = params["target_width"]?.jsonPrimitive?.floatOrNull ?: 100f
                val targetH = params["target_height"]?.jsonPrimitive?.floatOrNull ?: 100f
                val maxW = params["max_width"]?.jsonPrimitive?.floatOrNull ?: 800f
                val maxH = params["max_height"]?.jsonPrimitive?.floatOrNull ?: 600f
                val scale = minOf(maxW / targetW, maxH / targetH)
                val fitW = targetW * scale
                val fitH = targetH * scale
                val res = buildJsonObject {
                    put("fit_width", fitW)
                    put("fit_height", fitH)
                    put("scale_factor", scale)
                }
                McpToolResult.success(res.toString())
            }
            else -> McpToolResult.error("未支持的几何运算: $op")
        }
    }
}

/**
 * 3. 通用网页内容抓取阅读工具
 */
class WebFetchTool : McpToolDefinition {
    override val name: String = "web_fetch"
    override val description: String =
        "通过 HTTP/HTTPS GET 抓取指定网页内容，并提取纯净可读正文文本，便于 AI 获取最新设计参考或官方规范。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        openWorldHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("url", buildJsonObject {
                put("type", "string")
                put("description", "要抓取的网页绝对 URL，如 'https://example.com/spec.html'")
            })
            put("maxLength", buildJsonObject {
                put("type", "integer")
                put("description", "最大返回字符数，默认 4000")
            })
        })
        put("required", buildJsonArray { add("url") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'url' 不能为空")
        val maxLen = arguments["maxLength"]?.jsonPrimitive?.intOrNull ?: 4000

        onProgress?.invoke(0.2f, "正在连接: $url...")
        return try {
            val response = NetworkClient.shared.get(url)
            if (!response.status.isSuccess()) {
                return McpToolResult.error("HTTP 请求失败: ${response.status}")
            }
            onProgress?.invoke(0.6f, "正在解析网页内容...")
            val rawHtml = response.bodyAsText()

            // 轻量剥离 script, style 与 HTML 标签
            val noScript = rawHtml.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            val noStyle = noScript.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            val textOnly = noStyle.replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
                .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
                .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
                .replace(Regex("\\s+"), " ")
                .trim()

            val truncated = if (textOnly.length > maxLen) {
                textOnly.take(maxLen) + "\n\n...(已截断，总长度: ${textOnly.length} 字符)"
            } else {
                textOnly
            }

            onProgress?.invoke(1.0f, "内容提取完成")
            McpToolResult.success(truncated)
        } catch (e: Exception) {
            McpToolResult.error("网页抓取失败: ${e.message}")
        }
    }
}

/**
 * 4. 工作区与项目文档只读阅读器
 */
class ReadProjectFileTool(
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {
    override val name: String = "read_project_file"
    override val description: String =
        "安全只读检索当前项目私有存储目录下的文本文件、设计规范 Markdown 或 JSON 配置文件。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("relativePath", buildJsonObject {
                put("type", "string")
                put("description", "相对于应用存储根目录的路径，例如 'templates/Football/spec.md' 或 'config.json'")
            })
        })
        put("required", buildJsonArray { add("relativePath") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val relPath = arguments["relativePath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'relativePath' 不能为空")

        // 安全防越权检查
        if (relPath.contains("..") || relPath.startsWith("/") || relPath.startsWith("\\")) {
            return McpToolResult.error("非法的相对路径，禁止跨目录访问")
        }

        onProgress?.invoke(0.3f, "正在读取: $relPath...")
        val content = fileStorage.readFromFile(relPath)
            ?: return McpToolResult.error("未找到文件或文件内容为空: $relPath")

        onProgress?.invoke(1.0f, "读取完成")
        return McpToolResult.success(content)
    }
}

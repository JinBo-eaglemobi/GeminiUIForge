package org.gemini.ui.forge.service.tool.ai.exclusive

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 通用网页内容抓取阅读工具
 */
class WebFetchTool : ToolDefinition {

    override val name: String = "web_fetch"

    override val description: String =
        "通过 HTTP/HTTPS GET 抓取指定网页内容，并提取纯净可读正文文本，便于 AI 获取最新设计参考或官方规范。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
    ): ToolResult {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'url' 不能为空")
        val maxLen = arguments["maxLength"]?.jsonPrimitive?.intOrNull ?: 4000

        onProgress?.invoke(0.2f, "正在连接: $url...")
        return try {
            val response = NetworkClient.shared.get(url)
            if (!response.status.isSuccess()) {
                return ToolResult.error("HTTP 请求失败: ${response.status}")
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
            ToolResult.success(truncated)
        } catch (e: Exception) {
            ToolResult.error("网页抓取失败: ${e.message}")
        }
    }
}

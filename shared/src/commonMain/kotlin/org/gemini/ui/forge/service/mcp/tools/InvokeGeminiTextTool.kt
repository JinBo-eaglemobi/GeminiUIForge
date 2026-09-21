package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * AI 直调工具：使用应用程序已配置的 Gemini 密钥直接进行纯文本推理调用
 * 零接触任何工程与模板文件，不改变应用程序状态
 */
class InvokeGeminiTextTool(
    private val configManager: ConfigManager = ConfigManager(),
    private val geminiClient: GeminiClient = GeminiClient()
) : McpToolDefinition {

    override val name: String = "invoke_gemini_text"

    override val description: String =
        "使用本程序已配置的 Gemini 密钥直接执行一次纯文本推理调用（翻译、解析、文本润色、方案评估等）。不读写任何工程模板数据，对程序状态零影响。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 120_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("prompt", buildJsonObject {
                put("type", "string")
                put("description", "要提交给 Gemini 的用户提示词或待处理文本")
            })
            put("systemPrompt", buildJsonObject {
                put("type", "string")
                put("description", "可选的系统级指令（System Instruction），设定 AI 角色或回答规范")
            })
            put("model", buildJsonObject {
                put("type", "string")
                put("description", "使用的 Gemini 模型名，默认 gemini-2.5-flash。备选：gemini-2.5-flash-lite, gemini-2.5-pro 等")
            })
            put("temperature", buildJsonObject {
                put("type", "number")
                put("description", "采样随机度 (0.0 ~ 2.0)，默认 0.7")
            })
        })
        put("required", buildJsonArray {
            add("prompt")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val prompt = arguments["prompt"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'prompt' 不能为空")
        val systemPrompt = arguments["systemPrompt"]?.jsonPrimitive?.contentOrNull
        val model = arguments["model"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "gemini-2.5-flash"
        val temperature = arguments["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.7

        onProgress?.invoke(0.1f, "正在读取已配置的 Gemini 凭据...")
        val apiKey = configManager.loadKey("GEMINI_API_KEY")?.ifBlank { null }
            ?: configManager.loadGlobalGeminiKey()?.ifBlank { null }
            ?: return McpToolResult.error("本地未配置 Gemini API Key，请先在应用设置中填写 GEMINI_API_KEY 或配置环境变量")

        val baseUrl = configManager.loadKey("GEMINI_BASE_URL")?.ifBlank { null }
            ?: "https://generativelanguage.googleapis.com"
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val url = "$cleanBaseUrl/v1beta/models/$model:streamGenerateContent?key=$apiKey&alt=sse"

        val requestBody = buildJsonObject {
            if (!systemPrompt.isNullOrBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemPrompt) })
                    })
                })
            }
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
            })
        }.toString()

        onProgress?.invoke(0.3f, "正在向 Gemini 发起生成推理 ($model)...")
        val textBuilder = StringBuilder()

        return try {
            geminiClient.streamGenerateContent(
                url = url,
                requestBody = requestBody,
                onLog = { logMsg ->
                    onProgress?.invoke(0.6f, logMsg)
                },
                onChunk = { chunk ->
                    textBuilder.append(chunk)
                }
            )

            val fullText = textBuilder.toString()
            val resultJson = buildJsonObject {
                put("model", model)
                put("text", fullText)
                put("length", fullText.length)
            }
            McpToolResult.success(resultJson.toString())
        } catch (e: Exception) {
            McpToolResult.error("调用 Gemini 失败: ${e.message}")
        }
    }
}

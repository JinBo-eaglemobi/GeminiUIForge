package org.gemini.ui.forge.service

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.utils.AppLogger

/**
 * 统一的 Gemini AI 通信客户端
 * 
 * 本类负责封装与 Google Gemini (以及 Imagen) API 的底层 HTTP 通信。
 * 核心功能包括：
 * 1. **流式与非流式调用**：支持基于 Server-Sent Events (SSE) 的流式响应以及普通的一键式请求。
 * 2. **数据解析统一化**：兼容 Gemini 的 `candidates` 和 Imagen 的 `predictions` 数据结构，自动提取有效文本。
 * 3. **日志脱敏与跟踪**：自动拦截并替换请求中庞大的 Base64 图片数据，保持控制台与磁盘日志的整洁。
 * 4. **超时与异常处理**：内置合理的请求超时配置，并对网络异常进行统一捕获与抛出。
 */
class GeminiClient {
    private val TAG = "GeminiClient"
    
    /** 
     * 全局复用的 JSON 解析器配置 
     * ignoreUnknownKeys = true 确保当 API 新增未知字段时不会导致解析崩溃。
     */
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 执行流式生成内容请求 (Stream Generate Content)
     * 
     * 该方法使用 Ktor 的 `preparePost` 建立长连接，并逐行读取 Server-Sent Events (SSE) 格式的数据。
     * 主要用于耗时较长的文本生成、代码生成等场景，允许 UI 实时打字机式展示进度。
     *
     * @param url 请求的 API 完整路径
     * @param requestBody 序列化后的 JSON 请求体字符串
     * @param onLog 外部传入的日志回调，用于实时向 UI 或调用方输出执行阶段日志
     * @param onRawData 当解析到有效 JSON 块时触发，暴露原始 [JsonElement]，可用于手动提取非文本数据（如内联的生图 Base64 结果）
     * @param onChunk 当提取到有效的纯文本片段时触发，供外部拼接最终文本
     * @throws Exception 网络断开或 HTTP 状态码非 200 时抛出异常
     */
    suspend fun streamGenerateContent(
        url: String,
        requestBody: String,
        onLog: (String) -> Unit = {},
        onRawData: (JsonElement) -> Unit = {}, 
        onChunk: (String) -> Unit = {}
    ) {
        val client = NetworkClient.shared
        // 打印脱敏后的请求体日志，避免 Base64 刷屏
        logRequest(url, requestBody, onLog)

        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                // 配置长连接超时：流式生成可能耗时达数分钟
                timeout { 
                    requestTimeoutMillis = 300_000L // 总请求超时: 5分钟
                    connectTimeoutMillis = 30_000L  // 建立连接超时: 30秒
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    // 持续读取直到通道关闭
                    while (!channel.isClosedForRead) {
                        // 替换已废弃的 readUTF8Line()，改用官方推荐的 readLine()
                        val line = channel.readLine() ?: break
                        
                        // 过滤掉非 SSE 的空行，只处理以 "data: " 开头的标准负载
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            
                            // 忽略流结束标记
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            
                            try {
                                val jsonElement = jsonConfig.parseToJsonElement(dataJson)
                                onRawData(jsonElement) // 向外抛出完整的响应结构，方便外部高度自定义(如图文混合)
                                
                                val textChunk = extractText(jsonElement)
                                if (textChunk != null) {
                                    onChunk(textChunk)
                                }
                            } catch (_: Exception) {
                                // 忽略单次 JSON 块解析错误，避免中断整个流
                            }
                        }
                    }
                } else {
                    // HTTP 非 20x 时，记录详细的错误 Body 并向上抛出
                    val errorBody = response.bodyAsText()
                    AppLogger.e(TAG, "API 响应失败: ${response.status}\n$errorBody")
                    throw Exception("API 失败: ${response.status}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "流式通信异常", e)
            throw e
        }
    }

    /**
     * 执行一次性生成内容请求 (Generate Content)
     * 
     * 适用于短文本交互、属性分析、抠图请求等不需要打字机效果的场景。
     * 它将等待整个服务端生成完毕后一次性返回结果。
     *
     * @param url 请求的 API 完整路径
     * @param requestBody 序列化后的 JSON 请求体字符串
     * @param onLog 外部传入的日志回调
     * @return 解析后的 AI 回复纯文本（或特定关键字段）
     * @throws Exception 当找不到有效文本或网络失败时抛出异常
     */
    suspend fun generateContent(
        url: String,
        requestBody: String,
        onLog: (String) -> Unit = {}
    ): String {
        val client = NetworkClient.shared
        logRequest(url, requestBody, onLog)

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                // 非流式请求超时时间较短
                timeout { 
                    requestTimeoutMillis = 60_000L // 60秒 
                }
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val jsonElement = jsonConfig.parseToJsonElement(responseText)
                // 尝试提取文本内容，若提取不到则视为失败
                return extractText(jsonElement) ?: throw Exception("响应中未找到有效文本")
            } else {
                val errorBody = response.bodyAsText()
                AppLogger.e(TAG, "API 响应失败: ${response.status}\n$errorBody")
                throw Exception("API 失败: ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "非流式通信异常", e)
            throw e
        }
    }

    /**
     * 从 API 响应的 JsonElement 中智能提取文本内容
     * 
     * 该方法内置了对 Google 不同 AI 产品线响应结构的兼容：
     * - **Gemini 模型**：数据包裹在 `candidates[0].content.parts[...].text`
     * - **Imagen 模型**：数据包裹在 `predictions[...].bytesBase64Encoded` 等
     * 
     * @param jsonElement Ktor 解析出的顶层 JSON 节点
     * @return 提取到的字符串（文本或 Base64），若无匹配格式则返回 null
     */
    private fun extractText(jsonElement: JsonElement): String? {
        val candidates = jsonElement.jsonObject["candidates"]?.jsonArray
            ?: jsonElement.jsonObject["predictions"]?.jsonArray
        
        candidates?.firstOrNull()?.jsonObject?.let { candidate ->
            // 1. 尝试匹配常规 Gemini 文本节点
            val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            if (parts != null) {
                return parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            }
            
            // 2. 尝试匹配 Imagen 模型结构或老版扁平 text 字段
            return candidate["text"]?.jsonPrimitive?.content 
                ?: candidate["bytesBase64Encoded"]?.jsonPrimitive?.content
        }
        return null
    }

    /**
     * 过滤日志中臃肿的 Base64 图片数据并统一输出 HTTP 请求日志
     * 
     * 该方法使用正则替换，防止巨型图片的 Base64 编码 (可达数MB) 污染控制台和磁盘日志文件。
     */
    private fun logRequest(url: String, requestBody: String, onLog: (String) -> Unit) {
        val sanitizedBody = requestBody
            // 替换 inlineData 中的 data 节点
            .replace(Regex("\"data\"\\s*:\\s*\"[^\"]+\""), "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            // 替换 Imagen 请求中的 bytesBase64Encoded 节点
            .replace(Regex("\"bytesBase64Encoded\"\\s*:\\s*\"[^\"]+\""), "\"bytesBase64Encoded\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            // 隐藏可能非常长的历史 JSON 状态
            .replace(Regex("\"text\"\\s*:\\s*\"CURRENT_JSON_STATE: [\\s\\S]*?\""), "\"text\": \"CURRENT_JSON_STATE: <HIDDEN_FOR_LOGS>\"")
        
        val logMessage = "---- [AI REQUEST] ----\nURL: $url\nBody: \n$sanitizedBody\n------------------------"
        onLog(logMessage)
        AppLogger.i(TAG, logMessage)
    }

    /**
     * 清理 AI 返回文本中可能携带的 Markdown JSON 代码块格式
     * 
     * 当要求 AI 严格输出 JSON 时，大模型常常会自动包裹 ` ```json `。
     * 该方法去除这些无关字符，使之能够被 kotlinx.serialization 直接解析。
     */
    fun cleanJson(text: String): String {
        return text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}

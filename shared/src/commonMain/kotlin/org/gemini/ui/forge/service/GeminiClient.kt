package org.gemini.ui.forge.service

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson

/**
 * 统一的 Gemini AI 通信客户端
 * 
 * 本类负责封装与 Google Gemini API 的底层 HTTP 通信。
 * 核心功能包括：
 * 1. **流式与非流式调用**：支持基于 Server-Sent Events (SSE) 的流式响应以及普通的一键式请求。
 * 2. **数据解析统一化**：兼容 Gemini 的 `candidates` 数据结构，自动提取有效文本。
 * 3. **日志脱敏与跟踪**：自动拦截并替换请求中庞大的 Base64 图片数据，保持控制台与磁盘日志的整洁。
 * 4. **超时与异常处理**：内置合理的请求超时配置，并对网络异常进行统一捕获与抛出。
 */
class GeminiClient {
    private val TAG = "GeminiClient"

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
        onChunk: (String) -> Unit = {},
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)? = null
    ) {
        val client = NetworkClient.shared
        // ★ 原始档案通道：在任何脱敏/截断前捕获完整请求原文
        onRawTraffic?.invoke(TrafficDirection.REQ, url, requestBody)
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
                    val rawResponses = mutableListOf<String>()
                    // 持续读取直到通道关闭
                    while (!channel.isClosedForRead) {
                        // 替换已废弃的 readUTF8Line()，改用官方推荐的 readLine()
                        val line = channel.readLine() ?: break
                        
                        // 过滤掉非 SSE 的空行，只处理以 "data: " 开头的标准负载
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            
                            // 忽略流结束标记
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            rawResponses.add(dataJson)
                            
                            try {
                                val jsonElement = looseJson.parseToJsonElement(dataJson)
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

                    // 流式读取完成后，将聚合的原始完整响应原文回调归档
                    if (rawResponses.isNotEmpty()) {
                        val fullRawResp = if (rawResponses.size == 1) rawResponses.first() else "[\n" + rawResponses.joinToString(",\n") + "\n]"
                        onRawTraffic?.invoke(TrafficDirection.RESP, url, fullRawResp)
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
     * 从 API 响应的 JsonElement 中智能提取文本内容
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

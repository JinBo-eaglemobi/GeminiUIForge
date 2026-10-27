package org.gemini.ui.forge.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 报文一句话业务摘要提取器
 * 用于在通信档案中提取请求/响应的直观业务说明，避免用户面对大段 JSON 看不懂轮次
 */
object TrafficSummaryExtractor {

    private val jsonHelper = Json { ignoreUnknownKeys = true }

    /**
     * 根据报文方向与 Body 提取直观说明
     */
    fun extractSummary(direction: String, body: String): String {
        if (body.isBlank()) return "空报文"
        return try {
            val element = jsonHelper.parseToJsonElement(body)
            if (element is JsonObject) {
                if (direction.contains("REQ", ignoreCase = true)) {
                    extractRequestSummary(element)
                } else {
                    extractResponseSummary(element)
                }
            } else {
                body.take(60).replace("\n", " ")
            }
        } catch (_: Exception) {
            // 正则兜底解析
            fallbackRegexSummary(direction, body)
        }
    }

    private fun extractRequestSummary(root: JsonObject): String {
        // 1. 检查 contents 数组
        val contents = root["contents"]?.jsonArray
        if (contents != null && contents.isNotEmpty()) {
            val lastContent = contents.last().jsonObject
            val parts = lastContent["parts"]?.jsonArray
            if (parts != null && parts.isNotEmpty()) {
                val lastPart = parts.last().jsonObject
                // 检查是否为 functionResponse
                val funcResponse = lastPart["functionResponse"]?.jsonObject
                if (funcResponse != null) {
                    val name = funcResponse["name"]?.jsonPrimitive?.content ?: "未知工具"
                    return "回传工具执行结果: $name"
                }
                // 检查是否包含用户文本
                val text = lastPart["text"]?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) {
                    return "用户指令: ${cleanSummaryText(text)}"
                }
            }
        }
        // 2. 检查 prompt / message 顶层
        val directText = root["prompt"]?.jsonPrimitive?.content
            ?: root["message"]?.jsonPrimitive?.content
        if (!directText.isNullOrBlank()) {
            return "用户指令: ${cleanSummaryText(directText)}"
        }
        return "发送对话请求"
    }

    private fun extractResponseSummary(root: JsonObject): String {
        // 1. 检查 candidates 数组
        val candidates = root["candidates"]?.jsonArray
        if (candidates != null && candidates.isNotEmpty()) {
            val firstCandidate = candidates.first().jsonObject
            val content = firstCandidate["content"]?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            if (parts != null && parts.isNotEmpty()) {
                val functionCalls = mutableListOf<String>()
                var candidateText = ""
                for (part in parts) {
                    val pObj = part.jsonObject
                    val fCall = pObj["functionCall"]?.jsonObject
                    if (fCall != null) {
                        val name = fCall["name"]?.jsonPrimitive?.content ?: "未知"
                        functionCalls.add(name)
                    }
                    val text = pObj["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        candidateText += text
                    }
                }
                if (functionCalls.isNotEmpty()) {
                    return "模型请求调用工具: ${functionCalls.joinToString(", ")}"
                }
                if (candidateText.isNotBlank()) {
                    return "模型回复: ${cleanSummaryText(candidateText)}"
                }
            }
        }
        // 2. 检查顶层 text / message
        val topText = root["text"]?.jsonPrimitive?.content
            ?: root["response"]?.jsonPrimitive?.content
        if (!topText.isNullOrBlank()) {
            return "模型回复: ${cleanSummaryText(topText)}"
        }
        return "收到模型响应"
    }

    private fun fallbackRegexSummary(direction: String, body: String): String {
        if (direction.contains("REQ", ignoreCase = true)) {
            val funcRespMatch = Regex(""""functionResponse"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"""").find(body)
            if (funcRespMatch != null) {
                return "回传工具执行结果: ${funcRespMatch.groupValues[1]}"
            }
            val textMatch = Regex(""""text"\s*:\s*"([^"]+)"""").find(body)
            if (textMatch != null) {
                return "用户指令: ${cleanSummaryText(textMatch.groupValues[1])}"
            }
            return "发送对话请求"
        } else {
            val funcCallMatch = Regex(""""functionCall"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"""").find(body)
            if (funcCallMatch != null) {
                return "模型请求调用工具: ${funcCallMatch.groupValues[1]}"
            }
            val textMatch = Regex(""""text"\s*:\s*"([^"]+)"""").find(body)
            if (textMatch != null) {
                return "模型回复: ${cleanSummaryText(textMatch.groupValues[1])}"
            }
            return "收到模型响应"
        }
    }

    private fun cleanSummaryText(raw: String): String {
        val singleLine = raw.replace("\r", " ").replace("\n", " ").trim()
        return if (singleLine.length > 50) singleLine.take(50) + "..." else singleLine
    }
}

package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 投影展示内容模型
 *
 * @param text 提纯后的核心回答文本 (人类可读排版/内容)
 * @param thoughtText 提取出的思维链思考过程文本 (若存在)
 * @param thoughtSignature 官方防篡改思考加密签名 (若存在)
 */
data class ProjectedChatContent(
    val text: String,
    val thoughtText: String? = null,
    val thoughtSignature: String? = null
)

/**
 * 会话报文视图投影提取器 (单例)
 *
 * 核心架构思想：
 * 遵循“原始报文作为唯一事实源，渲染时只按需投影提取指定内容展示”的设计。
 * 数据存储层无损保留完整的原始回复 JSON；本提取器在 UI 渲染时，动态从原始 JSON 的标准路径
 * (`steps -> model_output -> content -> text` 与 `steps -> thought`) 中提取指定内容赋值给 UI 组件，
 * 绝不向用户倾倒未解析的纯 JSON，同时具备轻量级缓存防止高频重复解析。
 */
object ChatPayloadViewProjector {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // 轻量级简易缓存，避免同一条消息高频重组时重复 parse JSON
    private val cache = mutableMapOf<String, ProjectedChatContent>()
    private const val MAX_CACHE_SIZE = 128

    /**
     * 将原始数据载荷动态投影为用户可读的展示内容。
     */
    fun projectDisplayContent(rawPayload: String): ProjectedChatContent {
        if (rawPayload.isBlank()) {
            return ProjectedChatContent(text = "")
        }

        val trimmed = rawPayload.trim()

        // 快速判断是否可能为 JSON 对象
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return ProjectedChatContent(text = rawPayload)
        }

        val cached = cache[trimmed]
        if (cached != null) return cached

        val projected = try {
            val rootObj = jsonParser.parseToJsonElement(trimmed).jsonObject

            var extractedText = ""
            var extractedThought: String? = null
            var extractedSignature: String? = null

            // 0. 优先拦截 Google 官方错误对象 (error: { message, code, status })
            val errorObj = rootObj["error"]?.jsonObject
            if (errorObj != null) {
                val errMsg = errorObj["message"]?.jsonPrimitive?.contentOrNull
                    ?: rootObj["message"]?.jsonPrimitive?.contentOrNull
                    ?: "未知服务端错误"
                val errCode = errorObj["code"]?.jsonPrimitive?.contentOrNull
                    ?: errorObj["status"]?.jsonPrimitive?.contentOrNull
                val formattedError = if (!errCode.isNullOrBlank()) "[$errCode] $errMsg" else errMsg
                return ProjectedChatContent(text = formattedError)
            }

            // 1. 优先解析 Google Interactions API 官方真实标准：steps 数组
            val steps = rootObj["steps"]?.jsonArray
            if (steps != null) {
                for (step in steps) {
                    val stepObj = step.jsonObject
                    val stepType = stepObj["type"]?.jsonPrimitive?.contentOrNull

                    if (stepType == "model_output") {
                        val contentArr = stepObj["content"]?.jsonArray
                        val stepText = contentArr?.mapNotNull { c ->
                            c.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                        }?.joinToString("\n") ?: ""

                        if (stepText.isNotBlank()) {
                            extractedText = if (extractedText.isBlank()) stepText else "$extractedText\n$stepText"
                        }
                    } else if (stepType == "thought") {
                        val sig = stepObj["signature"]?.jsonPrimitive?.contentOrNull
                        if (!sig.isNullOrBlank()) extractedSignature = sig

                        val thoughtStr = stepObj["thought"]?.jsonPrimitive?.contentOrNull
                            ?: stepObj["text"]?.jsonPrimitive?.contentOrNull
                        if (!thoughtStr.isNullOrBlank()) {
                            extractedThought = (extractedThought ?: "") + thoughtStr
                        }
                    }
                }
            }

            // 2. 兼容兜底：outputs 数组 (旧格式或第三方网关转换)
            if (extractedText.isBlank()) {
                val outputs = rootObj["outputs"]?.jsonArray
                extractedText = outputs?.mapNotNull { item ->
                    item.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                }?.joinToString("\n") ?: ""
            }

            // 3. 兼容兜底：candidates 数组 (官方标准 generateContent 格式)
            if (extractedText.isBlank()) {
                val candidates = rootObj["candidates"]?.jsonArray
                extractedText = candidates?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("\n") ?: ""
            }

            // 4. 若上述特定字段均未命中，但存在顶层 text 字段
            if (extractedText.isBlank()) {
                val directText = rootObj["text"]?.jsonPrimitive?.contentOrNull
                if (!directText.isNullOrBlank()) {
                    extractedText = directText
                }
            }

            // 若依然提取不出特定字段，说明该 JSON 可能是其它格式或错误信息，兜底使用原文本
            if (extractedText.isBlank()) {
                ProjectedChatContent(text = rawPayload)
            } else {
                ProjectedChatContent(
                    text = extractedText,
                    thoughtText = extractedThought,
                    thoughtSignature = extractedSignature
                )
            }
        } catch (_: Exception) {
            // 解析失败（非合法 JSON），直接原样展示
            ProjectedChatContent(text = rawPayload)
        }

        if (cache.size >= MAX_CACHE_SIZE) {
            val eldest = cache.keys.firstOrNull()
            if (eldest != null) cache.remove(eldest)
        }
        cache[trimmed] = projected

        return projected
    }
}

package org.gemini.ui.forge.service.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor

/**
 * 官方标准 Gemini 思考配置模型
 */
@Serializable
data class GeminiThinkingConfig(
    val thinkingBudget: Int? = null,
    val thinkingLevel: String? = null // LOW, MEDIUM, HIGH
)

/**
 * 官方标准 Gemini 生成配置模型 (GenerationConfig)
 * 涵盖 generateContent 与 Interactions API 支持的完整字段
 */
@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val candidateCount: Int? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val responseMimeType: String? = null, // 如 "application/json", "text/plain"
    val responseSchema: JsonObject? = null, // JSON Schema 约束
    val presencePenalty: Double? = null,
    val frequencyPenalty: Double? = null,
    val responseModalities: List<String>? = null, // 如 listOf("TEXT"), listOf("TEXT", "IMAGE")
    val speechConfig: JsonObject? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
    val imageAspectRatio: String? = null, // Interactions 生图规格
    val imageSize: String? = null         // Interactions 图像尺寸
)

/**
 * 官方标准 Gemini 工具与函数调用配置
 */
@Serializable
data class GeminiToolConfig(
    val functionDeclarations: List<JsonObject>? = null,
    val googleSearch: JsonObject? = null,
    val codeExecution: JsonObject? = null,
    val mode: String? = null, // AUTO, ANY, NONE
    val allowedFunctionNames: List<String>? = null
)

/**
 * 官方标准 Gemini 安全性拦截配置
 */
@Serializable
data class GeminiSafetySetting(
    val category: String,  // 如 "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH" 等
    val threshold: String  // 如 "BLOCK_NONE", "BLOCK_ONLY_HIGH", "BLOCK_MEDIUM_AND_ABOVE"
)

/**
 * 统一对话附件载荷
 */
data class GeminiChatAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray? = null,
    val localUri: String? = null,
    val fileUri: String? = null, // Google File API 返回的云端资源 URI
    val isCloudUploaded: Boolean = false
)

/**
 * 单次函数调用记录（model 轮发起）
 */
data class GeminiFunctionCallRecord(
    val name: String,
    val argsJson: String,        // arguments/args 原始 JSON 字符串
    val callId: String? = null   // Interactions function_call step.id
)

/**
 * 单次函数执行结果（回传轮）
 */
data class GeminiFunctionResponse(
    val name: String,
    val callId: String? = null,
    val responseJson: String     // 官方要求 JSON 序列化结果
)

/**
 * 单轮历史会话项 (用于上下文构建)
 */
data class GeminiChatHistoryItem(
    val role: String, // "user" 或 "model"
    val text: String,
    val attachments: List<GeminiChatAttachment> = emptyList(),
    val functionCalls: List<GeminiFunctionCallRecord> = emptyList(), // model 轮发起的函数调用
    val functionResults: List<GeminiFunctionResponse> = emptyList()  // 回传轮返回的函数执行结果
)

/**
 * 统一的 Gemini 文本/多模态对话请求契约
 */
data class GeminiChatRequest(
    val model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH,
    val userPrompt: String = "",
    val apiKey: String = "",
    val baseUrl: String? = null,
    val systemInstruction: String? = null,
    val history: List<GeminiChatHistoryItem> = emptyList(),
    val attachments: List<GeminiChatAttachment> = emptyList(),
    val preferCloudUpload: Boolean = true,
    val apiFlavor: ApiFlavor = ApiFlavor.INTERACTIONS,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    val tools: List<GeminiToolConfig> = emptyList(),
    val safetySettings: List<GeminiSafetySetting> = emptyList(),
    val previousInteractionId: String? = null,
    val functionResults: List<GeminiFunctionResponse> = emptyList(), // 当前轮次工具回传结果 (反哺模型)
    val scopeId: String = "default",
    val sessionId: String? = null,
    val prompt: String = "" // 兼容 prompt 别名字段
) {
    val effectivePrompt: String get() = userPrompt.ifBlank { prompt }
}

/**
 * 统一的 Gemini 对话响应结果契约
 */
data class GeminiChatResult(
    val text: String,
    val interactionId: String? = null,
    val thoughtText: String? = null,
    val thoughtSignature: String? = null,
    val rawResponseJson: String = "",
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val modelName: String? = null,
    val durationMs: Long? = null,
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val generatedImageData: String? = null,
    val generatedImageMime: String? = null,
    val functionCalls: List<GeminiFunctionCallRecord> = emptyList()
)

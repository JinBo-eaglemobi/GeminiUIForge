package org.gemini.ui.forge.service.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.gemini.ui.forge.model.GeminiModel

/**
 * 统一的 AI 生图请求参数契约 (上层业务 100% 公用)
 */
data class UnifiedAiRequest(
    /** 目标模型 */
    val model: GeminiModel,
    /** 最终组装的 Prompt 提示词 */
    val prompt: String,
    /** 认证密钥 */
    val apiKey: String,
    /** 目标像素宽 */
    val targetWidth: Float? = null,
    /** 目标像素高 */
    val targetHeight: Float? = null,
    /** 比例 (如 "1:1", "16:9") */
    val aspectRatio: String = "1:1",
    /** 尺寸规格 (如 "1k", "2k") */
    val imageSize: String = "1k",
    /** 参考图的 URI 或物理路径 (可选) */
    val referenceImageUri: String? = null,
    /** 链式多轮引用的上一轮交互 ID (Interactions API 专享，旧版将自动回退忽略) */
    val previousInteractionId: String? = null,
    /** 思考模式配置 (可选) */
    val thinkingConfigJson: JsonObject? = null,
    /** 思考级别 (如 "low", "medium", "high") */
    val thinkingLevel: String? = null
)

/**
 * 统一的 AI 生图响应契约 (上层业务 100% 公用)
 */
data class UnifiedAiResult(
    /** 生成的图片 Data URI 列表 (格式如 data:image/png;base64,...) */
    val imageUris: List<String> = emptyList(),
    /** 文本输出 / 伴生解释 */
    val outputText: String = "",
    /** 本轮会话交互 ID (供下一轮作为 previousInteractionId 继承) */
    val interactionId: String? = null,
    /** 思维链思考内容 */
    val thoughtText: String? = null,
    /** 思考签名指纹 */
    val thoughtSignature: String? = null,
    /** 原始完整 JSON 报文 */
    val rawResponseJson: String = ""
)

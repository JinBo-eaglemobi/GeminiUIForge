package org.gemini.ui.forge.model.api.gemini.interactions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Google Interactions API 输入项
 */
@Serializable
data class InteractionInputItem(
    /** 输入类型: "text" 或 "image" */
    val type: String,
    /** 当 type="text" 时的文本内容 */
    val text: String? = null,
    /** 当 type="image" 时的 Base64 数据 */
    val data: String? = null,
    /** 当 type="image" 时的 MIME 类型，如 "image/png" */
    @SerialName("mime_type")
    val mimeType: String? = null
)

/**
 * Google Interactions API 生图配置
 */
@Serializable
data class InteractionImageGenerationConfig(
    /** 比例，如 "1:1", "16:9", "4:3" */
    @SerialName("aspect_ratio")
    val aspectRatio: String? = null,
    /** 尺寸，如 "1K", "2K" */
    @SerialName("image_size")
    val imageSize: String? = null
)

/**
 * Google Interactions API 生成控制配置
 */
@Serializable
data class InteractionGenerationConfig(
    /** 思考级别: "low", "medium", "high" */
    @SerialName("thinking_level")
    val thinkingLevel: String? = null,
    /** 图像生成专属参数 */
    @SerialName("image_generation_config")
    val imageGenerationConfig: InteractionImageGenerationConfig? = null
)

/**
 * Google Interactions API 请求载荷
 */
@Serializable
data class InteractionRequest(
    /** 目标模型名称，例如 "gemini-3.1-flash-image" */
    val model: String,
    /** 多模态输入流 (文本与图片混合) */
    val input: List<InteractionInputItem>,
    /** 链式多轮引用的上一轮交互 ID (免传历史图片) */
    @SerialName("previous_interaction_id")
    val previousInteractionId: String? = null,
    /** 生成控制配置 */
    @SerialName("generation_config")
    val generationConfig: InteractionGenerationConfig? = null,
    /** 期望的输出模态列表: ["text", "image"] */
    @SerialName("response_modalities")
    val responseModalities: List<String> = listOf("text", "image")
)

/**
 * 输出图像数据
 */
@Serializable
data class InteractionOutputImage(
    /** Base64 编码的图像二进制数据 */
    val data: String,
    /** 图像 MIME 类型 */
    @SerialName("mime_type")
    val mimeType: String? = "image/png"
)

/**
 * 交互执行步骤 (包含思维链等)
 */
@Serializable
data class InteractionStep(
    /** 步骤类型: "thought", "model_output" 等 */
    val type: String,
    /** 思维链总结摘要 */
    val summary: String? = null,
    /** 思考签名 */
    @SerialName("thought_signature")
    val thoughtSignature: String? = null,
    /** 原始步骤内容 */
    val content: JsonElement? = null
)

/**
 * Google Interactions API 响应载荷
 */
@Serializable
data class InteractionResponse(
    /** 本轮交互唯一标识符，供后续轮次传入 previous_interaction_id */
    val id: String? = null,
    /** 模型输出的文本正文 */
    @SerialName("output_text")
    val outputText: String? = null,
    /** 模型输出的图像 */
    @SerialName("output_image")
    val outputImage: InteractionOutputImage? = null,
    /** 步骤列表 */
    val steps: List<InteractionStep> = emptyList(),
    /** 错误信息 */
    val error: JsonElement? = null
)

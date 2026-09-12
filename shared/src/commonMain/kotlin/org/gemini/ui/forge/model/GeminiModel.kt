package org.gemini.ui.forge.model

/**
 * 自动生成的 Gemini 模型枚举类。
 * 由 [GeminiModelsGeneratorTest] 从服务器 `models.list` 接口拉取并生成，请勿手工编辑。
 *
 * @param modelName 模型唯一标识（API 调用时使用的名称，如 gemini-2.5-flash）
 * @param displayName 服务端返回的模型显示名称（英文）
 * @param description 服务端返回的模型功能描述（英文原文）
 * @param supportedMethods 该模型支持的生成方法集合（逗号分隔，如 generateContent, countTokens）
 * @param version 模型版本号（服务端元数据，如 2.5-flash-002）
 * @param baseModelId 基座模型标识（微调/衍生模型的来源基座；原生模型为空）
 * @param inputTokenLimit 单次请求允许的最大输入 Token 数（0 表示服务端未披露）
 * @param outputTokenLimit 单次响应允许生成的最大输出 Token 数（0 表示服务端未披露）
 * @param temperature 服务端默认采样温度（null 表示服务端未披露）
 * @param maxTemperature 服务端允许的最高采样温度（null 表示服务端未披露）
 * @param topP 服务端默认核采样概率阈值（null 表示服务端未披露）
 * @param topK 服务端默认 Top-K 采样候选数（null 表示服务端未披露）
 * @param supportsThinking 是否具备思考/推理能力（服务端官方 thinking 字段；字段缺失时启发式兜底）
 */
enum class GeminiModel(
    val modelName: String,
    val displayName: String,
    val description: String,
    val supportedMethods: String,
    val version: String = "",
    val baseModelId: String = "",
    val inputTokenLimit: Long = 0L,
    val outputTokenLimit: Long = 0L,
    val temperature: Float? = null,
    val maxTemperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val supportsThinking: Boolean = false
) {
    /** 版本: 001 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_FLASH("gemini-2.5-flash", "Gemini 2.5 Flash", "Stable version of Gemini 2.5 Flash, our mid-size multimodal model that supports up to 1 million tokens, released in June of 2025.", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "001", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 2.5 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_PRO("gemini-2.5-pro", "Gemini 2.5 Pro", "Stable release (June 17th, 2025) of Gemini 2.5 Pro", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "2.5", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: gemini-2.5-flash-exp-tts-2025-05-19 · Token: 输入 8192 / 输出 16384 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    GEMINI_2_5_FLASH_PREVIEW_TTS("gemini-2.5-flash-preview-tts", "Gemini 2.5 Flash Preview TTS", "Gemini 2.5 Flash Preview TTS", "countTokens, generateContent", version = "gemini-2.5-flash-exp-tts-2025-05-19", baseModelId = "", inputTokenLimit = 8192, outputTokenLimit = 16384, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: gemini-2.5-pro-preview-tts-2025-05-19 · Token: 输入 8192 / 输出 16384 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    GEMINI_2_5_PRO_PREVIEW_TTS("gemini-2.5-pro-preview-tts", "Gemini 2.5 Pro Preview TTS", "Gemini 2.5 Pro Preview TTS", "countTokens, generateContent, batchGenerateContent", version = "gemini-2.5-pro-preview-tts-2025-05-19", baseModelId = "", inputTokenLimit = 8192, outputTokenLimit = 16384, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: 001 · Token: 输入 262144 / 输出 32768 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMMA_4_26B_A4B_IT("gemma-4-26b-a4b-it", "Gemma 4 26B A4B IT", "Gemma 4 26B A4B IT", "generateContent, countTokens", version = "001", baseModelId = "", inputTokenLimit = 262144, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 001 · Token: 输入 262144 / 输出 32768 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMMA_4_31B_IT("gemma-4-31b-it", "Gemma 4 31B IT", "Gemma 4 31B IT", "generateContent, countTokens", version = "001", baseModelId = "", inputTokenLimit = 262144, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: Gemini Flash Latest · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_FLASH_LATEST("gemini-flash-latest", "Gemini Flash Latest", "Latest release of Gemini Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "Gemini Flash Latest", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: Gemini Flash-Lite Latest · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_FLASH_LITE_LATEST("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", "Latest release of Gemini Flash-Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "Gemini Flash-Lite Latest", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: Gemini Pro Latest · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_PRO_LATEST("gemini-pro-latest", "Gemini Pro Latest", "Latest release of Gemini Pro", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "Gemini Pro Latest", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 001 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Stable version of Gemini 2.5 Flash-Lite, released in July of 2025", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "001", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 2.0 · Token: 输入 32768 / 输出 32768 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    GEMINI_2_5_FLASH_IMAGE("gemini-2.5-flash-image", "Nano Banana", "Gemini 2.5 Flash Preview Image", "generateContent, countTokens, batchGenerateContent", version = "2.0", baseModelId = "", inputTokenLimit = 32768, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: 3-flash-preview-12-2025 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_FLASH_PREVIEW("gemini-3-flash-preview", "Gemini 3 Flash Preview", "Gemini 3 Flash Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3-flash-preview-12-2025", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.1-pro-preview-01-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_PRO_PREVIEW("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Gemini 3.1 Pro Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.1-pro-preview-01-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.1-pro-preview-01-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_PRO_PREVIEW_CUSTOMTOOLS("gemini-3.1-pro-preview-customtools", "Gemini 3.1 Pro Preview Custom Tools", "Gemini 3.1 Pro Preview optimized for custom tool usage", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.1-pro-preview-01-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.1-flash-lite-preview-03-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_LITE_PREVIEW("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview", "Gemini 3.1 Flash Lite Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.1-flash-lite-preview-03-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.1-flash-lite-05-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", "Gemini 3.1 Flash Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.1-flash-lite-05-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 131072 / 输出 32768 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_PRO_IMAGE_PREVIEW("gemini-3-pro-image-preview", "Nano Banana Pro", "Gemini 3 Pro Image Preview", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 131072 / 输出 32768 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_PRO_IMAGE("gemini-3-pro-image", "Nano Banana Pro", "Gemini 3 Pro Image", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 131072 / 输出 32768 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    NANO_BANANA_PRO_PREVIEW("nano-banana-pro-preview", "Nano Banana Pro", "Gemini 3 Pro Image Preview", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 65536 / 输出 65536 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_IMAGE_PREVIEW("gemini-3.1-flash-image-preview", "Nano Banana 2", "Gemini 3.1 Flash Image Preview.", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 65536, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 65536 / 输出 65536 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_IMAGE("gemini-3.1-flash-image", "Nano Banana 2", "Gemini 3.1 Flash Image.", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 65536, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 65536 / 输出 65536 · 温度 1.0 · 最高温度 1.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_LITE_IMAGE("gemini-3.1-flash-lite-image", "Nano Banana 2 Lite", "Gemini 3.1 Flash Lite Image.", "generateContent, countTokens, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 65536, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 1.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.5-flash-05-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_5_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash", "Gemini 3.5 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.5-flash-05-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.5-flash-lite-07-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Gemini 3.5 Flash Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.5-flash-lite-07-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 001 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_OMNI_FLASH_PREVIEW("gemini-omni-flash-preview", "Gemini Omni Flash Preview", "Gemini Omni Flash Preview", "generateContent, countTokens", version = "001", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 001 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_OMNI_1_1_FLASH("gemini-omni-1.1-flash", "Gemini Omni 1.1 Flash", "Gemini Omni 1.1 Flash ", "generateContent, countTokens", version = "001", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.5-transcribe-08-2026 · Token: 输入 98304 / 输出 32768 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_5_TRANSCRIBE("gemini-3.5-transcribe", "Gemini 3.5 Transcribe", "Gemini 3.5 Transcribe", "generateContent, countTokens", version = "3.5-transcribe-08-2026", baseModelId = "", inputTokenLimit = 98304, outputTokenLimit = 32768, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.6-flash-07-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_6_FLASH("gemini-3.6-flash", "Gemini 3.6 Flash", "Gemini 3.6 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.6-flash-07-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.7-flash-08-2026 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_7_FLASH("gemini-3.7-flash", "Gemini 3.7 Flash", "Gemini 3.7 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.7-flash-08-2026", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 3.0 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Gemini 3.8 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "3.0", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: lyria-3-clip-preview · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    LYRIA_3_CLIP_PREVIEW("lyria-3-clip-preview", "Lyria 3 Clip Preview", "Lyria 3 30s model Preview", "generateContent, countTokens", version = "lyria-3-clip-preview", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: lyria-3-pro-preview · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    LYRIA_3_PRO_PREVIEW("lyria-3-pro-preview", "Lyria 3 Pro Preview", "Lyria 3 Pro Preview", "generateContent, countTokens", version = "lyria-3-pro-preview", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: 3.5 · Token: 输入 1048576 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    LYRIA_3_5("lyria-3.5", "Lyria 3.5", "Music Generation model", "generateContent, countTokens", version = "3.5", baseModelId = "", inputTokenLimit = 1048576, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: 3.1-flash-tts-preview · Token: 输入 8192 / 输出 16384 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_3_1_FLASH_TTS_PREVIEW("gemini-3.1-flash-tts-preview", "Gemini 3.1 Flash TTS Preview", "Gemini 3.1 Flash TTS Preview", "generateContent, countTokens, batchGenerateContent", version = "3.1-flash-tts-preview", baseModelId = "", inputTokenLimit = 8192, outputTokenLimit = 16384, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 2-preview · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_ROBOTICS_ER_2_PREVIEW("gemini-robotics-er-2-preview", "Gemini Robotics-ER 2 Preview", "Gemini Robotics-ER 2 Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent", version = "2-preview", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: Gemini 2.5 Computer Use Preview 10-2025 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_COMPUTER_USE_PREVIEW_10_2025("gemini-2.5-computer-use-preview-10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "generateContent, countTokens", version = "Gemini 2.5 Computer Use Preview 10-2025", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 0.1 · Token: 输入 131072 / 输出 65536 · 思考: 不支持 */
    ANTIGRAVITY_PREVIEW_05_2026("antigravity-preview-05-2026", "Antigravity Agent Preview", "Preview release of Antigravity Agent (05-2026)", "generateContent, countTokens", version = "0.1", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: deepthink-exp-05-20 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    DEEP_RESEARCH_MAX_PREVIEW_04_2026("deep-research-max-preview-04-2026", "Deep Research Max Preview (Apr-21-2026)", "Preview release (April 21st, 2026) of Deep Research Max", "generateContent, countTokens", version = "deepthink-exp-05-20", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: deepthink-exp-05-20 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    DEEP_RESEARCH_PREVIEW_04_2026("deep-research-preview-04-2026", "Deep Research Preview (Apr-21-2026)", "Preview release (April 21th, 2026) of Deep Research", "generateContent, countTokens", version = "deepthink-exp-05-20", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: deepthink-exp-05-20 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    DEEP_RESEARCH_PRO_PREVIEW_12_2025("deep-research-pro-preview-12-2025", "Deep Research Pro Preview (Dec-12-2025)", "Preview release (December 12th, 2025) of Deep Research Pro", "generateContent, countTokens", version = "deepthink-exp-05-20", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: 001 · Token: 输入 2048 / 输出 1 · 思考: 不支持 */
    GEMINI_EMBEDDING_001("gemini-embedding-001", "Gemini Embedding 001", "Obtain a distributed representation of a text.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent", version = "001", baseModelId = "", inputTokenLimit = 2048, outputTokenLimit = 1, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 2 · Token: 输入 8192 / 输出 1 · 思考: 不支持 */
    GEMINI_EMBEDDING_2_PREVIEW("gemini-embedding-2-preview", "Gemini Embedding 2 Preview", "Obtain a distributed representation of multimodal content.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent", version = "2", baseModelId = "", inputTokenLimit = 8192, outputTokenLimit = 1, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 2 · Token: 输入 8192 / 输出 1 · 思考: 不支持 */
    GEMINI_EMBEDDING_2("gemini-embedding-2", "Gemini Embedding 2", "Obtain a distributed representation of multimodal content.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent", version = "2", baseModelId = "", inputTokenLimit = 8192, outputTokenLimit = 1, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 001 · Token: 输入 7168 / 输出 1024 · 温度 0.2 · TopP 1.0 · TopK 40 · 思考: 不支持 */
    AQA("aqa", "Model that performs Attributed Question Answering.", "Model trained to return answers to questions that are grounded in provided sources, along with estimating answerable probability.", "generateAnswer", version = "001", baseModelId = "", inputTokenLimit = 7168, outputTokenLimit = 1024, temperature = 0.2f, maxTemperature = null, topP = 1.0f, topK = 40, supportsThinking = false),

    /** 版本: 3.1 · Token: 输入 480 / 输出 8192 · 思考: 不支持 */
    VEO_3_1_GENERATE_PREVIEW("veo-3.1-generate-preview", "Veo 3.1", "Veo 3.1", "predictLongRunning", version = "3.1", baseModelId = "", inputTokenLimit = 480, outputTokenLimit = 8192, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 3.1 · Token: 输入 480 / 输出 8192 · 思考: 不支持 */
    VEO_3_1_FAST_GENERATE_PREVIEW("veo-3.1-fast-generate-preview", "Veo 3.1 fast", "Veo 3.1 fast", "predictLongRunning", version = "3.1", baseModelId = "", inputTokenLimit = 480, outputTokenLimit = 8192, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 3.1 · Token: 输入 480 / 输出 8192 · 思考: 不支持 */
    VEO_3_1_LITE_GENERATE_PREVIEW("veo-3.1-lite-generate-preview", "Veo 3.1 lite", "Veo 3.1 lite", "predictLongRunning", version = "3.1", baseModelId = "", inputTokenLimit = 480, outputTokenLimit = 8192, temperature = null, maxTemperature = null, topP = null, topK = null, supportsThinking = false),

    /** 版本: 3.5-transcribe-live-08-2026 · Token: 输入 131072 / 输出 65536 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 不支持 */
    GEMINI_3_5_TRANSCRIBE_LIVE("gemini-3.5-transcribe-live", "Gemini 3.5 Transcribe Live", "Gemini 3.5 Transcribe Live", "bidiGenerateContent", version = "3.5-transcribe-live-08-2026", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 65536, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = false),

    /** 版本: Gemini 2.5 Flash Native Audio Latest · Token: 输入 131072 / 输出 8192 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_FLASH_NATIVE_AUDIO_LATEST("gemini-2.5-flash-native-audio-latest", "Gemini 2.5 Flash Native Audio Latest", "Latest release of Gemini 2.5 Flash Native Audio", "countTokens, bidiGenerateContent", version = "Gemini 2.5 Flash Native Audio Latest", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 8192, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true),

    /** 版本: gemini-2.5-flash-preview-native-audio-dialog-2025-05-19 · Token: 输入 131072 / 输出 8192 · 温度 1.0 · 最高温度 2.0 · TopP 0.95 · TopK 64 · 思考: 支持 */
    GEMINI_2_5_FLASH_NATIVE_AUDIO_PREVIEW_09_2025("gemini-2.5-flash-native-audio-preview-09-2025", "Gemini 2.5 Flash Native Audio Preview 09-2025", "Gemini 2.5 Flash Native Audio Preview 09-2025", "countTokens, bidiGenerateContent", version = "gemini-2.5-flash-preview-native-audio-dialog-2025-05-19", baseModelId = "", inputTokenLimit = 131072, outputTokenLimit = 8192, temperature = 1.0f, maxTemperature = 2.0f, topP = 0.95f, topK = 64, supportsThinking = true);
}

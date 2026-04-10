package org.gemini.ui.forge.model.api
/**
 * 自动生成的 Gemini 模型枚举类
 * 包含了当前 API Key 支持的所有可用模型。
 *
 * @property modelName 实际传给 API 请求的模型标识符（如 "gemini-3-pro-preview"）
 * @property displayName 用于 UI 展示的人类可读名称
 * @property description 模型的官方描述及功能说明
 * @property supportedMethods 该模型支持调用的 API 方法列表
 */
enum class GeminiModel(
    val modelName: String,
    val displayName: String,
    val description: String,
    val supportedMethods: String
) {
    /**
     * 显示名称: Gemini 2.5 Flash
     * 功能描述: Stable version of Gemini 2.5 Flash, our mid-size multimodal model that supports up to 1 million tokens, released in June of 2025.
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_5_FLASH("gemini-2.5-flash", "Gemini 2.5 Flash", "Stable version of Gemini 2.5 Flash, our mid-size multimodal model that supports up to 1 million tokens, released in June of 2025.", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Pro
     * 功能描述: Stable release (June 17th, 2025) of Gemini 2.5 Pro
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_5_PRO("gemini-2.5-pro", "Gemini 2.5 Pro", "Stable release (June 17th, 2025) of Gemini 2.5 Pro", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.0 Flash
     * 功能描述: Gemini 2.0 Flash
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_0_FLASH("gemini-2.0-flash", "Gemini 2.0 Flash", "Gemini 2.0 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.0 Flash 001
     * 功能描述: Stable version of Gemini 2.0 Flash, our fast and versatile multimodal model for scaling across diverse tasks, released in January of 2025.
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_0_FLASH_001("gemini-2.0-flash-001", "Gemini 2.0 Flash 001", "Stable version of Gemini 2.0 Flash, our fast and versatile multimodal model for scaling across diverse tasks, released in January of 2025.", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.0 Flash-Lite 001
     * 功能描述: Stable version of Gemini 2.0 Flash-Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_0_FLASH_LITE_001("gemini-2.0-flash-lite-001", "Gemini 2.0 Flash-Lite 001", "Stable version of Gemini 2.0 Flash-Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.0 Flash-Lite
     * 功能描述: Gemini 2.0 Flash-Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_0_FLASH_LITE("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "Gemini 2.0 Flash-Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Flash Preview TTS
     * 功能描述: Gemini 2.5 Flash Preview TTS
     * 支持的方法: countTokens, generateContent
     */
    GEMINI_2_5_FLASH_PREVIEW_TTS("gemini-2.5-flash-preview-tts", "Gemini 2.5 Flash Preview TTS", "Gemini 2.5 Flash Preview TTS", "countTokens, generateContent"),

    /**
     * 显示名称: Gemini 2.5 Pro Preview TTS
     * 功能描述: Gemini 2.5 Pro Preview TTS
     * 支持的方法: countTokens, generateContent, batchGenerateContent
     */
    GEMINI_2_5_PRO_PREVIEW_TTS("gemini-2.5-pro-preview-tts", "Gemini 2.5 Pro Preview TTS", "Gemini 2.5 Pro Preview TTS", "countTokens, generateContent, batchGenerateContent"),

    /**
     * 显示名称: Gemma 3 1B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3_1B_IT("gemma-3-1b-it", "Gemma 3 1B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 3 4B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3_4B_IT("gemma-3-4b-it", "Gemma 3 4B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 3 12B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3_12B_IT("gemma-3-12b-it", "Gemma 3 12B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 3 27B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3_27B_IT("gemma-3-27b-it", "Gemma 3 27B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 3n E4B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3N_E4B_IT("gemma-3n-e4b-it", "Gemma 3n E4B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 3n E2B
     * 功能描述: 
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_3N_E2B_IT("gemma-3n-e2b-it", "Gemma 3n E2B", "", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini Flash Latest
     * 功能描述: Latest release of Gemini Flash
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_FLASH_LATEST("gemini-flash-latest", "Gemini Flash Latest", "Latest release of Gemini Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini Flash-Lite Latest
     * 功能描述: Latest release of Gemini Flash-Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_FLASH_LITE_LATEST("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", "Latest release of Gemini Flash-Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini Pro Latest
     * 功能描述: Latest release of Gemini Pro
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_PRO_LATEST("gemini-pro-latest", "Gemini Pro Latest", "Latest release of Gemini Pro", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Flash-Lite
     * 功能描述: Stable version of Gemini 2.5 Flash-Lite, released in July of 2025
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Stable version of Gemini 2.5 Flash-Lite, released in July of 2025", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana
     * 功能描述: Gemini 2.5 Flash Preview Image
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_2_5_FLASH_IMAGE("gemini-2.5-flash-image", "Nano Banana", "Gemini 2.5 Flash Preview Image", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Flash-Lite Preview Sep 2025
     * 功能描述: Preview release (Septempber 25th, 2025) of Gemini 2.5 Flash-Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_2_5_FLASH_LITE_PREVIEW_09_2025("gemini-2.5-flash-lite-preview-09-2025", "Gemini 2.5 Flash-Lite Preview Sep 2025", "Preview release (Septempber 25th, 2025) of Gemini 2.5 Flash-Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3 Pro Preview
     * 功能描述: Gemini 3 Pro Preview
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_PRO_PREVIEW("gemini-3-pro-preview", "Gemini 3 Pro Preview", "Gemini 3 Pro Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3 Flash Preview
     * 功能描述: Gemini 3 Flash Preview
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_FLASH_PREVIEW("gemini-3-flash-preview", "Gemini 3 Flash Preview", "Gemini 3 Flash Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.1 Pro Preview
     * 功能描述: Gemini 3.1 Pro Preview
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_1_PRO_PREVIEW("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Gemini 3.1 Pro Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.1 Pro Preview Custom Tools
     * 功能描述: Gemini 3.1 Pro Preview optimized for custom tool usage
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_1_PRO_PREVIEW_CUSTOMTOOLS("gemini-3.1-pro-preview-customtools", "Gemini 3.1 Pro Preview Custom Tools", "Gemini 3.1 Pro Preview optimized for custom tool usage", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.1 Flash Lite Preview
     * 功能描述: Gemini 3.1 Flash Lite Preview
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_1_FLASH_LITE_PREVIEW("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview", "Gemini 3.1 Flash Lite Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana Pro
     * 功能描述: Gemini 3 Pro Image Preview
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_PRO_IMAGE_PREVIEW("gemini-3-pro-image-preview", "Nano Banana Pro", "Gemini 3 Pro Image Preview", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana Pro
     * 功能描述: Gemini 3 Pro Image Preview
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    NANO_BANANA_PRO_PREVIEW("nano-banana-pro-preview", "Nano Banana Pro", "Gemini 3 Pro Image Preview", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana 2
     * 功能描述: Gemini 3.1 Flash Image Preview.
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_1_FLASH_IMAGE_PREVIEW("gemini-3.1-flash-image-preview", "Nano Banana 2", "Gemini 3.1 Flash Image Preview.", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Lyria 3 Clip Preview
     * 功能描述: Lyria 3 30s model Preview
     * 支持的方法: generateContent, countTokens
     */
    LYRIA_3_CLIP_PREVIEW("lyria-3-clip-preview", "Lyria 3 Clip Preview", "Lyria 3 30s model Preview", "generateContent, countTokens"),

    /**
     * 显示名称: Lyria 3 Pro Preview
     * 功能描述: Lyria 3 Pro Preview
     * 支持的方法: generateContent, countTokens
     */
    LYRIA_3_PRO_PREVIEW("lyria-3-pro-preview", "Lyria 3 Pro Preview", "Lyria 3 Pro Preview", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini Robotics-ER 1.5 Preview
     * 功能描述: Gemini Robotics-ER 1.5 Preview
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_ROBOTICS_ER_1_5_PREVIEW("gemini-robotics-er-1.5-preview", "Gemini Robotics-ER 1.5 Preview", "Gemini Robotics-ER 1.5 Preview", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini 2.5 Computer Use Preview 10-2025
     * 功能描述: Gemini 2.5 Computer Use Preview 10-2025
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_2_5_COMPUTER_USE_PREVIEW_10_2025("gemini-2.5-computer-use-preview-10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "generateContent, countTokens"),

    /**
     * 显示名称: Deep Research Pro Preview (Dec-12-2025)
     * 功能描述: Preview release (December 12th, 2025) of Deep Research Pro
     * 支持的方法: generateContent, countTokens
     */
    DEEP_RESEARCH_PRO_PREVIEW_12_2025("deep-research-pro-preview-12-2025", "Deep Research Pro Preview (Dec-12-2025)", "Preview release (December 12th, 2025) of Deep Research Pro", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini Embedding 001
     * 功能描述: Obtain a distributed representation of a text.
     * 支持的方法: embedContent, countTextTokens, countTokens, asyncBatchEmbedContent
     */
    GEMINI_EMBEDDING_001("gemini-embedding-001", "Gemini Embedding 001", "Obtain a distributed representation of a text.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent"),

    /**
     * 显示名称: Gemini Embedding 2 Preview
     * 功能描述: Obtain a distributed representation of multimodal content.
     * 支持的方法: embedContent, countTextTokens, countTokens, asyncBatchEmbedContent
     */
    GEMINI_EMBEDDING_2_PREVIEW("gemini-embedding-2-preview", "Gemini Embedding 2 Preview", "Obtain a distributed representation of multimodal content.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent"),

    /**
     * 显示名称: Model that performs Attributed Question Answering.
     * 功能描述: Model trained to return answers to questions that are grounded in provided sources, along with estimating answerable probability.
     * 支持的方法: generateAnswer
     */
    AQA("aqa", "Model that performs Attributed Question Answering.", "Model trained to return answers to questions that are grounded in provided sources, along with estimating answerable probability.", "generateAnswer"),

    /**
     * 显示名称: Imagen 4
     * 功能描述: Vertex served Imagen 4.0 model
     * 支持的方法: predict
     */
    IMAGEN_4_0_GENERATE_001("imagen-4.0-generate-001", "Imagen 4", "Vertex served Imagen 4.0 model", "predict"),

    /**
     * 显示名称: Imagen 4 Ultra
     * 功能描述: Vertex served Imagen 4.0 ultra model
     * 支持的方法: predict
     */
    IMAGEN_4_0_ULTRA_GENERATE_001("imagen-4.0-ultra-generate-001", "Imagen 4 Ultra", "Vertex served Imagen 4.0 ultra model", "predict"),

    /**
     * 显示名称: Imagen 4 Fast
     * 功能描述: Vertex served Imagen 4.0 Fast model
     * 支持的方法: predict
     */
    IMAGEN_4_0_FAST_GENERATE_001("imagen-4.0-fast-generate-001", "Imagen 4 Fast", "Vertex served Imagen 4.0 Fast model", "predict"),

    /**
     * 显示名称: Veo 2
     * 功能描述: Vertex served Veo 2 model. Access to this model requires billing to be enabled on the associated Google Cloud Platform account. Please visit https://console.cloud.google.com/billing to enable it.
     * 支持的方法: predictLongRunning
     */
    VEO_2_0_GENERATE_001("veo-2.0-generate-001", "Veo 2", "Vertex served Veo 2 model. Access to this model requires billing to be enabled on the associated Google Cloud Platform account. Please visit https://console.cloud.google.com/billing to enable it.", "predictLongRunning"),

    /**
     * 显示名称: Veo 3
     * 功能描述: Veo 3
     * 支持的方法: predictLongRunning
     */
    VEO_3_0_GENERATE_001("veo-3.0-generate-001", "Veo 3", "Veo 3", "predictLongRunning"),

    /**
     * 显示名称: Veo 3 fast
     * 功能描述: Veo 3 fast
     * 支持的方法: predictLongRunning
     */
    VEO_3_0_FAST_GENERATE_001("veo-3.0-fast-generate-001", "Veo 3 fast", "Veo 3 fast", "predictLongRunning"),

    /**
     * 显示名称: Veo 3.1
     * 功能描述: Veo 3.1
     * 支持的方法: predictLongRunning
     */
    VEO_3_1_GENERATE_PREVIEW("veo-3.1-generate-preview", "Veo 3.1", "Veo 3.1", "predictLongRunning"),

    /**
     * 显示名称: Veo 3.1 fast
     * 功能描述: Veo 3.1 fast
     * 支持的方法: predictLongRunning
     */
    VEO_3_1_FAST_GENERATE_PREVIEW("veo-3.1-fast-generate-preview", "Veo 3.1 fast", "Veo 3.1 fast", "predictLongRunning"),

    /**
     * 显示名称: Veo 3.1 lite
     * 功能描述: Veo 3.1 lite
     * 支持的方法: predictLongRunning
     */
    VEO_3_1_LITE_GENERATE_PREVIEW("veo-3.1-lite-generate-preview", "Veo 3.1 lite", "Veo 3.1 lite", "predictLongRunning"),

    /**
     * 显示名称: Gemini 2.5 Flash Native Audio Latest
     * 功能描述: Latest release of Gemini 2.5 Flash Native Audio
     * 支持的方法: countTokens, bidiGenerateContent
     */
    GEMINI_2_5_FLASH_NATIVE_AUDIO_LATEST("gemini-2.5-flash-native-audio-latest", "Gemini 2.5 Flash Native Audio Latest", "Latest release of Gemini 2.5 Flash Native Audio", "countTokens, bidiGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Flash Native Audio Preview 09-2025
     * 功能描述: Gemini 2.5 Flash Native Audio Preview 09-2025
     * 支持的方法: countTokens, bidiGenerateContent
     */
    GEMINI_2_5_FLASH_NATIVE_AUDIO_PREVIEW_09_2025("gemini-2.5-flash-native-audio-preview-09-2025", "Gemini 2.5 Flash Native Audio Preview 09-2025", "Gemini 2.5 Flash Native Audio Preview 09-2025", "countTokens, bidiGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Flash Native Audio Preview 12-2025
     * 功能描述: Gemini 2.5 Flash Native Audio Preview 12-2025
     * 支持的方法: countTokens, bidiGenerateContent
     */
    GEMINI_2_5_FLASH_NATIVE_AUDIO_PREVIEW_12_2025("gemini-2.5-flash-native-audio-preview-12-2025", "Gemini 2.5 Flash Native Audio Preview 12-2025", "Gemini 2.5 Flash Native Audio Preview 12-2025", "countTokens, bidiGenerateContent"),

    /**
     * 显示名称: Gemini 3.1 Flash Live Preview
     * 功能描述: Gemini 3.1 Flash Live Preview
     * 支持的方法: bidiGenerateContent
     */
    GEMINI_3_1_FLASH_LIVE_PREVIEW("gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live Preview", "Gemini 3.1 Flash Live Preview", "bidiGenerateContent");
}

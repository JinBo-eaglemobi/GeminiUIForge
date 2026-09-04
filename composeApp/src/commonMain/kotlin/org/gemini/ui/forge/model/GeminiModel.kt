package org.gemini.ui.forge.model

/**
 * 自动生成的 Gemini 模型枚举类
 * 包含了当前 API Key 支持的所有可用模型。
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
     * 显示名称: Gemma 4 26B A4B IT
     * 功能描述: Gemma 4 26B A4B IT
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_4_26B_A4B_IT("gemma-4-26b-a4b-it", "Gemma 4 26B A4B IT", "Gemma 4 26B A4B IT", "generateContent, countTokens"),

    /**
     * 显示名称: Gemma 4 31B IT
     * 功能描述: Gemma 4 31B IT
     * 支持的方法: generateContent, countTokens
     */
    GEMMA_4_31B_IT("gemma-4-31b-it", "Gemma 4 31B IT", "Gemma 4 31B IT", "generateContent, countTokens"),

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
     * 显示名称: Gemini 3.1 Flash Lite
     * 功能描述: Gemini 3.1 Flash Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", "Gemini 3.1 Flash Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana Pro
     * 功能描述: Gemini 3 Pro Image Preview
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_PRO_IMAGE_PREVIEW("gemini-3-pro-image-preview", "Nano Banana Pro", "Gemini 3 Pro Image Preview", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana Pro
     * 功能描述: Gemini 3 Pro Image
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_PRO_IMAGE("gemini-3-pro-image", "Nano Banana Pro", "Gemini 3 Pro Image", "generateContent, countTokens, batchGenerateContent"),

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
     * 显示名称: Nano Banana 2
     * 功能描述: Gemini 3.1 Flash Image.
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_1_FLASH_IMAGE("gemini-3.1-flash-image", "Nano Banana 2", "Gemini 3.1 Flash Image.", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Nano Banana 2 Lite
     * 功能描述: Gemini 3.1 Flash Lite Image.
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_1_FLASH_LITE_IMAGE("gemini-3.1-flash-lite-image", "Nano Banana 2 Lite", "Gemini 3.1 Flash Lite Image.", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.5 Flash
     * 功能描述: Gemini 3.5 Flash
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_5_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash", "Gemini 3.5 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.5 Flash Lite
     * 功能描述: Gemini 3.5 Flash Lite
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Gemini 3.5 Flash Lite", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini Omni Flash Preview
     * 功能描述: Gemini Omni Flash Preview
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_OMNI_FLASH_PREVIEW("gemini-omni-flash-preview", "Gemini Omni Flash Preview", "Gemini Omni Flash Preview", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini Omni 1.1 Flash
     * 功能描述: Gemini Omni 1.1 Flash 
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_OMNI_1_1_FLASH("gemini-omni-1.1-flash", "Gemini Omni 1.1 Flash", "Gemini Omni 1.1 Flash ", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini 3.5 Transcribe
     * 功能描述: Gemini 3.5 Transcribe
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_3_5_TRANSCRIBE("gemini-3.5-transcribe", "Gemini 3.5 Transcribe", "Gemini 3.5 Transcribe", "generateContent, countTokens"),

    /**
     * 显示名称: Gemini 3.6 Flash
     * 功能描述: Gemini 3.6 Flash
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_6_FLASH("gemini-3.6-flash", "Gemini 3.6 Flash", "Gemini 3.6 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 3.7 Flash
     * 功能描述: Gemini 3.7 Flash
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_3_7_FLASH("gemini-3.7-flash", "Gemini 3.7 Flash", "Gemini 3.7 Flash", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

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
     * 显示名称: Gemini 3.1 Flash TTS Preview
     * 功能描述: Gemini 3.1 Flash TTS Preview
     * 支持的方法: generateContent, countTokens, batchGenerateContent
     */
    GEMINI_3_1_FLASH_TTS_PREVIEW("gemini-3.1-flash-tts-preview", "Gemini 3.1 Flash TTS Preview", "Gemini 3.1 Flash TTS Preview", "generateContent, countTokens, batchGenerateContent"),

    /**
     * 显示名称: Gemini Robotics-ER 2 Preview
     * 功能描述: Gemini Robotics-ER 2 Preview
     * 支持的方法: generateContent, countTokens, createCachedContent, batchGenerateContent
     */
    GEMINI_ROBOTICS_ER_2_PREVIEW("gemini-robotics-er-2-preview", "Gemini Robotics-ER 2 Preview", "Gemini Robotics-ER 2 Preview", "generateContent, countTokens, createCachedContent, batchGenerateContent"),

    /**
     * 显示名称: Gemini 2.5 Computer Use Preview 10-2025
     * 功能描述: Gemini 2.5 Computer Use Preview 10-2025
     * 支持的方法: generateContent, countTokens
     */
    GEMINI_2_5_COMPUTER_USE_PREVIEW_10_2025("gemini-2.5-computer-use-preview-10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "Gemini 2.5 Computer Use Preview 10-2025", "generateContent, countTokens"),

    /**
     * 显示名称: Antigravity Agent Preview
     * 功能描述: Preview release of Antigravity Agent (05-2026)
     * 支持的方法: generateContent, countTokens
     */
    ANTIGRAVITY_PREVIEW_05_2026("antigravity-preview-05-2026", "Antigravity Agent Preview", "Preview release of Antigravity Agent (05-2026)", "generateContent, countTokens"),

    /**
     * 显示名称: Deep Research Max Preview (Apr-21-2026)
     * 功能描述: Preview release (April 21st, 2026) of Deep Research Max
     * 支持的方法: generateContent, countTokens
     */
    DEEP_RESEARCH_MAX_PREVIEW_04_2026("deep-research-max-preview-04-2026", "Deep Research Max Preview (Apr-21-2026)", "Preview release (April 21st, 2026) of Deep Research Max", "generateContent, countTokens"),

    /**
     * 显示名称: Deep Research Preview (Apr-21-2026)
     * 功能描述: Preview release (April 21th, 2026) of Deep Research
     * 支持的方法: generateContent, countTokens
     */
    DEEP_RESEARCH_PREVIEW_04_2026("deep-research-preview-04-2026", "Deep Research Preview (Apr-21-2026)", "Preview release (April 21th, 2026) of Deep Research", "generateContent, countTokens"),

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
     * 显示名称: Gemini Embedding 2
     * 功能描述: Obtain a distributed representation of multimodal content.
     * 支持的方法: embedContent, countTextTokens, countTokens, asyncBatchEmbedContent
     */
    GEMINI_EMBEDDING_2("gemini-embedding-2", "Gemini Embedding 2", "Obtain a distributed representation of multimodal content.", "embedContent, countTextTokens, countTokens, asyncBatchEmbedContent"),

    /**
     * 显示名称: Model that performs Attributed Question Answering.
     * 功能描述: Model trained to return answers to questions that are grounded in provided sources, along with estimating answerable probability.
     * 支持的方法: generateAnswer
     */
    AQA("aqa", "Model that performs Attributed Question Answering.", "Model trained to return answers to questions that are grounded in provided sources, along with estimating answerable probability.", "generateAnswer"),

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
     * 显示名称: Gemini 3.5 Transcribe Live
     * 功能描述: Gemini 3.5 Transcribe Live
     * 支持的方法: bidiGenerateContent
     */
    GEMINI_3_5_TRANSCRIBE_LIVE("gemini-3.5-transcribe-live", "Gemini 3.5 Transcribe Live", "Gemini 3.5 Transcribe Live", "bidiGenerateContent"),

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

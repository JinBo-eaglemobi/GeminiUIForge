package org.gemini.ui.forge.service

import org.gemini.ui.forge.GeminiModel

object ApiConfig {
    /**
     * Gemini API 的基础 URL
     */
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * 获取图片生成的完整 Endpoint URL (Imagen)
     * 使用 Imagen 3 或 4 模型进行 Text-to-Image
     */
    fun getImagenEndpoint(apiKey: String, modelName: String = GeminiModel.IMAGEN_4_0_GENERATE_001.modelName): String {
        return "$BASE_URL/models/$modelName:predict?key=$apiKey"
    }

    /**
     * 获取 Gemini 官方多模态交互 Endpoint URL
     * 使用具备视觉推理能力的 Gemini Pro 模型
     */
    fun getGenerateContentEndpoint(apiKey: String, modelName: String = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW.modelName): String {
        return "$BASE_URL/models/$modelName:generateContent?key=$apiKey"
    }

    /**
     * 获取 Gemini 流式生成 Endpoint URL (SSE)
     */
    fun getStreamGenerateContentEndpoint(apiKey: String, modelName: String = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW.modelName): String {
        return "$BASE_URL/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"
    }
}

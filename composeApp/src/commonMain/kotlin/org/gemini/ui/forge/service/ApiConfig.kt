package org.gemini.ui.forge.service

import org.gemini.ui.forge.GeminiModel

/**
 * 集中管理和配置应用内使用到的所有网络 API 地址
 */
object ApiConfig {
    /**
     * Gemini API 的基础 URL
     */
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * 获取图片生成的完整 Endpoint URL (Imagen)
     * 使用 Imagen 3 或 4 模型进行 Text-to-Image
     * @param apiKey 用于认证的 API 密钥
     * @param modelName 选用的 Imagen 模型名称
     */
    fun getImagenEndpoint(apiKey: String, modelName: String = GeminiModel.IMAGEN_4_0_GENERATE_001.modelName): String {
        return "$BASE_URL/models/$modelName:predict?key=$apiKey"
    }

    /**
     * 获取 Gemini 流式生成 Endpoint URL (SSE)
     * @param apiKey 用于认证的 API 密钥
     * @param modelName 选用的 Gemini 文本/多模态模型名称
     */
    fun getStreamGenerateContentEndpoint(apiKey: String, modelName: String = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW.modelName): String {
        return "$BASE_URL/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"
    }

    /**
     * 获取 Gemini 非流式生成 Endpoint URL
     * @param apiKey 用于认证的 API 密钥
     * @param modelName 选用的 Gemini 文本/多模态模型名称
     */
    fun getGenerateContentEndpoint(apiKey: String, modelName: String = GeminiModel.GEMINI_3_PRO_IMAGE_PREVIEW.modelName): String {
        return "$BASE_URL/models/$modelName:generateContent?key=$apiKey"
    }
}

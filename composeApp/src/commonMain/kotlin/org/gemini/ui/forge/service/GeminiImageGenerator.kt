package org.gemini.ui.forge.service

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient

/**
 * 专门处理 Gemini 系列模型 (如 Gemini 2.0 Flash) 的生图生成器
 * 使用 generateContent 接口，输出包含图片的 Response
 */
class GeminiImageGenerator(
    private val cloudAssetManager: CloudAssetManager,
    private val promptManager: PromptManager
) : BaseImageGenerator() {

    private val TAG = "GeminiImageGenerator"
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true 
    }

    suspend fun generate(
        model: String,
        params: GenParams,
        onLog: (String) -> Unit
    ): List<String> {
        val url = ApiConfig.getGenerateContentEndpoint(params.apiKey, model)
        val client = NetworkClient.shared
        
        val stylePart = if (params.style.isNotBlank()) {
            "Style: ${params.style}."
        } else {
            ""
        }
        
        val aspectRatio = calculateAspectRatio(params.targetWidth, params.targetHeight)
        val fullPrompt = """
            Generate an image for a UI component. 
            Type: ${params.blockType}. 
            $stylePart 
            Description: ${params.userPrompt}. 
            Aspect ratio: $aspectRatio. 
            [MANDATORY] Respond only with the generated image.
        """.trimIndent()

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { 
                            put("text", fullPrompt) 
                        })
                        
                        // 如果有参考图，以 inlineData 方式加入 (Gemini 模式常用方式)
                        if (!params.referenceImageUri.isNullOrBlank()) {
                            val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(params.referenceImageUri)
                            if (bytes != null) {
                                val mime = org.gemini.ui.forge.utils.getMimeType(params.referenceImageUri)
                                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                                val base64 = kotlin.io.encoding.Base64.encode(bytes)
                                
                                add(buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", mime)
                                        put("data", base64)
                                    })
                                })
                            }
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
//                put("n", params.count.coerceIn(1, 8))
            })
        }.toString()

        syncLog(TAG, "📡 正在向 Gemini 发送生图请求 ($model)...", onLog)
        
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (response.status.isSuccess()) {
            val jsonResponse = jsonConfig.parseToJsonElement(response.bodyAsText())
            val candidates = jsonResponse.jsonObject["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            
            return parts?.mapNotNull { part ->
                val inlineData = part.jsonObject["inlineData"]?.jsonObject
                val base64 = inlineData?.get("data")?.jsonPrimitive?.content
                val mimeType = inlineData?.get("mimeType")?.jsonPrimitive?.content ?: "image/png"
                
                if (base64 != null) {
                    "data:$mimeType;base64,$base64"
                } else {
                    null
                }
            } ?: emptyList()
        } else {
            throw Exception("Gemini 生图错误: ${response.status}")
        }
    }
}

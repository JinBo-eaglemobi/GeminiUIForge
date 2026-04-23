package org.gemini.ui.forge.service

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.utils.getMimeType
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.gemini.ui.forge.manager.*

/**
 * 专门处理 Gemini 系列模型 (如 Gemini 2.0 Flash) 的生图生成器
 * 使用 generateContent 接口，输出包含图片的 Response
 */
class GeminiImageGenerator(
    private val cloudAssetManager: CloudAssetManager,
    private val promptManager: PromptManager,
    private val geminiClient: GeminiClient
) : BaseImageGenerator() {

    private val TAG = "GeminiImageGenerator"

    suspend fun generate(
        model: String,
        params: GenParams,
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit = {}
    ): List<String> {
        val url = ApiConfig.getStreamGenerateContentEndpoint(params.apiKey, model)
        
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
                        
                        if (!params.referenceImageUri.isNullOrBlank()) {
                            val bytes = readLocalFileBytes(params.referenceImageUri)
                            if (bytes != null) {
                                val displayName = params.referenceImageUri.substringAfterLast("/")
                                    .substringAfterLast("\\")
                                    .ifEmpty { "ref_${getCurrentTimeMillis()}.jpg" }
                                val mime = getMimeType(params.referenceImageUri)
                                val imagePart = cloudAssetManager.buildGeminiImagePart(displayName, bytes, mime, onLog)
                                add(imagePart)
                            }
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                // Gemini 2.0 Flash 等模型支持在 generationConfig 中设置候选数量
            })
        }.toString()

        syncLog(TAG, "📡 正在向 Gemini 建立生图流连接 ($model)...", onLog)
        
        val allImages = mutableListOf<String>()
        try {
            geminiClient.streamGenerateContent(
                url = url,
                requestBody = requestBody,
                onLog = onLog,
                onRawData = { jsonElement ->
                    try {
                        val candidates = jsonElement.jsonObject["candidates"]?.jsonArray
                        val parts = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        
                        parts?.forEach { part ->
                            // 提取文本（思考过程等）
                            val textChunk = part.jsonObject["text"]?.jsonPrimitive?.content
                            if (!textChunk.isNullOrEmpty()) {
                                syncLog(TAG, "💬 AI: $textChunk", onLog)
                            }

                            // 提取图片数据 (inlineData)
                            val inlineData = part.jsonObject["inlineData"]?.jsonObject
                            val base64 = inlineData?.get("data")?.jsonPrimitive?.content
                            val mimeType = inlineData?.get("mimeType")?.jsonPrimitive?.content ?: "image/png"
                            
                            if (base64 != null) {
                                syncLog(TAG, "🖼️ 已接收到第 ${allImages.size + 1} 张图片数据", onLog)
                                val dataUri = "data:$mimeType;base64,$base64"
                                allImages.add(dataUri)
                                onImageGenerated(dataUri)
                            }
                        }
                    } catch (_: Exception) {}
                }
            )
            return allImages
        } catch (e: Exception) {
            throw e
        }
    }
}

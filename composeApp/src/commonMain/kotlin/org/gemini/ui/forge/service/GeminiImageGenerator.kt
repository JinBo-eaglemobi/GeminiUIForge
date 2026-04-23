package org.gemini.ui.forge.service

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
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
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit = {}
    ): List<String> {
        val url = ApiConfig.getStreamGenerateContentEndpoint(params.apiKey, model)
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
                // Gemini 2.0 Flash 等模型支持在 generationConfig 中设置候选数量
                // 暂时注释掉，因为有些模型可能不支持 candidate_count
                // put("candidate_count", params.count.coerceIn(1, 4))
            })
        }.toString()

        // 过滤请求中的 Base64 数据以进行日志打印
        val sanitizedRequestBody = requestBody
            .replace(Regex("\"data\"\\s*:\\s*\"[^\"]+\""), "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            
        syncLog(TAG, "---- [HTTP REQUEST] ----\nURL: $url\nBody: \n$sanitizedRequestBody\n------------------------", onLog)
        syncLog(TAG, "📡 正在向 Gemini 建立生图流连接 ($model)...", onLog)
        
        val allImages = mutableListOf<String>()
        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    var chunkIndex = 0
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") {
                                if (dataJson == "[DONE]") syncLog(TAG, "🏁 生图任务结束", onLog)
                                continue
                            }
                            
                            chunkIndex++
                            try {
                                val jsonElement = jsonConfig.parseToJsonElement(dataJson)
                                val candidates = jsonElement.jsonObject["candidates"]?.jsonArray
                                val parts = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                                
                                parts?.forEach { part ->
                                    // 仅提取核心文字描述或思考过程
                                    val textChunk = part.jsonObject["text"]?.jsonPrimitive?.content
                                    if (!textChunk.isNullOrEmpty()) {
                                        syncLog(TAG, "💬 AI: $textChunk", onLog)
                                    }

                                    // 仅提取图片生成成功的状态
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
                            } catch (e: Exception) {
                                // 静默处理解析异常，保持日志干净
                            }
                        }
                    }
                } else {
                    val errorText = response.bodyAsText()
                    syncLog(TAG, "❌ Gemini 生图流错误: HTTP ${response.status}\n$errorText", onLog)
                    throw Exception("Gemini 生图流错误: ${response.status}")
                }
            }
            return allImages
        } catch (e: Exception) {
            throw e
        }
    }
}

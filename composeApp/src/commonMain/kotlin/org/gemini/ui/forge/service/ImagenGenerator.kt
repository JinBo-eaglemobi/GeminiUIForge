package org.gemini.ui.forge.service

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 专门处理 Imagen 系列模型 (Imagen 3, Imagen 4) 的生成器
 */
class ImagenGenerator(
    private val cloudAssetManager: CloudAssetManager,
    private val promptManager: PromptManager
) : BaseImageGenerator() {

    private val TAG = "ImagenGenerator"
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true 
    }
    private val geminiClient = GeminiClient()

    suspend fun generate(
        model: String,
        params: GenParams,
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit = {}
    ): List<String> {
        val url = if (params.isVertexAI) {
            "https://us-central1-aiplatform.googleapis.com/v1/projects/unused/locations/us-central1/publishers/google/models/$model:predict"
        } else {
            ApiConfig.getImagenEndpoint(params.apiKey, model)
        }

        val aspectRatio = calculateAspectRatio(params.targetWidth, params.targetHeight)
        val baseRes = getResolutionBase(params.imageSize)
        val shapeType = calculateShapeType(params.targetWidth, params.targetHeight)

        // 分辨率映射逻辑
        val resolutionMap = mapOf(
            "1:1" to (baseRes.toInt() to baseRes.toInt()),
            "4:3" to (baseRes.toInt() to (baseRes * 0.75f).toInt()),
            "3:4" to ((baseRes * 0.75f).toInt() to baseRes.toInt()),
            "16:9" to (baseRes.toInt() to (baseRes * 0.5625f).toInt()),
            "9:16" to ((baseRes * 0.5625f).toInt() to baseRes.toInt()),
            "3:2" to (baseRes.toInt() to (baseRes * 0.666f).toInt()),
            "2:3" to ((baseRes * 0.666f).toInt() to baseRes.toInt())
        )

        val (maximizedWidth, maximizedHeight) = if (params.targetWidth != null && params.targetHeight != null && params.targetHeight > 0) {
            val canvasRes = resolutionMap[aspectRatio] ?: (baseRes.toInt() to baseRes.toInt())
            val canvasW = canvasRes.first.toFloat()
            val canvasH = canvasRes.second.toFloat()
            val targetRatio = params.targetWidth / params.targetHeight
            val canvasRatio = canvasW / canvasH

            if (targetRatio > canvasRatio) {
                // 模块更宽，受限于画布宽度
                canvasW.toInt() to (canvasW / targetRatio).toInt()
            } else {
                // 模块更高，受限于画布高度
                (canvasH * targetRatio).toInt() to canvasH.toInt()
            }
        } else {
            null to null
        }

        val widthStr = maximizedWidth?.toString() ?: params.targetWidth?.toInt()?.toString() ?: "auto"
        val heightStr = maximizedHeight?.toString() ?: params.targetHeight?.toInt()?.toString() ?: "auto"

        val touchInstruction = if (maximizedWidth != null && maximizedHeight != null) {
            val canvasRes = resolutionMap[aspectRatio] ?: (baseRes.toInt() to baseRes.toInt())
            val isWidthFull = maximizedWidth >= canvasRes.first - 15
            val isHeightFull = maximizedHeight >= canvasRes.second - 15
            
            when {
                isWidthFull && isHeightFull -> "[MANDATORY] This $shapeType object MUST be MASSIVE, filling the entire canvas edge-to-edge. ZERO padding on any side."
                isWidthFull -> "[MANDATORY] This $shapeType object MUST be edge-to-edge horizontally and VERTICALLY STRETCHED to occupy exactly ${maximizedHeight}px."
                else -> "[MANDATORY] This $shapeType object MUST be edge-to-edge vertically and HORIZONTALLY STRETCHED to occupy exactly ${maximizedWidth}px."
            }
        } else {
            "The object should be massive."
        }

        val transparentRequirements = if (params.isPng) {
            promptManager.getPrompt("image_gen_transparent")
        } else {
            ""
        }
        
        val finalLayoutInstruction = if (params.isPng) {
            "$touchInstruction The background MUST only exist as thin solid flat white strips."
        } else {
            touchInstruction
        }
        
        val stylePart = if (params.style.isNotBlank()) {
            "Style: ${params.style}."
        } else {
            ""
        }
        
        val fullPrompt = "{5}. Asset Type: {0}. Shape: $shapeType. Target Size: {3}x{4} pixels. $stylePart {1}. {2}"
            .replace("{0}", params.blockType)
            .replace("{1}", params.userPrompt)
            .replace("{2}", if (params.isPng && maximizedWidth == null) transparentRequirements else "")
            .replace("{3}", widthStr)
            .replace("{4}", heightStr)
            .replace("{5}", finalLayoutInstruction)

        // 处理参考图：云端优先，Base64 兜底
        var refImageJson: JsonObject? = null
        if (!params.referenceImageUri.isNullOrBlank()) {
            val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(params.referenceImageUri)
            if (bytes != null) {
                val displayName = params.referenceImageUri.substringAfterLast("/")
                    .substringAfterLast("\\")
                    .ifEmpty { "ref_${org.gemini.ui.forge.getCurrentTimeMillis()}.jpg" }
                val mime = org.gemini.ui.forge.utils.getMimeType(params.referenceImageUri)
                
                val cloudUri = cloudAssetManager.getOrUploadFile(displayName, bytes, mime)
                if (cloudUri != null) {
                    refImageJson = buildJsonObject { 
                        put("gcsUri", cloudUri) 
                    }
                } else {
                    @OptIn(ExperimentalEncodingApi::class)
                    val base64 = Base64.encode(bytes)
                    refImageJson = buildJsonObject { 
                        put("bytesBase64Encoded", base64) 
                    }
                }
            }
        }

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray { 
                add(buildJsonObject { 
                    put("prompt", fullPrompt) 
                    if (refImageJson != null) {
                        put("image", refImageJson)
                    }
                }) 
            })
            put("parameters", buildJsonObject {
                put("sampleCount", params.count.coerceIn(1, 4))
                put("aspectRatio", aspectRatio)
                put("imageSize", params.imageSize)
                put("outputOptions", buildJsonObject { 
                    put("mimeType", "image/jpeg") 
                })
            })
        }.toString()

        syncLog(TAG, "📡 正在向 Imagen 发送请求...", onLog)
        
        try {
            // 注意：GeminiClient.generateContent 目前只返回第一个发现的文本或 base64
            // 但 Imagen 可能会返回多张图。我们需要更细粒度的控制，或者更新 GeminiClient。
            // 鉴于 Imagen 返回的是 predictions 列表，我将直接在这里处理响应，
            // 或者稍后进一步完善 GeminiClient 以支持列表返回。
            // 目前为了保持兼容并复用日志脱敏，我们仍使用 geminiClient 的辅助方法。
            
            val client = NetworkClient.shared
            // 这里我们仍手动调用 Ktor 以获取原始 JsonResponse，但复用 GeminiClient 的日志逻辑
            // 实际上，我应该让 GeminiClient 更加通用。
            
            // 为了简单起见，我暂时在 ImagenGenerator 中保留部分逻辑，但移除冗余日志
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (params.isVertexAI) {
                    header("Authorization", "Bearer ${params.apiKey}")
                }
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val jsonResponse = jsonConfig.parseToJsonElement(responseText)
                val predictions = jsonResponse.jsonObject["predictions"]?.jsonArray
                val results = predictions?.mapNotNull {
                    val base64 = it.jsonObject["bytesBase64Encoded"]?.jsonPrimitive?.content
                    if (base64 != null) {
                        "data:${if (params.isPng) "image/png" else "image/jpeg"};base64,$base64"
                    } else {
                        null
                    }
                } ?: emptyList()
                
                results.forEach { onImageGenerated(it) }
                return results
            } else {
                throw Exception("Imagen API 错误: ${response.status}")
            }
        } catch (e: Exception) {
            throw e
        }
    }
}

package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger

/**
 * AI 生成服务类，封装了与 Google Gemini 和 Imagen API 的交互逻辑
 */
class AIGenerationService(
    private val cloudAssetManager: CloudAssetManager
) {
    private val TAG = "AIGenerationService"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 内部辅助方法：同步将日志发送到 UI 回调和磁盘日志系统。
     */
    private fun syncLog(message: String, onLog: (String) -> Unit) {
        onLog(message)
        AppLogger.i(TAG, message)
    }

    suspend fun validateImageUris(imageUris: List<String>): String? {
        val client = NetworkClient.shared
        for ((index, uri) in imageUris.withIndex()) {
            try {
                when {
                    uri.startsWith("http") -> {
                        val response = client.head(uri)
                        if (response.status.value != 200) return "参考图 ${index + 1} 链接不可访问"
                    }

                    uri.startsWith("data:image") -> {
                        if (uri.length < 100) return "参考图 ${index + 1} 的 Base64 数据错误"
                    }

                    else -> {
                        if (!org.gemini.ui.forge.utils.isFileExists(uri)) return "无法找到本地参考图 ${index + 1}"
                    }
                }
            } catch (e: Exception) {
                return "验证参考图 ${index + 1} 时发生错误: ${e.message}"
            }
        }
        return null
    }

    suspend fun generateImages(
        blockType: String,
        userPrompt: String,
        count: Int = 4,
        apiKey: String = "",
        maxRetries: Int = 3,
        targetWidth: Float? = null,
        targetHeight: Float? = null,
        isPng: Boolean = false // 新增参数
    ): List<String> {
        if (apiKey.isBlank()) throw Exception("API 密钥未配置")
        val url = ApiConfig.getImagenEndpoint(apiKey)
        val client = NetworkClient.shared
        
        // 核心优化：如果请求 PNG，自动追加易于抠图的 Prompt 指令
        val transparentRequirements = if (isPng) {
            ", on a clean, solid, high-contrast flat white background. The background should be completely plain and simple to facilitate background removal. Ensure the main object's edges and any gradients or glows are clean and preserved."
        } else ""
        
        val fullPrompt = "Game UI asset for a Slot game. Type: $blockType. Style requirements: $userPrompt $transparentRequirements"

        // 计算最接近的标准比例
        val aspectRatio = if (targetWidth != null && targetHeight != null && targetHeight > 0) {
            val ratio = targetWidth / targetHeight
            val options = mapOf(
                "1:1" to 1.0f,
                "4:3" to 1.333f,
                "3:4" to 0.75f,
                "16:9" to 1.777f,
                "9:16" to 0.562f
            )
            options.minByOrNull { kotlin.math.abs(it.value - ratio) }?.key ?: "1:1"
        } else {
            "1:1"
        }

        AppLogger.d(TAG, "Generating images for $blockType. Calculated AspectRatio: $aspectRatio (target: ${targetWidth}x${targetHeight})")

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray { add(buildJsonObject { put("prompt", fullPrompt) }) })
            put("parameters", buildJsonObject {
                put("sampleCount", count.coerceIn(1, 4))
                put("aspectRatio", aspectRatio)
                put("outputOptions", buildJsonObject { put("mimeType", if (isPng) "image/png" else "image/jpeg") })
            })
        }.toString()

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (response.status.isSuccess()) {
                    val jsonResponse = jsonConfig.parseToJsonElement(response.bodyAsText())
                    return jsonResponse.jsonObject["predictions"]?.jsonArray?.mapNotNull {
                        val base64 = it.jsonObject["bytesBase64Encoded"]?.jsonPrimitive?.content
                        if (base64 != null) "data:${if (isPng) "image/png" else "image/jpeg"};base64,$base64" else null
                    } ?: emptyList()
                } else throw Exception("API 错误: ${response.status}")
            } catch (e: Exception) {
                lastException = e
                delay(1000L * attempt)
            }
        }
        throw lastException ?: Exception("生成图片未知错误")
    }

    /**
     * 调用云端 API 执行背景去除 (Vertex AI / Imagen 4 标准版)
     */
    suspend fun removeBackgroundCloud(
        imageBytes: ByteArray,
        apiKey: String
    ): ByteArray? {
        if (apiKey.isBlank()) {
            AppLogger.e(TAG, "Cloud BG removal failed: API Key is empty")
            return null
        }
        
        val url = ApiConfig.getImagenEndpoint(apiKey) 
        val client = NetworkClient.shared

        AppLogger.i(TAG, "Cloud BG removal starting... Image size: ${imageBytes.size / 1024} KB")

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val base64Image = kotlin.io.encoding.Base64.Default.encode(imageBytes)

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject {
                    put("image", buildJsonObject {
                        put("bytesBase64Encoded", base64Image)
                    })
                    put("prompt", "remove the background and make it transparent, keep only the main object")
                })
            })
            put("parameters", buildJsonObject {
                put("editConfig", buildJsonObject {
                    put("editMode", "background_removal") 
                })
                put("outputOptions", buildJsonObject {
                    put("mimeType", "image/png")
                })
            })
        }.toString()

        val startTime = org.gemini.ui.forge.getCurrentTimeMillis()
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val duration = org.gemini.ui.forge.getCurrentTimeMillis() - startTime
            
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val jsonResponse = jsonConfig.parseToJsonElement(responseText)
                val resultBase64 = jsonResponse.jsonObject["predictions"]?.jsonArray?.firstOrNull()?.jsonObject?.get("bytesBase64Encoded")?.jsonPrimitive?.content
                
                if (resultBase64 != null) {
                    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                    val resultBytes = kotlin.io.encoding.Base64.Default.decode(resultBase64)
                    AppLogger.i(TAG, "Cloud BG removal success! Duration: ${duration}ms, Output size: ${resultBytes.size / 1024} KB")
                    resultBytes
                } else {
                    AppLogger.e(TAG, "Cloud BG removal failed: No image data in response. Full body: $responseText")
                    null
                }
            } else {
                AppLogger.e(TAG, "Cloud BG removal failed with status ${response.status}. Body: ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Cloud BG removal exception after ${org.gemini.ui.forge.getCurrentTimeMillis() - startTime}ms", e)
            null
        }
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun analyzeImagesForTemplate(
        imageUris: List<String>,
        apiKey: String = "",
        maxRetries: Int = 3,
        onLog: (String) -> Unit = {},
        onProgress: (String) -> Unit = {},
        onChunk: (String) -> Unit = {}
    ): ProjectState {
        syncLog("开始使用 Gemini 分析图片，数量: ${imageUris.size}", onLog)

        if (apiKey.isBlank()) throw Exception("Gemini API 密钥为空")
        val url = ApiConfig.getStreamGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared

        val promptText = try {
            @OptIn(InternalResourceApi::class)
            readResourceBytes("prompts/analyze_template.txt").decodeToString()
        } catch (e: Exception) {
            "Analyze UI template..."
        }

        syncLog("🚀 正在同步参考资源 (并发模式)...", onLog)

        val partsArray = buildJsonArray {
            add(buildJsonObject { put("text", promptText) })
            coroutineScope {
                val deferredParts = imageUris.mapIndexed { index, localUri ->
                    async {
                        try {
                            val bytes = if (localUri.startsWith("http")) client.get(localUri)
                                .readRawBytes() else org.gemini.ui.forge.utils.readLocalFileBytes(localUri)
                            if (bytes == null) {
                                syncLog("❌ 无法读取图 ${index + 1}", onLog)
                                return@async null
                            }
                            val displayName =
                                localUri.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "image_$index.jpg" }
                            val mime = org.gemini.ui.forge.utils.getMimeType(localUri)
                            val fileUri = cloudAssetManager.getOrUploadFile(displayName, bytes, mime)

                            if (fileUri != null) {
                                syncLog("✅ 图 [${index + 1}] 云端同步成功", onLog)
                                buildJsonObject {
                                    put(
                                        "fileData",
                                        buildJsonObject { put("mimeType", mime); put("fileUri", fileUri) })
                                }
                            } else {
                                syncLog("⚠️ 图 [${index + 1}] 触发 Base64 降级补偿", onLog)
                                buildJsonObject {
                                    put(
                                        "inlineData",
                                        buildJsonObject {
                                            put("mimeType", mime); put(
                                            "data",
                                            kotlin.io.encoding.Base64.Default.encode(bytes)
                                        )
                                        })
                                }
                            }
                        } catch (e: Exception) {
                            syncLog("❌ 处理图 ${index + 1} 异常: ${e.message}", onLog)
                            null
                        }
                    }
                }
                deferredParts.awaitAll().filterNotNull().forEach { add(it) }
            }
        }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", partsArray) }) })
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
        }.toString()

        // 屏蔽日志里的 Base64
        val fileLogBody =
            requestBody.replace(Regex("\"data\"\\s*:\\s*\"[^\"]+\""), "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
        AppLogger.d(TAG, "---- [FULL ANALYZE REQUEST] ----\n$fileLogBody\n---------------------------------")

        syncLog("📡 正在向服务器建立安全连接...", onLog)

        val accumulatedText = StringBuilder()
        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout { 
                    requestTimeoutMillis = 300_000L 
                }
            }.execute { response ->
                syncLog("收到响应，状态: ${response.status}，接收数据流...", onLog)
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            try {
                                val textChunk =
                                    jsonConfig.parseToJsonElement(dataJson).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get(
                                        "content"
                                    )?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                if (textChunk != null) {
                                    accumulatedText.append(textChunk)
                                    onChunk(textChunk)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                    syncLog("UI 框架解析完毕。", onLog)
                } else {
                    val error = response.bodyAsText()
                    AppLogger.e(TAG, "API 失败: $error")
                    throw Exception("API 失败: ${response.status}")
                }
            }

            val finalString = accumulatedText.toString()
            if (finalString.isEmpty()) throw Exception("响应为空")
            val cleanJson = finalString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsedState = jsonConfig.decodeFromString<ProjectState>(cleanJson)
            return parsedState.copy(pages = parsedState.pages.mapIndexed { i, p ->
                p.copy(
                    sourceImageUri = imageUris.getOrNull(
                        i
                    )
                )
            })
        } catch (e: Exception) {
            AppLogger.e(TAG, "分析异常", e)
            throw e
        }
    }

    suspend fun refineAreaForTemplate(
        originalImageUri: String,
        croppedBytes: ByteArray,
        currentJson: String,
        userInstruction: String,
        apiKey: String = "",
        onLog: (String) -> Unit = {},
        onChunk: (String) -> Unit = {}
    ): ProjectState {
        syncLog("开始区域重塑流式分析: $userInstruction", onLog)

        if (apiKey.isBlank()) throw Exception("API 密钥缺失")
        val url = ApiConfig.getStreamGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared

        val promptTemplate = try {
            @OptIn(InternalResourceApi::class)
            readResourceBytes("prompts/refine_template.txt").decodeToString()
        } catch (e: Exception) {
            "Please refine..."
        }

        val fullPrompt = promptTemplate.replace($$"${USER_INSTRUCTION}", userInstruction)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", fullPrompt) })
                        add(buildJsonObject { put("text", "CURRENT_JSON_STATE: \n$currentJson") })
//                        if (originalImageUri.isNotBlank()) {
//                            add(buildJsonObject {
//                                put(
//                                    "fileData",
//                                    buildJsonObject {
//                                        put("mimeType", "image/jpeg");
//                                        put("fileUri", originalImageUri)
//                                    })
//                            })
//                        } else syncLog("⚠️ 未找到原图云端引用，仅使用局部裁剪。", onLog)

                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", "image/jpeg")
                                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                                val base64 = kotlin.io.encoding.Base64.Default.encode(croppedBytes)
                                put("data", base64)
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
        }.toString()

        // 核心同步：UI 显示精简版，文件记录完整版
        val loggableBody = requestBody
            .replace(Regex("\"data\"\\s*:\\s*\"[^\"]+\""), "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            .replace(
                Regex("\"text\"\\s*:\\s*\"CURRENT_JSON_STATE: [\\s\\S]*?\""),
                "\"text\": \"CURRENT_JSON_STATE: <HIDDEN_FOR_LOGS>\""
            )

        AppLogger.d(TAG, "---- [FULL REFINE REQUEST] ----\n$requestBody\n-------------------------------")

        onLog("---- [HTTP REQUEST (REFINE)] ----")
        onLog("URL: $url")
        onLog("Body: \n$loggableBody")

        val accumulatedText = StringBuilder()
        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout { 
                    requestTimeoutMillis = 300_000L 
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            try {
                                val textChunk =
                                    jsonConfig.parseToJsonElement(dataJson).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get(
                                        "content"
                                    )?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                if (textChunk != null) {
                                    accumulatedText.append(textChunk)
                                    onChunk(textChunk)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                    syncLog("✅ 重塑结构解析完毕。", onLog)
                } else {
                    val err = response.bodyAsText()
                    AppLogger.e(TAG, "重塑失败: $err")
                    throw Exception("重塑失败: ${response.status}")
                }
            }
            val finalString = accumulatedText.toString()
            if (finalString.isEmpty()) throw Exception("响应为空")
            val cleanJson = finalString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            return jsonConfig.decodeFromString<ProjectState>(cleanJson)
        } catch (e: Exception) {
            AppLogger.e(TAG, "重塑异常", e)
            throw e
        }
    }

    suspend fun optimizePrompt(originalPrompt: String, apiKey: String, maxRetries: Int = 3): String {
        if (apiKey.isBlank()) throw Exception("API 密钥缺失")
        val url = ApiConfig.getGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject {
                                put(
                                    "text",
                                    "Please translate and optimize: $originalPrompt"
                                )
                            })
                        })
                })
            })
        }.toString()

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = client.post(url) { setBody(requestBody); contentType(ContentType.Application.Json) }
                if (response.status.isSuccess()) {
                    return jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get(
                        "content"
                    )?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim()
                        ?: ""
                } else throw Exception("API 错误: ${response.status}")
            } catch (e: Exception) {
                lastException = e
                delay(1000L * attempt)
            }
        }
        throw lastException ?: Exception("优化未知错误")
    }
}

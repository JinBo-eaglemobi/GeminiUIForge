package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.domain.UIPage
import org.gemini.ui.forge.utils.AppLogger
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

class AIGenerationService {
    private val TAG = "AIGenerationService"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun generateImages(
        blockType: String,
        userPrompt: String,
        count: Int = 4,
        apiKey: String = ""
    ): List<String> {
        AppLogger.i(TAG, "开始通过 Imagen 生成图片: blockType=$blockType, userPrompt=$userPrompt, count=$count")

        if (apiKey.isBlank()) {
            val errorMsg = "Gemini API 密钥为空，请在设置中配置。"
            AppLogger.e(TAG, errorMsg)
            throw Exception(errorMsg)
        }

        // 使用 Imagen 模型生成真正的图像字节数据
        val url = ApiConfig.getImagenEndpoint(apiKey)

        val client = NetworkClient.shared

        val fullPrompt = "Game UI asset for a Slot game. Type: $blockType. Style requirements: $userPrompt"

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject {
                    put("prompt", fullPrompt)
                })
            })
            put("parameters", buildJsonObject {
                put("sampleCount", count.coerceIn(1, 4))
                put("outputOptions", buildJsonObject {
                    put("mimeType", "image/jpeg")
                })
            })
        }.toString()

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)

                retry {
                    maxRetries = 3
                    retryOnExceptionOrServerErrors(3)
                    delayMillis { retry -> retry * 2000L }
                }
            }

            if (response.status.isSuccess()) {
                val jsonResponse = jsonConfig.parseToJsonElement(response.bodyAsText())
                val predictions = jsonResponse.jsonObject["predictions"]?.jsonArray

                if (predictions != null) {
                    return predictions.mapNotNull { prediction ->
                        val base64 = prediction.jsonObject["bytesBase64Encoded"]?.jsonPrimitive?.content
                        if (base64 != null) {
                            "data:image/jpeg;base64,$base64"
                        } else null
                    }
                } else {
                    throw Exception("API 响应中未找到预测数据 (predictions)")
                }
            } else {
                throw Exception("API 请求失败: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            val finalError = "生成图片失败: ${e.message}"
            AppLogger.e(TAG, finalError, e)
            throw Exception(finalError, e)
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
        AppLogger.i(TAG, "开始使用 Gemini 3 Pro Preview 分析图片，数量: ${imageUris.size}")
        onLog("准备上传 ${imageUris.size} 张图片进行分析...")

        if (apiKey.isBlank()) {
            throw Exception("Gemini API 密钥为空，请在设置中配置。")
        }

        val url = ApiConfig.getStreamGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared

        // 构造请求的 Part 列表
        val promptText = try {
            @OptIn(InternalResourceApi::class)
            readResourceBytes("prompts/analyze_template.txt").decodeToString()
        } catch (e: Exception) {
            "Please strictly analyze these images..." // fallback
        }

        val partsArray = buildJsonArray {
            add(buildJsonObject {
                put("text", promptText)
            })

            // 尝试将图片转为 Base64 并添加到请求体
            for ((index, uri) in imageUris.withIndex()) {
                try {
                    onLog("正在处理第 ${index + 1} 张图片: ${uri.take(30)}...")
                    val bytes = if (uri.startsWith("http")) {
                        client.get(uri).readRawBytes()
                    } else {
                        org.gemini.ui.forge.utils.readLocalFileBytes(uri)
                    }

                    if (bytes != null) {
                        AppLogger.i(TAG, "图片 ${index + 1} 大小: ${bytes.size / 1024} KB")
                        val base64Data = kotlin.io.encoding.Base64.Default.encode(bytes)
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", "image/jpeg")
                                put("data", base64Data)
                            })
                        })
                    } else {
                        onLog("警告: 无法读取图片 ${index + 1}")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "读取图片失败: $uri", e)
                    onLog("错误: 读取图片 ${index + 1} 失败")
                }
            }
        }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", partsArray)
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
            })
        }.toString()

        AppLogger.i(TAG, "请求体总大小: ${requestBody.length / 1024} KB")
        onLog("---- [HTTP REQUEST] ----")
        onLog("URL: $url")
        onLog("Method: POST")
        onLog("Headers: Content-Type=application/json")
        onLog("BodySize: ${requestBody.length / 1024} KB")
        onLog("BodyPreview: ${requestBody.take(200)}...")
        onLog("------------------------")
        onLog("请求已准备就绪，正在发送至 Gemini API (流式通道)...")

        var lastException: Exception? = null
        val accumulatedText = StringBuilder()

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)

                timeout {
                    requestTimeoutMillis = 60_000L
                    socketTimeoutMillis = 60_000L
                }

                retry {
                    this.maxRetries = maxRetries
                    retryOnExceptionOrServerErrors(maxRetries)
                    delayMillis { retry ->
                        val msg = "网络超时或异常，正在重试... (第 $retry 次，共 $maxRetries 次)"
                        AppLogger.i(TAG, msg)
                        onLog(msg)
                        retry * 3000L
                    }
                }
            }

            onLog("收到 API 响应，状态码: ${response.status}，开始接收数据流...")
            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val dataJson = line.substringAfter("data: ").trim()
                        if (dataJson.isEmpty() || dataJson == "[DONE]") continue

                        try {
                            val jsonElement = jsonConfig.parseToJsonElement(dataJson)
                            val textChunk = jsonElement.jsonObject["candidates"]
                                ?.jsonArray?.firstOrNull()
                                ?.jsonObject?.get("content")
                                ?.jsonObject?.get("parts")
                                ?.jsonArray?.firstOrNull()
                                ?.jsonObject?.get("text")?.jsonPrimitive?.content

                            if (textChunk != null) {
                                accumulatedText.append(textChunk)
                                onChunk(textChunk)
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "解析流数据块失败: $dataJson", e)
                        }
                    }
                }
            } else {
                throw Exception("API 请求失败: ${response.status} - ${response.bodyAsText()}")
            }

            val finalString = accumulatedText.toString()
            if (finalString.isEmpty()) {
                throw Exception("Gemini 响应中缺少文本数据。")
            }

            val cleanJson = finalString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsedState = jsonConfig.decodeFromString<ProjectState>(cleanJson)

            // 使用第一张输入图片的原始路径作为 coverImage
            val cover = imageUris.firstOrNull()
            return parsedState.copy(coverImage = cover)

        } catch (e: Exception) {
            lastException = e
            AppLogger.e(TAG, "请求异常", e)
        }
        throw Exception("生成模板失败 (已重试 $maxRetries 次): ${lastException?.message}", lastException)
    }
}

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

/**
 * AI 生成服务类，封装了与 Google Gemini 和 Imagen API 的交互逻辑
 */
class AIGenerationService {
    private val TAG = "AIGenerationService"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 调用 Imagen 模型根据文本 Prompt 生成 UI 图片资源
     * @param blockType 组件类型名称（如 "SPIN_BUTTON"），用于构造 Prompt
     * @param userPrompt 用户自定义的风格描述文本
     * @param count 请求生成的图片数量，默认为 4
     * @param apiKey Gemini API 密钥
     * @return 返回包含图片 Base64 数据的列表 (格式: data:image/jpeg;base64,...)
     * @throws Exception 当 API 密钥为空或网络请求失败时抛出异常
     */
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

    /**
     * 使用 Gemini 3 Pro Preview 模型多模态能力分析图片并生成项目模板
     * @param imageUris 待分析的图片路径或 URL 列表
     * @param apiKey Gemini API 密钥
     * @param maxRetries 最大重试次数，默认为 3
     * @param onLog 日志回调，用于 UI 层的实时日志展示
     * @param onProgress 进度回调
     * @param onChunk 流式数据块回调，每接收到一部分生成的 JSON 文本时触发
     * @return 返回解析后的项目状态对象 [ProjectState]
     * @throws Exception 当生成过程彻底失败时抛出异常
     */
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
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)

                timeout {
                    requestTimeoutMillis = 120_000L
                    socketTimeoutMillis = 120_000L
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
            }.execute { response ->
                onLog("收到 API 响应，状态码: ${response.status}，开始准备接收流式数据管道...")
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    onLog("数据管道已连接，等待 Gemini 吐字...")
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") {
                                onLog("收到结束标识符 [DONE] 或空数据包")
                                continue
                            }

                            onLog("接收到数据块: ${line.length} 字节")

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
                                onLog("警告: 解析当前数据块 JSON 失败 (Size: ${dataJson.length})")
                            }
                        } else if (line.trim().isNotEmpty()) {
                            onLog("收到非数据报文: ${line.take(20)}... (Size: ${line.length})")
                        }
                    }
                    onLog("流式读取循环已结束。")
                } else {
                    val errorBody = response.bodyAsText()
                    throw Exception("API 请求失败: ${response.status} - $errorBody")
                }
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

    /**
     * 优化并翻译给定的 prompt 为更适合生图的英文 prompt
     * @param originalPrompt 原始输入的文案
     * @param apiKey Gemini API 密钥
     * @return 优化后的英文 prompt
     */
    suspend fun optimizePrompt(originalPrompt: String, apiKey: String): String {
        AppLogger.i(TAG, "开始优化 Prompt: $originalPrompt")

        if (apiKey.isBlank()) {
            throw Exception("Gemini API 密钥为空，请在设置中配置。")
        }

        // 使用非流式端点，简化解析
        val url = ApiConfig.getGenerateContentEndpoint(apiKey, "gemini-2.0-flash-exp")
        val client = NetworkClient.shared

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put(
                                "text",
                                "Please translate and optimize the following text into a highly descriptive, comma-separated English prompt for an image generation AI (like Midjourney or Imagen). Do not include any conversational filler, just the prompt itself: $originalPrompt"
                            )
                        })
                    })
                })
            })
        }.toString()

        try {
            val response = client.post(url) {
                setBody(requestBody)
                contentType(ContentType.Application.Json)

                retry {
                    maxRetries = 3
                    retryOnExceptionOrServerErrors(3)
                    delayMillis { retry -> retry * 2000L }
                }
            }

            if (response.status.isSuccess()) {
                val jsonElement = jsonConfig.parseToJsonElement(response.bodyAsText())
                
                // 解析标准的 generateContent 返回格式
                val text = jsonElement.jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")
                    ?.jsonObject?.get("parts")
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content ?: ""

                return text.trim()
            } else {
                throw Exception("API 请求失败: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "优化 Prompt 失败", e)
            throw Exception("优化失败: ${e.message}", e)
        }
    }
}

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
class AIGenerationService(
    private val cloudAssetManager: CloudAssetManager
) {
    private val TAG = "AIGenerationService"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 在正式提交 AI 分析前，预先验证图片路径的有效性。
     * 支持验证本地文件、HTTP URL 连通性以及 Base64 字符串格式。
     * @return 如果所有路径均有效则返回 null，否则返回第一个错误描述
     */
    suspend fun validateImageUris(imageUris: List<String>): String? {
        val client = NetworkClient.shared
        for ((index, uri) in imageUris.withIndex()) {
            try {
                when {
                    uri.startsWith("http") -> {
                        val response = client.head(uri)
                        if (response.status.value != 200) {
                            return "参考图 ${index + 1} 链接不可访问 (状态码: ${response.status.value})"
                        }
                    }
                    uri.startsWith("data:image") -> {
                        val pureBase64 = if (uri.contains(",")) uri.substringAfter(",") else uri
                        if (pureBase64.isBlank() || pureBase64.length < 100) {
                            return "参考图 ${index + 1} 的 Base64 数据格式错误或过短"
                        }
                    }
                    else -> {
                        // 核心优化：使用存在性判断而非读取全量字节，避免内存浪费
                        if (!org.gemini.ui.forge.utils.isFileExists(uri)) {
                            return "无法找到本地参考图 ${index + 1}，请检查路径是否正确。"
                        }
                    }
                }
            } catch (e: Exception) {
                return "验证参考图 ${index + 1} 时发生错误: ${e.message}"
            }
        }
        return null
    }

    /**
     * 调用 Imagen 模型根据文本 Prompt 生成 UI 图片资源
     */
    suspend fun generateImages(
        blockType: String,
        userPrompt: String,
        count: Int = 4,
        apiKey: String = "",
        maxRetries: Int = 3
    ): List<String> {
        AppLogger.i(TAG, "开始通过 Imagen 生成图片: blockType=$blockType, userPrompt=$userPrompt, count=$count, maxRetries=$maxRetries")

        if (apiKey.isBlank()) {
            val errorMsg = "Gemini API 密钥为空，请在设置中配置。"
            AppLogger.e(TAG, errorMsg)
            throw Exception(errorMsg)
        }

        val url = ApiConfig.getImagenEndpoint(apiKey)
        val client = NetworkClient.shared
        val fullPrompt = "Game UI asset for a Slot game. Type: $blockType. Style requirements: $userPrompt"

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject { put("prompt", fullPrompt) })
            })
            put("parameters", buildJsonObject {
                put("sampleCount", count.coerceIn(1, 4))
                put("outputOptions", buildJsonObject {
                    put("mimeType", "image/jpeg")
                })
            })
        }.toString()

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    AppLogger.i(TAG, "generateImages 请求失败，正在进行第 $attempt 次重试...")
                    delay(2000L * attempt)
                }

                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
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
                lastException = e
                AppLogger.e(TAG, "第 $attempt 次生成图片失败: ${e.message}", e)
            }
        }
        throw Exception("生成图片失败 (已重试 $maxRetries 次): ${lastException?.message}", lastException)
    }

    /**
     * 并发批量生成项目状态中所有空缺的 UIBlock 的图片 (Demo 画像填充)。
     * 为了避免达到并发上限，采取了分批（Chunking）机制。
     */
    suspend fun generateDemoImagesForProject(
        projectState: ProjectState,
        apiKey: String,
        batchSize: Int = 3,
        onBlockGenerated: (UIBlock, String) -> Unit = { _, _ -> }
    ) = coroutineScope {
        AppLogger.i(TAG, "开始批量生成组件 Demo 图片...")
        
        // 收集所有需要生成图片的 Block
        val pendingBlocks = mutableListOf<UIBlock>()
        projectState.pages.forEach { page ->
            pendingBlocks.addAll(page.blocks)
        }
        
        AppLogger.i(TAG, "共计 ${pendingBlocks.size} 个模块需要生成图片，按每批 $batchSize 个并发处理")

        // 分批执行，以避免一次性开启过多请求导致 Rate Limit
        val chunkedBlocks = pendingBlocks.chunked(batchSize)
        
        chunkedBlocks.forEachIndexed { index, batch ->
            AppLogger.d(TAG, "处理第 ${index + 1}/${chunkedBlocks.size} 批次...")
            
            // 当前批次内部并发请求
            val deferredResults = batch.map { block ->
                async {
                    try {
                        // 只要求生成 1 张高质量 Demo 图即可
                        val generatedImages = generateImages(
                            blockType = block.type.name,
                            userPrompt = block.userPrompt,
                            count = 1,
                            apiKey = apiKey
                        )
                        val resultImage = generatedImages.firstOrNull()
                        if (resultImage != null) {
                            onBlockGenerated(block, resultImage)
                        } else {
                            AppLogger.i(TAG, "模块 ${block.id} 成功调用但返回了空图片。")
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "模块 ${block.id} 生成图片时出错: ${e.message}")
                    }
                }
            }
            
            // 等待当前批次完成
            deferredResults.awaitAll()
            
            // 如果还有下一批，可稍微延迟降低 QPS 压力
            if (index < chunkedBlocks.size - 1) {
                delay(1500L) 
            }
        }
        AppLogger.i(TAG, "批量生成 Demo 图片任务结束。")
    }

    /**
     * 使用 Gemini 3 Pro Preview 模型多模态能力分析图片并生成项目模板
     * 使用 Gemini File API 机制先上传图片获取 URI，再执行轻量级推断。
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
        onLog("正在准备图片资源，启用云端资产管理器上传加速...")

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

        // 核心优化：并发处理参考图，若云端同步失败则强制执行 Base64 降级，确保流程 100% 可用
        onLog("🚀 正在同步参考资源 (并发模式)...")

        val partsArray = buildJsonArray {
            add(buildJsonObject { put("text", promptText) })

            coroutineScope {
                val deferredParts = imageUris.mapIndexed { index, localUri ->
                    async {
                        try {
                            val bytes = if (localUri.startsWith("http")) {
                                client.get(localUri).readRawBytes()
                            } else {
                                org.gemini.ui.forge.utils.readLocalFileBytes(localUri)
                            }

                            if (bytes == null) {
                                onLog("❌ 错误: 无法读取参考图 ${index + 1}")
                                return@async null
                            }

                            val displayName = localUri.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "image_$index.jpg" }
                            val currentMimeType = org.gemini.ui.forge.utils.getMimeType(localUri)
                            
                            // 尝试云端上传 (File API)
                            val fileUri = try {
                                cloudAssetManager.getOrUploadFile(displayName, bytes, currentMimeType)
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "图 ${index + 1} 云端上传异常: ${e.message}")
                                null
                            }

                            if (fileUri != null) {
                                onLog("✅ 图 [${index + 1}] 云端同步成功")
                                buildJsonObject {
                                    put("fileData", buildJsonObject {
                                        put("mimeType", currentMimeType)
                                        put("fileUri", fileUri)
                                    })
                                }
                            } else {
                                // 关键：如果上传失败，强制执行 Base64 降级补偿
                                onLog("⚠️ 图 [${index + 1}] 云端上传失败，正在执行 Base64 降级补偿...")
                                val base64Data = kotlin.io.encoding.Base64.Default.encode(bytes)
                                buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", currentMimeType)
                                        put("data", base64Data)
                                    })
                                }
                            }
                        } catch (e: Exception) {
                            onLog("❌ 处理图 ${index + 1} 发生致命错误: ${e.message}")
                            null
                        }
                    }
                }
                
                // 收集所有部分并装载到请求体
                deferredParts.awaitAll().filterNotNull().forEach { add(it) }
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

        // 屏蔽庞大的 Base64 字符串以防止日志刷屏和 UI 卡顿
        val loggableBody = requestBody.replace(
            Regex("\"data\"\\s*:\\s*\"[^\"]+\""), 
            "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\""
        )

        AppLogger.i(TAG, "请求体总大小: ${requestBody.length / 1024} KB")
        onLog("---- [HTTP REQUEST] ----")
        onLog("URL: $url")
        onLog("Method: POST")
        onLog("Headers: Content-Type=application/json")
        onLog("Body: \n$loggableBody")
        onLog("------------------------")
        onLog("📡 正在向服务器建立安全连接并提交分析协议...")

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
                onLog("收到 API 响应，状态码: ${response.status}，开始接收 JSON 模块流...")
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
                    onLog("UI 框架结构解析完毕。")
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
     * 调用 Gemini 3 Pro Preview 的多轮对话/上下文修正能力，针对特定区域重新分析 UI。
     * @param originalImageUri 原始参考图的云端 URI (File API)
     * @param croppedBytes 该区域的局部高分辨率裁剪图字节
     * @param currentJson 当前的完整 ProjectState JSON 字符串
     * @param userInstruction 用户提供的修正建议文案
     */
    suspend fun refineAreaForTemplate(
        originalImageUri: String,
        croppedBytes: ByteArray,
        currentJson: String,
        userInstruction: String,
        apiKey: String = "",
        onLog: (String) -> Unit = {}
    ): ProjectState {
        AppLogger.i(TAG, "开始区域重塑分析: $userInstruction")
        onLog("正在准备上下文数据（原图引用 + 局部细节）...")

        if (apiKey.isBlank()) throw Exception("API 密钥缺失")

        val url = ApiConfig.getGenerateContentEndpoint(apiKey) // 修正通常使用非流式以获得完整 JSON
        val client = NetworkClient.shared

        val promptTemplate = try {
            @OptIn(InternalResourceApi::class)
            readResourceBytes("prompts/refine_template.txt").decodeToString()
        } catch (e: Exception) {
            "Please refine the template..." 
        }

        val fullPrompt = promptTemplate.replace("\${USER_INSTRUCTION}", userInstruction)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", fullPrompt) })
                        add(buildJsonObject { put("text", "CURRENT_JSON_STATE: \n$currentJson") })
                        // 原图上下文（URI 引用）
                        add(buildJsonObject {
                            put("fileData", buildJsonObject {
                                put("mimeType", "image/jpeg")
                                put("fileUri", originalImageUri)
                            })
                        })
                        // 局部细节（Inline Base64）
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", "image/jpeg")
                                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                                put("data", kotlin.io.encoding.Base64.Default.encode(croppedBytes))
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
            })
        }.toString()

        onLog("---- [HTTP REQUEST (REFINE)] ----")
        onLog("指令: $userInstruction")
        onLog("------------------------")
        onLog("正在请求 Gemini 重新评估该区域结构...")

        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (response.status.isSuccess()) {
            val text = jsonConfig.parseToJsonElement(response.bodyAsText())
                .jsonObject["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            
            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            onLog("✅ 区域重塑成功，正在合并数据...")
            return jsonConfig.decodeFromString<ProjectState>(cleanJson)
        } else {
            throw Exception("修正失败: ${response.status} - ${response.bodyAsText()}")
        }
    }

    /**
     * 优化并翻译给定的 prompt 为更适合生图的英文 prompt
     */
    suspend fun optimizePrompt(originalPrompt: String, apiKey: String, maxRetries: Int = 3): String {
        AppLogger.i(TAG, "开始优化 Prompt: $originalPrompt, maxRetries=$maxRetries")

        if (apiKey.isBlank()) {
            throw Exception("Gemini API 密钥为空，请在设置中配置。")
        }

        val url = ApiConfig.getGenerateContentEndpoint(apiKey)
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

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    delay(2000L * attempt)
                }

                val response = client.post(url) {
                    setBody(requestBody)
                    contentType(ContentType.Application.Json)
                }

                if (response.status.isSuccess()) {
                    val jsonElement = jsonConfig.parseToJsonElement(response.bodyAsText())
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
                lastException = e
                AppLogger.e(TAG, "第 $attempt 次优化 Prompt 失败", e)
            }
        }
        throw Exception("优化失败 (已重试 $maxRetries 次): ${lastException?.message}", lastException)
    }
}

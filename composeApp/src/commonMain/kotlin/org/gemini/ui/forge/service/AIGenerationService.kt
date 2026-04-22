package org.gemini.ui.forge.service

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger

/**
 * AI 生成服务类（门面），协调 ImagenGenerator 和 GeminiImageGenerator。
 */
class AIGenerationService(
    private val cloudAssetManager: CloudAssetManager,
    private val configManager: ConfigManager
) {
    private val TAG = "AIGenerationService"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    // 初始化存储与管理器
    private val storage = LocalFileStorage()
    val promptManager = PromptManager(storage)
    private val scriptManager = ScriptManager(storage)

    private val imagenGenerator = ImagenGenerator(cloudAssetManager, promptManager)
    private val geminiGenerator = GeminiImageGenerator(cloudAssetManager, promptManager)

    /**
     * 内部辅助方法：同步将日志发送到 UI 回调和磁盘日志系统。
     */
    private fun syncLog(message: String, onLog: (String) -> Unit) {
        onLog(message)
        AppLogger.i(TAG, message)
    }

    /**
     * 过滤 Base64 数据并统一输出 HTTP 请求日志
     */
    private fun logRequest(url: String, requestBody: String, onLog: (String) -> Unit) {
        val sanitizedBody = requestBody
            .replace(Regex("\"data\"\\s*:\\s*\"[^\"]+\""), "\"data\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            .replace(Regex("\"bytesBase64Encoded\"\\s*:\\s*\"[^\"]+\""), "\"bytesBase64Encoded\": \"<BASE64_IMAGE_DATA_OMITTED>\"")
            .replace(Regex("\"text\"\\s*:\\s*\"CURRENT_JSON_STATE: [\\s\\S]*?\""), "\"text\": \"CURRENT_JSON_STATE: <HIDDEN_FOR_LOGS>\"")
        
        val logMessage = "---- [HTTP REQUEST] ----\nURL: $url\nBody: \n$sanitizedBody\n------------------------"
        syncLog(logMessage, onLog)
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

    /**
     * 核心生图方法，根据选中的 GeminiModel 自动路由到 Imagen 或 Native Gemini 生成逻辑。
     * 现在支持从设置中读取默认生图数量，并自动拆分为并发批次请求（单次上限 4 张）。
     */
    suspend fun generateImages(
        model: GeminiModel,
        blockType: String,
        userPrompt: String,
        apiKey: String = "",
        maxRetries: Int = 3,
        targetWidth: Float? = null,
        targetHeight: Float? = null,
        isPng: Boolean = false,
        imageSize: String = "1k",
        style: String = "",
        referenceImageUri: String? = null,
        isVertexAI: Boolean = false,
        onLog: (String) -> Unit = {}
    ): List<String> = coroutineScope {
        // 从配置中读取总数量，默认为 4
        val configCountStr = configManager.loadKey("IMAGE_GEN_COUNT") ?: "4"
        val totalCount = configCountStr.toIntOrNull() ?: 4

        // 路由逻辑：
        // 1. 如果支持 predict 方法或者是 imagen 名下的，走 ImagenGenerator
        // 2. 如果支持 generateContent 且包含 image 关键字，走 GeminiImageGenerator
        // 3. 否则默认走 ImagenGenerator (兼容性考虑)
        val isImagen = model.supportedMethods.contains("predict") || model.modelName.contains("imagen")
        val isGeminiNative = model.supportedMethods.contains("generateContent") && model.modelName.contains("image")

        // 单次 API 请求的最大张数限制：Imagen 3 和原生 Gemini 通常单次最高支持 4 张 (candidateCount/sampleCount)
        val maxBatchSize = if (isGeminiNative) 8 else 4
        
        // 计算批次
        val batchCounts = mutableListOf<Int>()
        var remaining = totalCount
        while (remaining > 0) {
            val nextBatch = if (remaining > maxBatchSize) maxBatchSize else remaining
            batchCounts.add(nextBatch)
            remaining -= nextBatch
        }
        
        syncLog("🚀 开始生图任务: 总数 $totalCount, 拆分为 ${batchCounts.size} 个批次", onLog)

        // 并发执行批次
        val deferredResults = batchCounts.mapIndexed { index, batchSize ->
            async {
                val batchTag = if (batchCounts.size > 1) "[批次 ${index + 1}/${batchSize}张]" else ""
                val params = BaseImageGenerator.GenParams(
                    blockType = blockType,
                    userPrompt = userPrompt,
                    count = batchSize,
                    apiKey = apiKey,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    isPng = isPng,
                    imageSize = imageSize,
                    style = style,
                    referenceImageUri = referenceImageUri,
                    isVertexAI = isVertexAI
                )

                var lastException: Exception? = null
                for (attempt in 0..maxRetries) {
                    try {
                        if (attempt > 0) {
                            syncLog("⚠️ $batchTag 重试中 (${attempt + 1})...", onLog)
                        }
                        
                        return@async if (isGeminiNative) {
                            geminiGenerator.generate(model.modelName, params, onLog)
                        } else {
                            imagenGenerator.generate(model.modelName, params, onLog)
                        }
                    } catch (e: Exception) {
                        lastException = e
                        syncLog("❌ $batchTag 请求异常: ${e.message}", onLog)
                        if (attempt < maxRetries) delay(1000L * (attempt + 1))
                    }
                }
                throw lastException ?: Exception("生图未知错误")
            }
        }

        // 等待所有批次完成并汇总结果
        deferredResults.awaitAll().flatten()
    }

    /**
     * 调用本地 Python 脚本执行背景去除 (rembg)
     */
    suspend fun removeBackgroundLocal(
        imageBytes: ByteArray,
        onLog: (String) -> Unit = {}
    ): ByteArray? {
        val scriptPath = scriptManager.getScriptPath("remove_bg.py") ?: run {
            syncLog("❌ 未能获取本地抠图脚本路径", onLog)
            return null
        }

        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val inputPath = storage.getFilePath("temp/input_$timestamp.png")
        val outputPath = storage.getFilePath("temp/output_$timestamp.png")

        return try {
            storage.saveBytesToFile("temp/input_$timestamp.png", imageBytes)
            
            syncLog("🚀 启动本地 Python 抠图引擎...", onLog)
            
            val commands = listOf("python", "python3")
            var success = false
            for (cmd in commands) {
                success = org.gemini.ui.forge.utils.executeSystemCommand(
                    command = cmd,
                    args = listOf(scriptPath, inputPath, outputPath),
                    onLog = { syncLog("[LocalRembg] $it", onLog) }
                )
                if (success) break
            }

            if (success && org.gemini.ui.forge.utils.isFileExists(outputPath)) {
                val result = org.gemini.ui.forge.utils.readLocalFileBytes(outputPath)
                syncLog("✅ 本地抠图处理完成", onLog)
                result
            } else {
                syncLog("❌ 本地抠图脚本执行失败", onLog)
                null
            }
        } catch (e: Exception) {
            syncLog("❌ 本地抠图异常: ${e.message}", onLog)
            null
        } finally {
            org.gemini.ui.forge.utils.deleteLocalFile(inputPath)
            org.gemini.ui.forge.utils.deleteLocalFile(outputPath)
        }
    }

    /**
     * 调用云端 API 执行背景去除 (Vertex AI / Imagen 4 标准版)
     */
    suspend fun removeBackgroundCloud(
        imageBytes: ByteArray,
        apiKey: String,
        onLog: (String) -> Unit = {}
    ): ByteArray? {
        if (apiKey.isBlank()) {
            syncLog("❌ 云端抠图失败: API Key 为空", onLog)
            return null
        }
        
        val url = ApiConfig.getImagenEndpoint(apiKey) 
        val client = NetworkClient.shared

        syncLog("☁️ 启动云端 AI 抠图引擎...", onLog)

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val base64Image = kotlin.io.encoding.Base64.Default.encode(imageBytes)

        val prompt = promptManager.getPrompt("cloud_bg_removal")

        val requestBody = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject {
                    put("image", buildJsonObject {
                        put("bytesBase64Encoded", base64Image)
                    })
                    put("prompt", prompt)
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
            logRequest(url, requestBody, onLog)
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
                    syncLog("✅ 云端抠图处理完成 (${duration}ms)", onLog)
                    resultBytes
                } else {
                    syncLog("❌ 云端抠图失败: 响应数据为空", onLog)
                    null
                }
            } else {
                syncLog("❌ 云端抠图失败: 接口状态码 ${response.status}", onLog)
                null
            }
        } catch (e: Exception) {
            syncLog("❌ 云端抠图异常: ${e.message}", onLog)
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

        val promptText = promptManager.getPrompt("analyze_template")

        syncLog("🚀 正在同步参考资源 (并发模式)...", onLog)

        val partsArray = buildJsonArray {
            add(buildJsonObject { put("text", promptText) })
            coroutineScope {
                val deferredParts = imageUris.mapIndexed { index, localUri ->
                    async {
                        try {
                            val bytes = if (localUri.startsWith("http") && !localUri.startsWith("data:")) {
                                client.get(localUri).readRawBytes()
                            } else {
                                org.gemini.ui.forge.utils.readLocalFileBytes(localUri)
                            }
                            if (bytes == null) {
                                syncLog("❌ 无法读取图 ${index + 1}", onLog)
                                return@async null
                            }
                            val displayName = localUri.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "image_$index.jpg" }
                            val mime = org.gemini.ui.forge.utils.getMimeType(localUri)
                            val fileUri = cloudAssetManager.getOrUploadFile(displayName, bytes, mime)

                            if (fileUri != null) {
                                syncLog("✅ 图 [${index + 1}] 云端同步成功", onLog)
                                buildJsonObject {
                                    put("fileData", buildJsonObject { put("mimeType", mime); put("fileUri", fileUri) })
                                }
                            } else {
                                syncLog("⚠️ 图 [${index + 1}] 触发 Base64 降级补偿", onLog)
                                buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", mime)
                                        put("data", kotlin.io.encoding.Base64.Default.encode(bytes))
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

        logRequest(url, requestBody, onLog)

        syncLog("📡 正在向服务器建立安全连接...", onLog)

        val accumulatedText = StringBuilder()
        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout { requestTimeoutMillis = 300_000L }
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
                                val textChunk = jsonConfig.parseToJsonElement(dataJson).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                if (textChunk != null) {
                                    accumulatedText.append(textChunk)
                                    onChunk(textChunk)
                                }
                            } catch (e: Exception) {}
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
            return jsonConfig.decodeFromString<ProjectState>(cleanJson)
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
        history: List<org.gemini.ui.forge.model.api.ChatMessage> = emptyList(),
        onLog: (String) -> Unit = {},
        onChunk: (String) -> Unit = {}
    ): ProjectState {
        syncLog("开始区域重塑流式分析: $userInstruction", onLog)

        if (apiKey.isBlank()) throw Exception("API 密钥缺失")
        val url = ApiConfig.getStreamGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared

        val promptTemplate = promptManager.getPrompt("refine_template")
        val fullPrompt = promptTemplate.replace($$"${USER_INSTRUCTION}", userInstruction)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                // 1. 注入历史
                history.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", msg.text) })
                        })
                    })
                }

                // 2. 当前最新请求 (带图)
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", fullPrompt) })
                        add(buildJsonObject { put("text", "CURRENT_JSON_STATE: \n$currentJson") })
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

        logRequest(url, requestBody, onLog)

        val accumulatedText = StringBuilder()
        try {
            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout { requestTimeoutMillis = 300_000L }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJson = line.substringAfter("data: ").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            try {
                                val textChunk = jsonConfig.parseToJsonElement(dataJson).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                if (textChunk != null) {
                                    accumulatedText.append(textChunk)
                                    onChunk(textChunk)
                                }
                            } catch (e: Exception) {}
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

    suspend fun optimizePrompt(
        originalPrompt: String, 
        apiKey: String, 
        maxRetries: Int = 3,
        history: List<org.gemini.ui.forge.model.api.ChatMessage> = emptyList()
    ): String {
        if (apiKey.isBlank()) throw Exception("API 密钥缺失")
        val url = ApiConfig.getGenerateContentEndpoint(apiKey)
        val client = NetworkClient.shared

        val promptTemplate = promptManager.getPrompt("ai_optimize_prompt")
        val fullPrompt = promptTemplate.replace("{0}", originalPrompt)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                // 1. 注入历史会话记录
                history.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", msg.text) })
                        })
                    })
                }
                
                // 2. 追加当前最新请求
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", fullPrompt) })
                    })
                })
            })
        }.toString()

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                logRequest(url, requestBody, {})
                val response = client.post(url) { setBody(requestBody); contentType(ContentType.Application.Json) }
                if (response.status.isSuccess()) {
                    return jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim() ?: ""
                } else throw Exception("API 错误: ${response.status}")
            } catch (e: Exception) {
                lastException = e
                delay(1000L * attempt)
            }
        }
        throw lastException ?: Exception("优化未知错误")
    }
}

package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.utils.AppLogger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 全局统一 Gemini 聊天与推理核心引擎 (GeminiChatEngine)
 *
 * 核心特性与架构职责：
 * 1. 【全参数强类型对齐】：全面支持 Google Gemini 官方 `generateContent` 与 `Interactions API` 完整可选参数；
 * 2. 【Google File API 自动打通】：基于 [CloudAssetManager] 自动上传云端并组装为 `fileData` / `file_uri`，支持 Base64 安全降级；
 * 3. 【协议严格合规】：系统指令一律使用官方合规的独立字段（`systemInstruction` / `system_instruction`），严禁与用户输入混淆；
 * 4. 【多协议流式与通信归档】：无缝对接 SSE 流式打字机与 [TrafficDirection] 报文持久化；
 * 5. 【数据保真】：严格遵循用户指示，保留 Markdown 代码块供上层 Markdown 渲染器解析。
 */
class GeminiChatEngine(
    private val geminiClient: GeminiClient = GeminiClient(),
    private val configManager: ConfigManager = ConfigManager(),
    private val cloudAssetManager: CloudAssetManager = CloudAssetManager(configManager)
) {
    private val TAG = "GeminiChatEngine"
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }

    /**
     * 解析生效的 API Key (优先显式入参，其次配置中心，最后全局环境变量)
     */
    suspend fun resolveEffectiveApiKey(explicitKey: String? = null): String {
        return explicitKey?.ifBlank { null }
            ?: configManager.loadKey("GEMINI_API_KEY")?.ifBlank { null }
            ?: configManager.loadGlobalGeminiKey()?.ifBlank { null }
            ?: ""
    }

    /**
     * 解析生效的 API Base URL
     */
    suspend fun resolveEffectiveBaseUrl(explicitUrl: String? = null): String {
        val raw = explicitUrl?.ifBlank { null }
            ?: configManager.loadKey("GEMINI_BASE_URL")?.ifBlank { null }
            ?: "https://generativelanguage.googleapis.com"
        return raw.trimEnd('/')
    }

    /**
     * 一次性执行聊天推理 (底层复用 [streamChat])
     */
    suspend fun executeChat(
        request: GeminiChatRequest,
        onChunk: (String) -> Unit = {},
        onStatusLog: (String) -> Unit = {},
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)? = null
    ): GeminiChatResult {
        return streamChat(
            request = request,
            onChunk = onChunk,
            onStatusLog = onStatusLog,
            onRawTraffic = onRawTraffic
        )
    }

    /**
     * 核心流式对话管道 (Stream Chat)
     *
     * @param request 请求参数对象
     * @param onChunk 流式文本片段回调 (用于打字机效果)
     * @param onThought 思维链思考过程回调
     * @param onStatusLog 阶段状态进度日志
     * @param onRawTraffic 网络层原始报文拦截归档回调
     * @return 最终聚合的 [GeminiChatResult]
     */
    suspend fun streamChat(
        request: GeminiChatRequest,
        onChunk: (String) -> Unit = {},
        onThought: ((thought: String, signature: String?) -> Unit)? = null,
        onStatusLog: (String) -> Unit = {},
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)? = null
    ): GeminiChatResult {
        val apiKey = resolveEffectiveApiKey(request.apiKey)
        if (apiKey.isBlank()) {
            return GeminiChatResult(
                text = "",
                isSuccess = false,
                errorMessage = "未配置 Gemini API Key，请先在设置中填写有效密钥"
            )
        }
        val baseUrl = resolveEffectiveBaseUrl(request.baseUrl)

        // 1. 处理附件上传：若开启 preferCloudUpload 则优先通过 Google File API 同步
        val processedAttachments = processAttachments(request.attachments, request.preferCloudUpload, apiKey, onStatusLog)

        return when (request.apiFlavor) {
            ApiFlavor.GENERATE_CONTENT -> {
                executeGenerateContentStream(
                    request = request.copy(apiKey = apiKey, baseUrl = baseUrl, attachments = processedAttachments),
                    onChunk = onChunk,
                    onThought = onThought,
                    onStatusLog = onStatusLog,
                    onRawTraffic = onRawTraffic
                )
            }
            ApiFlavor.INTERACTIONS -> {
                executeInteractionsStream(
                    request = request.copy(apiKey = apiKey, baseUrl = baseUrl, attachments = processedAttachments),
                    onChunk = onChunk,
                    onThought = onThought,
                    onStatusLog = onStatusLog,
                    onRawTraffic = onRawTraffic
                )
            }
        }
    }

    /**
     * 非流式执行纯文本生成推理 (如 MCP 工具、快速总结等)
     */
    suspend fun executeText(
        request: GeminiChatRequest,
        onStatusLog: (String) -> Unit = {},
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)? = null
    ): GeminiChatResult {
        val stringBuilder = StringBuilder()
        var capturedThought: String? = null
        var capturedSig: String? = null

        val result = streamChat(
            request = request,
            onChunk = { stringBuilder.append(it) },
            onThought = { thought, sig ->
                capturedThought = thought
                capturedSig = sig
            },
            onStatusLog = onStatusLog,
            onRawTraffic = onRawTraffic
        )

        return result.copy(
            text = stringBuilder.toString(),
            thoughtText = capturedThought ?: result.thoughtText,
            thoughtSignature = capturedSig ?: result.thoughtSignature
        )
    }

    /**
     * 执行结构化 JSON 推理生成 (如资产表提取、图元分类)，自动校验并解析为 [JsonObject]
     */
    suspend fun executeStructuredJson(
        request: GeminiChatRequest,
        onStatusLog: (String) -> Unit = {},
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)? = null
    ): Pair<String, JsonObject?> {
        val updatedConfig = request.generationConfig.copy(
            responseMimeType = "application/json"
        )
        val result = executeText(
            request = request.copy(generationConfig = updatedConfig),
            onStatusLog = onStatusLog,
            onRawTraffic = onRawTraffic
        )
        if (!result.isSuccess || result.text.isBlank()) {
            return result.text to null
        }
        val cleanText = geminiClient.cleanJson(result.text)
        return try {
            val jsonObject = jsonParser.parseToJsonElement(cleanText).jsonObject
            cleanText to jsonObject
        } catch (_: Exception) {
            cleanText to null
        }
    }

    /**
     * 附件预处理与 Google File API 上传管道
     */
    private suspend fun processAttachments(
        attachments: List<GeminiChatAttachment>,
        preferCloudUpload: Boolean,
        apiKey: String,
        onStatusLog: (String) -> Unit
    ): List<GeminiChatAttachment> {
        if (attachments.isEmpty()) return emptyList()

        return attachments.map { attach ->
            if (attach.fileUri != null) {
                // 已有云端 URI，直接复用
                attach.copy(isCloudUploaded = true)
            } else if (preferCloudUpload && attach.bytes != null) {
                onStatusLog("☁️ 正在通过 Google File API 同步附件: ${attach.name}...")
                val uploadedUri = try {
                    cloudAssetManager.getOrUploadFile(
                        displayName = attach.name,
                        fileBytes = attach.bytes,
                        mimeType = attach.mimeType
                    ) { _, status -> onStatusLog("CloudAsset [${attach.name}]: $status") }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "云端附件上传失败，降级为 Base64: ${e.message}")
                    null
                }

                if (uploadedUri != null) {
                    onStatusLog("✅ 附件 [${attach.name}] 云端同步完成")
                    attach.copy(fileUri = uploadedUri, isCloudUploaded = true)
                } else {
                    onStatusLog("⚠️ 附件 [${attach.name}] 云端同步受限，降级 Base64 传输")
                    attach.copy(isCloudUploaded = false)
                }
            } else {
                attach
            }
        }
    }

    /**
     * generateContent 官方协议流式构建与执行
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun executeGenerateContentStream(
        request: GeminiChatRequest,
        onChunk: (String) -> Unit,
        onThought: ((String, String?) -> Unit)?,
        onStatusLog: (String) -> Unit,
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)?
    ): GeminiChatResult {
        val url = "${request.baseUrl}/v1beta/models/${request.model.modelName}:streamGenerateContent?key=${request.apiKey}&alt=sse"

        val requestJson = buildJsonObject {
            // 1. 官方规范：独立 systemInstruction
            if (!request.systemInstruction.isNullOrBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", request.systemInstruction) })
                    })
                })
            }

            // 2. 组装 contents（历史上下文 + 当前轮次提问与附件）
            put("contents", buildJsonArray {
                // 历史对话多轮
                request.history.forEach { historyItem ->
                    add(buildJsonObject {
                        put("role", historyItem.role)
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", historyItem.text) })
                            historyItem.attachments.forEach { attach ->
                                addAttachmentPart(attach)
                            }
                        })
                    })
                }

                // 当前轮次用户输入
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", request.effectivePrompt) })
                        request.attachments.forEach { attach ->
                            addAttachmentPart(attach)
                        }
                    })
                })
            })

            // 3. 官方全量 generationConfig 参数映射
            val genConfig = request.generationConfig
            val genConfigObj = buildJsonObject {
                genConfig.temperature?.let { put("temperature", it) }
                genConfig.topP?.let { put("topP", it) }
                genConfig.topK?.let { put("topK", it) }
                genConfig.candidateCount?.let { put("candidateCount", it) }
                genConfig.maxOutputTokens?.let { put("maxOutputTokens", it) }
                genConfig.responseMimeType?.let { put("responseMimeType", it) }
                genConfig.responseSchema?.let { put("responseSchema", it) }
                genConfig.presencePenalty?.let { put("presencePenalty", it) }
                genConfig.frequencyPenalty?.let { put("frequencyPenalty", it) }
                genConfig.stopSequences?.let { seqs ->
                    put("stopSequences", buildJsonArray { seqs.forEach { add(JsonPrimitive(it)) } })
                }
                genConfig.responseModalities?.let { modalities ->
                    put("responseModalities", buildJsonArray { modalities.forEach { add(JsonPrimitive(it)) } })
                }
                genConfig.speechConfig?.let { put("speechConfig", it) }
                genConfig.thinkingConfig?.let { tc ->
                    put("thinkingConfig", buildJsonObject {
                        tc.thinkingBudget?.let { put("thinkingBudget", it) }
                        tc.thinkingLevel?.let { put("thinkingLevel", it) }
                    })
                }
            }
            if (genConfigObj.isNotEmpty()) {
                put("generationConfig", genConfigObj)
            }

            // 4. Tools & ToolConfig
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { t ->
                        t.functionDeclarations?.let { fns ->
                            add(buildJsonObject {
                                put("functionDeclarations", buildJsonArray { fns.forEach { add(it) } })
                            })
                        }
                        t.googleSearch?.let { add(buildJsonObject { put("googleSearch", it) }) }
                        t.codeExecution?.let { add(buildJsonObject { put("codeExecution", it) }) }
                    }
                })
            }

            // 5. SafetySettings
            if (request.safetySettings.isNotEmpty()) {
                put("safetySettings", buildJsonArray {
                    request.safetySettings.forEach { ss ->
                        add(buildJsonObject {
                            put("category", ss.category)
                            put("threshold", ss.threshold)
                        })
                    }
                })
            }
        }.toString()

        onStatusLog("📡 [generateContent] 建立流连接 (${request.model.modelName})...")
        val fullTextBuilder = StringBuilder()
        var lastThought: String? = null
        var lastSig: String? = null
        var promptTokens: Int? = null
        var candidatesTokens: Int? = null
        var totalTokens: Int? = null
        val startTime = getCurrentTimeMillis()

        return try {
            geminiClient.streamGenerateContent(
                url = url,
                requestBody = requestJson,
                onLog = { onStatusLog(it) },
                onRawData = { element ->
                    if (element is JsonObject) {
                        val errObj = element["error"]?.jsonObject
                        if (errObj != null) {
                            val msg = errObj["message"]?.jsonPrimitive?.contentOrNull ?: "流式传输异常"
                            throw IllegalStateException(msg)
                        }
                    }
                    val (thought, sig) = geminiClient.extractThoughtInfo(element)
                    if (thought != null || sig != null) {
                        lastThought = (lastThought ?: "") + (thought ?: "")
                        if (sig != null) lastSig = sig
                        onThought?.invoke(lastThought ?: "", lastSig)
                    }
                    if (element is JsonObject) {
                        val usage = element["usageMetadata"]?.jsonObject
                        if (usage != null) {
                            usage["promptTokenCount"]?.jsonPrimitive?.intOrNull?.let { promptTokens = it }
                            usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull?.let { candidatesTokens = it }
                            usage["totalTokenCount"]?.jsonPrimitive?.intOrNull?.let { totalTokens = it }
                        }
                    }
                },
                onChunk = { chunk ->
                    fullTextBuilder.append(chunk)
                    onChunk(chunk)
                },
                onRawTraffic = onRawTraffic
            )

            val durationMs = getCurrentTimeMillis() - startTime
            val resultText = fullTextBuilder.toString()
            if (resultText.isBlank() && lastThought.isNullOrBlank()) {
                return GeminiChatResult(
                    text = "",
                    modelName = request.model.displayName,
                    durationMs = durationMs,
                    isSuccess = false,
                    errorMessage = "未收到有效回复内容，请重试"
                )
            }

            GeminiChatResult(
                text = resultText,
                thoughtText = lastThought,
                thoughtSignature = lastSig,
                modelName = request.model.displayName,
                durationMs = durationMs,
                promptTokens = promptTokens,
                candidatesTokens = candidatesTokens,
                totalTokens = totalTokens ?: if (promptTokens != null || candidatesTokens != null) (promptTokens ?: 0) + (candidatesTokens ?: 0) else null,
                isSuccess = true
            )
        } catch (e: Exception) {
            val durationMs = getCurrentTimeMillis() - startTime
            AppLogger.e(TAG, "generateContent 流式执行失败", e)
            GeminiChatResult(
                text = fullTextBuilder.toString(),
                modelName = request.model.displayName,
                durationMs = durationMs,
                isSuccess = false,
                errorMessage = e.message ?: "网络通信异常"
            )
        }
    }

    /**
     * Interactions API 官方协议执行
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun executeInteractionsStream(
        request: GeminiChatRequest,
        onChunk: (String) -> Unit,
        onThought: ((String, String?) -> Unit)?,
        onStatusLog: (String) -> Unit,
        onRawTraffic: ((direction: TrafficDirection, url: String, body: String) -> Unit)?
    ): GeminiChatResult {
        val url = "${request.baseUrl}/v1beta/interactions?key=${request.apiKey}"

        val requestJson = buildJsonObject {
            put("model", request.model.modelName)

            // 1. 官方规范：独立 system_instruction
            if (!request.systemInstruction.isNullOrBlank()) {
                put("system_instruction", request.systemInstruction)
            }

            // 2. 链式上下文继承 ID
            if (!request.previousInteractionId.isNullOrBlank()) {
                put("previous_interaction_id", request.previousInteractionId)
            }

            // 3. 官方输入项规范 (input: text / image / file)
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", request.effectivePrompt)
                })

                request.attachments.forEach { attach ->
                    if (!attach.fileUri.isNullOrBlank()) {
                        add(buildJsonObject {
                            put("type", "file")
                            put("file_uri", attach.fileUri)
                            put("mime_type", attach.mimeType)
                        })
                    } else if (attach.bytes != null) {
                        add(buildJsonObject {
                            put("type", "image")
                            put("data", Base64.encode(attach.bytes))
                            put("mime_type", attach.mimeType)
                        })
                    }
                }
            })

            // 4. generation_config
            val genConfig = request.generationConfig
            put("generation_config", buildJsonObject {
                genConfig.temperature?.let { put("temperature", it) }
                genConfig.topP?.let { put("top_p", it) }
                genConfig.topK?.let { put("top_k", it) }
                genConfig.maxOutputTokens?.let { put("max_output_tokens", it) }
                genConfig.thinkingConfig?.thinkingLevel?.let { put("thinking_level", it.lowercase()) }
            })

            put("response_modalities", buildJsonArray {
                add(JsonPrimitive("text"))
            })
        }.toString()

        onStatusLog("⚡ [Interactions API] 发起请求 (${request.model.modelName})...")
        val startTime = getCurrentTimeMillis()

        return try {
            val responseString = geminiClient.postInteraction(
                url = url,
                requestBody = requestJson,
                onLog = { onStatusLog(it) },
                onRawTraffic = onRawTraffic
            )

            val durationMs = getCurrentTimeMillis() - startTime

            // 解析 Interactions API 返回结构并动态投影提取展示内容
            val respJson = jsonParser.parseToJsonElement(responseString).jsonObject
            val errorObj = respJson["error"]?.jsonObject
            if (errorObj != null) {
                val errorMsg = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "未知接口错误"
                val errorCode = errorObj["code"]?.jsonPrimitive?.contentOrNull ?: "error"
                val status = errorObj["status"]?.jsonPrimitive?.contentOrNull
                val fullErrorDesc = if (!status.isNullOrBlank() && status != errorCode) {
                    "[$errorCode / $status] $errorMsg"
                } else {
                    "[$errorCode] $errorMsg"
                }
                val durationMsErr = getCurrentTimeMillis() - startTime
                AppLogger.e(TAG, "Interactions API 响应错误: $fullErrorDesc")
                return GeminiChatResult(
                    text = "",
                    modelName = request.model.displayName,
                    durationMs = durationMsErr,
                    isSuccess = false,
                    errorMessage = fullErrorDesc,
                    rawResponseJson = responseString
                )
            }

            val interactionId = respJson["id"]?.jsonPrimitive?.contentOrNull
                ?: respJson["interaction_id"]?.jsonPrimitive?.contentOrNull

            val usageObj = respJson["usage_metadata"]?.jsonObject ?: respJson["usageMetadata"]?.jsonObject
            val promptTokens = usageObj?.get("input_tokens")?.jsonPrimitive?.intOrNull
                ?: usageObj?.get("promptTokenCount")?.jsonPrimitive?.intOrNull
            val candidatesTokens = usageObj?.get("output_tokens")?.jsonPrimitive?.intOrNull
                ?: usageObj?.get("candidatesTokenCount")?.jsonPrimitive?.intOrNull
            val totalTokens = usageObj?.get("total_tokens")?.jsonPrimitive?.intOrNull
                ?: usageObj?.get("totalTokenCount")?.jsonPrimitive?.intOrNull
                ?: if (promptTokens != null || candidatesTokens != null) (promptTokens ?: 0) + (candidatesTokens ?: 0) else null

            val projected = ChatPayloadViewProjector.projectDisplayContent(responseString)
            val extractedText = projected.text

            if (projected.thoughtText != null || projected.thoughtSignature != null) {
                onThought?.invoke(projected.thoughtText ?: "", projected.thoughtSignature)
            }

            // 模拟打字机回调给 UI
            if (extractedText.isNotBlank()) {
                onChunk(extractedText)
            }

            GeminiChatResult(
                text = extractedText,
                thoughtText = projected.thoughtText,
                thoughtSignature = projected.thoughtSignature,
                interactionId = interactionId,
                rawResponseJson = responseString,
                modelName = request.model.displayName,
                durationMs = durationMs,
                promptTokens = promptTokens,
                candidatesTokens = candidatesTokens,
                totalTokens = totalTokens,
                isSuccess = true
            )
        } catch (e: Exception) {
            val durationMs = getCurrentTimeMillis() - startTime
            AppLogger.e(TAG, "Interactions API 执行失败", e)
            GeminiChatResult(
                text = "",
                modelName = request.model.displayName,
                durationMs = durationMs,
                isSuccess = false,
                errorMessage = e.message ?: "网络通信异常"
            )
        }
    }

    /**
     * 将附件加入 generateContent 的 Part
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun JsonArrayBuilder.addAttachmentPart(attach: GeminiChatAttachment) {
        if (!attach.fileUri.isNullOrBlank()) {
            add(buildJsonObject {
                put("fileData", buildJsonObject {
                    put("mimeType", attach.mimeType)
                    put("fileUri", attach.fileUri)
                })
            })
        } else if (attach.bytes != null) {
            add(buildJsonObject {
                put("inlineData", buildJsonObject {
                    put("mimeType", attach.mimeType)
                    put("data", Base64.encode(attach.bytes))
                })
            })
        }
    }
}

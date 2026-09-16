package org.gemini.ui.forge.service

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.manager.*
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.service.ai.UnifiedAiResult
import org.gemini.ui.forge.utils.looseJson
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.transcodeForUpload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 统一的 Gemini 图像生成调度中枢
 *
 * 架构规范：
 * - 业务逻辑与提示词组装 100% 公用，绝无分支分叉；
 * - 底层通信根据 [ApiFlavor] 采用两套独立的适配器驱动：
 *   1. [generateWithGenerateContent]：传统流式 generateContent 协议栈；
 *   2. [generateWithInteractionsApi]：新一代 Interactions API 协议栈 (支持 previous_interaction_id 链式免传历史参考图)；
 * - 两种协议对上层程序接受与反馈的数据结构完全同构一致。
 */
class GeminiImageGenerator(
    private val cloudAssetManager: CloudAssetManager,
    private val promptManager: PromptManager,
    private val geminiClient: GeminiClient,
    private val configManager: ConfigManager = ConfigManager()
) : BaseImageGenerator() {

    private val TAG = "GeminiImageGenerator"

    /**
     * 核心统一生图入口
     *
     * @param model 目标模型名称
     * @param params 统一生图配置参数
     * @param onLog 实时阶段日志回调
     * @param onImageGenerated 图像单张生成回调
     * @param onRawTraffic 原始报文监控回调
     * @param apiFlavorOverride 可选协议覆盖，为 null 时自动从全局设置中读取
     * @param onResult 统一结果对象回调 (包含 interactionId 与思考链信息)
     * @return 生成的图片 Data URI 列表
     */
    suspend fun generate(
        model: String,
        params: GenParams,
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit = {},
        onRawTraffic: ((TrafficDirection, String, String) -> Unit)? = null,
        apiFlavorOverride: ApiFlavor? = null,
        onResult: ((UnifiedAiResult) -> Unit)? = null
    ): List<String> {
        val effectiveFlavor = apiFlavorOverride ?: run {
            val flavorStr = configManager.loadKey("API_FLAVOR") ?: "INTERACTIONS"
            try {
                ApiFlavor.valueOf(flavorStr)
            } catch (_: Exception) {
                ApiFlavor.INTERACTIONS
            }
        }

        // 1. 公用提示词组装与比例计算
        val (fullPrompt, aspectRatio) = buildCommonPrompt(params)

        // 2. 双栈适配器分流调用
        return when (effectiveFlavor) {
            ApiFlavor.GENERATE_CONTENT -> {
                generateWithGenerateContent(
                    model = model,
                    params = params,
                    fullPrompt = fullPrompt,
                    onLog = onLog,
                    onImageGenerated = onImageGenerated,
                    onRawTraffic = onRawTraffic,
                    onResult = onResult
                )
            }
            ApiFlavor.INTERACTIONS -> {
                generateWithInteractionsApi(
                    model = model,
                    params = params,
                    fullPrompt = fullPrompt,
                    aspectRatio = aspectRatio,
                    onLog = onLog,
                    onImageGenerated = onImageGenerated,
                    onRawTraffic = onRawTraffic,
                    onResult = onResult
                )
            }
        }
    }

    /**
     * 公用逻辑：组装提示词模板与比例
     */
    private suspend fun buildCommonPrompt(params: GenParams): Pair<String, String> {
        val stylePart = if (params.style.isNotBlank()) {
            "Style: ${params.style}."
        } else {
            ""
        }

        val aspectRatio = calculateAspectRatio(params.targetWidth, params.targetHeight)
        val template = promptManager.getPrompt("gemini_image_gen")
        val fullPrompt = template
            .replace("{0}", params.blockType)
            .replace("{1}", stylePart)
            .replace("{2}", params.userPrompt)
            .replace("{3}", aspectRatio)

        return fullPrompt to aspectRatio
    }

    /**
     * 适配器栈 1: 传统 generateContent / streamGenerateContent
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun generateWithGenerateContent(
        model: String,
        params: GenParams,
        fullPrompt: String,
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit,
        onRawTraffic: ((TrafficDirection, String, String) -> Unit)?,
        onResult: ((UnifiedAiResult) -> Unit)?
    ): List<String> {
        val url = ApiConfig.getStreamGenerateContentEndpoint(params.apiKey, model)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", fullPrompt)
                        })

                        if (!params.referenceImageUri.isNullOrBlank()) {
                            val rawBytes = readLocalFileBytes(params.referenceImageUri)
                            if (rawBytes != null && rawBytes.isNotEmpty()) {
                                val transcodeRes = transcodeForUpload(rawBytes)
                                val finalBytes = transcodeRes.bytes
                                val mime = transcodeRes.mimeType

                                val imagePart = buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", mime)
                                        put("data", Base64.encode(finalBytes))
                                    })
                                }
                                add(imagePart)
                                syncLog(TAG, "📎 已将参考底图转码优化打包至请求 (格式: $mime, 体积: ${finalBytes.size / 1024} KB)", onLog)
                            } else {
                                syncLog(TAG, "⚠️ 未能读取到参考图本地文件: ${params.referenceImageUri}", onLog)
                            }
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                if (params.thinkingConfigJson != null) {
                    put("thinkingConfig", params.thinkingConfigJson)
                }
            })
        }.toString()

        syncLog(TAG, "📡 [generateContent] 正在向 Gemini 建立生图流连接 ($model)...", onLog)

        val allImages = mutableListOf<String>()
        val textBuilder = StringBuilder()
        var thoughtText: String? = null
        var thoughtSignature: String? = null

        try {
            geminiClient.streamGenerateContent(
                url = url,
                requestBody = requestBody,
                onLog = onLog,
                onRawTraffic = onRawTraffic,
                onRawData = { jsonElement ->
                    try {
                        val (tText, tSig) = geminiClient.extractThoughtInfo(jsonElement)
                        if (!tText.isNullOrBlank()) thoughtText = (thoughtText ?: "") + tText
                        if (!tSig.isNullOrBlank()) thoughtSignature = tSig

                        val candidates = jsonElement.jsonObject["candidates"]?.jsonArray
                        val parts = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray

                        parts?.forEach { part ->
                            val textChunk = part.jsonObject["text"]?.jsonPrimitive?.content
                            if (!textChunk.isNullOrEmpty()) {
                                textBuilder.append(textChunk)
                                syncLog(TAG, "💬 AI: $textChunk", onLog)
                            }

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

            val unifiedResult = UnifiedAiResult(
                imageUris = allImages,
                outputText = textBuilder.toString(),
                interactionId = null,
                thoughtText = thoughtText,
                thoughtSignature = thoughtSignature
            )
            onResult?.invoke(unifiedResult)

            return allImages
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 适配器栈 2: Google 新一代 Interactions API 协议驱动
     *
     * 依据 Google 官方 2026 最新 Cookbook 实证规范组装载荷与解析响应
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun generateWithInteractionsApi(
        model: String,
        params: GenParams,
        fullPrompt: String,
        aspectRatio: String,
        onLog: (String) -> Unit,
        onImageGenerated: (String) -> Unit,
        onRawTraffic: ((TrafficDirection, String, String) -> Unit)?,
        onResult: ((UnifiedAiResult) -> Unit)?
    ): List<String> {
        val url = ApiConfig.getInteractionsEndpoint(params.apiKey)

        val requestBody = buildJsonObject {
            put("model", model)

            put("input", buildJsonArray {
                // 1. 文本提示词输入项
                add(buildJsonObject {
                    put("type", "text")
                    put("text", fullPrompt)
                })

                // 2. 多模态图片输入项 (当未继承上一轮且存在参考图时上传；若有 previous_interaction_id 则服务端已保活，自动免传)
                if (params.previousInteractionId.isNullOrBlank() && !params.referenceImageUri.isNullOrBlank()) {
                    val rawBytes = readLocalFileBytes(params.referenceImageUri)
                    if (rawBytes != null && rawBytes.isNotEmpty()) {
                        val transcodeRes = transcodeForUpload(rawBytes)
                        val finalBytes = transcodeRes.bytes
                        val mime = transcodeRes.mimeType

                        add(buildJsonObject {
                            put("type", "image")
                            put("data", Base64.encode(finalBytes))
                            put("mime_type", mime)
                        })
                        syncLog(TAG, "📎 [Interactions API] 注入参考图多模态载荷 (格式: $mime, 体积: ${finalBytes.size / 1024} KB)", onLog)
                    } else {
                        syncLog(TAG, "⚠️ 未能读取到参考图本地文件: ${params.referenceImageUri}", onLog)
                    }
                } else if (!params.previousInteractionId.isNullOrBlank()) {
                    syncLog(TAG, "🔗 [Interactions API] 继承上一轮会话 (${params.previousInteractionId})，自动跳过底图重复上传", onLog)
                }
            })

            // 3. 链式引用上一轮 interaction ID
            if (!params.previousInteractionId.isNullOrBlank()) {
                put("previous_interaction_id", params.previousInteractionId)
            }

            // 4. 生成控制与尺寸比例配置
            put("generation_config", buildJsonObject {
                // 思考级别配置 (low / medium / high)
                val thinkingLevel = params.thinkingConfigJson?.get("thinkingLevel")?.jsonPrimitive?.content
                    ?: if (model.contains("gemini-3") || model.contains("nano-banana")) "high" else null
                if (!thinkingLevel.isNullOrBlank()) {
                    put("thinking_level", thinkingLevel.lowercase())
                }

                // 图像尺寸与比例
                put("image_generation_config", buildJsonObject {
                    put("aspect_ratio", aspectRatio)
                    put("image_size", params.imageSize.uppercase())
                })
            })

            // 5. 期望输出模态: 文本与图像
            put("response_modalities", buildJsonArray {
                add("text")
                add("image")
            })
        }.toString()

        syncLog(TAG, "⚡ [Interactions API] 正在调用 Google 统一交互接口 ($model)...", onLog)

        val allImages = mutableListOf<String>()

        try {
            val responseText = geminiClient.postInteraction(
                url = url,
                requestBody = requestBody,
                onLog = onLog,
                onRawTraffic = onRawTraffic
            )

            val rootJson = looseJson.parseToJsonElement(responseText).jsonObject

            // 1. 提取交互 ID
            val interactionId = rootJson["id"]?.jsonPrimitive?.contentOrNull
            if (!interactionId.isNullOrBlank()) {
                syncLog(TAG, "🆔 已捕获本轮交互 ID: $interactionId (就绪链式多轮)", onLog)
            }

            // 2. 提取文本正文
            val outputText = rootJson["output_text"]?.jsonPrimitive?.contentOrNull ?: ""
            if (outputText.isNotBlank()) {
                syncLog(TAG, "💬 AI: $outputText", onLog)
            }

            // 3. 提取图像
            val outputImage = rootJson["output_image"]?.jsonObject
            val imgData = outputImage?.get("data")?.jsonPrimitive?.contentOrNull
            val mimeType = outputImage?.get("mime_type")?.jsonPrimitive?.contentOrNull ?: "image/png"

            if (!imgData.isNullOrBlank()) {
                syncLog(TAG, "🖼️ [Interactions API] 成功接收到生成图像数据", onLog)
                val dataUri = "data:$mimeType;base64,$imgData"
                allImages.add(dataUri)
                onImageGenerated(dataUri)
            }

            // 4. 提取思考链 steps
            var thoughtText: String? = null
            var thoughtSignature: String? = null
            rootJson["steps"]?.jsonArray?.forEach { stepElement ->
                val stepObj = stepElement.jsonObject
                if (stepObj["type"]?.jsonPrimitive?.contentOrNull == "thought") {
                    val summary = stepObj["summary"]?.jsonPrimitive?.contentOrNull
                    if (!summary.isNullOrBlank()) {
                        thoughtText = (thoughtText ?: "") + summary
                    }
                    val sig = stepObj["thought_signature"]?.jsonPrimitive?.contentOrNull
                    if (!sig.isNullOrBlank()) {
                        thoughtSignature = sig
                    }
                }
            }

            val unifiedResult = UnifiedAiResult(
                imageUris = allImages,
                outputText = outputText,
                interactionId = interactionId,
                thoughtText = thoughtText,
                thoughtSignature = thoughtSignature,
                rawResponseJson = responseText
            )
            onResult?.invoke(unifiedResult)

            return allImages
        } catch (e: Exception) {
            throw e
        }
    }
}

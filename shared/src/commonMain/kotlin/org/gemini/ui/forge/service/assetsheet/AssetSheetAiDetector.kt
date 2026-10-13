package org.gemini.ui.forge.service.assetsheet

import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.model.assetsheet.AssetSheetParseResult
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.getMimeType
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * 纯图 SpriteSheet AI 视觉多模态逆向识别引擎。
 *
 * 当用户未提供任何坐标配置文件时，将原始图集提交给 Gemini 视觉大模型进行全景目标检测，
 * 自动识别图集中散布的各个独立图元组件，推导语义分类与紧致外切坐标，
 * 并自动衔接至 [AssetSheetNormalizer] 进行清洗规范化，物理保存为 detected_coords.json。
 */
object AssetSheetAiDetector {

    private const val TAG = "AssetSheetAiDetector"

    /**
     * 对给定的图集图片执行 AI 视觉目标检测与坐标识别。
     *
     * @param imagePath 本地绝对路径、网络 URL 或 base64 Data URI
     * @param apiKey 可选的 Gemini API Key，缺省时自动从本地配置中读取
     * @param projectName 可选的目标工程名，提供时将坐标 JSON 物理落盘至模板目录
     * @param onProgress 进度与阶段日志回调 (0.0f ~ 1.0f, message)
     * @return 包含画布尺寸与标准化图元列表的解析结果以及原始 JSON 文本
     */
    suspend fun detectAssetSheet(
        imagePath: String,
        apiKey: String? = null,
        projectName: String? = null,
        onProgress: ((progress: Float, message: String) -> Unit)? = null
    ): Pair<AssetSheetParseResult, String> {
        onProgress?.invoke(0.05f, "正在读取图集图片数据...")

        // 1. 读取图片字节与 MIME
        val (imageBytes, mimeType) = readImageBytesAndMime(imagePath)
            ?: throw IllegalArgumentException("无法读取指定路径的图片资源: $imagePath")

        onProgress?.invoke(0.15f, "正在校验 API 密钥与大模型连接...")

        // 2. 准备 API 密钥
        val configManager = ConfigManager()
        val effectiveApiKey = apiKey?.ifBlank { null }
            ?: configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: throw IllegalStateException("未检测到有效的 Gemini API Key，请在应用设置中配置或在入参中提供")

        // 3. 准备提示词与上下文
        val storage = LocalFileStorage()
        val promptManager = PromptManager(storage)
        val basePrompt = try {
            promptManager.getPrompt("detect_asset_sheet_coords")
        } catch (_: Exception) {
            "Please detect all discrete UI elements, buttons, symbols, icons, badges in this SpriteSheet image and output a JSON with canvas size [w, h] and elements array with [id, name, type, bounds: [x,y,w,h], description]."
        }

        onProgress?.invoke(0.25f, "正在上传图集视觉资源到云端多模态分析网关...")

        val cloudAssetManager = CloudAssetManager(configManager)
        val fileName = imagePath.substringAfterLast("/").substringAfterLast("\\").ifBlank { "sprite_sheet.png" }

        val imagePart = cloudAssetManager.buildGeminiImagePart(
            displayName = fileName,
            bytes = imageBytes,
            mimeType = mimeType,
            onLog = { logMsg -> onProgress?.invoke(0.35f, "资源准备: $logMsg") }
        )

        // 4. 组装请求报文
        val partsArray = buildJsonArray {
            add(buildJsonObject { put("text", basePrompt) })
            add(imagePart)
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
                put("temperature", 0.2) // 低采样温度确保坐标与边界预测的精准与严密
            })
        }.toString()

        onProgress?.invoke(0.45f, "📡 正在请求 Gemini 视觉大模型执行图元目标检测与坐标识别...")

        val geminiClient = GeminiClient()
        val url = ApiConfig.getStreamGenerateContentEndpoint(effectiveApiKey)
        val accumulatedResponse = StringBuilder()

        try {
            geminiClient.streamGenerateContent(
                url = url,
                requestBody = requestBody,
                onLog = { logMsg -> onProgress?.invoke(0.60f, "模型分析中: $logMsg") },
                onChunk = { chunk -> accumulatedResponse.append(chunk) }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Gemini 目标检测通信失败", e)
            throw IllegalStateException("AI 视觉目标检测通信失败: ${e.message}", e)
        }

        val rawText = accumulatedResponse.toString().trim()
        if (rawText.isBlank()) {
            throw IllegalStateException("Gemini 视觉大模型返回了空响应")
        }

        onProgress?.invoke(0.80f, "正在清洗与结构化自适应提取图元数据...")
        val cleanJson = geminiClient.cleanJson(rawText)

        // 5. 调用自适应解析引擎进行标准化清洗与自愈
        val parseResult = AssetSheetNormalizer.normalizeJsonOffline(cleanJson)
        if (parseResult.items.isEmpty()) {
            throw IllegalStateException("AI 识别完成但未能提取出任何有效图元，原始输出: ${cleanJson.take(200)}")
        }

        onProgress?.invoke(0.90f, "已识别出 ${parseResult.items.size} 个图元组件，正在持久化归档...")

        // 6. 物理落盘留痕
        if (!projectName.isNullOrBlank()) {
            try {
                val sanitizedName = projectName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetDir = TemplateFile("templates/$sanitizedName")
                if (targetDir.exists()) {
                    TemplateFile("templates/$sanitizedName/detected_coords.json").writeBytes(cleanJson.encodeToByteArray())
                    AppLogger.i(TAG, "已将 AI 识别坐标保存到工程目录: templates/$sanitizedName/detected_coords.json")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "保存 detected_coords.json 失败: ${e.message}")
            }
        }

        onProgress?.invoke(1.0f, "✅ AI 视觉识别完成！成功捕获 ${parseResult.items.size} 个图元")
        return Pair(parseResult, cleanJson)
    }

    /**
     * 辅助函数：根据路径模式读取图片原始字节与 MIME 类型
     */
    private suspend fun readImageBytesAndMime(uri: String): Pair<ByteArray, String>? {
        return when {
            uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
                val client = NetworkClient.shared
                val response = client.get(uri)
                val bytes = response.readRawBytes()
                val mime = response.headers[io.ktor.http.HttpHeaders.ContentType] ?: getMimeType(uri)
                bytes to mime
            }
            uri.startsWith("data:image", ignoreCase = true) -> {
                val b64 = if (uri.contains(",")) uri.substringAfter(",") else uri
                val decoded = kotlin.io.encoding.Base64.decode(b64)
                val inferredMime = if (uri.contains(";")) uri.substringBefore(";").removePrefix("data:") else "image/png"
                decoded to inferredMime
            }
            else -> {
                val fileBytes = readLocalFileBytes(uri) ?: return null
                val fileMime = getMimeType(uri)
                fileBytes to fileMime
            }
        }
    }
}

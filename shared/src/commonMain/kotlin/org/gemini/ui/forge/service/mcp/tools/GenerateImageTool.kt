package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpContent
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 通用独立文生图工具 (Text-to-Image Tool)
 *
 * 允许大模型（如文本模型 Gemini 3.8 Flash 或生图模型）在识别出用户的生图意图时，
 * 自主通过 Function Calling 调起本工具执行独立文本生图并保存落盘。
 */
class GenerateImageTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "generate_image"

    override val description: String =
        "执行通用独立文生图（Text-to-Image）任务。根据用户的自然语言视觉描述生成高品质图片，支持画面宽高比（1:1、16:9 等）与透明背景去背，自动落盘并回传图像。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 120_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("prompt", buildJsonObject {
                put("type", "string")
                put("description", "要生成的画面的详细视觉描述提示词（例如：'一只趴在窗台上晒太阳的可爱小猫，温暖毛茸茸，高画质'）")
            })
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "可选：保存的目标项目工程名称。若未指定将自动归入当前活跃工程或通用资产目录")
            })
            put("aspectRatio", buildJsonObject {
                put("type", "string")
                put("description", "可选：画面宽高比。支持 '1:1', '16:9', '9:16', '4:3', '3:4'，默认为 '1:1'")
            })
            put("isPng", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否自动通过本地 AI 算法进行透明抠图去背，默认为 false。对图标、独立角色、小动物切片建议设为 true")
            })
            put("modelName", buildJsonObject {
                put("type", "string")
                put("description", "可选：调用的生图模型代号。若系统设置了默认生图模型预设，将自动强制锁定为预设模型")
            })
        })
        put("required", buildJsonArray {
            add("prompt")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val prompt = arguments["prompt"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'prompt' 不能为空")

        val explicitProject = arguments["projectName"]?.jsonPrimitive?.contentOrNull
        val activeProject = McpUiActionPipeline.getActiveViewModel()?.state?.value?.projectName?.takeIf { it.isNotBlank() }
        val projectName = explicitProject?.takeIf { it.isNotBlank() } ?: activeProject ?: "_common"

        val aspectRatio = arguments["aspectRatio"]?.jsonPrimitive?.contentOrNull ?: "1:1"
        val isPng = arguments["isPng"]?.jsonPrimitive?.booleanOrNull ?: false

        val configManager = ConfigManager()
        val presetModel = configManager.loadKey("DEFAULT_IMAGE_GEN_MODEL")?.ifBlank { null }
        val requestedModelName = arguments["modelName"]?.jsonPrimitive?.contentOrNull
        val effectiveModelName = presetModel ?: requestedModelName ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE.modelName

        val targetModel = GeminiModel.entries.firstOrNull { it.modelName == effectiveModelName }
            ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE

        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到有效的 Gemini API Key，请先在设置中配置密钥")

        val (targetWidth, targetHeight) = when (aspectRatio) {
            "16:9" -> 1280f to 720f
            "9:16" -> 720f to 1280f
            "4:3" -> 1024f to 768f
            "3:4" -> 768f to 1024f
            else -> 1024f to 1024f
        }

        onProgress?.invoke(0.1f, "正在初始化文生图请求，使用模型: ${targetModel.displayName}...")
        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        var rawResultUri: String? = null
        try {
            onProgress?.invoke(0.3f, "正在通过 AI 模型生成图像...")
            aiService.generateImages(
                model = targetModel,
                blockType = "IMAGE",
                userPrompt = prompt,
                apiKey = apiKey,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                isPng = isPng,
                generationCount = 1,
                onLog = { logMsg -> onProgress?.invoke(0.5f, logMsg) },
                onImageGenerated = { rawResultUri = it }
            )
        } catch (e: Exception) {
            AppLogger.e("GenerateImageTool", "文生图执行失败", e)
            return McpToolResult.error("文生图执行失败: ${e.message}")
        }

        val finalRaw = rawResultUri ?: return McpToolResult.error("AI 生图服务未返回有效图像数据")

        @OptIn(ExperimentalEncodingApi::class)
        val initialBytes = if (finalRaw.startsWith("data:image")) {
            val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
            Base64.decode(pureB64)
        } else {
            readLocalFileBytes(finalRaw)
        } ?: return McpToolResult.error("无法解析生成的图像字节数据")

        val processedBytes = if (isPng) {
            onProgress?.invoke(0.75f, "正在执行本地 AI 透明抠图去背...")
            try {
                mattingService.removeBackground(initialBytes) ?: initialBytes
            } catch (e: Throwable) {
                AppLogger.w("GenerateImageTool", "透明抠图去背失败，保留原始图片", e)
                initialBytes
            }
        } else {
            initialBytes
        }

        onProgress?.invoke(0.9f, "正在保存图片资产至本地磁盘...")
        val timestamp = getCurrentTimeMillis()
        val ext = if (isPng) "png" else "jpg"
        val relativeDir = "templates/$projectName/assets/generated"
        val fileName = "gen_${timestamp}.$ext"

        val savedPath = try {
            storage.saveBytesToFile("$relativeDir/$fileName", processedBytes)
        } catch (e: Exception) {
            AppLogger.e("GenerateImageTool", "保存生成资产失败", e)
            null
        }

        @OptIn(ExperimentalEncodingApi::class)
        val returnBase64 = Base64.encode(processedBytes)
        val mimeType = if (isPng) "image/png" else "image/jpeg"

        val resultMessage = buildString {
            append("🎉 AI 独立文生图生成成功！\n")
            append("🎨 提示词: $prompt\n")
            append("🤖 采用模型: ${targetModel.displayName} ($effectiveModelName)\n")
            append("📐 画面规格: $aspectRatio (${targetWidth.toInt()}x${targetHeight.toInt()})\n")
            if (isPng) {
                append("✂️ 透明背景: 已完成本地 AI 自动去背 (PNG)\n")
            }
            if (savedPath != null) {
                append("📁 物理落盘: `$savedPath`")
            }
        }

        onProgress?.invoke(1.0f, "文生图任务完成")
        return McpToolResult.image(
            base64Data = returnBase64,
            mimeType = mimeType,
            message = resultMessage
        )
    }
}

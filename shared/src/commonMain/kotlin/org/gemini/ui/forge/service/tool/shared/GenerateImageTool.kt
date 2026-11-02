package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
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
) : ToolDefinition {

    override val name: String = "generate_image"

    override val description: String =
        "执行通用独立文生图（Text-to-Image）任务。根据用户的自然语言视觉描述生成高品质图片，支持画面宽高比（1:1、16:9 等）与透明背景去背，纯内存生成与 Data URI 返回；支持可选直接保存至素材库或添加到当前页面。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
                put("description", "可选：目标项目工程名称。若未指定将自动归入当前活跃工程")
            })
            putJsonObject("aspectRatio") {
                put("type", "string")
                putJsonArray("enum") {
                    add("1:1")
                    add("2:3")
                    add("3:2")
                    add("3:4")
                    add("4:3")
                    add("4:5")
                    add("5:4")
                    add("9:16")
                    add("16:9")
                    add("21:9")
                    add("1:8")
                    add("8:1")
                    add("1:4")
                    add("4:1")
                }
                put(
                    "description",
                    "可选：画面宽高比例，默认为 '1:1'。支持 14 种标准比例：'1:1'（正方形图标/角色头像）、'16:9'/'21:9'（横屏宽幅全景底图）、'9:16'（竖屏全屏底图）、'4:3'/'3:2'/'5:4'（常规横向面板）、'3:4'/'2:3'/'4:5'（竖向卡片/列表项）、'8:1'/'4:1'（横条饰带/标题横幅）、'1:8'/'1:4'（极窄竖条背景/侧边装饰栏）。"
                )
            }
            put("isPng", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否自动通过本地 AI 算法进行透明抠图去背，默认为 false。对图标、独立角色、小动物切片建议设为 true")
            })
            put("modelName", buildJsonObject {
                put("type", "string")
                put("description", "可选：调用的生图模型代号。若系统设置了默认生图模型预设，将自动强制锁定为预设模型")
            })
            put("customSubPath", buildJsonObject {
                put("type", "string")
                put("description", "可选：指定存入素材库的子文件夹路径（例如 'icons/buttons'，留空存入 assets/image/ 根目录）")
            })
            put("customFileName", buildJsonObject {
                put("type", "string")
                put("description", "可选：指定存入素材库的文件名（无需后缀）。若提供该参数将直接持久化落盘至素材库，否则仅保留在内存与日志中")
            })
            put("addToView", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否在生成后直接作为图元添加到当前 UI Studio 页面画布中心，默认为 false")
            })
        })
        put("required", buildJsonArray {
            add("prompt")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val prompt = arguments["prompt"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'prompt' 不能为空")

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
            ?: return ToolResult.error("未找到有效的 Gemini API Key，请先在设置中配置密钥")

        val (targetWidth, targetHeight) = when (aspectRatio) {
            "1:1" -> 1024f to 1024f
            "2:3" -> 832f to 1248f
            "3:2" -> 1248f to 832f
            "3:4" -> 768f to 1024f
            "4:3" -> 1024f to 768f
            "4:5" -> 816f to 1020f
            "5:4" -> 1020f to 816f
            "9:16" -> 720f to 1280f
            "16:9" -> 1280f to 720f
            "21:9" -> 1536f to 656f
            "1:8" -> 160f to 1280f
            "8:1" -> 1280f to 160f
            "1:4" -> 320f to 1280f
            "4:1" -> 1280f to 320f
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
            return ToolResult.error("文生图执行失败: ${e.message}")
        }

        val finalRaw = rawResultUri ?: return ToolResult.error("AI 生图服务未返回有效图像数据")

        @OptIn(ExperimentalEncodingApi::class)
        val initialBytes = if (finalRaw.startsWith("data:image")) {
            val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
            Base64.decode(pureB64)
        } else {
            readLocalFileBytes(finalRaw)
        } ?: return ToolResult.error("无法解析生成的图像字节数据")

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

        val customSubPath = arguments["customSubPath"]?.jsonPrimitive?.contentOrNull?.trim()?.trim('/', '\\') ?: ""
        val customFileName = arguments["customFileName"]?.jsonPrimitive?.contentOrNull?.trim()
        val addToView = arguments["addToView"]?.jsonPrimitive?.booleanOrNull ?: false

        val ext = if (isPng) "png" else "jpg"
        var savedRelativePath: String? = null

        // 仅当用户或 AI 显式指定了 customFileName 时，才直接将图片物理落盘存入 assets/image/
        if (!customFileName.isNullOrBlank()) {
            onProgress?.invoke(0.9f, "正在保存图片至项目素材库...")
            val cleanFileName = if (customFileName.endsWith(".$ext", ignoreCase = true)) customFileName else "$customFileName.$ext"
            val targetRelDir = if (customSubPath.isBlank()) {
                "templates/$projectName/assets/image"
            } else {
                "templates/$projectName/assets/image/$customSubPath"
            }
            val targetFullPath = "$targetRelDir/$cleanFileName"

            savedRelativePath = try {
                storage.saveBytesToFile(targetFullPath, processedBytes)
            } catch (e: Exception) {
                AppLogger.e("GenerateImageTool", "保存资产至素材库失败: $targetFullPath", e)
                null
            }

            // 若有活跃 UIStudioViewModel，触发素材库自动自愈刷新
            if (savedRelativePath != null) {
                try {
                    McpUiActionPipeline.getActiveUiStudioViewModel()?.assetLibrary?.refreshAssetLibrary()
                } catch (e: Throwable) {
                    AppLogger.w("GenerateImageTool", "刷新素材库失败", e)
                }
            }
        }

        // 若要求直接添加到页面
        if (addToView && savedRelativePath != null) {
            try {
                val studioVm = McpUiActionPipeline.getActiveUiStudioViewModel()
                if (studioVm != null) {
                    val blockName = customFileName?.ifBlank { "ai_img_${getCurrentTimeMillis()}" } ?: "ai_img_${getCurrentTimeMillis()}"
                    studioVm.assetLibrary.addChatImageToCurrentView(
                        imageBytes = processedBytes,
                        fileName = blockName,
                        width = targetWidth,
                        height = targetHeight,
                        subPath = customSubPath,
                        isPng = isPng
                    )
                }
            } catch (e: Throwable) {
                AppLogger.w("GenerateImageTool", "添加图元到当前页面失败", e)
            }
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
            if (savedRelativePath != null) {
                append("📁 素材库归档: `$savedRelativePath`\n")
                if (addToView) {
                    append("🖼️ 页面挂载: 已作为图元挂载至当前视图中心")
                }
            } else {
                append("💾 内存模式: 图片数据已完整持久化于会话日志中，可通过操作栏按需存入素材库。")
            }
        }

        onProgress?.invoke(1.0f, "文生图任务完成")
        return ToolResult.image(
            base64Data = returnBase64,
            mimeType = mimeType,
            message = resultMessage
        )
    }
}


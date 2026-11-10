package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.job.ImageJobManager
import org.gemini.ui.forge.service.job.ImageJobStatus
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 通用独立文生图工具 (Text-to-Image Tool)
 *
 * 允许大模型（如文本模型 Gemini 3.8 Flash 或生图模型）在识别出用户的生图意图时，
 * 自主通过 Function Calling 调起本工具提交异步文生图作业并获取任务 jobId。
 */
class GenerateImageTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "generate_image"

    override val description: String =
        "提交通用独立文生图（Text-to-Image）异步任务。根据用户的自然语言视觉描述生成高品质图片，秒级返回任务 jobId，后续必须使用 query_image_job 工具轮询生成结果。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 310_000L

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
            put("targetWidth", buildJsonObject {
                put("type", "number")
                put("description", "可选：精确像素宽度（须与 targetHeight 成对提供，提供后优先于 aspectRatio 枚举）。重生图元场景请先调用 get_runtime_status 查询选中图元 bounds 的 width/height 原样传入，确保生成图与原图元尺寸完全一致")
            })
            put("targetHeight", buildJsonObject {
                put("type", "number")
                put("description", "可选：精确像素高度（须与 targetWidth 成对提供，提供后优先于 aspectRatio 枚举）")
            })
            put("waitSeconds", buildJsonObject {
                put("type", "number")
                put("description", "可选：同步等待生图完成的最长秒数（默认 0 = 保持异步提交语义，仅返回 jobId）。大于 0 时将阻塞至任务完成并直接返回 results 结果数组，适合需要立即拿到图像数据的调用方")
            })
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
                put("description", "可选：指定存入素材库的文件名（无需后缀）。若提供该参数将直接持久化落盘至素材库，否则仅保留在会话日志与内存中")
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

        // 可选：调用方显式指定的精确像素尺寸（成对提供且宽高比在物理夹具范围内时优先于枚举比例）
        val explicitWidth = arguments["targetWidth"]?.jsonPrimitive?.doubleOrNull?.toFloat()
        val explicitHeight = arguments["targetHeight"]?.jsonPrimitive?.doubleOrNull?.toFloat()

        // 可选：同步等待秒数（默认 0 = 保持异步提交语义，兼容 MCP 现有调用）
        val waitSeconds = arguments["waitSeconds"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0

        val configManager = ConfigManager()
        val presetModel = configManager.loadKey("DEFAULT_IMAGE_GEN_MODEL")?.ifBlank { null }
        val requestedModelName = arguments["modelName"]?.jsonPrimitive?.contentOrNull
        val effectiveModelName = presetModel ?: requestedModelName ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE.modelName

        val targetModel = GeminiModel.entries.firstOrNull { it.modelName == effectiveModelName }
            ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE

        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return ToolResult.error("未找到有效的 Gemini API Key，请先在设置中配置密钥")

        // 尺寸决策：显式像素成对提供且宽高比在物理夹具 0.05~20 内时优先，否则回退 14 档枚举映射
        val explicitRatioValid = explicitWidth != null && explicitHeight != null &&
            explicitWidth > 0f && explicitHeight > 0f &&
            (explicitWidth / explicitHeight) in 0.05f..20f

        val (targetWidth, targetHeight) = if (explicitRatioValid) {
            explicitWidth!! to explicitHeight!!
        } else {
            when (aspectRatio) {
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
        }

        val customSubPath = arguments["customSubPath"]?.jsonPrimitive?.contentOrNull?.trim()?.trim('/', '\\') ?: ""
        val customFileName = arguments["customFileName"]?.jsonPrimitive?.contentOrNull?.trim()
        val addToView = arguments["addToView"]?.jsonPrimitive?.booleanOrNull ?: false

        val jobId = ImageJobManager.submitJob(
            prompt = prompt,
            projectName = projectName,
            aspectRatio = aspectRatio,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            isPng = isPng,
            targetModel = targetModel,
            effectiveModelName = effectiveModelName,
            apiKey = apiKey,
            customSubPath = customSubPath,
            customFileName = customFileName,
            addToView = addToView,
            storage = storage
        )

        // 可选同步语义：waitSeconds > 0 时阻塞等待任务终态并直接返回图像结果（默认 0 保持异步零波及）
        if (waitSeconds > 0) {
            onProgress?.invoke(0.5f, "生图任务已提交，同步等待生成完成（最长 ${waitSeconds}s）...")
            val finalRecord = try {
                ImageJobManager.awaitJobCompleted(jobId, timeoutSeconds = waitSeconds)
            } catch (e: Exception) {
                return ToolResult.error("同步等待生图结果失败: ${e.message}")
            }

            val syncJson = buildJsonObject {
                put("status", finalRecord.status.name)
                put("jobId", jobId)
                put("prompt", prompt)
                put("aspectRatio", aspectRatio)
                put("width", finalRecord.width)
                put("height", finalRecord.height)
                if (!finalRecord.imageUri.isNullOrBlank()) put("imageUri", finalRecord.imageUri)
                if (!finalRecord.base64Data.isNullOrBlank()) put("base64Data", finalRecord.base64Data)
                if (!finalRecord.mimeType.isNullOrBlank()) put("mimeType", finalRecord.mimeType)
                if (!finalRecord.savedRelativePath.isNullOrBlank()) put("savedRelativePath", finalRecord.savedRelativePath)
                if (!finalRecord.errorMessage.isNullOrBlank()) put("errorMessage", finalRecord.errorMessage)
                put("results", buildJsonArray {
                    add(buildJsonObject {
                        put("jobId", jobId)
                        if (!finalRecord.imageUri.isNullOrBlank()) put("imageUri", finalRecord.imageUri)
                        if (!finalRecord.base64Data.isNullOrBlank()) put("base64Data", finalRecord.base64Data)
                        if (!finalRecord.mimeType.isNullOrBlank()) put("mimeType", finalRecord.mimeType)
                        if (!finalRecord.savedRelativePath.isNullOrBlank()) put("savedRelativePath", finalRecord.savedRelativePath)
                    })
                })
                put(
                    "message",
                    if (finalRecord.status == ImageJobStatus.SUCCESS) {
                        "生图任务已同步完成并返回图像数据。"
                    } else {
                        "生图任务失败: ${finalRecord.errorMessage ?: "未知错误"}"
                    }
                )
            }

            onProgress?.invoke(1.0f, "生图同步等待结束: ${finalRecord.status.name}")
            return if (finalRecord.status == ImageJobStatus.SUCCESS) {
                ToolResult.text(syncJson.toString())
            } else {
                ToolResult.error(syncJson.toString())
            }
        }

        val responseJson = buildJsonObject {
            put("status", "RUNNING")
            put("jobId", jobId)
            put("prompt", prompt)
            put("aspectRatio", aspectRatio)
            put("message", "AI 生图任务已成功提交并在后台异步执行中。请立即调用 query_image_job(jobId = '$jobId') 轮询查询结果，在生图未完成前严禁执行后续依赖任务。")
        }

        onProgress?.invoke(1.0f, "生图任务已提交后台异步生成，JobId: $jobId")
        return ToolResult.text(responseJson.toString())
    }
}



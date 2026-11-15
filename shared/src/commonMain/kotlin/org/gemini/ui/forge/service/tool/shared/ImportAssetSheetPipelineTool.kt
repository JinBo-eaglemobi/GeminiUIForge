package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.assetsheet.AssetSheetParseResult
import org.gemini.ui.forge.service.assetsheet.AssetSheetAiDetector
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.service.mcp.McpJobManager
import org.gemini.ui.forge.service.studio.StudioAssetManagerService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.SmartEdgeSnapper
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.Image

/**
 * AI 图集导入流水线工具（统一复用 StudioAssetManagerService.importAssetSheetToStudioProject 公用函数）。
 */
class ImportAssetSheetPipelineTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "import_asset_sheet_pipeline"

    override val description: String =
        "【AI图集导入流水线核心工具】接收图集大图 (SpriteSheet) 与可选的坐标配置文件 (JSON)，自动执行规范自适应解析、智能微观吸附、Skia透明物理切片落盘至素材库与 UI Studio 页面生成。当未提供坐标JSON时，自动调用Gemini视觉多模态大模型逆向检测全景图元坐标。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 180_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程名称 (小驼峰或下划线命名)")
            })
            put("imagePath", buildJsonObject {
                put("type", "string")
                put("description", "图集大图文件绝对物理路径 (如 E:\\dir/img.png)")
            })
            put("jsonPath", buildJsonObject {
                put("type", "string")
                put("description", "可选：坐标配置文件绝对物理路径；缺省时自动走纯图 AI 视觉目标检测分支")
            })
            put("jsonContent", buildJsonObject {
                put("type", "string")
                put("description", "可选：坐标配置 JSON 字符串文本内容 (与 jsonPath 二选一)")
            })
            put("apiKey", buildJsonObject {
                put("type", "string")
                put("description", "可选：Gemini API 密钥，缺省时自动从本地配置中心读取")
            })
            put("autoSnap", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否在切片前执行 Alpha 通道智能微观吸附自愈，默认 true")
            })
            put("async", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否采用异步任务模式执行，默认 true")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("imagePath")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val isAsync = arguments["async"]?.jsonPrimitive?.booleanOrNull ?: true
        if (isAsync) {
            val jobId = McpJobManager.submitJob(name) { progressReporter ->
                executeInternal(arguments, progressReporter)
            }
            val initialJson = buildJsonObject {
                put("success", true)
                put("jobId", jobId)
                put("status", "PENDING")
                put("message", "任务已提交至后台异步执行，请调用 'get_job_status' 配合 waitSeconds 参数进行长轮询获取进度与结果")
            }.toString()
            return ToolResult.text(initialJson)
        }
        return executeInternal(arguments, onProgress)
    }

    private suspend fun executeInternal(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val imagePath = arguments["imagePath"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'imagePath' 不能为空")
        val jsonPath = arguments["jsonPath"]?.jsonPrimitive?.contentOrNull
        val jsonContent = arguments["jsonContent"]?.jsonPrimitive?.contentOrNull
        val apiKey = arguments["apiKey"]?.jsonPrimitive?.contentOrNull
        val autoSnap = arguments["autoSnap"]?.jsonPrimitive?.booleanOrNull ?: true

        val sanitizedName = repository.sanitizeName(projectName)

        onProgress?.invoke(0.05f, "正在读取图集图像数据...")
        val imageBytes = readLocalFileBytes(imagePath)
            ?: return ToolResult.error("无法读取指定的图集图片: $imagePath")

        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (e: Exception) {
            return ToolResult.error("Skia 解码图片失败: ${e.message}")
        }

        val imgWidth = skiaImage.width.toFloat()
        val imgHeight = skiaImage.height.toFloat()

        val parseResult: AssetSheetParseResult = if (!jsonContent.isNullOrBlank()) {
            onProgress?.invoke(0.15f, "正在自适应解析直接传入的 JSON 文本...")
            AssetSheetNormalizer.normalizeJsonOffline(jsonContent)
        } else if (!jsonPath.isNullOrBlank()) {
            onProgress?.invoke(0.15f, "正在读取并解析坐标配置文件: $jsonPath")
            val rawJson = readLocalFileBytes(jsonPath)?.decodeToString()
                ?: return ToolResult.error("无法读取坐标配置文件: $jsonPath")
            AssetSheetNormalizer.normalizeJsonOffline(rawJson)
        } else {
            onProgress?.invoke(0.2f, "未提供坐标文件，启动 Gemini 视觉大模型多模态全景识别...")
            val (aiResult, _) = AssetSheetAiDetector.detectAssetSheet(
                imagePath = imagePath,
                apiKey = apiKey,
                projectName = sanitizedName,
                onProgress = { progress, msg ->
                    onProgress?.invoke(0.2f + progress * 0.4f, "AI 识图中: $msg")
                }
            )
            aiResult
        }

        if (parseResult.items.isEmpty()) {
            return ToolResult.error("未能从指定输入中识别出任何有效的图元坐标")
        }

        var finalItems = parseResult.items
        if (autoSnap) {
            onProgress?.invoke(0.7f, "正在执行多轮 Alpha 通道紧致外切微观吸附校对...")
            finalItems = finalItems.map { item ->
                try {
                    val snapRes = SmartEdgeSnapper.snapBounds(
                        imageBytes = imageBytes,
                        logicalBounds = item.bounds,
                        canvasWidth = imgWidth,
                        canvasHeight = imgHeight
                    )
                    if (snapRes != null) item.copy(bounds = snapRes.logicalRect) else item
                } catch (e: Exception) {
                    AppLogger.w("ImportAssetSheetPipelineTool", "吸附校对异常 [${item.id}]: ${e.message}")
                    item
                }
            }
        }

        onProgress?.invoke(0.8f, "正在调用公用服务执行并发切片入库与页面装配...")
        val (savedProject, savedLib) = StudioAssetManagerService.importAssetSheetToStudioProject(
            projectName = sanitizedName,
            skiaImage = skiaImage,
            canvasWidth = parseResult.canvasWidth,
            canvasHeight = parseResult.canvasHeight,
            items = finalItems,
            repository = repository,
            syncActiveWorkspace = true,
            onProgress = { p, msg ->
                onProgress?.invoke(0.8f + p * 0.18f, msg)
            }
        )

        onProgress?.invoke(1.0f, "🎉 AI 图集导入流水线执行完成！")
        val mainPage = savedProject.studioPages.firstOrNull()
        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", sanitizedName)
            put("viewId", mainPage?.id ?: "MainPage")
            put("blocksCount", mainPage?.blocks?.size ?: 0)
            put("assetsCount", savedLib.items.size)
            put("canvasWidth", parseResult.canvasWidth)
            put("canvasHeight", parseResult.canvasHeight)
        }.toString()

        return ToolResult.text(resultJson)
    }
}

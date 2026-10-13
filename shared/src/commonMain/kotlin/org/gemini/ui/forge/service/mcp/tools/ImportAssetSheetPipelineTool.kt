package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.assetsheet.AssetSheetParseResult
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.service.TemplateOverlayRenderer
import org.gemini.ui.forge.service.assetsheet.AssetSheetAiDetector
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.service.mcp.McpJobManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.Image

/**
 * AI 图集导入流水线 MCP 工具。
 *
 * 接收图集大图 (SpriteSheet) 与可选的坐标配置文件 (JSON)，
 * 自动执行规范自适应解析、智能微观吸附、Skia 透明物理切片落盘与 UI 项目模板生成。
 * 当未提供坐标 JSON 时，自动调用 Gemini 视觉多模态大模型逆向检测全景图元坐标。
 * 支持异步长轮询彻底免疫客户端超时。
 */
class ImportAssetSheetPipelineTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "import_asset_sheet_pipeline"

    override val description: String =
        "【AI图集导入流水线核心工具】接收图集大图 (SpriteSheet) 与可选的坐标配置文件 (JSON)，自动执行规范自适应解析、智能微观吸附、Skia透明物理切片落盘与UI项目模板生成。当未提供坐标JSON时，自动调用Gemini视觉多模态大模型逆向检测全景图元坐标。支持异步长轮询彻底免疫客户端超时。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 180_000L // 3 分钟超时时限

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程模板名称 (小驼峰或下划线命名)")
            })
            put("imagePath", buildJsonObject {
                put("type", "string")
                put("description", "图集大图文件绝对物理路径或网络URL (如 E:\\god_cup_assets.png)")
            })
            put("jsonPath", buildJsonObject {
                put("type", "string")
                put("description", "可选：坐标配置文件绝对物理路径 (如 E:\\god_cup_assets_coords.json)；缺省时自动走纯图 AI 视觉目标检测分支")
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
                put("description", "可选：是否采用异步任务模式执行，默认 true (立即返回 jobId 并通过 get_job_status 长轮询，免疫超时)")
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
    ): McpToolResult {
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
            return McpToolResult.text(initialJson)
        }
        return executeInternal(arguments, onProgress)
    }

    private suspend fun executeInternal(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val imagePath = arguments["imagePath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'imagePath' 不能为空")
        val jsonPath = arguments["jsonPath"]?.jsonPrimitive?.contentOrNull
        val jsonContent = arguments["jsonContent"]?.jsonPrimitive?.contentOrNull
        val apiKey = arguments["apiKey"]?.jsonPrimitive?.contentOrNull
        val autoSnap = arguments["autoSnap"]?.jsonPrimitive?.booleanOrNull ?: true

        val sanitizedName = projectName.trim().replace(" ", "_")

        onProgress?.invoke(0.05f, "正在读取图集图像数据...")
        val imageBytes = readLocalFileBytes(imagePath)
            ?: return McpToolResult.error("无法读取指定的图集图片: $imagePath")

        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (e: Exception) {
            return McpToolResult.error("Skia 解码图片失败: ${e.message}")
        }

        val imgWidth = skiaImage.width.toFloat()
        val imgHeight = skiaImage.height.toFloat()

        // 1. 获取解析结果 (外部 JSON 或 AI 视觉逆向识别)
        val parseResult: AssetSheetParseResult = if (!jsonContent.isNullOrBlank()) {
            onProgress?.invoke(0.15f, "正在自适应解析直接传入的 JSON 文本...")
            AssetSheetNormalizer.normalizeJsonOffline(jsonContent)
        } else if (!jsonPath.isNullOrBlank()) {
            onProgress?.invoke(0.15f, "正在读取并解析坐标配置文件: $jsonPath")
            val rawJson = readLocalFileBytes(jsonPath)?.decodeToString()
                ?: return McpToolResult.error("无法读取坐标配置文件: $jsonPath")
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
            return McpToolResult.error("未能从指定输入中识别出任何有效的图元坐标")
        }

        onProgress?.invoke(0.65f, "成功解析出 ${parseResult.items.size} 个图元组件")

        // 2. 智能微观吸附自愈校对 (可选)
        var finalItems = parseResult.items
        if (autoSnap) {
            onProgress?.invoke(0.7f, "正在执行多轮 Alpha 通道紧致外切微观吸附校对...")
            finalItems = finalItems.map { item ->
                try {
                    val snapRes = org.gemini.ui.forge.utils.SmartEdgeSnapper.snapBounds(
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

        // 3. 执行 Skia 物理透明切片落盘与构建 UIBlock
        onProgress?.invoke(0.8f, "正在执行物理透明切片并落盘至资产库...")
        val canvasW = parseResult.canvasWidth
        val canvasH = parseResult.canvasHeight

        val createdBlocks = mutableListOf<UIBlock>()
        val savedAssetPaths = mutableListOf<String>()

        finalItems.forEachIndexed { index, item ->
            val b = item.bounds
            val localCropRect = SerialRect(
                left = b.left.coerceIn(0f, imgWidth),
                top = b.top.coerceIn(0f, imgHeight),
                right = b.right.coerceIn(0f, imgWidth),
                bottom = b.bottom.coerceIn(0f, imgHeight)
            )

            val sliceBytes = cropImage(
                imageBytes = imageBytes,
                bounds = localCropRect,
                originalWidth = imgWidth,
                originalHeight = imgHeight,
                isPng = true
            )

            val relativeAssetPath = "templates/$sanitizedName/assets/${item.id}/${item.id}.png"
            var fileHandle: TemplateFile? = null
            if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                val tFile = TemplateFile(relativeAssetPath)
                tFile.writeBytes(sliceBytes)
                fileHandle = tFile
                savedAssetPaths.add(relativeAssetPath)
            }

            val block = UIBlock(
                id = item.id,
                type = item.type,
                bounds = b,
                cropRect = b,
                currentImageUri = fileHandle,
                referenceImage = fileHandle,
                userPromptZh = item.name,
                userPromptEn = item.name
            )
            createdBlocks.add(block)

            onProgress?.invoke(0.8f + (index.toFloat() / finalItems.size) * 0.15f, "切片中 (${index + 1}/${finalItems.size}): ${item.name}")
        }

        // 4. 构建并保存 ProjectState
        val page = UIPage(
            id = "page_main",
            nameStr = "主页面",
            width = canvasW,
            height = canvasH,
            blocks = createdBlocks
        )

        val project = ProjectState(
            projectId = sanitizedName,
            pages = listOf(page)
        )

        repository.saveTemplate(sanitizedName, project)
        AppLogger.i("ImportAssetSheetPipelineTool", "💾 成功生成并持久化工程模板: $sanitizedName (${createdBlocks.size} 个图元)")

        // 5. 渲染离屏标注图
        try {
            TemplateOverlayRenderer.renderAndSaveInitialAndLatest(sanitizedName, project)
        } catch (e: Exception) {
            AppLogger.w("ImportAssetSheetPipelineTool", "离屏全景标注图渲染警告: ${e.message}")
        }

        onProgress?.invoke(1.0f, "🎉 AI 图集导入流水线执行完成！")

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", sanitizedName)
            put("blocksCount", createdBlocks.size)
            put("canvasWidth", canvasW)
            put("canvasHeight", canvasH)
            put("savedAssetsCount", savedAssetPaths.size)
            put("templatePath", "templates/$sanitizedName/template.json")
            put("message", "AI 图集导入流水线已顺利完成，成功生成 $sanitizedName 工程并切片归档 ${createdBlocks.size} 个图元。")
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

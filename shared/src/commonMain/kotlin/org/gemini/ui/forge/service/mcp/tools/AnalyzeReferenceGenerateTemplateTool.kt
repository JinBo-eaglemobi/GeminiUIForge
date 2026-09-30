package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 视觉大模型深度识图并生成模板工具
 */
class AnalyzeReferenceGenerateTemplateTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "analyze_reference_generate_template"

    override val description: String =
        "【UI模板生成核心工具】调用 Gemini 视觉多模态大模型对一组设计参考图进行全景深度分析，自动提取页面架构、图元层级树、局部相对坐标与双语提示词，并直接生成可编辑的 UI 项目模板。当用户提供参考图并要求生成、创建或重构UI模板时必须优先调用本工具。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 180_000L // 识图长任务 3 分钟超时时限

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "要生成的目标模板工程名称 (小驼峰或下划线命名)")
            })
            put("referenceImages", buildJsonObject {
                put("type", "array")
                put("description", "设计参考图列表。支持：1. 本地物理绝对路径 (如 D:/assets/bg.png)；2. http:// 或 https:// 访问的网络图片链接；3. data:image/...;base64,... 格式的 Data URI。")
                put("items", buildJsonObject { put("type", "string") })
            })
            put("apiKey", buildJsonObject {
                put("type", "string")
                put("description", "可选：Gemini API 密钥，缺省时自动从本地配置中读取")
            })
            put("autoCropReferenceImage", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否自动从原图切割参考图片并绑定为各模块的 referenceImage。默认 false (仅校正模块物理坐标与大小，不切图)")
            })
            put("async", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否采用异步任务模式执行。默认为 true（立即返回 jobId 并通过 get_job_status 长轮询进度，彻底免疫客户端超时）。传入 false 则保持同步阻塞等待。")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("referenceImages")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val isAsync = arguments["async"]?.jsonPrimitive?.booleanOrNull ?: true
        if (isAsync) {
            val jobId = org.gemini.ui.forge.service.mcp.McpJobManager.submitJob(name) { progressReporter ->
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
        val imageArray = arguments["referenceImages"]?.jsonArray
            ?: return McpToolResult.error("参数 'referenceImages' 不能为空")

        val imagePaths = imageArray.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }
        if (imagePaths.isEmpty()) {
            return McpToolResult.error("参考图片路径列表不能为空")
        }

        val configManager = ConfigManager()
        val apiKey = arguments["apiKey"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到有效的 Gemini API Key，请在参数中传入或在应用设置中配置")

        onProgress?.invoke(0.1f, "正在准备 AI 视觉大模型连接...")
        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)

        val validateError = aiService.validateImageUris(imagePaths)
        if (validateError != null) {
            return McpToolResult.error("参考图片资源无效: $validateError")
        }

        // 进入大模型分析前，在大厅注册生成中状态，锁定卡片防乱点
        UiRoadmapRegistry.markProjectGenerating(projectName, "正在由 AI 视觉大模型反向生成图元工程...")

        onProgress?.invoke(0.3f, "正在向 Gemini 发起设计图结构化分析请求...")
        val generatedProjectState = try {
            aiService.analyzeImagesForTemplate(
                imageUris = imagePaths,
                apiKey = apiKey,
                onLog = { logMsg ->
                    onProgress?.invoke(0.5f, "大模型分析中: $logMsg")
                }
            )
        } catch (e: Exception) {
            UiRoadmapRegistry.unmarkProjectGenerating(projectName)
            return McpToolResult.error("视觉大模型分析失败: ${e.message}")
        }

        onProgress?.invoke(0.75f, "正在物理归档参考图并绑定页面背景...")
        val archivedFiles = try {
            repository.archiveExternalImages(projectName, imagePaths)
        } catch (_: Exception) {
            emptyList()
        }

        val autoCropRef = arguments["autoCropReferenceImage"]?.jsonPrimitive?.booleanOrNull ?: false

        val boundProjectState = generatedProjectState.copy(
            createdAt = org.gemini.ui.forge.getCurrentTimeMillis(),
            styleReferenceUri = archivedFiles.firstOrNull(),
            referenceImages = archivedFiles,
            pages = generatedProjectState.pages.mapIndexed { index, page ->
                page.copy(sourceImageUri = archivedFiles.getOrNull(index) ?: archivedFiles.firstOrNull())
            }
        )

        onProgress?.invoke(0.80f, "正在执行离线物理边缘梯度吸附与图元坐标纠偏...")
        val firstRefPath = archivedFiles.firstOrNull()?.getAbsolutePath()
        val refBytes = if (!firstRefPath.isNullOrBlank()) org.gemini.ui.forge.utils.readLocalFileBytes(firstRefPath) else null

        val calibratedPages = if (refBytes != null && refBytes.isNotEmpty()) {
            boundProjectState.pages.map { page ->
                val pageW = page.width
                val pageH = page.height

                suspend fun calibrateBlock(block: org.gemini.ui.forge.model.ui.UIBlock): org.gemini.ui.forge.model.ui.UIBlock {
                    val absBounds = block.toAbsoluteBounds()
                    val snapped = org.gemini.ui.forge.utils.SmartEdgeSnapper.snapBounds(
                        imageBytes = refBytes,
                        logicalBounds = absBounds,
                        canvasWidth = pageW,
                        canvasHeight = pageH
                    )
                    val calibratedBounds = if (snapped != null) {
                        block.toLocalBounds(snapped.logicalRect)
                    } else {
                        block.bounds
                    }

                    val newRefImage = if (autoCropRef) {
                        val cropBytes = org.gemini.ui.forge.utils.SmartEdgeSnapper.cropSnappedComponent(
                            imageBytes = refBytes,
                            logicalBounds = absBounds,
                            canvasWidth = pageW,
                            canvasHeight = pageH
                        )
                        if (cropBytes != null) {
                            repository.saveBlockResource(
                                templateName = projectName,
                                blockId = block.id,
                                fileNamePrefix = "ref_snap",
                                bytes = cropBytes,
                                isPng = true
                            )
                        } else block.referenceImage
                    } else {
                        block.referenceImage
                    }

                    val calibratedChildren = block.children.map { calibrateBlock(it) }
                    return block.copy(bounds = calibratedBounds, referenceImage = newRefImage, children = calibratedChildren)
                }

                val calibratedBlocks = page.blocks.map { calibrateBlock(it) }
                page.copy(blocks = calibratedBlocks)
            }
        } else {
            boundProjectState.pages
        }

        val finalSavedState = boundProjectState.copy(pages = calibratedPages)

        onProgress?.invoke(0.88f, "正在归档工程与保存校准图元数据...")
        repository.saveTemplate(projectName, finalSavedState)

        // 纯本地离屏渲染生成初始与最新图元标注图并落盘缓存
        try {
            org.gemini.ui.forge.service.TemplateOverlayRenderer.renderAndSaveInitialAndLatest(projectName, finalSavedState)
        } catch (e: Exception) {
            println("[AnalyzeReference] 生成标注图异常: ${e.message}")
        }

        // 保持大厅卡片生成中锁定状态，标识进入微观核验阶段
        UiRoadmapRegistry.markProjectGenerating(projectName, "图元工程已生成，正在执行微观吸附与实机核验...")

        // 触发 UI 界面跟随广播
        org.gemini.ui.forge.service.mcp.McpUiBridge.notifyTemplateCreated(projectName, finalSavedState)
        onProgress?.invoke(1.0f, "模板生成成功，已完成物理边缘吸附纠偏并绑定参考图背景")

        val firstPage = finalSavedState.pages.firstOrNull()
        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectName)
            put("pageCount", boundProjectState.pages.size)
            put("canvasWidth", firstPage?.width ?: 1080)
            put("canvasHeight", firstPage?.height ?: 1920)
            put("blockCount", firstPage?.blocks?.size ?: 0)
            put("sourceImageUri", firstPage?.sourceImageUri?.getAbsolutePath() ?: "")
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

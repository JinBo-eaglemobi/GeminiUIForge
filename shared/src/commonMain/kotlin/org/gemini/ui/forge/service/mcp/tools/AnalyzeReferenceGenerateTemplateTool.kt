package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
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
            return McpToolResult.error("视觉大模型分析失败: ${e.message}")
        }

        onProgress?.invoke(0.75f, "正在物理归档参考图并绑定页面背景...")
        val archivedFiles = try {
            repository.archiveExternalImages(projectName, imagePaths)
        } catch (_: Exception) {
            emptyList()
        }

        val boundProjectState = generatedProjectState.copy(
            createdAt = org.gemini.ui.forge.getCurrentTimeMillis(),
            styleReferenceUri = archivedFiles.firstOrNull(),
            referenceImages = archivedFiles,
            pages = generatedProjectState.pages.mapIndexed { index, page ->
                page.copy(sourceImageUri = archivedFiles.getOrNull(index) ?: archivedFiles.firstOrNull())
            }
        )

        onProgress?.invoke(0.85f, "正在归档工程与保存图元数据...")
        repository.saveTemplate(projectName, boundProjectState)

        // 触发 UI 界面跟随广播
        org.gemini.ui.forge.service.mcp.McpUiBridge.notifyTemplateCreated(projectName, boundProjectState)
        onProgress?.invoke(1.0f, "模板生成成功，已自动绑定参考图背景并同步至工作区")

        val firstPage = boundProjectState.pages.firstOrNull()
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

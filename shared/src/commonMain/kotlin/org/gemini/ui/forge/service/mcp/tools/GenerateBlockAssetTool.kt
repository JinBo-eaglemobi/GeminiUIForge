package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 单图元 AI 生图/以图生图工具（直接回传 ImageContent，AI 可直接审查图片）
 */
class GenerateBlockAssetTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "generate_block_asset"

    override val description: String =
        "针对指定模板中的特定图元执行 AI 生图任务。支持结合图元绑定的参考底图以图生图，自动执行本地 AI 抠图去除背景，生成高清透明 PNG 并回传图像多模态数据。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 120_000L // 生图 2 分钟超时

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元 ID")
            })
            put("customPrompt", buildJsonObject {
                put("type", "string")
                put("description", "可选：覆盖图元默认提示词的自定义生图提示词")
            })
            put("modelName", buildJsonObject {
                put("type", "string")
                put("description", "可选：使用的 Gemini 模型名，默认 gemini-2.5-flash-image")
            })
            put("isPng", buildJsonObject {
                put("type", "boolean")
                put("description", "是否自动执行本地透明去背，默认 true")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")
        val customPrompt = arguments["customPrompt"]?.jsonPrimitive?.contentOrNull
        val isPng = arguments["isPng"]?.jsonPrimitive?.booleanOrNull ?: true
        val modelName = arguments["modelName"]?.jsonPrimitive?.contentOrNull ?: "gemini-2.5-flash-image"

        onProgress?.invoke(0.1f, "正在读取图元属性与参考图配置...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(projectName, ignoreCase = true) }
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        var targetBlock: UIBlock? = null
        fun findBlock(blocks: List<UIBlock>) {
            for (b in blocks) {
                if (b.id == blockId) {
                    targetBlock = b
                    return
                }
                findBlock(b.children)
            }
        }
        state.pages.forEach { findBlock(it.blocks) }
        val block = targetBlock ?: return McpToolResult.error("未找到图元 '$blockId'")

        val promptToSend = customPrompt?.ifBlank { null }
            ?: block.userPromptEn.ifBlank { null }
            ?: block.userPromptZh.ifBlank { null }
            ?: "Game UI element, high quality, transparent background"

        val refImageUri = block.referenceImage?.getAbsolutePath()

        val configManager = ConfigManager()
        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到 Gemini API Key")

        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        onProgress?.invoke(0.3f, "正在向 Gemini 发起生图请求 ($modelName)...")
        var rawResultUri: String? = null
        try {
            val selectedModel = GeminiModel.entries.firstOrNull { it.modelName == modelName }
                ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE

            aiService.generateImages(
                model = selectedModel,
                blockType = block.type.name,
                userPrompt = promptToSend,
                apiKey = apiKey,
                isPng = isPng,
                referenceImageUri = refImageUri,
                generationCount = 1,
                onLog = { logMsg -> onProgress?.invoke(0.5f, logMsg) },
                onImageGenerated = { uri -> rawResultUri = uri }
            )
        } catch (e: Exception) {
            return McpToolResult.error("AI 生图异常: ${e.message}")
        }

        val finalRaw = rawResultUri ?: return McpToolResult.error("模型未返回有效图像")
        @OptIn(ExperimentalEncodingApi::class)
        val imageBytes = if (finalRaw.startsWith("data:image")) {
            val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
            Base64.decode(pureB64)
        } else {
            readLocalFileBytes(finalRaw)
        } ?: return McpToolResult.error("无法解析生成的图像字节数据")

        onProgress?.invoke(0.7f, if (isPng) "正在执行本地离线 AI 抠图去除背景..." else "正在归档图像...")
        val finalBytes = if (isPng) {
            try {
                mattingService.removeBackground(imageBytes) ?: imageBytes
            } catch (_: Throwable) {
                imageBytes
            }
        } else imageBytes

        onProgress?.invoke(0.9f, "正在将物理文件写入模块资产库...")
        val savedFile = repository.saveBlockResource(
            templateName = projectName,
            blockId = blockId,
            fileNamePrefix = "gen",
            bytes = finalBytes,
            isPng = isPng
        )

        // 回填给 block.currentImageUri
        fun updateCurrentUri(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                if (b.id == blockId) {
                    b.copy(currentImageUri = savedFile)
                } else {
                    b.copy(children = updateCurrentUri(b.children))
                }
            }
        }
        val newPages = state.pages.map { it.copy(blocks = updateCurrentUri(it.blocks)) }
        repository.saveTemplate(projectName, state.copy(pages = newPages))

        @OptIn(ExperimentalEncodingApi::class)
        val returnBase64 = Base64.encode(finalBytes)

        onProgress?.invoke(1.0f, "图元生图完成并已就绪")
        return McpToolResult.image(
            base64Data = returnBase64,
            mimeType = if (isPng) "image/png" else "image/jpeg",
            message = "✅ 图元 [$blockId] AI 生图完成，已绑定物理资产: ${savedFile.getAbsolutePath()}"
        )
    }
}

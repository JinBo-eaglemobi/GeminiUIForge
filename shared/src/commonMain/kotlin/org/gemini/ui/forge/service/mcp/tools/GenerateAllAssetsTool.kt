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
 * 全项目/全页面图元批量并发一键生图工具
 */
class GenerateAllAssetsTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "generate_all_assets"

    override val description: String =
        "对指定模板中的所有图元执行一键全量批量生图。可配置是否仅针对尚未绑定图片资产的空白模块（onlyMissing）。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 300_000L // 批量超长任务 5 分钟

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("onlyMissing", buildJsonObject {
                put("type", "boolean")
                put("description", "是否仅针对尚未绑定图片的图元执行生图，默认 true")
            })
            put("modelName", buildJsonObject {
                put("type", "string")
                put("description", "使用的模型名，默认 gemini-2.5-flash-image")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val onlyMissing = arguments["onlyMissing"]?.jsonPrimitive?.booleanOrNull ?: true
        val modelName = arguments["modelName"]?.jsonPrimitive?.contentOrNull ?: "gemini-2.5-flash-image"

        onProgress?.invoke(0.05f, "正在检索待生成图元清单...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(projectName, ignoreCase = true) }
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val allBlocks = mutableListOf<UIBlock>()
        fun collect(blocks: List<UIBlock>) {
            for (b in blocks) {
                allBlocks.add(b)
                collect(b.children)
            }
        }
        state.pages.forEach { collect(it.blocks) }

        val targetBlocks = if (onlyMissing) allBlocks.filter { it.currentImageUri == null } else allBlocks
        if (targetBlocks.isEmpty()) {
            return McpToolResult.text("✅ 当前模板中没有需要生成的图元 (目标数: 0)")
        }

        val configManager = ConfigManager()
        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到 Gemini API Key")

        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)
        val selectedModel = GeminiModel.entries.firstOrNull { it.modelName == modelName }
            ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE

        var successCount = 0
        var currentState = state

        targetBlocks.forEachIndexed { idx, block ->
            val progressVal = (idx.toFloat() / targetBlocks.size.toFloat()).coerceIn(0.1f, 0.95f)
            onProgress?.invoke(progressVal, "正在为图元 [${block.id}] 生图 (${idx + 1}/${targetBlocks.size})...")

            val promptToSend = block.userPromptEn.ifBlank { null }
                ?: block.userPromptZh.ifBlank { null }
                ?: "Game UI element, high quality"

            var rawUri: String? = null
            try {
                aiService.generateImages(
                    model = selectedModel,
                    blockType = block.type.name,
                    userPrompt = promptToSend,
                    apiKey = apiKey,
                    isPng = true,
                    referenceImageUri = block.referenceImage?.getAbsolutePath(),
                    generationCount = 1,
                    onImageGenerated = { rawUri = it }
                )
            } catch (_: Throwable) {}

            val finalRaw = rawUri
            if (finalRaw != null) {
                @OptIn(ExperimentalEncodingApi::class)
                val bytes = if (finalRaw.startsWith("data:image")) {
                    val b64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
                    Base64.decode(b64)
                } else {
                    readLocalFileBytes(finalRaw)
                }

                if (bytes != null) {
                    val noBg = try { mattingService.removeBackground(bytes) ?: bytes } catch (_: Throwable) { bytes }
                    val saved = repository.saveBlockResource(projectName, block.id, "batch", noBg, isPng = true)

                    fun updateBlock(blocks: List<UIBlock>): List<UIBlock> {
                        return blocks.map { b ->
                            if (b.id == block.id) b.copy(currentImageUri = saved)
                            else b.copy(children = updateBlock(b.children))
                        }
                    }
                    val newPages = currentState.pages.map { it.copy(blocks = updateBlock(it.blocks)) }
                    currentState = currentState.copy(pages = newPages)
                    successCount++
                }
            }
        }

        repository.saveTemplate(projectName, currentState)
        onProgress?.invoke(1.0f, "全量生图流程结束")

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectName)
            put("totalTargetBlocks", targetBlocks.size)
            put("successCount", successCount)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

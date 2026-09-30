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
 * 对话式局部微调与指令重绘工具（支持以图生图与文字指令驱动，并回传 ImageContent）
 */
class ChatRefineImageTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "chat_refine_image"

    override val description: String =
        "针对指定图元进行对话式微调重绘（以图生图）。可传入新的微调指令（如“把金色边框加厚一点、材质换成磨砂亚光”），结合图元当前图片或指定参考底图进行二次重绘，并回传图像供视觉审查。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 120_000L

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
            put("instruction", buildJsonObject {
                put("type", "string")
                put("description", "针对当前图像的二次微调修改指令（如'增加水晶质感与高光反光'）")
            })
            put("referenceImageUri", buildJsonObject {
                put("type", "string")
                put("description", "可选：指定的参考底图绝对路径，缺省时默认使用该图元当前的 currentImageUri 或 referenceImage")
            })
            put("async", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否采用异步任务模式执行。默认为 true（立即返回 jobId 并通过 get_job_status 长轮询进度，彻底免疫客户端超时）。传入 false 则保持同步阻塞等待。")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
            add("instruction")
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
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")
        val instruction = arguments["instruction"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'instruction' 不能为空")
        val explicitRefUri = arguments["referenceImageUri"]?.jsonPrimitive?.contentOrNull

        onProgress?.invoke(0.1f, "正在读取图元上下文...")
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

        val refUri = explicitRefUri
            ?: block.currentImageUri?.getAbsolutePath()
            ?: block.referenceImage?.getAbsolutePath()

        val configManager = ConfigManager()
        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到 Gemini API Key")

        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        onProgress?.invoke(0.3f, "正在执行多模态微调重绘...")
        val promptToSend = "${block.userPromptEn.ifBlank { block.userPromptZh }}, refined with instruction: $instruction"
        var rawResultUri: String? = null

        try {
            aiService.generateImages(
                model = GeminiModel.GEMINI_2_5_FLASH_IMAGE,
                blockType = block.type.name,
                userPrompt = promptToSend,
                apiKey = apiKey,
                isPng = true,
                referenceImageUri = refUri,
                generationCount = 1,
                onLog = { logMsg -> onProgress?.invoke(0.5f, logMsg) },
                onImageGenerated = { rawResultUri = it }
            )
        } catch (e: Exception) {
            return McpToolResult.error("微调重绘异常: ${e.message}")
        }

        val finalRaw = rawResultUri ?: return McpToolResult.error("模型未返回有效图像")
        @OptIn(ExperimentalEncodingApi::class)
        val imageBytes = if (finalRaw.startsWith("data:image")) {
            val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
            Base64.decode(pureB64)
        } else {
            readLocalFileBytes(finalRaw)
        } ?: return McpToolResult.error("无法解析生成的图像字节数据")

        onProgress?.invoke(0.7f, "正在执行本地离线 AI 抠图...")
        val finalBytes = try { mattingService.removeBackground(imageBytes) ?: imageBytes } catch (_: Throwable) { imageBytes }

        onProgress?.invoke(0.9f, "正在保存微调资产...")
        val savedFile = repository.saveBlockResource(projectName, blockId, "refine", finalBytes, isPng = true)

        fun updateCurrentUri(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                if (b.id == blockId) b.copy(currentImageUri = savedFile)
                else b.copy(children = updateCurrentUri(b.children))
            }
        }
        val newPages = state.pages.map { it.copy(blocks = updateCurrentUri(it.blocks)) }
        repository.saveTemplate(projectName, state.copy(pages = newPages))

        @OptIn(ExperimentalEncodingApi::class)
        val returnBase64 = Base64.encode(finalBytes)

        onProgress?.invoke(1.0f, "微调完成")
        return McpToolResult.image(
            base64Data = returnBase64,
            mimeType = "image/png",
            message = "✅ 图元 [$blockId] 微调重塑完成，新资产已绑定: ${savedFile.getAbsolutePath()}"
        )
    }
}

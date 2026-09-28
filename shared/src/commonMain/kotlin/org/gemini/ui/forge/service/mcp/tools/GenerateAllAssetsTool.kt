package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 全项目/全页面图元批量并发一键生图工具
 * 自动过滤纯容器与占位层，接入自动切片校验、非透明主体对齐与缩放实际宽高保存闭环。
 */
class GenerateAllAssetsTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "generate_all_assets"

    override val description: String =
        "对指定模板中的所有业务图元执行一键全量批量生图（自动排除纯容器与占位层）。" +
        "自动执行切片校验兜底、AI生图去背、非透明主体等比对齐并以缩放后的实际宽高物理落盘，实时重载工作区。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 600_000L // 批量超长任务 10 分钟

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

        onProgress?.invoke(0.02f, "正在检索待生成图元清单...")
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

        // 严格过滤纯容器与占位层
        val eligibleBlocks = allBlocks.filter { block ->
            !block.isPureContainer && block.type != UIBlockType.CONTAINER
        }

        val targetBlocks = if (onlyMissing) {
            eligibleBlocks.filter { it.currentImageUri == null }
        } else {
            eligibleBlocks
        }

        if (targetBlocks.isEmpty()) {
            return McpToolResult.text("✅ 当前模板中没有需要生成的业务图元 (纯容器已自动忽略，待生成目标数: 0)")
        }

        val configManager = ConfigManager()
        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到 Gemini API Key，请在桌面端应用设置中配置或通过环境变量提供")

        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        var successCount = 0
        var currentState = state
        val results = mutableListOf<BlockAssetGenerationResult>()

        targetBlocks.forEachIndexed { idx, block ->
            val blockBaseProgress = idx.toFloat() / targetBlocks.size.toFloat()
            val blockStepWeight = 1.0f / targetBlocks.size.toFloat()

            onProgress?.invoke(
                (blockBaseProgress + 0.01f).coerceIn(0.05f, 0.95f),
                "正在为图元 [${block.id}] 执行闭环生图 (${idx + 1}/${targetBlocks.size})..."
            )

            val (newState, pipelineResult) = GenerateBlockAssetTool.executeBlockPipeline(
                projectName = projectName,
                blockId = block.id,
                customPrompt = null,
                modelName = modelName,
                isPng = true,
                currentState = currentState,
                repository = repository,
                storage = storage,
                configManager = configManager,
                cloudAssetManager = cloudAssetManager,
                aiService = aiService,
                mattingService = mattingService,
                apiKey = apiKey,
                onProgress = { p, msg ->
                    val overallProgress = (blockBaseProgress + p * blockStepWeight).coerceIn(0.05f, 0.98f)
                    onProgress?.invoke(overallProgress, "图元 [${block.id}] $msg")
                }
            )

            currentState = newState
            results.add(pipelineResult)
            if (pipelineResult.success) {
                successCount++
            }
        }

        onProgress?.invoke(1.0f, "全量批量生图与对齐自愈流程全部完成")

        val resultJson = buildJsonObject {
            put("success", successCount > 0)
            put("projectName", projectName)
            put("totalTargetBlocks", targetBlocks.size)
            put("successCount", successCount)
            put("failedCount", targetBlocks.size - successCount)
            put("details", buildJsonArray {
                results.forEach { res ->
                    add(buildJsonObject {
                        put("blockId", res.blockId)
                        put("success", res.success)
                        if (res.success) {
                            put("savedPath", res.savedFile?.getAbsolutePath() ?: "")
                            put("actualWidth", res.finalWidth)
                            put("actualHeight", res.finalHeight)
                            res.scaleConfig?.let { sc ->
                                put("scaleConfig", buildJsonObject {
                                    put("scaleX", sc.scaleX)
                                    put("scaleY", sc.scaleY)
                                    put("offsetX", sc.offsetX)
                                    put("offsetY", sc.offsetY)
                                })
                            }
                            res.alignmentRating?.let { put("alignmentRating", it) }
                            res.compositeAlignmentScore?.let { put("alignmentScore", it) }
                        } else {
                            put("error", res.errorMessage ?: "未知错误")
                        }
                    })
                }
            })
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

package org.gemini.ui.forge.service.tool.ai

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.GeminiToolConfig
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolExposurePolicy
import org.gemini.ui.forge.service.tool.ToolHub
import org.gemini.ui.forge.service.tool.ai.exclusive.*
import org.gemini.ui.forge.service.tool.shared.*

/**
 * AI 专属工具注册中心
 *
 * 聚合全量可供 AI 智能体调用的业务工具（包含跨通道共享工具与 AI 专属工具），
 * 负责依据 [ToolExposurePolicy] 实施核心分级暴露，并将工具定义转化为 Gemini 官方 Function Calling 声明。
 *
 * 【架构红线】
 * 1. 彻底废除全量字典暴露函数 (buildFullGeminiTools)，彻底消除工具列表膨胀引发的模型幻觉与上下文浪费；
 * 2. 严禁反向 import [org.gemini.ui.forge.service.tool.mcp.*]，AI 域工具与 MCP 域工具物理隔离。
 */
object AiToolRegistry {

    /** AI 专属基础与被动发现工具集合 */
    val builtinAiTools: List<ToolDefinition> = listOf(
        DiscoverToolsTool(),
        GetCurrentTimeTool(),
        CalculateGeometryTool(),
        WebFetchTool(),
        ReadProjectFileTool(),
        ListAssetsTool(),
        SearchSkillsTool(),
        ReadSkillSpecTool()
    )

    init {
        // 向全局工具中枢注册 AI 工具源
        ToolHub.registerSource("ai") { getAllAvailableTools() }
    }

    /**
     * 获取全部供 AI 调用的可用工具集（过滤掉禁止暴露给 AI 的内部工具）
     */
    fun getAllAvailableTools(): List<ToolDefinition> {
        val sharedTools = listOf(
            // 模板与图元编辑共享工具
            CreateTemplateTool(),
            GetTemplateTool(),
            ListTemplatesTool(),
            AddBlockTool(),
            UpdateBlockTool(),
            MoveBlockTool(),
            DeleteBlockTool(),
            SetBlockReferenceImageTool(),
            AnalyzeReferenceGenerateTemplateTool(),
            ImportAssetSheetPipelineTool(),
            ManageAssetLibraryTool(),
            GetAssetsTool(),

            // 图像生成与处理共享工具
            GenerateImageTool(),
            GenerateBlockAssetTool(),
            GenerateAllAssetsTool(),
            ChatRefineImageTool(),
            RemoveBackgroundTool(),
            BakeNinePatchTool(),

            // 离屏渲染与视觉比对共享工具
            ComposePageScreenshotTool(),
            CompareWithReferenceTool(),
            ScreenshotWindowTool(),
            RenderTemplateOverlayTool(),
            InspectBlockWithReferenceTool(),
            ExecuteUiActionSequenceTool(),

            // 会话通信与运行状态感知共享工具
            ListChatSessionsTool(),
            GetTrafficRecordTool(),
            GetRuntimeStatusTool(),
            CheckEnvTool(),
            GetJobStatusTool(),
            CancelJobTool(),

            // 提示词资产管理共享工具
            ListPromptsTool(),
            GetPromptTool(),
            SavePromptTool(),
            ResetPromptTool(),
            ListMattingPresetsTool(),
            InvokeGeminiTextTool()
        ).filter { ToolExposurePolicy.isExposedToAi(it.name) }

        val builtinNames = builtinAiTools.map { it.name }.toSet()
        val uniqueSharedTools = sharedTools.filter { it.name !in builtinNames }
        return builtinAiTools + uniqueSharedTools
    }

    /**
     * 根据工具名称查找对应实现
     */
    fun findTool(name: String): ToolDefinition? = ToolHub.findTool(name)

    /**
     * 将 [ToolDefinition] 转化为 Gemini 官方标准的 FunctionDeclaration JSON 对象
     */
    fun toGeminiFunctionDeclaration(tool: ToolDefinition, presetImageModel: String? = null): JsonObject {
        val effectivePreset = presetImageModel
        val finalSchema = if (tool.name in setOf("generate_block_asset", "generate_all_assets", "generate_image")) {
            patchImageGenSchema(tool.inputSchema, effectivePreset)
        } else {
            tool.inputSchema
        }
        return buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", finalSchema)
        }
    }

    /**
     * 针对生图类工具，根据默认生图模型预设动态调整 inputSchema 的 modelName 参数
     */
    private fun patchImageGenSchema(originalSchema: JsonObject, presetModel: String?): JsonObject {
        val originalProps = originalSchema["properties"]?.jsonObject ?: return originalSchema
        val modelProp = originalProps["modelName"]?.jsonObject ?: return originalSchema

        val newModelProp = buildJsonObject {
            modelProp.forEach { (k, v) -> put(k, v) }
            if (!presetModel.isNullOrBlank()) {
                put("description", "当前系统已预设并锁定生图模型为: $presetModel。调用必须使用此模型。")
                put("enum", buildJsonArray { add(presetModel) })
            } else {
                val specializedModels = org.gemini.ui.forge.model.StudioModelResolver.resolveStudioModels()
                    .filter { it.group == org.gemini.ui.forge.model.ModelStudioGroup.IMAGE_SPECIALIZED }
                    .map { it.model.modelName }
                if (specializedModels.isNotEmpty()) {
                    put("description", "可选生图模型，默认为 ${specializedModels.first()}。可用列表: ${specializedModels.joinToString(", ")}")
                    put("enum", buildJsonArray { specializedModels.forEach { add(it) } })
                }
            }
        }

        val newProps = buildJsonObject {
            originalProps.forEach { (k, v) ->
                if (k == "modelName") put(k, newModelProp) else put(k, v)
            }
        }

        return buildJsonObject {
            originalSchema.forEach { (k, v) ->
                if (k == "properties") put(k, newProps) else put(k, v)
            }
        }
    }

    /**
     * UI Studio 默认下发的精炼核心工具与元工具集（严格遵循 ToolExposurePolicy 核心清单）
     * 过滤一切噪声工具，仅下发系统核心交互工具与 DiscoverToolsTool 元工具。
     */
    fun buildStudioCoreGeminiTools(
        enableGoogleSearch: Boolean = true,
        presetImageModel: String? = null,
        extraExplicitToolNames: Set<String> = emptySet()
    ): List<GeminiToolConfig> {
        val coreNames = ToolExposurePolicy.CORE_TOOL_NAMES + extraExplicitToolNames
        val allTools = getAllAvailableTools()
        val filteredTools = allTools.filter { tool ->
            ToolExposurePolicy.isCoreExposed(tool.name) || tool.name in extraExplicitToolNames
        }
        val declarations = filteredTools.map { toGeminiFunctionDeclaration(it, presetImageModel) }

        val configs = mutableListOf<GeminiToolConfig>()
        configs.add(
            GeminiToolConfig(
                functionDeclarations = declarations,
                googleSearch = if (enableGoogleSearch) buildJsonObject {} else null
            )
        )
        return configs
    }
}

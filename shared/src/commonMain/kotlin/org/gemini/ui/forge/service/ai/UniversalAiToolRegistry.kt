package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.tools.CalculateGeometryTool
import org.gemini.ui.forge.service.ai.tools.GetCurrentTimeTool
import org.gemini.ui.forge.service.ai.tools.ListToolsTool
import org.gemini.ui.forge.service.ai.tools.ReadProjectFileTool
import org.gemini.ui.forge.service.ai.tools.ReadSkillSpecTool
import org.gemini.ui.forge.service.ai.tools.SearchSkillsTool
import org.gemini.ui.forge.service.ai.tools.SearchToolsTool
import org.gemini.ui.forge.service.ai.tools.WebFetchTool
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolRegistry

/**
 * UI Studio 全能 AI 工具总控注册中心
 *
 * 聚合原生 43 个 MCP 核心工具与 4 个通用内置 AI 标配工具，统一转换为
 * Gemini 官方 Function Calling 与 Google Search Grounding 声明。
 */
object UniversalAiToolRegistry {

    /** 通用内置 AI 基础与探索元工具清单 */
    val builtinAiTools: List<McpToolDefinition> = listOf(
        GetCurrentTimeTool(),
        CalculateGeometryTool(),
        WebFetchTool(),
        ReadProjectFileTool(),
        SearchToolsTool(),
        ListToolsTool(),
        SearchSkillsTool(),
        ReadSkillSpecTool()
    )

    /** 排除工具黑名单（防止递归自调用死循环或纯底层私有探测） */
    private val EXCLUDED_TOOL_NAMES = setOf(
        "invoke_gemini_text",
        "trigger_initial_verification"
    )

    /**
     * 获取全部供 AI 调用的可用工具集（内置标配 + MCP 开放工具）
     */
    fun getAllAvailableTools(): List<McpToolDefinition> {
        val mcpTools = McpToolRegistry.tools.value.filter { it.name !in EXCLUDED_TOOL_NAMES }
        val builtinNames = builtinAiTools.map { it.name }.toSet()
        val uniqueMcpTools = mcpTools.filter { it.name !in builtinNames }
        return builtinAiTools + uniqueMcpTools
    }

    /**
     * 根据工具名称查找对应实现
     */
    fun findTool(name: String): McpToolDefinition? {
        return builtinAiTools.firstOrNull { it.name == name }
            ?: McpToolRegistry.findTool(name)
    }

    /**
     * 将 McpToolDefinition 转化为 Gemini 官方标准的 FunctionDeclaration JSON 对象
     */
    fun McpToolDefinition.toGeminiFunctionDeclaration(presetImageModel: String? = null): JsonObject {
        val effectivePreset = presetImageModel
        val finalSchema = if (name in setOf("generate_block_asset", "generate_all_assets", "generate_image")) {
            patchImageGenSchema(inputSchema, effectivePreset)
        } else {
            inputSchema
        }
        return buildJsonObject {
            put("name", name)
            put("description", description)
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
     * 组装全量 Gemini Tool 配置（包含全部函数声明与可选的 Google 官方实时联网搜索）
     */
    fun buildFullGeminiTools(
        enableGoogleSearch: Boolean = true,
        presetImageModel: String? = null
    ): List<GeminiToolConfig> {
        val allTools = getAllAvailableTools()
        val declarations = allTools.map { it.toGeminiFunctionDeclaration(presetImageModel) }

        val configs = mutableListOf<GeminiToolConfig>()
        configs.add(
            GeminiToolConfig(
                functionDeclarations = declarations,
                googleSearch = if (enableGoogleSearch) buildJsonObject {} else null
            )
        )
        return configs
    }

    /**
     * UI Studio 默认下发的精炼核心工具与元工具集（过滤 Slots 模板底层运维等噪音工具）
     */
    fun buildStudioCoreGeminiTools(
        enableGoogleSearch: Boolean = true,
        presetImageModel: String? = null,
        extraExplicitToolNames: Set<String> = emptySet()
    ): List<GeminiToolConfig> {
        val excludedStudioNoise = setOf(
            "list_templates",
            "get_template",
            "create_template",
            "delete_template",
            "duplicate_template",
            "rename_template",
            "list_matting_presets",
            "check_environment"
        )
        // 核心工作区工具名：内置基础工具与探索元工具 + UI Studio 核心交互工具
        val coreStudioToolNames = setOf(
            "apply_view_layout",
            "generate_image",
            "generate_block_asset",
            "crop_image",
            "remove_image_background",
            "get_runtime_status"
        ) + builtinAiTools.map { it.name }.toSet() + extraExplicitToolNames

        val allTools = getAllAvailableTools()
        val filteredTools = allTools.filter { tool ->
            tool.name !in excludedStudioNoise && tool.name in coreStudioToolNames
        }
        val declarations = filteredTools.map { it.toGeminiFunctionDeclaration(presetImageModel) }

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

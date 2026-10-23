package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.ai.tools.CalculateGeometryTool
import org.gemini.ui.forge.service.ai.tools.GetCurrentTimeTool
import org.gemini.ui.forge.service.ai.tools.ReadProjectFileTool
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

    /** 通用内置 AI 基础工具清单 */
    val builtinAiTools: List<McpToolDefinition> = listOf(
        GetCurrentTimeTool(),
        CalculateGeometryTool(),
        WebFetchTool(),
        ReadProjectFileTool()
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
    fun McpToolDefinition.toGeminiFunctionDeclaration(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", inputSchema)
        }
    }

    /**
     * 组装全量 Gemini Tool 配置（包含全部函数声明与可选的 Google 官方实时联网搜索）
     */
    fun buildFullGeminiTools(enableGoogleSearch: Boolean = true): List<GeminiToolConfig> {
        val allTools = getAllAvailableTools()
        val declarations = allTools.map { it.toGeminiFunctionDeclaration() }

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

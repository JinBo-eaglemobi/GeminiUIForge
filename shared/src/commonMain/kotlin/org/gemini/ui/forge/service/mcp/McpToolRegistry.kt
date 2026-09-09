package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.service.mcp.tools.CheckEnvTool
import org.gemini.ui.forge.service.mcp.tools.GetTemplateTool
import org.gemini.ui.forge.service.mcp.tools.ListTemplatesTool

/**
 * MCP 工具注册中心单例。
 */
object McpToolRegistry {
    private val _tools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val tools: StateFlow<List<McpToolDefinition>> = _tools.asStateFlow()

    init {
        registerBuiltinTools()
    }

    private fun registerBuiltinTools() {
        val builtin = listOf(
            ListTemplatesTool(),
            GetTemplateTool(),
            CheckEnvTool()
        )
        _tools.value = builtin
    }

    fun registerTool(tool: McpToolDefinition) {
        if (_tools.value.none { it.name == tool.name }) {
            _tools.value = _tools.value + tool
        }
    }

    fun findTool(name: String): McpToolDefinition? =
        _tools.value.firstOrNull { it.name == name }
}

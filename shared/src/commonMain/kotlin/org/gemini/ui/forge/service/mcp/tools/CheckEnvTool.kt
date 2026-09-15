package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.createEnvironmentCheckService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 检查当前主机的依赖与运行环境
 */
class CheckEnvTool : McpToolDefinition {

    override val name: String = "check_environment"

    override val description: String =
        "检查宿主机的运行环境状态，包括 Python 是否就绪、关键 Pip 包安装情况等。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        onProgress?.invoke(0.2f, "正在探测 Python 与本地环境...")
        val envService = createEnvironmentCheckService()
        val isPythonOk = envService.isPythonAvailable()
        onProgress?.invoke(0.6f, "正在核对依赖包版本与就绪状态...")
        val status = envService.checkAll()
        onProgress?.invoke(1.0f, "环境检测完成")

        val resultJson = buildJsonObject {
            put("isPythonAvailable", isPythonOk)
            put("isAllReady", status.isAllReady)
            put("dependencies", buildJsonArray {
                status.items.forEach { item ->
                    add(buildJsonObject {
                        put("name", item.name)
                        put("isInstalled", item.isInstalled)
                        put("version", item.version)
                    })
                }
            })
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

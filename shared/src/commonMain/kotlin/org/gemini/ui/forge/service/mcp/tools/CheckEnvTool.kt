package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.createEnvironmentCheckService
import org.gemini.ui.forge.service.mcp.McpToolDefinition

/**
 * 检查当前主机的依赖与运行环境
 */
class CheckEnvTool : McpToolDefinition {

    override val name: String = "check_environment"

    override val description: String =
        "检查宿主机的运行环境状态，包括 Python 是否就绪、关键 Pip 包安装情况等。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(arguments: JsonObject): String {
        val envService = createEnvironmentCheckService()
        val isPythonOk = envService.isPythonAvailable()
        val status = envService.checkAll()

        return buildJsonObject {
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
    }
}

package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.service.mcp.McpInteractionBridge
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 获取应用运行环境状态工具
 */
class GetRuntimeStatusTool(
    private val configManager: ConfigManager = ConfigManager(),
    private val repository: TemplateRepository = TemplateRepository(),
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : McpToolDefinition {

    override val name: String = "get_runtime_status"

    override val description: String =
        "获取应用程序当前的整体运行状态，包括软件版本、UI 界面连接状态、已配置的 API 密钥脱敏信息、当前工作区工程总数及提示词定制统计。"

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
        onProgress?.invoke(0.2f, "正在收集运行时健康数据...")

        val templates = try {
            repository.getTemplates()
        } catch (_: Exception) {
            emptyList()
        }

        val geminiKey = configManager.loadKey("GEMINI_API_KEY")?.ifBlank { null }
            ?: configManager.loadGlobalGeminiKey()?.ifBlank { null }
        val nanobananaKey = configManager.loadKey("NANOBANANA_API_KEY")?.ifBlank { null }
        val baseUrl = configManager.loadKey("GEMINI_BASE_URL")?.ifBlank { null }
            ?: "https://generativelanguage.googleapis.com"

        fun maskKey(key: String?): String {
            if (key.isNullOrBlank()) return "UNSET"
            if (key.length <= 8) return "******"
            return "${key.take(4)}...${key.takeLast(4)}"
        }

        var customizedPromptsCount = 0
        promptManager.promptMetas.forEach { meta ->
            try {
                if (promptManager.isCustomized(meta.id)) {
                    customizedPromptsCount++
                }
            } catch (_: Exception) {}
        }

        val resultJson = buildJsonObject {
            put("version", ProjectConfig.VERSION)
            put("uiReachable", McpInteractionBridge.isUiReachable())
            put("templatesCount", templates.size)
            put("customizedPromptsCount", customizedPromptsCount)
            put("totalRegisteredPrompts", promptManager.promptMetas.size)
            put("apiCredentials", buildJsonObject {
                put("geminiKeyConfigured", !geminiKey.isNullOrBlank())
                put("geminiKeyMasked", maskKey(geminiKey))
                put("nanobananaKeyConfigured", !nanobananaKey.isNullOrBlank())
                put("nanobananaKeyMasked", maskKey(nanobananaKey))
                put("geminiBaseUrl", baseUrl)
            })
        }

        return McpToolResult.success(resultJson.toString())
    }
}

package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 恢复出厂默认提示词工具
 */
class ResetPromptTool(
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : McpToolDefinition {

    override val name: String = "reset_prompt"

    override val description: String =
        "清除外部本地对指定提示词模板的自定义覆盖，回滚至系统出厂内置预设。注意：若进行全局重置或删除重要自定义配置，建议先通过 ask_user_confirmation 获得人工授权。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "要重置的提示词模板 ID（如 'analyze_template', 'IMAGE_TO_UI_SPEC' 等）")
            })
        })
        put("required", buildJsonArray {
            add("id")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'id' 不能为空")

        val meta = promptManager.promptMetas.find { it.id == id }
            ?: return McpToolResult.error("未找到标识符为 '$id' 的提示词模板。请调用 list_prompts 查看有效列表。")

        onProgress?.invoke(0.3f, "正在清除外部自定义缓存...")
        val existed = promptManager.hasPhysicalExternalFile(id)
        val resetSuccess = promptManager.resetPrompt(id)

        val defaultContent = try {
            promptManager.getDefaultResourcePrompt(id)
        } catch (_: Exception) {
            ""
        }

        val resultJson = buildJsonObject {
            put("id", meta.id)
            put("displayName", meta.displayNameZh)
            put("hadExternalOverride", existed)
            put("resetSuccess", resetSuccess)
            put("currentEffectiveDefaultLength", defaultContent.length)
        }

        return McpToolResult.success(resultJson.toString())
    }
}

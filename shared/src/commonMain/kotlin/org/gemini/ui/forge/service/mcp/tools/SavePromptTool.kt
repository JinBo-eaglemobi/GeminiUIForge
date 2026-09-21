package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 保存或覆盖提示词工具
 */
class SavePromptTool(
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : McpToolDefinition {

    override val name: String = "save_prompt"

    override val description: String =
        "保存或更新指定的 AI 提示词/规范模板内容。修改后立即持久化至本地外部存储目录 (~/.geminiuiforge/prompts/) 并对后续全部生成任务即时生效。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "要更新的提示词模板 ID（如 'analyze_template', 'IMAGE_TO_UI_SPEC' 等）")
            })
            put("content", buildJsonObject {
                put("type", "string")
                put("description", "新的完整提示词或规范文本内容（不能为空白）")
            })
        })
        put("required", buildJsonArray {
            add("id")
            add("content")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'id' 不能为空")
        val content = arguments["content"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'content' 不能为空")

        if (content.isBlank()) {
            return McpToolResult.error("提示词内容不能为空白字符串")
        }

        val meta = promptManager.promptMetas.find { it.id == id }
            ?: return McpToolResult.error("未找到标识符为 '$id' 的提示词模板。请调用 list_prompts 查看有效列表。")

        onProgress?.invoke(0.3f, "正在写入外部本地存储...")
        val success = promptManager.savePrompt(id, content)

        if (!success) {
            return McpToolResult.error("保存提示词 [$id] 到本地磁盘失败，请检查文件系统写权限")
        }

        val isCustomized = try {
            promptManager.isCustomized(id)
        } catch (_: Exception) {
            true
        }

        val resultJson = buildJsonObject {
            put("id", meta.id)
            put("displayName", meta.displayNameZh)
            put("fileName", meta.fileName)
            put("saved", true)
            put("isCustomized", isCustomized)
            put("length", content.length)
        }

        return McpToolResult.success(resultJson.toString())
    }
}

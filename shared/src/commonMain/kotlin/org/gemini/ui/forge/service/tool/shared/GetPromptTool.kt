package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 获取指定提示词的完整文本工具
 */
class GetPromptTool(
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : ToolDefinition {

    override val name: String = "get_prompt"

    override val description: String =
        "获取指定 AI 提示词或规范模板的完整文本内容。可选择获取当前最终生效的文本，或仅获取系统出厂原始预设。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "提示词模板唯一标识 ID（可通过 list_prompts 获取，如 'analyze_template', 'IMAGE_TO_UI_SPEC', 'ai_optimize_prompt'）")
            })
            put("onlyDefault", buildJsonObject {
                put("type", "boolean")
                put("description", "是否强制仅读取内部出厂内置预设（忽略外部用户自定义修改），默认 false")
            })
        })
        put("required", buildJsonArray {
            add("id")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'id' 不能为空")
        val onlyDefault = arguments["onlyDefault"]?.jsonPrimitive?.booleanOrNull ?: false

        val meta = promptManager.promptMetas.find { it.id == id }
            ?: return ToolResult.error("未找到标识符为 '$id' 的提示词模板。请调用 list_prompts 查看有效列表。")

        onProgress?.invoke(0.3f, "正在加载提示词 [$id] 内容...")

        return try {
            val content = if (onlyDefault) {
                promptManager.getDefaultResourcePrompt(id)
            } else {
                promptManager.getPrompt(id)
            }

            val isCustomized = try {
                promptManager.isCustomized(id)
            } catch (_: Exception) {
                false
            }

            val resultJson = buildJsonObject {
                put("id", meta.id)
                put("displayName", meta.displayNameZh)
                put("isMarkdown", meta.isMarkdown)
                put("isCustomized", isCustomized)
                put("isDefault", onlyDefault || !isCustomized)
                put("length", content.length)
                put("content", content)
            }

            ToolResult.success(resultJson.toString())
        } catch (e: Exception) {
            ToolResult.error("读取提示词 [$id] 失败: ${e.message}")
        }
    }
}


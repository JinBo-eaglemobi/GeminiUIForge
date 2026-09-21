package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 列出所有 AI 提示词与规范模板清单及定制状态工具
 */
class ListPromptsTool(
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : McpToolDefinition {

    override val name: String = "list_prompts"

    override val description: String =
        "列出系统中所有注册的 AI 提示词模板、系统微调指令与组件规范元数据，包含各模板的唯一标识 (id)、业务显示名称、用途描述以及当前是否已被用户自定义修改。"

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
        onProgress?.invoke(0.3f, "正在读取提示词元数据与自定义状态...")
        val promptsArray = buildJsonArray {
            promptManager.promptMetas.forEach { meta ->
                val isCustomized = try {
                    promptManager.isCustomized(meta.id)
                } catch (_: Exception) {
                    false
                }
                val hasPhysicalFile = try {
                    promptManager.hasPhysicalExternalFile(meta.id)
                } catch (_: Exception) {
                    false
                }

                add(buildJsonObject {
                    put("id", meta.id)
                    put("displayName", meta.displayNameZh)
                    put("description", meta.descZh)
                    put("fileName", meta.fileName)
                    put("isMarkdown", meta.isMarkdown)
                    put("isCustomized", isCustomized)
                    put("hasExternalOverride", hasPhysicalFile)
                })
            }
        }

        val resultJson = buildJsonObject {
            put("count", promptManager.promptMetas.size)
            put("prompts", promptsArray)
        }

        return McpToolResult.success(resultJson.toString())
    }
}

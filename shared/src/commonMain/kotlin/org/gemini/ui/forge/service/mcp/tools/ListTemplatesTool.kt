package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 列出所有已保存的项目模板列表
 */
class ListTemplatesTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "list_templates"

    override val description: String =
        "列出本地保存的所有 UI 游戏项目模板的概要信息（模板名称、画布页面尺寸、图元数量）。"

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
        onProgress?.invoke(0.3f, "正在检索本地工程模板...")
        val templates = repository.getTemplates()
        onProgress?.invoke(0.8f, "正在组装模板概要清单 (${templates.size} 个)...")
        val array = buildJsonArray {
            templates.forEach { (name, state) ->
                val firstPage = state.pages.firstOrNull()
                add(buildJsonObject {
                    put("name", name)
                    put("canvasWidth", firstPage?.width ?: 1280)
                    put("canvasHeight", firstPage?.height ?: 720)
                    put("blockCount", firstPage?.blocks?.size ?: 0)
                })
            }
        }
        onProgress?.invoke(1.0f, "模板清单检索就绪")
        return McpToolResult.text(array.toString())
    }
}

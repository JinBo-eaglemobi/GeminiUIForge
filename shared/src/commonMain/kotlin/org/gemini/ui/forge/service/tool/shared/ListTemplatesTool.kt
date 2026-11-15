package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 列出所有已保存的 UI Studio 项目工程列表
 */
class ListTemplatesTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "list_templates"

    override val description: String =
        "列出本地保存的所有 UI Studio 游戏项目工程的概要信息（工程标识名、视图列表、图元总数）。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
    ): ToolResult {
        onProgress?.invoke(0.3f, "正在检索本地工程列表...")
        val templates = repository.getTemplates()
        onProgress?.invoke(0.8f, "正在组装工程概要清单 (${templates.size} 个)...")
        val array = buildJsonArray {
            templates.forEach { (dirTitle, state) ->
                val pages = state.studioPages
                val firstPage = pages.firstOrNull()
                val effectiveProjectName = state.projectId.ifBlank { repository.sanitizeName(dirTitle) }
                add(buildJsonObject {
                    put("projectName", effectiveProjectName)
                    put("canvasWidth", firstPage?.width ?: 1280f)
                    put("canvasHeight", firstPage?.height ?: 720f)
                    put("viewCount", pages.size)
                    put("views", buildJsonArray { pages.forEach { add(it.id) } })
                    put("blockCount", pages.sumOf { it.blocks.size })
                })
            }
        }
        onProgress?.invoke(1.0f, "工程清单检索就绪")
        return ToolResult.text(array.toString())
    }
}

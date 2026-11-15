package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 删除 UI Studio 指定图元及其子节点工具（100% 复用 UiStudioLayoutService.deleteStudioBlock 公用函数）
 */
class DeleteBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "delete_block"

    override val description: String =
        "从指定 UI Studio 工程的视图中永久删除某个图元组件块及其所有的嵌套子图元节点。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "工程项目名称")
            })
            put("viewName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标视图 ID（如 'MainPage'）")
            })
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "要删除的目标图元唯一 id")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("id")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val targetId = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'id' 不能为空")
        val viewName = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()

        val res = UiStudioLayoutService.deleteStudioBlock(
            projectName = projectName,
            viewName = viewName,
            id = targetId,
            repository = repository,
            recordUndo = true,
            syncActiveWorkspace = true
        )

        return res.fold(
            onSuccess = { viewId ->
                ToolResult.text(buildJsonObject {
                    put("success", true)
                    put("viewId", viewId)
                    put("deletedId", targetId)
                }.toString())
            },
            onFailure = { err -> ToolResult.error(err.message ?: "删除图元失败") }
        )
    }
}

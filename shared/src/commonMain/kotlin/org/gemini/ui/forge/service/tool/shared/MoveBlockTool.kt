package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 移动/调整 UI Studio 图元层级或顺序工具（100% 复用 UiStudioLayoutService.moveStudioBlock 公用函数）
 */
class MoveBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "move_block"

    override val description: String =
        "移动或重排序 UI Studio 图元节点。可将图元移入新的父容器内部（自动执行局部相对坐标代数守恒换算），或调整在兄弟节点列表中的排列索引。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
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
                put("description", "要移动的目标图元唯一 id")
            })
            put("newParentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：新父级容器的 id；为 null 或空时移至页面根层级")
            })
            put("insertIndex", buildJsonObject {
                put("type", "integer")
                put("description", "可选：插入到目标容器子列表中的位置索引，默认追加到末尾")
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
        val newParentId = arguments["newParentId"]?.jsonPrimitive?.contentOrNull
        val insertIndex = arguments["insertIndex"]?.jsonPrimitive?.intOrNull

        val res = UiStudioLayoutService.moveStudioBlock(
            projectName = projectName,
            viewName = viewName,
            id = targetId,
            newParentId = newParentId,
            insertIndex = insertIndex,
            preserveAbsolutePosition = true,
            repository = repository,
            recordUndo = true,
            syncActiveWorkspace = true
        )

        return res.fold(
            onSuccess = { viewId ->
                ToolResult.text(buildJsonObject {
                    put("success", true)
                    put("viewId", viewId)
                    put("id", targetId)
                    put("newParentId", newParentId)
                }.toString())
            },
            onFailure = { err -> ToolResult.error(err.message ?: "移动图元失败") }
        )
    }
}

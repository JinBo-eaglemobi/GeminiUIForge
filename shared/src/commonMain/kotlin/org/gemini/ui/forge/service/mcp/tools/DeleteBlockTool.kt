package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 删除指定图元及其子节点工具
 */
class DeleteBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "delete_block"

    override val description: String =
        "从指定模板中永久删除某个图元组件块及其所有的嵌套子图元节点。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "要删除的目标图元 blockId")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")

        onProgress?.invoke(0.2f, "正在加载工程结构...")
        val match = repository.findTemplatePair(projectName)
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        var deletedCount = 0

        fun removeRecursively(blocks: List<UIBlock>): List<UIBlock> {
            val result = mutableListOf<UIBlock>()
            for (b in blocks) {
                if (b.id == blockId) {
                    deletedCount++
                    continue
                }
                val updatedChildren = removeRecursively(b.children)
                result.add(b.copy(children = updatedChildren))
            }
            return result
        }

        onProgress?.invoke(0.5f, "正在从模块树中剔除节点...")
        val updatedPages = state.pages.map { page ->
            page.copy(blocks = removeRecursively(page.blocks))
        }

        if (deletedCount == 0) {
            return McpToolResult.error("在模板 '$projectName' 中未找到 ID 为 '$blockId' 的图元")
        }

        onProgress?.invoke(0.8f, "正在持久化最新工程状态...")
        repository.saveTemplate(projectName, state.copy(pages = updatedPages))
        onProgress?.invoke(1.0f, "图元已成功移除")

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectName)
            put("deletedBlockId", blockId)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

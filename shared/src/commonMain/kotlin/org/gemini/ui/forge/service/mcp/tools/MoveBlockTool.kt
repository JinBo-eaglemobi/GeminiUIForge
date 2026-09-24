package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 移动/调整图元层级或顺序工具
 */
class MoveBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "move_block"

    override val description: String =
        "移动或重排序图元节点。可将图元移入新的父容器内部，或调整在兄弟节点列表中的排列索引。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
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
                put("description", "要移动的目标图元 ID")
            })
            put("newParentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：新父级容器的 ID；为 null 或空时移至页面根层级")
            })
            put("insertIndex", buildJsonObject {
                put("type", "integer")
                put("description", "可选：插入到目标容器子列表中的位置索引，默认追加到末尾")
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
        val newParentId = arguments["newParentId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val insertIndex = arguments["insertIndex"]?.jsonPrimitive?.intOrNull

        onProgress?.invoke(0.2f, "正在读取工程树...")
        val match = repository.findTemplatePair(projectName)
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        var targetBlock: UIBlock? = null

        // 1. 递归查找并摘除目标节点
        fun extractBlock(blocks: List<UIBlock>): List<UIBlock> {
            val result = mutableListOf<UIBlock>()
            for (b in blocks) {
                if (b.id == blockId) {
                    targetBlock = b
                } else {
                    val updatedChildren = extractBlock(b.children)
                    result.add(b.copy(children = updatedChildren))
                }
            }
            return result
        }

        val page = state.pages.firstOrNull() ?: return McpToolResult.error("工程未包含有效页面")
        val blocksWithoutTarget = extractBlock(page.blocks)

        val extracted = targetBlock ?: return McpToolResult.error("未找到 ID 为 '$blockId' 的图元")

        // 2. 插入到新位置
        fun insertIntoTree(blocks: List<UIBlock>, parentId: String?): List<UIBlock> {
            if (parentId == null) {
                val list = blocks.toMutableList()
                val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                list.add(idx, extracted)
                return list
            }
            return blocks.map { b ->
                if (b.id == parentId) {
                    val list = b.children.toMutableList()
                    val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                    list.add(idx, extracted)
                    b.copy(children = list)
                } else {
                    b.copy(children = insertIntoTree(b.children, parentId))
                }
            }
        }

        onProgress?.invoke(0.6f, "正在重新拼接图元层级...")
        val newBlocks = insertIntoTree(blocksWithoutTarget, newParentId)
        val updatedPage = page.copy(blocks = newBlocks)

        onProgress?.invoke(0.8f, "正在保存最新层级树...")
        val updatedProject = state.copy(pages = listOf(updatedPage))
        repository.saveTemplate(projectName, updatedProject)

        // ★ 同步通知当前活跃工作区刷新内存状态与画布渲染
        try {
            val activeVm = org.gemini.ui.forge.service.mcp.McpUiActionPipeline.getActiveViewModel()
            if (activeVm != null) {
                activeVm.reload(updatedProject)
            }
        } catch (e: Throwable) {
            org.gemini.ui.forge.utils.AppLogger.w("MoveBlockTool", "通知活跃工作区刷新失败", e)
        }

        onProgress?.invoke(1.0f, "图元移动完成")

        val resultJson = buildJsonObject {
            put("success", true)
            put("blockId", blockId)
            put("newParentId", newParentId)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

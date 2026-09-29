package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.TemplateOverlayRenderer
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson

/**
 * 新增图元组件块工具
 */
class AddBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "add_block"

    override val description: String =
        "向指定模板中新增图元组件块。支持指定父级容器 (parentId) 或追加到页面根层级，支持配置坐标 (bounds)、切片区域 (cropRect)、提示词及纯容器标识，自动离屏刷新标注图并同步活跃工作区。"

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
            put("parentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：父级容器的 ID；为 null 或空时添加到页面根层级")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "可选：新图元的唯一标识 ID（缺省时自动生成）")
            })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "可选：图元组件类型（如 BUTTON, IMAGE, TEXT, CONTAINER, REEL, SYMBOL, SPIN_BUTTON 等，默认 IMAGE）")
            })
            put("bounds", buildJsonObject {
                put("type", "object")
                put("description", "必填：局部相对坐标与尺寸对象 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
                put("required", buildJsonArray {
                    add("left")
                    add("top")
                    add("right")
                    add("bottom")
                })
            })
            put("cropRect", buildJsonObject {
                put("type", "object")
                put("description", "可选：参考图局部裁切区域坐标 { left, top, right, bottom }（缺省时自动基于父容器绝对位移推导）")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
            put("isPureContainer", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否为纯容器/占位层（true 表示仅用于排版，不参与任何 AI 图片资源生成，默认 false）")
            })
            put("userPromptZh", buildJsonObject {
                put("type", "string")
                put("description", "可选：中文 AI 生图提示词")
            })
            put("userPromptEn", buildJsonObject {
                put("type", "string")
                put("description", "可选：英文 AI 生图提示词")
            })
            put("insertIndex", buildJsonObject {
                put("type", "integer")
                put("description", "可选：插入到目标容器子列表中的位置索引，默认追加到末尾")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("bounds")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")

        val boundsObj = arguments["bounds"]?.let { it as? JsonObject }
            ?: return McpToolResult.error("参数 'bounds' 必须是包含 left, top, right, bottom 的对象")

        val bLeft = boundsObj["left"]?.jsonPrimitive?.floatOrNull
            ?: return McpToolResult.error("bounds.left 不能为空")
        val bTop = boundsObj["top"]?.jsonPrimitive?.floatOrNull
            ?: return McpToolResult.error("bounds.top 不能为空")
        val bRight = boundsObj["right"]?.jsonPrimitive?.floatOrNull
            ?: return McpToolResult.error("bounds.right 不能为空")
        val bBottom = boundsObj["bottom"]?.jsonPrimitive?.floatOrNull
            ?: return McpToolResult.error("bounds.bottom 不能为空")

        val parentId = arguments["parentId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val insertIndex = arguments["insertIndex"]?.jsonPrimitive?.intOrNull

        val typeStr = arguments["type"]?.jsonPrimitive?.contentOrNull ?: "IMAGE"
        val blockType = try {
            UIBlockType.valueOf(typeStr.uppercase())
        } catch (_: Throwable) {
            UIBlockType.IMAGE
        }

        val rawBlockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val finalBlockId = rawBlockId ?: "${blockType.name.lowercase()}_${kotlin.random.Random.nextInt(1000, 9999)}"

        val isPureContainer = arguments["isPureContainer"]?.jsonPrimitive?.booleanOrNull ?: false
        val userPromptZh = arguments["userPromptZh"]?.jsonPrimitive?.contentOrNull ?: ""
        val userPromptEn = arguments["userPromptEn"]?.jsonPrimitive?.contentOrNull ?: ""

        onProgress?.invoke(0.2f, "正在读取工程结构...")
        val match = repository.findTemplatePair(projectName)
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val page = state.pages.firstOrNull()
            ?: return McpToolResult.error("工程未包含有效页面")

        // 检查 ID 是否重复
        fun checkIdExists(blocks: List<UIBlock>): Boolean {
            for (b in blocks) {
                if (b.id == finalBlockId) return true
                if (checkIdExists(b.children)) return true
            }
            return false
        }

        if (checkIdExists(page.blocks)) {
            return McpToolResult.error("图元 ID '$finalBlockId' 在模板工程中已存在，请指定不同的 blockId")
        }

        // 计算父容器绝对偏移量
        var foundParent = false
        var parentOffsetX = 0f
        var parentOffsetY = 0f

        fun findParentOffset(blocks: List<UIBlock>, curX: Float, curY: Float): Boolean {
            for (b in blocks) {
                if (b.id == parentId) {
                    parentOffsetX = curX + b.bounds.left
                    parentOffsetY = curY + b.bounds.top
                    foundParent = true
                    return true
                }
                if (findParentOffset(b.children, curX + b.bounds.left, curY + b.bounds.top)) {
                    return true
                }
            }
            return false
        }

        if (parentId != null) {
            findParentOffset(page.blocks, 0f, 0f)
            if (!foundParent) {
                return McpToolResult.error("在模板工程中未找到指定的父容器 ID '$parentId'")
            }
        }

        val localBounds = SerialRect(bLeft, bTop, bRight, bBottom)

        // 若提供了自定义 cropRect 则使用，未提供则保持为 null（杜绝将 bounds 替用落盘）
        val finalCropRect = arguments["cropRect"]?.let { it as? JsonObject }?.let { cObj ->
            val cLeft = cObj["left"]?.jsonPrimitive?.floatOrNull ?: localBounds.left
            val cTop = cObj["top"]?.jsonPrimitive?.floatOrNull ?: localBounds.top
            val cRight = cObj["right"]?.jsonPrimitive?.floatOrNull ?: localBounds.right
            val cBottom = cObj["bottom"]?.jsonPrimitive?.floatOrNull ?: localBounds.bottom
            SerialRect(cLeft, cTop, cRight, cBottom)
        }

        val newBlock = UIBlock(
            id = finalBlockId,
            type = blockType,
            bounds = localBounds,
            cropRect = finalCropRect,
            isPureContainer = isPureContainer,
            userPromptZh = userPromptZh,
            userPromptEn = userPromptEn
        )

        onProgress?.invoke(0.5f, "正在将新图元接入层级树...")

        fun insertBlockRecursively(blocks: List<UIBlock>): List<UIBlock> {
            if (parentId == null) {
                val list = blocks.toMutableList()
                val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                list.add(idx, newBlock)
                return list
            }
            return blocks.map { b ->
                if (b.id == parentId) {
                    val list = b.children.toMutableList()
                    val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                    list.add(idx, newBlock)
                    b.copy(children = list)
                } else {
                    b.copy(children = insertBlockRecursively(b.children))
                }
            }
        }

        val updatedPage = page.copy(blocks = insertBlockRecursively(page.blocks))
        val updatedProject = state.copy(pages = listOf(updatedPage))

        onProgress?.invoke(0.7f, "正在持久化工程...")
        repository.saveTemplate(projectName, updatedProject)

        // 离屏渲染更新最新标注图
        try {
            TemplateOverlayRenderer.renderAndSaveLatest(projectName, updatedProject)
        } catch (e: Exception) {
            AppLogger.w("AddBlockTool", "更新标注图异常", e)
        }

        // 同步通知当前活跃工作区刷新内存状态与画布渲染
        try {
            val activeVm = McpUiActionPipeline.getActiveViewModel()
            if (activeVm != null) {
                activeVm.reload(updatedProject)
            }
        } catch (e: Throwable) {
            AppLogger.w("AddBlockTool", "通知活跃工作区刷新失败", e)
        }

        onProgress?.invoke(1.0f, "新图元已成功添加")

        val resultJson = buildJsonObject {
            put("success", true)
            put("addedBlock", looseJson.encodeToJsonElement(newBlock))
            put("parentId", parentId)
            put("projectName", projectName)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}

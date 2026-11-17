package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.looseJson

/**
 * 向指定 UI Studio 工程视图中新增图元组件块工具（100% 复用 UiStudioLayoutService.addStudioBlock 公用函数）
 */
class AddBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "add_block"

    override val description: String =
        "向指定 UI Studio 工程视图中新增图元组件块。支持指定父级容器 (parentId) 或追加到页面根层级，支持配置坐标 (bounds)、素材哈希 (assetHash) 及纯容器标识，自动跨页同步并刷新活跃工作区。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程项目名称")
            })
            put("viewName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标视图 ID（如 'MainPage'），缺省时取首个视图")
            })
            put("parentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：父容器图元 id；留空表示添加到页面根层级")
            })
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "新图元的唯一标识 id")
            })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "图元类型，例如 VIEW, BUTTON, IMAGE, TEXT 等，默认 IMAGE")
            })
            put("bounds", buildJsonObject {
                put("description", "局部相对矩形区域，支持 [left, top, right, bottom] 数组或 { left, top, right, bottom } 对象")
            })
            put("assetHash", buildJsonObject {
                put("type", "string")
                put("description", "可选：绑定的素材切片哈希")
            })
            put("targetPageId", buildJsonObject {
                put("type", "string")
                put("description", "可选：引用的平级视图页面 ID")
            })
            put("insertIndex", buildJsonObject {
                put("type", "integer")
                put("description", "可选：插入位置索引，默认追加到末尾")
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
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val viewName = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()
        val localBounds = parseSerialRect(arguments["bounds"])
            ?: return ToolResult.error("参数 'bounds' 必须是 [left, top, right, bottom] 数组或包含 left, top, right, bottom 的对象")

        val parentId = arguments["parentId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val insertIndex = arguments["insertIndex"]?.jsonPrimitive?.intOrNull
        val typeStr = arguments["type"]?.jsonPrimitive?.contentOrNull ?: "IMAGE"
        val blockType = runCatching { UIBlockType.valueOf(typeStr.uppercase()) }.getOrDefault(UIBlockType.IMAGE)

        val rawId = arguments["id"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val finalId = rawId ?: "${blockType.name.lowercase()}_${kotlin.random.Random.nextInt(1000, 9999)}"
        val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val targetPageId = arguments["targetPageId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

        val newBlock = UIStudioBlock(
            id = finalId,
            type = if (targetPageId != null) UIBlockType.COMPONENT else blockType,
            bounds = localBounds,
            assetHash = assetHash,
            targetPageId = targetPageId
        )

        val res = UiStudioLayoutService.addStudioBlock(
            projectName = projectName,
            viewName = viewName,
            parentId = parentId,
            block = newBlock,
            insertIndex = insertIndex,
            repository = repository,
            recordUndo = true,
            syncActiveWorkspace = true
        )

        return res.fold(
            onSuccess = { (viewId, added) ->
                ToolResult.text(buildJsonObject {
                    put("success", true)
                    put("viewId", viewId)
                    put("addedBlock", looseJson.encodeToJsonElement(added))
                    put("parentId", parentId)
                }.toString())
            },
            onFailure = { err -> ToolResult.error(err.message ?: "添加图元失败") }
        )
    }

    private fun parseSerialRect(element: JsonElement?): SerialRect? {
        if (element == null) return null
        if (element is JsonArray && element.size >= 4) {
            val l = element[0].jsonPrimitive.floatOrNull ?: return null
            val t = element[1].jsonPrimitive.floatOrNull ?: return null
            val r = element[2].jsonPrimitive.floatOrNull ?: return null
            val b = element[3].jsonPrimitive.floatOrNull ?: return null
            return SerialRect(l, t, r, b)
        }
        if (element is JsonObject) {
            val l = element["left"]?.jsonPrimitive?.floatOrNull ?: return null
            val t = element["top"]?.jsonPrimitive?.floatOrNull ?: return null
            val r = element["right"]?.jsonPrimitive?.floatOrNull ?: return null
            val b = element["bottom"]?.jsonPrimitive?.floatOrNull ?: return null
            return SerialRect(l, t, r, b)
        }
        return null
    }
}

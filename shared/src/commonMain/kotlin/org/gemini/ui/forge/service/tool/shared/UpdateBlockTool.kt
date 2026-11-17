package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.service.studio.StudioAssetManagerService
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 更新 UI Studio 图元属性与元数据工具（100% 复用 UiStudioLayoutService.updateStudioBlock 公用函数）
 */
class UpdateBlockTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "update_block"

    override val description: String =
        "更新指定 UI Studio 工程视图中某个图元的属性配置。支持修改坐标与尺寸(bounds)、组件类型(type)、切片哈希(assetHash/assetFileName)、层级移入移出(parentId)、显隐(isVisible)与透明度(opacity)。"

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
                put("description", "目标图元的唯一标识 id")
            })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "可选：图元组件类型（如 'VIEW', 'BUTTON', 'IMAGE', 'TEXT' 等）")
            })
            put("assetHash", buildJsonObject {
                put("type", "string")
                put("description", "可选：UI Studio 切片 SHA-256 哈希值，传入空字符串 \"\" 表示解绑")
            })
            put("assetFileName", buildJsonObject {
                put("type", "string")
                put("description", "可选：素材库切片文件名（如 'icon_gem.png'），自动匹配并绑定哈希")
            })
            put("parentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：新父容器图元 id。移到页面根层级传入 \"\" 或 \"null\"")
            })
            put("isVisible", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：图层是否可见")
            })
            put("opacity", buildJsonObject {
                put("type", "number")
                put("description", "可选：独立不透明度 (0.0 ~ 1.0)")
            })
            put("bounds", buildJsonObject {
                put("description", "可选：局部相对坐标与尺寸，支持 [left, top, right, bottom] 或 { left, top, right, bottom }")
            })
            put("cropRect", buildJsonObject {
                put("description", "可选：局部裁切区域，支持 [left, top, right, bottom] 或 { left, top, right, bottom }")
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
        val rawProjectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val targetId = arguments["id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.error("参数 'id' 不能为空")
        val viewNameArg = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()

        val assetHashArg = arguments["assetHash"]?.jsonPrimitive?.contentOrNull
        val assetFileNameArg = arguments["assetFileName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val parentIdArg = arguments["parentId"]?.jsonPrimitive?.contentOrNull
        val isVisibleArg = arguments["isVisible"]?.jsonPrimitive?.booleanOrNull
        val opacityArg = arguments["opacity"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f)
        val targetType = arguments["type"]?.jsonPrimitive?.contentOrNull?.let {
            runCatching { UIBlockType.valueOf(it.uppercase()) }.getOrNull()
        }

        val parsedBounds = parseSerialRect(arguments["bounds"])
        val parsedCropRect = parseSerialRect(arguments["cropRect"])

        var resolvedAssetHash: String? = null
        var shouldUpdateAssetHash = false

        if (assetHashArg != null) {
            shouldUpdateAssetHash = true
            resolvedAssetHash = if (assetHashArg.isBlank()) null else assetHashArg
        } else if (assetFileNameArg != null) {
            val library = StudioAssetManagerService.loadLibrary(rawProjectName, fileStorage, preferActiveMemory = true)
            val queryClean = assetFileNameArg.trim().lowercase()
            val matches = library.items.filter { item ->
                val pureName = item.name.lowercase()
                val fileName = item.relativePath.substringAfterLast('/').substringAfterLast('\\').lowercase()
                pureName == queryClean || fileName == queryClean ||
                    pureName == queryClean.substringBeforeLast('.') ||
                    fileName.substringBeforeLast('.') == queryClean.substringBeforeLast('.')
            }
            if (matches.isEmpty()) {
                return ToolResult.error("在素材库中未找到名称匹配 '$assetFileNameArg' 的切片素材")
            }
            if (matches.size > 1) {
                val candidateList = matches.joinToString(", ") { "${it.name} [hash=${it.hash}]" }
                return ToolResult.error("在素材库中匹配到多个同名切片 ($candidateList)，请传入具体的 'assetHash' 以消除歧义")
            }
            shouldUpdateAssetHash = true
            resolvedAssetHash = matches.first().hash
        }

        val res = UiStudioLayoutService.updateStudioBlock(
            projectName = rawProjectName,
            viewName = viewNameArg,
            id = targetId,
            parentId = parentIdArg,
            hasExplicitBounds = parsedBounds != null,
            repository = repository,
            recordUndo = true,
            syncActiveWorkspace = true
        ) { existing ->
            var updated = existing
            if (shouldUpdateAssetHash) updated = updated.copy(assetHash = resolvedAssetHash)
            isVisibleArg?.let { updated = updated.copy(isVisible = it) }
            opacityArg?.let { updated = updated.copy(opacity = it) }
            targetType?.let { updated = updated.copy(type = it) }
            parsedBounds?.let { updated = updated.copy(bounds = it) }
            parsedCropRect?.let { updated = updated.copy(cropRect = it) }
            updated
        }

        return res.fold(
            onSuccess = { (viewId, updatedBlock) ->
                ToolResult.text(buildJsonObject {
                    put("success", true)
                    put("viewId", viewId)
                    put("updatedBlock", looseJson.encodeToJsonElement(updatedBlock))
                }.toString())
            },
            onFailure = { err -> ToolResult.error(err.message ?: "更新图元失败") }
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

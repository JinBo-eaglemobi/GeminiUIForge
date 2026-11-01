package org.gemini.ui.forge.service.tool.ai.exclusive

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 查询当前项目中用来拼接界面的素材资产清单工具。
 * 遵循纯哈希与文件名规范，绝不返回底层物理相对路径。
 */
class ListAssetsTool(
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {
    override val name: String = "list_assets"
    override val description: String =
        "查询当前项目中已准备好、用来拼接界面的素材资产清单（Asset Items / Visual Assets）。" +
        "返回每个素材资产的文件名 (fileName)、尺寸规格 (width x height)、唯一哈希标识 (hash) 以及所属目录分类。" +
        "在设计或拼接界面布局时，调用此工具即可精确获知哪些素材可用，并在 apply_view_layout 的图元节点中直接指定 assetHash 或 assetFileName 即可完成素材绑定。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "项目工程 ID/名称。如在 UI Studio 环境中可省略，系统会自动填充当前活跃项目。")
            })
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "可选按素材名称或文件名模糊搜索过滤。")
            })
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "可选按业务分类标签过滤（如 'button', 'icon', 'symbol', 'default' 等）。")
            })
            put("folder", buildJsonObject {
                put("type", "string")
                put("description", "可选按文件夹名称过滤（如 'image', 'buttons' 等）。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: ConfigManager().loadKey("CURRENT_ACTIVE_PROJECT")?.ifBlank { null }
            ?: "DefaultProject"

        val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val categoryFilter = arguments["category"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val folderFilter = arguments["folder"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()

        onProgress?.invoke(0.2f, "正在检索项目 [$projName] 的素材资产清单...")

        val assetLibPath = "templates/$projName/asset_library.json"
        val rawJson = fileStorage.readFromFile(assetLibPath)
        if (rawJson.isNullOrBlank()) {
            return ToolResult.success(
                buildJsonObject {
                    put("total", 0)
                    put("projectName", projName)
                    put("assets", buildJsonArray {})
                    put("message", "当前项目 [$projName] 素材库为空或未找到素材。")
                }.toString()
            )
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val library = runCatching { jsonParser.decodeFromString<UIAssetLibrary>(rawJson) }.getOrElse { e ->
            AppLogger.w("ListAssetsTool", "解析素材库 JSON 失败: ${e.message}")
            return ToolResult.error("解析素材库数据失败: ${e.message}")
        }

        val folderMap = library.folders.associate { it.id to it.name }

        val filteredItems = library.items.filter { item ->
            val fileName = item.relativePath.substringAfterLast("/").substringAfterLast("\\")
            val folderName = item.folderId?.let { folderMap[it] } ?: ""

            val matchQuery = query.isNullOrBlank() ||
                    item.name.lowercase().contains(query) ||
                    fileName.lowercase().contains(query) ||
                    folderName.lowercase().contains(query)

            val matchCategory = categoryFilter.isNullOrBlank() ||
                    item.category.lowercase() == categoryFilter

            val matchFolder = folderFilter.isNullOrBlank() ||
                    folderName.lowercase() == folderFilter

            matchQuery && matchCategory && matchFolder
        }

        onProgress?.invoke(0.8f, "正在组装素材资产数据 (共 ${filteredItems.size} 项)...")

        val assetArray = buildJsonArray {
            filteredItems.forEach { item ->
                val fileName = item.relativePath.substringAfterLast("/").substringAfterLast("\\")
                val folderName = item.folderId?.let { folderMap[it] } ?: "default"
                add(
                    buildJsonObject {
                        put("fileName", fileName)
                        put("name", item.name)
                        put("hash", item.hash ?: "")
                        put("width", item.width)
                        put("height", item.height)
                        put("category", item.category)
                        put("folder", folderName)
                    }
                )
            }
        }

        val summaryText = buildString {
            append("项目 [").append(projName).append("] 共有 ")
            append(filteredItems.size).append(" 个可用来拼接界面的素材资产：\n")
            filteredItems.forEach { item ->
                val fileName = item.relativePath.substringAfterLast("/").substringAfterLast("\\")
                val folderName = item.folderId?.let { folderMap[it] } ?: "default"
                append("- [").append(fileName).append("] (")
                append(item.width).append("x").append(item.height).append(") ")
                append("hash: ").append(item.hash ?: "none")
                if (folderName.isNotBlank() && folderName != "default") {
                    append(" | 目录: ").append(folderName)
                }
                append("\n")
            }
            append("\n提示：在 apply_view_layout 装配图元时，可通过 assetFileName=\"<文件名>\" 或 assetHash=\"<哈希>\" 直接绑定对应素材。")
        }

        val resultJson = buildJsonObject {
            put("total", filteredItems.size)
            put("projectName", projName)
            put("assets", assetArray)
            put("summary", summaryText)
        }

        return ToolResult.success(resultJson.toString())
    }
}


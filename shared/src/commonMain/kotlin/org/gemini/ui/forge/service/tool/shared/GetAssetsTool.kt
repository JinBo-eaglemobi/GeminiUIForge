package org.gemini.ui.forge.service.tool.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 批量获取素材资产详细信息与图像数据工具 (GetAssetsTool)。
 *
 * 核心契约：
 * 遵循资产唯一 Hash 驱动铁律，接收一组素材哈希 [assetHashes]，
 * 批量返回其完备规格（文件名、真实尺寸、推断角色、所属目录树路径、页面图元引用反查）
 * 以及可选的高清 Base64 图像数据（供大模型多模态视觉直接审阅材质）。
 */
class GetAssetsTool(
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "get_assets"

    override val description: String =
        "批量查询并获取指定哈希列表 (assetHashes) 的素材资产完整信息。" +
        "支持通过素材不可变 Hash 批量返回其文件名、物理宽高规格 (width x height)、智能推断角色 (role)、所属文件夹目录与全工程图元引用情况。" +
        "通过配置 includeImageData=true，可同时返回素材切片的 Base64 图片数据供视觉多模态直接审阅材质质感。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("required", buildJsonArray {
            add("assetHashes")
        })
        put("properties", buildJsonObject {
            put("assetHashes", buildJsonObject {
                put("type", "array")
                put("description", "要批量获取的素材资产唯一 Hash 列表。支持传入 1 个或多个哈希。")
                put("items", buildJsonObject {
                    put("type", "string")
                })
            })
            put("includeImageData", buildJsonObject {
                put("type", "boolean")
                put("description", "是否在返回结果中包含切片的 Base64 图片数据（如 data:image/png;base64,...）。默认 false。当需要视觉审阅材质细节时开启。")
            })
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程名称。省略时自动使用当前活跃工程。")
            })
        })
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult = withContext(Dispatchers.Default) {
        val projName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: ConfigManager().loadKey("CURRENT_ACTIVE_PROJECT")?.ifBlank { null }
            ?: "DefaultProject"

        val sanitized = projName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        val rawHashes = arguments["assetHashes"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.contentOrNull?.trim()?.ifBlank { null }
        } ?: emptyList()

        if (rawHashes.isEmpty()) {
            return@withContext ToolResult(
                isError = true,
                content = listOf(ToolContent.Text(text = "❌ 参数 'assetHashes' 为空，必须传入至少一个素材唯一 Hash。"))
            )
        }

        val targetHashSet = rawHashes.toSet()
        val includeImage = arguments["includeImageData"]?.jsonPrimitive?.booleanOrNull ?: false

        onProgress?.invoke(0.2f, "正在检索工程 [$sanitized] 的素材库账本...")

        val assetLibPath = "templates/$sanitized/asset_library.json"
        val rawJson = fileStorage.readFromFile(assetLibPath)
        if (rawJson.isNullOrBlank()) {
            return@withContext ToolResult(
                isError = true,
                content = listOf(ToolContent.Text(text = "❌ 工程 [$sanitized] 未找到 asset_library.json 素材库账本。"))
            )
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val library = runCatching { jsonParser.decodeFromString<UIAssetLibrary>(rawJson) }.getOrElse { e ->
            AppLogger.w("GetAssetsTool", "解析素材库失败: ${e.message}")
            return@withContext ToolResult(
                isError = true,
                content = listOf(ToolContent.Text(text = "❌ 解析素材库数据失败: ${e.message}"))
            )
        }

        onProgress?.invoke(0.5f, "正在批量匹配 ${targetHashSet.size} 个素材哈希...")

        // 尝试加载当前工程的视图页面，用于反查图元对素材的引用
        val templateRepo = TemplateRepository(fileStorage)
        val project = templateRepo.getTemplateByName(sanitized)
        val usageMap = mutableMapOf<String, MutableList<JsonObject>>()

        project?.studioPages?.forEach { page: UIStudioPage ->
            fun scanBlock(block: UIStudioBlock) {
                val blockHash = block.assetHash
                if (!blockHash.isNullOrBlank() && blockHash in targetHashSet) {
                    val list = usageMap.getOrPut(blockHash) { mutableListOf() }
                    list.add(buildJsonObject {
                        put("viewName", page.nameStr)
                        put("viewId", page.id)
                        put("blockId", block.id)
                    })
                }
                block.children.forEach { scanBlock(it) }
            }
            page.blocks.forEach { scanBlock(it) }
        }

        val folderMap = library.folders.associateBy { it.id }
        val matchedItems = library.items.filter { it.hash in targetHashSet }

        val assetsJsonArray = buildJsonArray {
            for (item in matchedItems) {
                val folder = item.folderId?.let { folderMap[it] }
                val folderDisplayPath = if (folder != null) library.getFolderDisplayPath(folder.id) else ""
                val role = AssetManifestHelper.inferAssetRole(item.name)
                val usedList = usageMap[item.hash] ?: emptyList()

                var imageDataUri: String? = null
                if (includeImage && item.relativePath.isNotBlank()) {
                    try {
                        val file = TemplateFile(item.relativePath)
                        val bytes = file.readBytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val base64 = Base64.encode(bytes)
                            val mime = when {
                                item.relativePath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                                item.relativePath.endsWith(".jpg", ignoreCase = true) || item.relativePath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                else -> "image/png"
                            }
                            imageDataUri = "data:$mime;base64,$base64"
                        }
                    } catch (e: Exception) {
                        AppLogger.w("GetAssetsTool", "读取素材 [${item.name}] 图像数据失败: ${e.message}")
                    }
                }

                add(buildJsonObject {
                    put("hash", item.hash)
                    put("name", item.name)
                    put("width", item.width)
                    put("height", item.height)
                    put("category", item.category)
                    if (role != null) {
                        put("role", role)
                    } else {
                        put("role", JsonNull)
                    }
                    put("folderId", item.folderId)
                    put("folderName", folder?.name ?: "根目录")
                    put("folderPath", folderDisplayPath.ifBlank { "/" })
                    put("usedInBlocks", JsonArray(usedList))
                    if (imageDataUri != null) {
                        put("imageData", imageDataUri)
                    }
                })
            }
        }

        onProgress?.invoke(1.0f, "检索完成，命中 ${matchedItems.size}/${targetHashSet.size} 个素材。")

        val notFoundHashes = targetHashSet - matchedItems.map { it.hash }.toSet()

        val resultJson = buildJsonObject {
            put("projectName", sanitized)
            put("requestedCount", targetHashSet.size)
            put("foundCount", matchedItems.size)
            put("assets", assetsJsonArray)
            if (notFoundHashes.isNotEmpty()) {
                put("notFoundHashes", buildJsonArray {
                    notFoundHashes.forEach { add(it) }
                })
            }
        }

        ToolResult(content = listOf(ToolContent.Text(text = resultJson.toString())))
    }
}

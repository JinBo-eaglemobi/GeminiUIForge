package org.gemini.ui.forge.service.tool.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.studio.StudioAssetManagerService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 通用素材库管理工具 (ManageAssetLibraryTool)。
 *
 * 100% 委托调用公用服务 [StudioAssetManagerService]，与界面手动操作共用同一套底层物理同步实现。
 */
class ManageAssetLibraryTool(
    private val fileStorage: LocalFileStorage = LocalFileStorage(),
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "manage_asset_library"

    override val description: String =
        "UI Studio 项目素材库全生命周期管理工具。支持 9 种核心操作：\n" +
            "1. list_assets: 查询文件夹结构与素材资产列表，支持按 folderId 或 category 过滤；\n" +
            "2. search_assets: 按关键词 keyword 模糊搜索素材显示名与分类；\n" +
            "3. create_folder: 创建新素材文件夹（支持指定父文件夹 parentFolderId，同步在磁盘创建物理目录）；\n" +
            "4. rename_folder: 重命名文件夹（需传入 folderId 与 newName，同步迁移磁盘物理目录与子素材路径）；\n" +
            "5. delete_folder: 删除文件夹（破坏性操作，强制要求 confirmDestructive: true，递归物理删除目录及内部素材）；\n" +
            "6. rename_asset: 重命名素材切片（需传入 assetHash 与 newName，同步重命名物理文件）；\n" +
            "7. move_asset: 移动素材至目标文件夹 targetFolderId（传空或 null 移至根目录，同步移动物理文件）；\n" +
            "8. delete_asset: 删除指定素材（破坏性操作，强制要求 confirmDestructive: true，同步删除物理文件）；\n" +
            "9. bind_to_block: 快捷将素材库切片绑定至目标图元 id（支持传入 assetHash 或 assetFileName）。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        idempotentHint = false
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("required", buildJsonArray {
            add("projectName")
            add("action")
        })
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程名称（如 'New_UI_Project'）。")
            })
            put("action", buildJsonObject {
                put("type", "string")
                put("description", "要执行的素材库管理动作。")
                put("enum", buildJsonArray {
                    add("list_assets")
                    add("get_assets")
                    add("search_assets")
                    add("create_folder")
                    add("rename_folder")
                    add("delete_folder")
                    add("rename_asset")
                    add("move_asset")
                    add("delete_asset")
                    add("bind_to_block")
                })
            })
            put("folderId", buildJsonObject {
                put("type", "string")
                put("description", "文件夹 ID（用于 list_assets 过滤、rename_folder、delete_folder）。传 'root' 或空表示根目录。")
            })
            put("folderName", buildJsonObject {
                put("type", "string")
                put("description", "新建文件夹名称（用于 create_folder，也可直接使用 newName）。")
            })
            put("parentFolderId", buildJsonObject {
                put("type", "string")
                put("description", "父文件夹 ID（用于 create_folder）。缺省或传空表示顶层文件夹。")
            })
            put("assetHash", buildJsonObject {
                put("type", "string")
                put("description", "素材内容哈希（16位短哈希，用于唯一精确定位素材或 bind_to_block）。")
            })
            put("assetHashes", buildJsonObject {
                put("type", "array")
                put("description", "用于 get_assets 批量获取的素材哈希数组。")
                put("items", buildJsonObject { put("type", "string") })
            })
            put("newName", buildJsonObject {
                put("type", "string")
                put("description", "新名称（用于 create_folder, rename_folder, rename_asset）。")
            })
            put("targetFolderId", buildJsonObject {
                put("type", "string")
                put("description", "移动目标文件夹 ID（用于 move_asset）。传空串或 null 表示移至根目录。")
            })
            put("id", buildJsonObject {
                put("type", "string")
                put("description", "目标图元组件唯一 id（用于 bind_to_block）。")
            })
            put("assetFileName", buildJsonObject {
                put("type", "string")
                put("description", "素材文件名或显示名称（用于 bind_to_block 时反查素材）。")
            })
            put("keyword", buildJsonObject {
                put("type", "string")
                put("description", "搜索关键词（用于 search_assets）。")
            })
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "业务分类标签过滤（如 'button', 'icon', 'symbol', 'default' 等）。")
            })
            put("confirmDestructive", buildJsonObject {
                put("type", "boolean")
                put("description", "破坏性操作二次确认标记（针对 delete_asset 与 delete_folder）。若为 false 将强制拦截。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult = withContext(Dispatchers.Default) {
        val rawProj = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val activeProj = McpUiActionPipeline.getActiveUiStudioViewModel()?.state?.project?.projectId
        val projectName = if (!rawProj.isNullOrBlank()) rawProj else {
            activeProj ?: ConfigManager().loadKey("CURRENT_ACTIVE_PROJECT")?.ifBlank { null } ?: "DefaultProject"
        }
        val sanitized = projectName.trim().replace(" ", "_")

        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return@withContext ToolResult.error("❌ 缺少必填参数 'action'。")

        val currentLib = StudioAssetManagerService.loadLibrary(sanitized, fileStorage)

        when (action) {
            "list_assets" -> {
                val folderFilter = arguments["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                val categoryFilter = arguments["category"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()

                val filteredItems = currentLib.items.filter { item ->
                    val folderMatch = when {
                        folderFilter == null -> true
                        folderFilter.isEmpty() || folderFilter.equals("root", ignoreCase = true) || folderFilter.equals("null", ignoreCase = true) -> item.folderId == null
                        else -> item.folderId == folderFilter
                    }
                    val catMatch = if (categoryFilter.isNullOrBlank()) true else item.category.lowercase() == categoryFilter
                    folderMatch && catMatch
                }

                val folderAssetCount = currentLib.items.groupingBy { it.folderId }.eachCount()
                val folderListJson = currentLib.folders.map { folder ->
                    buildJsonObject {
                        put("id", folder.id)
                        put("name", folder.name)
                        put("path", currentLib.getFolderDisplayPath(folder.id))
                        put("parentFolderId", folder.parentFolderId)
                        put("assetCount", folderAssetCount[folder.id] ?: 0)
                    }
                }

                val assetListJson = filteredItems.map { item ->
                    buildJsonObject {
                        put("hash", item.hash)
                        put("name", item.name)
                        put("width", item.width)
                        put("height", item.height)
                        put("category", item.category)
                        put("folderId", item.folderId)
                        put("updatedAt", item.updatedAt)
                    }
                }

                val resultObj = buildJsonObject {
                    put("projectName", sanitized)
                    put("totalFolders", currentLib.folders.size)
                    put("totalAssets", currentLib.items.size)
                    put("filteredAssetsCount", filteredItems.size)
                    put("folders", JsonArray(folderListJson))
                    put("assets", JsonArray(assetListJson))
                }
                ToolResult.text(resultObj.toString())
            }

            "get_assets" -> {
                val hashesArray = arguments["assetHashes"]?.jsonArray
                val singleHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                val targetHashes = when {
                    hashesArray != null -> hashesArray.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.ifBlank { null } }
                    !singleHash.isNullOrBlank() -> listOf(singleHash)
                    else -> emptyList()
                }
                if (targetHashes.isEmpty()) {
                    return@withContext ToolResult.error("❌ 'get_assets' 动作缺少必填参数 'assetHashes' 或 'assetHash'。")
                }
                val delegateArgs = buildJsonObject {
                    put("projectName", sanitized)
                    put("assetHashes", buildJsonArray { targetHashes.forEach { add(it) } })
                    arguments["includeImageData"]?.let { put("includeImageData", it) }
                }
                GetAssetsTool(fileStorage).execute(delegateArgs, onProgress)
            }

            "search_assets" -> {
                val keyword = arguments["keyword"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'search_assets' 动作缺少必填参数 'keyword'。")

                val kwLower = keyword.lowercase()
                val matched = currentLib.items.filter {
                    it.name.lowercase().contains(kwLower) ||
                        it.category.lowercase().contains(kwLower) ||
                        it.hash.lowercase().contains(kwLower)
                }

                val matchedJson = matched.map { item ->
                    buildJsonObject {
                        put("hash", item.hash)
                        put("name", item.name)
                        put("width", item.width)
                        put("height", item.height)
                        put("category", item.category)
                        put("folderId", item.folderId)
                        put("folderPath", item.folderId?.let { currentLib.getFolderDisplayPath(it) })
                    }
                }

                val resultObj = buildJsonObject {
                    put("projectName", sanitized)
                    put("keyword", keyword)
                    put("matchCount", matched.size)
                    put("matches", JsonArray(matchedJson))
                }
                ToolResult.text(resultObj.toString())
            }

            "create_folder" -> {
                val folderName = arguments["folderName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'create_folder' 缺少必填参数 'folderName' 或 'newName'。")
                val parentFolderId = arguments["parentFolderId"]?.jsonPrimitive?.contentOrNull

                val res = StudioAssetManagerService.createFolder(
                    projectName = sanitized,
                    folderName = folderName,
                    parentFolderId = parentFolderId,
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = { folder ->
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "文件夹 '${folder.name}' 创建成功（已同步创建物理目录）")
                            put("folderId", folder.id)
                            put("name", folder.name)
                            put("parentFolderId", folder.parentFolderId)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "rename_folder" -> {
                val folderId = arguments["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'rename_folder' 缺少必填参数 'folderId'。")
                val newName = arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'rename_folder' 缺少必填参数 'newName'。")

                val res = StudioAssetManagerService.renameFolder(
                    projectName = sanitized,
                    folderId = folderId,
                    newName = newName,
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = { folder ->
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "文件夹已成功重命名为 '${folder.name}'（物理目录与子素材路径已同步更新）")
                            put("folderId", folder.id)
                            put("newName", folder.name)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "delete_folder" -> {
                val confirmDestructive = arguments["confirmDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!confirmDestructive) {
                    return@withContext ToolResult.error("⚠️【破坏性操作警示】删除文件夹将永久物理删除该目录及内部全部素材。如确认删除，请显式传入 confirmDestructive: true。")
                }
                val folderId = arguments["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'delete_folder' 缺少必填参数 'folderId'。")

                val res = StudioAssetManagerService.deleteFolder(
                    projectName = sanitized,
                    folderId = folderId,
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = { deletedAssetCount ->
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "文件夹 (ID: $folderId) 及其内部 $deletedAssetCount 个素材已从磁盘彻底物理删除")
                            put("deletedFolderId", folderId)
                            put("deletedAssetCount", deletedAssetCount)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "rename_asset" -> {
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'rename_asset' 缺少必填参数 'assetHash'。")
                val newName = arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'rename_asset' 缺少必填参数 'newName'。")

                val res = StudioAssetManagerService.renameAsset(
                    projectName = sanitized,
                    assetHash = assetHash,
                    newName = newName,
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = { item ->
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "素材已成功重命名为 '${item.name}'")
                            put("assetHash", item.hash)
                            put("newName", item.name)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "move_asset" -> {
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'move_asset' 缺少必填参数 'assetHash'。")
                val targetFolderId = arguments["targetFolderId"]?.jsonPrimitive?.contentOrNull

                val res = StudioAssetManagerService.moveAssets(
                    projectName = sanitized,
                    assetHashes = setOf(assetHash),
                    targetFolderId = targetFolderId,
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = {
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "素材 (hash: $assetHash) 已成功移动并更新物理路径")
                            put("assetHash", assetHash)
                            put("targetFolderId", targetFolderId)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "delete_asset" -> {
                val confirmDestructive = arguments["confirmDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!confirmDestructive) {
                    return@withContext ToolResult.error("⚠️【破坏性操作警示】删除素材将永久删除对应本地物理切片文件。如确认删除，请显式传入 confirmDestructive: true。")
                }
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'delete_asset' 缺少必填参数 'assetHash'。")

                val res = StudioAssetManagerService.deleteAssets(
                    projectName = sanitized,
                    targetHashes = setOf(assetHash),
                    storage = fileStorage,
                    syncActiveWorkspace = true,
                    showToast = false
                )
                res.fold(
                    onSuccess = {
                        ToolResult.text(buildJsonObject {
                            put("status", "success")
                            put("message", "素材 (Hash: $assetHash) 已成功物理删除")
                            put("deletedAssetHash", assetHash)
                        }.toString())
                    },
                    onFailure = { err -> ToolResult.error("❌ ${err.message}") }
                )
            }

            "bind_to_block" -> {
                val targetId = arguments["id"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult.error("❌ 'bind_to_block' 缺少必填参数 'id'。")
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetFileName = arguments["assetFileName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()

                val updateArgs = buildJsonObject {
                    put("projectName", sanitized)
                    put("id", targetId)
                    if (!assetHash.isNullOrBlank()) put("assetHash", assetHash)
                    if (!assetFileName.isNullOrBlank()) put("assetFileName", assetFileName)
                }
                UpdateBlockTool(repository = repository, fileStorage = fileStorage).execute(updateArgs, onProgress)
            }

            else -> ToolResult.error("❌ 未知的 action '$action'。")
        }
    }
}

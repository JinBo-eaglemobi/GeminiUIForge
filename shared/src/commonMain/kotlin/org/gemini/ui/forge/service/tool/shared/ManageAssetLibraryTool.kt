package org.gemini.ui.forge.service.tool.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.asset.AssetFileNameGuard

/**
 * 通用素材库管理工具 (ManageAssetLibraryTool)。
 *
 * 专职于对 UI Studio 及游戏项目模板的素材库进行全生命周期管理：
 * - 列表与检索 (list_assets, search_assets)
 * - 文件夹管理 (create_folder, rename_folder, delete_folder)
 * - 素材资产管理 (rename_asset, move_asset, delete_asset)
 * - 图元资产关联 (bind_to_block)
 *
 * 遵循三网协同、破坏性操作强制二次确认红线 (confirmDestructive) 与活动 UIStudio 运行态无感热同步。
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
        "3. create_folder: 创建新素材文件夹（支持指定父文件夹 parentFolderId）；\n" +
        "4. rename_folder: 重命名文件夹（需传入 folderId 与 newName）；\n" +
        "5. delete_folder: 删除文件夹（破坏性操作，强制要求 confirmDestructive: true，内部素材自动移至根目录防丢失）；\n" +
        "6. rename_asset: 重命名素材切片（需传入 assetId 或 assetHash 以及 newName）；\n" +
        "7. move_asset: 移动素材至目标文件夹 targetFolderId（传空或 null 移至根目录）；\n" +
        "8. delete_asset: 删除指定素材（破坏性操作，强制要求 confirmDestructive: true）；\n" +
        "9. bind_to_block: 快捷将素材库切片绑定至目标图元 blockId（支持传入 assetHash、assetId 或 assetFileName）。"

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
                put("description", "目标工程名称（如 'New_UI_Project'）。若为空可自动回退当前活跃项目。")
            })
            put("action", buildJsonObject {
                put("type", "string")
                put("description", "要执行的素材库管理动作。")
                put("enum", buildJsonArray {
                    add("list_assets")
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
            put("assetId", buildJsonObject {
                put("type", "string")
                put("description", "素材唯一标识 ID（用于 rename_asset, move_asset, delete_asset, bind_to_block）。")
            })
            put("assetHash", buildJsonObject {
                put("type", "string")
                put("description", "素材内容哈希（16位短哈希或 SHA-256，用于精确定位素材或 bind_to_block）。")
            })
            put("newName", buildJsonObject {
                put("type", "string")
                put("description", "新名称（用于 create_folder, rename_folder, rename_asset）。系统会自动清洗非法字符。")
            })
            put("targetFolderId", buildJsonObject {
                put("type", "string")
                put("description", "移动目标文件夹 ID（用于 move_asset）。传空串或 null 表示移至根目录。")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元组件 blockId（用于 bind_to_block）。")
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
                put("description", "破坏性操作二次确认标记（针对 delete_asset 与 delete_folder）。若为 false 将强制拦截，确保安全。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult = withContext(Dispatchers.Default) {
        val rawProj = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val projectName = if (!rawProj.isNullOrBlank()) rawProj else {
            ConfigManager().loadKey("CURRENT_ACTIVE_PROJECT")?.ifBlank { null } ?: "DefaultProject"
        }
        val sanitized = projectName.trim().replace(" ", "_")

        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 缺少必填参数 'action'。支持动作：list_assets, search_assets, create_folder, rename_folder, delete_folder, rename_asset, move_asset, delete_asset, bind_to_block。")))

        val activeVm: UIStudioViewModel? = McpUiActionPipeline.getActiveUiStudioViewModel()?.takeIf {
            val curProj = it.state.project?.projectId?.trim()?.replace(" ", "_")
            curProj == sanitized
        }

        // 读取素材库
        val assetLibPath = "templates/$sanitized/asset_library.json"
        var currentLib = loadLibrary(sanitized, activeVm, assetLibPath)

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
                        put("parentFolderId", folder.parentFolderId)
                        put("assetCount", folderAssetCount[folder.id] ?: 0)
                    }
                }

                val assetListJson = filteredItems.map { item ->
                    buildJsonObject {
                        put("id", item.id)
                        put("name", item.name)
                        put("width", item.width)
                        put("height", item.height)
                        put("category", item.category)
                        put("folderId", item.folderId)
                        put("hash", item.hash)
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

                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "search_assets" -> {
                val keyword = arguments["keyword"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'search_assets' 动作缺少必填参数 'keyword'。")))

                val kwLower = keyword.lowercase()
                val folderMap = currentLib.folders.associate { it.id to it.name }

                val matched = currentLib.items.filter {
                    it.name.lowercase().contains(kwLower) ||
                    it.id.lowercase().contains(kwLower) ||
                    it.category.lowercase().contains(kwLower) ||
                    (it.hash != null && it.hash.lowercase().contains(kwLower))
                }

                val matchedJson = matched.map { item ->
                    buildJsonObject {
                        put("id", item.id)
                        put("name", item.name)
                        put("width", item.width)
                        put("height", item.height)
                        put("category", item.category)
                        put("folderId", item.folderId)
                        put("folderName", item.folderId?.let { folderMap[it] })
                        put("hash", item.hash)
                    }
                }

                val resultObj = buildJsonObject {
                    put("projectName", sanitized)
                    put("keyword", keyword)
                    put("matchCount", matched.size)
                    put("matches", JsonArray(matchedJson))
                }

                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "create_folder" -> {
                val folderName = arguments["folderName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'create_folder' 动作缺少必填参数 'folderName' 或 'newName'。")))

                val cleanName = AssetFileNameGuard.sanitizeFileName(folderName)
                if (cleanName.isBlank()) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 文件夹名称含有全非法字符或为空。")))
                }

                val parentFolderId = arguments["parentFolderId"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                    ?.takeIf { !it.equals("root", ignoreCase = true) && !it.equals("null", ignoreCase = true) }

                // 检查同级目录下是否重名
                val isDuplicate = currentLib.folders.any {
                    it.parentFolderId == parentFolderId && it.name.equals(cleanName, ignoreCase = true)
                }
                if (isDuplicate) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 同级目录下已存在同名文件夹 '$cleanName'。")))
                }

                val newFolderId = "folder_${getCurrentTimeMillis()}"
                val newFolder = UIAssetFolder(id = newFolderId, name = cleanName, parentFolderId = parentFolderId)
                val updatedFolders = currentLib.folders + newFolder
                currentLib = currentLib.copy(folders = updatedFolders)

                saveLibrary(sanitized, currentLib, activeVm, assetLibPath)

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "文件夹 '$cleanName' 创建成功")
                    put("folderId", newFolderId)
                    put("name", cleanName)
                    put("parentFolderId", parentFolderId)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "rename_folder" -> {
                val folderId = arguments["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'rename_folder' 缺少必填参数 'folderId'。")))
                val newName = arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'rename_folder' 缺少必填参数 'newName'。")))

                val cleanName = AssetFileNameGuard.sanitizeFileName(newName)
                if (cleanName.isBlank()) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 新文件夹名称含有全非法字符或为空。")))
                }

                val targetFolder = currentLib.folders.firstOrNull { it.id == folderId }
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到 ID 为 '$folderId' 的文件夹。")))

                // 检查同级目录下是否已有重名文件夹
                val isDuplicate = currentLib.folders.any {
                    it.id != folderId && it.parentFolderId == targetFolder.parentFolderId && it.name.equals(cleanName, ignoreCase = true)
                }
                if (isDuplicate) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 同级目录下已存在同名文件夹 '$cleanName'。")))
                }

                if (activeVm != null) {
                    activeVm.assetLibrary.commitRenameFolder(folderId, cleanName)
                } else {
                    val updatedFolders = currentLib.folders.map {
                        if (it.id == folderId) it.copy(name = cleanName) else it
                    }
                    currentLib = currentLib.copy(folders = updatedFolders)
                    saveLibrary(sanitized, currentLib, null, assetLibPath)
                }

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "文件夹已成功重命名为 '$cleanName'")
                    put("folderId", folderId)
                    put("newName", cleanName)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "delete_folder" -> {
                val confirmDestructive = arguments["confirmDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!confirmDestructive) {
                    return@withContext ToolResult(
                        isError = true,
                        content = listOf(ToolContent.Text(text = "⚠️【破坏性操作警示】删除文件夹将移除该目录结构，内部素材将自动移至根目录保留。如确认删除，请在参数中显式传入 confirmDestructive: true。"))
                    )
                }

                val folderId = arguments["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'delete_folder' 缺少必填参数 'folderId'。")))

                val targetFolder = currentLib.folders.firstOrNull { it.id == folderId }
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到 ID 为 '$folderId' 的文件夹。")))

                if (activeVm != null) {
                    activeVm.assetLibrary.deleteAssetFolder(folderId)
                } else {
                    // 递归收集该文件夹及所有子文件夹 ID
                    val toDeleteIds = mutableSetOf(folderId)
                    var added = true
                    while (added) {
                        val subIds = currentLib.folders
                            .filter { it.parentFolderId in toDeleteIds && it.id !in toDeleteIds }
                            .map { it.id }
                        if (subIds.isEmpty()) {
                            added = false
                        } else {
                            toDeleteIds.addAll(subIds)
                        }
                    }

                    val updatedFolders = currentLib.folders.filter { it.id !in toDeleteIds }
                    val updatedItems = currentLib.items.map { item ->
                        if (item.folderId in toDeleteIds) item.copy(folderId = null) else item
                    }
                    currentLib = currentLib.copy(folders = updatedFolders, items = updatedItems)
                    saveLibrary(sanitized, currentLib, null, assetLibPath)
                }

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "文件夹 '${targetFolder.name}' (ID: $folderId) 及其子文件夹已成功删除，关联素材已安全移至根目录")
                    put("deletedFolderId", folderId)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "rename_asset" -> {
                val assetId = arguments["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                val newName = arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'rename_asset' 缺少必填参数 'newName'。")))

                val cleanName = AssetFileNameGuard.sanitizeFileName(newName)
                if (cleanName.isBlank()) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 新素材名称含有全非法字符或为空。")))
                }

                val targetAsset = currentLib.items.firstOrNull {
                    (assetId != null && it.id == assetId) || (assetHash != null && it.hash == assetHash)
                } ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到匹配的素材资产（assetId: $assetId, assetHash: $assetHash）。")))

                if (activeVm != null) {
                    val success = activeVm.assetLibrary.commitRenameAsset(targetAsset.id, cleanName)
                    if (!success) {
                        return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 素材重命名失败，当前目录下可能已存在同名文件。")))
                    }
                } else {
                    // 离线环境重命名
                    val oldRelPath = targetAsset.relativePath.replace('\\', '/')
                    val parentDirRel = oldRelPath.substringBeforeLast('/')
                    val ext = oldRelPath.substringAfterLast('.', "png")
                    val newRelPath = "$parentDirRel/$cleanName.$ext"

                    val isTaken = AssetFileNameGuard.isFileNameTakenInDir(
                        storage = fileStorage,
                        dirRelPath = parentDirRel,
                        fileName = "$cleanName.$ext",
                        excludeFileName = oldRelPath.substringAfterLast('/')
                    )
                    if (isTaken) {
                        return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 当前目录下已存在同名文件 '$cleanName.$ext'。")))
                    }

                    val oldFile = TemplateFile(oldRelPath)
                    val newFile = TemplateFile(newRelPath)
                    if (oldFile.exists()) {
                        oldFile.copyTo(newFile)
                        oldFile.delete()
                    }

                    val updatedItems = currentLib.items.map {
                        if (it.id == targetAsset.id) {
                            it.copy(relativePath = newRelPath, updatedAt = getCurrentTimeMillis())
                        } else it
                    }
                    currentLib = currentLib.copy(items = updatedItems)
                    saveLibrary(sanitized, currentLib, null, assetLibPath)
                }

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "素材已成功重命名为 '$cleanName'")
                    put("assetId", targetAsset.id)
                    put("newName", cleanName)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "move_asset" -> {
                val assetId = arguments["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                val targetFolderId = arguments["targetFolderId"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                    ?.takeIf { !it.equals("root", ignoreCase = true) && !it.equals("null", ignoreCase = true) }

                val targetAsset = currentLib.items.firstOrNull {
                    (assetId != null && it.id == assetId) || (assetHash != null && it.hash == assetHash)
                } ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到匹配的素材资产（assetId: $assetId, assetHash: $assetHash）。")))

                if (targetFolderId != null && currentLib.folders.none { it.id == targetFolderId }) {
                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 目标文件夹 ID '$targetFolderId' 不存在。")))
                }

                if (activeVm != null) {
                    activeVm.assetLibrary.moveAssetToFolder(targetAsset.id, targetFolderId, repository)
                } else {
                    val updatedItems = currentLib.items.map {
                        if (it.id == targetAsset.id) it.copy(folderId = targetFolderId) else it
                    }
                    currentLib = currentLib.copy(items = updatedItems)
                    saveLibrary(sanitized, currentLib, null, assetLibPath)
                }

                val folderName = targetFolderId?.let { fid -> currentLib.folders.find { it.id == fid }?.name } ?: "根目录"
                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "素材 '${targetAsset.name}' 已成功移至 '$folderName'")
                    put("assetId", targetAsset.id)
                    put("targetFolderId", targetFolderId)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "delete_asset" -> {
                val confirmDestructive = arguments["confirmDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!confirmDestructive) {
                    return@withContext ToolResult(
                        isError = true,
                        content = listOf(ToolContent.Text(text = "⚠️【破坏性操作警示】删除素材将永久删除对应本地物理切片文件。如确认删除，请在参数中显式传入 confirmDestructive: true。"))
                    )
                }

                val assetId = arguments["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()

                val targetAsset = currentLib.items.firstOrNull {
                    (assetId != null && it.id == assetId) || (assetHash != null && it.hash == assetHash)
                } ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到匹配的素材资产（assetId: $assetId, assetHash: $assetHash）。")))

                if (activeVm != null) {
                    activeVm.assetLibrary.deleteAsset(targetAsset.id)
                } else {
                    val tFile = TemplateFile(targetAsset.relativePath)
                    try {
                        if (tFile.exists()) tFile.delete()
                    } catch (e: Exception) {
                        AppLogger.w("ManageAssetLibraryTool", "删除本地物理素材失败: ${e.message}")
                    }
                    val updatedItems = currentLib.items.filter { it.id != targetAsset.id }
                    currentLib = currentLib.copy(items = updatedItems)
                    saveLibrary(sanitized, currentLib, null, assetLibPath)
                }

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "素材 '${targetAsset.name}' (ID: ${targetAsset.id}) 已成功物理删除")
                    put("deletedAssetId", targetAsset.id)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            "bind_to_block" -> {
                val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'bind_to_block' 缺少必填参数 'blockId'。")))

                val assetHash = arguments["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetId = arguments["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val assetFileName = arguments["assetFileName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: arguments["newName"]?.jsonPrimitive?.contentOrNull?.trim()

                // 定位目标素材
                val resolvedHash = when {
                    !assetHash.isNullOrBlank() -> assetHash
                    !assetId.isNullOrBlank() -> {
                        val item = currentLib.items.firstOrNull { it.id == assetId }
                        item?.hash ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 未找到 ID 为 '$assetId' 的素材或该素材尚未生成内容哈希。")))
                    }
                    !assetFileName.isNullOrBlank() -> {
                        val candidates = currentLib.items.filter { it.name.equals(assetFileName, ignoreCase = true) }
                        when {
                            candidates.isEmpty() -> {
                                val fuzzy = currentLib.items.filter { it.name.contains(assetFileName, ignoreCase = true) }
                                if (fuzzy.isEmpty()) {
                                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 素材库中未找到名称为 '$assetFileName' 的素材。")))
                                } else if (fuzzy.size == 1) {
                                    fuzzy.first().hash ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 素材 '${fuzzy.first().name}' 缺少内容哈希。")))
                                } else {
                                    val candidateNames = fuzzy.map { "${it.name} (id=${it.id}, hash=${it.hash})" }
                                    return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 存在多个模糊匹配的素材，请传入明确的 assetId 或 assetHash：$candidateNames")))
                                }
                            }
                            candidates.size == 1 -> {
                                candidates.first().hash ?: return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 素材 '$assetFileName' 缺少内容哈希。")))
                            }
                            else -> {
                                val candidateNames = candidates.map { "${it.name} (id=${it.id}, hash=${it.hash})" }
                                return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 存在多个同名素材，请传入明确的 assetId 或 assetHash：$candidateNames")))
                            }
                        }
                    }
                    else -> return@withContext ToolResult(isError = true, content = listOf(ToolContent.Text(text = "❌ 'bind_to_block' 必须提供 assetHash、assetId 或 assetFileName 之一来定位素材。")))
                }

                // 调度 UpdateBlockTool 执行图元绑定
                val updateBlockTool = UpdateBlockTool(fileStorage = fileStorage, repository = repository)
                val updateArgs = buildJsonObject {
                    put("projectName", sanitized)
                    put("blockId", blockId)
                    put("assetHash", resolvedHash)
                }

                val updateResult = updateBlockTool.execute(updateArgs, onProgress)
                if (updateResult.isError) {
                    return@withContext updateResult
                }

                val resultObj = buildJsonObject {
                    put("status", "success")
                    put("message", "已成功将素材 (hash: $resolvedHash) 绑定到图元 '$blockId'")
                    put("blockId", blockId)
                    put("assetHash", resolvedHash)
                }
                ToolResult(content = listOf(ToolContent.Text(text = resultObj.toString())))
            }

            else -> ToolResult(
                isError = true,
                content = listOf(ToolContent.Text(text = "❌ 未知的操作动作: '$action'。支持的动作包括：list_assets, search_assets, create_folder, rename_folder, delete_folder, rename_asset, move_asset, delete_asset, bind_to_block。"))
            )
        }
    }

    private suspend fun loadLibrary(
        sanitized: String,
        activeVm: UIStudioViewModel?,
        path: String
    ): UIAssetLibrary {
        if (activeVm != null) {
            return activeVm.state.assetLibrary
        }
        val rawJson = fileStorage.readFromFile(path)
        return if (!rawJson.isNullOrBlank()) {
            try {
                Json { ignoreUnknownKeys = true }.decodeFromString<UIAssetLibrary>(rawJson)
            } catch (e: Exception) {
                AppLogger.w("ManageAssetLibraryTool", "反序列化素材库失败: ${e.message}")
                UIAssetLibrary()
            }
        } else {
            UIAssetLibrary()
        }
    }

    private suspend fun saveLibrary(
        sanitized: String,
        library: UIAssetLibrary,
        activeVm: UIStudioViewModel?,
        path: String
    ) {
        val jsonStr = Json { prettyPrint = true }.encodeToString(UIAssetLibrary.serializer(), library)
        fileStorage.saveToFile(path, jsonStr)
        activeVm?.assetLibrary?.loadAssetLibrary(sanitized)
    }
}

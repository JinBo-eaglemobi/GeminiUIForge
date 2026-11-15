package org.gemini.ui.forge.service.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.asset.AssetFileNameGuard
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.calculateShortFileHash
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * UI Studio 素材库与物理磁盘双向同步公用服务 (Single Source of Truth)。
 *
 * 统一承载界面手动操作 (AssetLibraryDelegate / SliceImportDelegate) 与 AI/MCP 外部工具
 * (ManageAssetLibraryTool / SaveImageToAssetLibraryTool / ListAssetsTool / GetAssetsTool / ImportAssetSheetPipelineTool)
 * 的全部素材与文件夹增、删、改、查、移动、入库逻辑，彻底消除多端重复手搓与磁盘脱节缺陷。
 */
object StudioAssetManagerService {

    private const val TAG = "StudioAssetManagerService"

    private fun sanitizeProjectName(projectName: String): String =
        projectName.trim().replace(" ", "_").ifBlank { "default" }

    private fun resolveActiveVm(sanitizedProject: String): UIStudioViewModel? {
        val vm = McpUiActionPipeline.getActiveUiStudioViewModel() ?: return null
        val activeId = vm.state.project?.projectId?.trim()?.replace(" ", "_")
        return if (activeId.equals(sanitizedProject, ignoreCase = true)) vm else null
    }

    /**
     * 读取指定工程的素材库（优先取当前活跃工作区内存状态，离线时读取磁盘 asset_library.json）
     */
    suspend fun loadLibrary(
        projectName: String,
        storage: LocalFileStorage = LocalFileStorage(),
        preferActiveMemory: Boolean = true
    ): UIAssetLibrary {
        val sanitized = sanitizeProjectName(projectName)
        if (preferActiveMemory) {
            resolveActiveVm(sanitized)?.let { return it.state.assetLibrary }
        }
        val path = "templates/$sanitized/asset_library.json"
        val raw = storage.readFromFile(path) ?: return UIAssetLibrary()
        return runCatching { looseJson.decodeFromString<UIAssetLibrary>(raw) }.getOrDefault(UIAssetLibrary())
    }

    /**
     * 原子保存素材库到磁盘 asset_library.json，并按需同步刷新活跃 UIStudioViewModel 内存状态
     */
    suspend fun saveLibrary(
        projectName: String,
        library: UIAssetLibrary,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        selectedFolderId: String? = null,
        selectedAssetHash: String? = null
    ) {
        val sanitized = sanitizeProjectName(projectName)
        val path = "templates/$sanitized/asset_library.json"
        try {
            val jsonStr = looseJson.encodeToString(UIAssetLibrary.serializer(), library)
            storage.saveToFileAtomic(path, jsonStr)
        } catch (e: Exception) {
            try {
                val jsonStr = looseJson.encodeToString(UIAssetLibrary.serializer(), library)
                TemplateFile(path).writeBytes(jsonStr.encodeToByteArray())
            } catch (ex: Exception) {
                AppLogger.e(TAG, "保存素材库失败: $path", ex)
            }
        }

        if (syncActiveWorkspace) {
            resolveActiveVm(sanitized)?.let { vm ->
                var newState = vm.state.copy(assetLibrary = library)
                if (selectedFolderId != null) {
                    newState = newState.copy(selectedFolderId = selectedFolderId, isNewFolderDialogVisible = false)
                }
                if (selectedAssetHash != null) {
                    newState = newState.copy(selectedAssetHash = selectedAssetHash)
                }
                vm.state = newState
            }
        }
    }

    /**
     * 解析文件夹在磁盘上的完整物理相对路径（支持无限级嵌套父目录，如 "templates/Proj/assets/image/ui/buttons"）
     */
    fun resolveFolderPhysicalRelPath(
        projectName: String,
        library: UIAssetLibrary,
        folderId: String?
    ): String {
        val sanitized = sanitizeProjectName(projectName)
        if (folderId.isNullOrBlank()) {
            return "templates/$sanitized/assets/image"
        }
        val displayPath = library.getFolderDisplayPath(folderId)
        return if (displayPath.isBlank()) {
            "templates/$sanitized/assets/image"
        } else {
            "templates/$sanitized/assets/image/$displayPath"
        }
    }

    /**
     * 公用函数 1：创建素材文件夹（支持多级父文件夹，100% 同步在磁盘创建真实物理目录）
     */
    suspend fun createFolder(
        projectName: String,
        folderName: String,
        parentFolderId: String? = null,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<UIAssetFolder> = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val cleanName = AssetFileNameGuard.sanitizeFileName(folderName.trim())
        if (cleanName.isBlank()) {
            val msg = "文件夹名称不能为空或仅含非法字符"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val effectiveParentId = parentFolderId?.trim()?.ifBlank { null }
            ?.takeIf { !it.equals("root", ignoreCase = true) && !it.equals("null", ignoreCase = true) }

        val currentLib = loadLibrary(sanitized, storage)
        val isDuplicate = currentLib.folders.any {
            it.parentFolderId == effectiveParentId && it.name.equals(cleanName, ignoreCase = true)
        }
        if (isDuplicate) {
            val msg = "同级目录下已存在同名文件夹 '$cleanName'"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val newFolder = UIAssetFolder(
            id = "folder_${getCurrentTimeMillis()}",
            name = cleanName,
            parentFolderId = effectiveParentId
        )
        val updatedLib = currentLib.copy(folders = currentLib.folders + newFolder)
        val fullFolderRelPath = resolveFolderPhysicalRelPath(sanitized, updatedLib, newFolder.id)

        try {
            TemplateFile("$fullFolderRelPath/placeholder.tmp").createParentDirs()
        } catch (e: Exception) {
            AppLogger.w(TAG, "创建文件夹物理目录警告: $fullFolderRelPath: ${e.message}")
        }

        saveLibrary(
            projectName = sanitized,
            library = updatedLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace,
            selectedFolderId = newFolder.id
        )

        if (showToast) {
            Toast.show("成功创建文件夹: $cleanName", ToastType.SUCCESS)
        }
        Result.success(newFolder)
    }

    /**
     * 公用函数 2：重命名素材文件夹（同步迁移磁盘物理目录并批量更新子孙文件夹内全部素材的 relativePath）
     */
    suspend fun renameFolder(
        projectName: String,
        folderId: String,
        newName: String,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<UIAssetFolder> = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val cleanName = AssetFileNameGuard.sanitizeFileName(newName.trim())
        if (cleanName.isBlank()) {
            val msg = "新文件夹名称不能为空或仅含非法字符"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val currentLib = loadLibrary(sanitized, storage)
        val targetFolder = currentLib.folders.find { it.id == folderId }
            ?: return@withContext Result.failure(IllegalArgumentException("未找到 ID 为 '$folderId' 的文件夹"))

        if (targetFolder.name == cleanName) {
            resolveActiveVm(sanitized)?.let { it.state = it.state.copy(renamingFolder = null) }
            return@withContext Result.success(targetFolder)
        }

        val isDuplicate = currentLib.folders.any {
            it.id != folderId && it.parentFolderId == targetFolder.parentFolderId && it.name.equals(cleanName, ignoreCase = true)
        }
        if (isDuplicate) {
            val msg = "同级目录下已存在同名文件夹 '$cleanName'"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val oldFolderRelDir = resolveFolderPhysicalRelPath(sanitized, currentLib, folderId)
        val updatedFolder = targetFolder.copy(name = cleanName)
        val updatedFolders = currentLib.folders.map { if (it.id == folderId) updatedFolder else it }
        val tempLib = currentLib.copy(folders = updatedFolders)
        val newFolderRelDir = resolveFolderPhysicalRelPath(sanitized, tempLib, folderId)

        // 确保新物理目录创建
        try {
            TemplateFile("$newFolderRelDir/placeholder.tmp").createParentDirs()
        } catch (_: Exception) {}

        // 收集该文件夹及所有子孙文件夹 ID，迁移内部所有素材物理文件并更新 relativePath
        val affectedFolderIds = currentLib.collectAllDescendantFolderIds(folderId) + folderId
        val now = getCurrentTimeMillis()
        val updatedItems = currentLib.items.map { item ->
            if (item.folderId != null && item.folderId in affectedFolderIds) {
                val targetDirRel = resolveFolderPhysicalRelPath(sanitized, tempLib, item.folderId)
                val fileName = item.relativePath.substringAfterLast('/').substringAfterLast('\\')
                val newRelPath = "$targetDirRel/$fileName"
                if (newRelPath != item.relativePath) {
                    movePhysicalFileSafely(item.relativePath, newRelPath)
                }
                item.copy(relativePath = newRelPath, updatedAt = now)
            } else {
                item
            }
        }

        // 删除旧物理空目录
        if (oldFolderRelDir != newFolderRelDir && oldFolderRelDir != "templates/$sanitized/assets/image") {
            try {
                TemplateFile(oldFolderRelDir).delete(recursive = true)
            } catch (_: Exception) {}
        }

        val finalLib = tempLib.copy(items = updatedItems)
        saveLibrary(
            projectName = sanitized,
            library = finalLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )
        resolveActiveVm(sanitized)?.let { it.state = it.state.copy(renamingFolder = null) }

        if (showToast) {
            Toast.show("文件夹已重命名为: $cleanName", ToastType.SUCCESS)
        }
        Result.success(updatedFolder)
    }

    /**
     * 公用函数 3：彻底级联删除文件夹及其包含的所有子文件夹与物理素材文件
     */
    suspend fun deleteFolder(
        projectName: String,
        folderId: String,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<Int> = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val currentLib = loadLibrary(sanitized, storage)
        val targetFolder = currentLib.folders.find { it.id == folderId }
            ?: return@withContext Result.failure(IllegalArgumentException("未找到 ID 为 '$folderId' 的文件夹"))

        val allFolderIds = currentLib.collectAllDescendantFolderIds(folderId) + folderId
        val affectedItems = currentLib.items.filter { it.folderId in allFolderIds }
        val folderRelPath = resolveFolderPhysicalRelPath(sanitized, currentLib, folderId)

        // 1. 备份并物理删除每个受影响的素材切片文件
        for (item in affectedItems) {
            backupAssetFileToTurnTrash(sanitized, item)
            try {
                val tFile = TemplateFile(item.relativePath)
                if (tFile.exists()) {
                    tFile.delete(recursive = false)
                }
            } catch (_: Exception) {}
        }

        // 2. 递归物理删除文件夹磁盘目录
        if (folderRelPath.isNotBlank() && folderRelPath != "templates/$sanitized/assets/image") {
            try {
                TemplateFile(folderRelPath).delete(recursive = true)
            } catch (e: Exception) {
                AppLogger.w(TAG, "物理删除文件夹目录警告: $folderRelPath: ${e.message}")
            }
        }

        val updatedFolders = currentLib.folders.filterNot { it.id in allFolderIds }
        val updatedItems = currentLib.items.filterNot { it.folderId in allFolderIds }
        val newLib = currentLib.copy(folders = updatedFolders, items = updatedItems)

        saveLibrary(
            projectName = sanitized,
            library = newLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )

        resolveActiveVm(sanitized)?.let { vm ->
            vm.state = vm.state.copy(
                selectedFolderId = if (vm.state.selectedFolderId in allFolderIds) null else vm.state.selectedFolderId,
                selectedAssetHash = if (affectedItems.any { it.hash == vm.state.selectedAssetHash }) null else vm.state.selectedAssetHash
            )
        }

        if (showToast) {
            Toast.show("已彻底删除文件夹「${targetFolder.name}」及 ${affectedItems.size} 个素材", ToastType.SUCCESS)
        }
        Result.success(affectedItems.size)
    }

    /**
     * 公用函数 4：重命名单个素材资产并同步重命名磁盘物理文件
     */
    suspend fun renameAsset(
        projectName: String,
        assetHash: String,
        newName: String,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<UIAssetItem> = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val currentLib = loadLibrary(sanitized, storage)
        val target = currentLib.items.find { it.hash == assetHash }
            ?: return@withContext Result.failure(IllegalArgumentException("未找到匹配的素材资产 (hash: $assetHash)"))

        val clean = AssetFileNameGuard.sanitizeFileName(newName.trim())
        if (clean.isBlank()) {
            val msg = "素材名称不能为空或仅含非法字符"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val oldFileName = target.relativePath.substringAfterLast('/').substringAfterLast('\\')
        val ext = oldFileName.substringAfterLast('.', "png")
        val newFileName = if (ext.isBlank()) clean else "$clean.$ext"
        val dir = target.relativePath.substringBeforeLast('/', "")

        if (AssetFileNameGuard.isFileNameTakenInDir(storage, dir, newFileName, excludeFileName = oldFileName)) {
            val msg = "当前目录已存在同名素材（不区分大小写）：$clean"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val newRelPath = if (dir.isNotBlank()) "$dir/$newFileName" else newFileName
        if (newRelPath != target.relativePath) {
            val moved = movePhysicalFileSafely(target.relativePath, newRelPath)
            if (!moved) {
                val msg = "重命名素材物理文件失败"
                if (showToast) Toast.show(msg, ToastType.ERROR)
                return@withContext Result.failure(IllegalStateException(msg))
            }
        }

        val updatedItem = target.copy(relativePath = newRelPath, updatedAt = getCurrentTimeMillis())
        val updatedItems = currentLib.items.map { if (it.hash == assetHash) updatedItem else it }
        val finalLib = currentLib.copy(items = updatedItems)

        saveLibrary(
            projectName = sanitized,
            library = finalLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )
        resolveActiveVm(sanitized)?.let { it.state = it.state.copy(renamingAsset = null) }

        if (showToast) {
            Toast.show("素材已重命名为: $clean", ToastType.SUCCESS)
        }
        Result.success(updatedItem)
    }

    /**
     * 公用函数 5：将单个或多个素材移动到目标文件夹（null 表示移至根目录），同步按完整多级路径移动物理文件
     */
    suspend fun moveAssets(
        projectName: String,
        assetHashes: Set<String>,
        targetFolderId: String?,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<Int> = withContext(Dispatchers.Default) {
        if (assetHashes.isEmpty()) return@withContext Result.success(0)
        val sanitized = sanitizeProjectName(projectName)
        val effectiveFolderId = targetFolderId?.trim()?.ifBlank { null }
            ?.takeIf { !it.equals("root", ignoreCase = true) && !it.equals("null", ignoreCase = true) }

        val currentLib = loadLibrary(sanitized, storage)
        if (effectiveFolderId != null && currentLib.folders.none { it.id == effectiveFolderId }) {
            return@withContext Result.failure(IllegalArgumentException("目标文件夹 ID '$effectiveFolderId' 不存在"))
        }

        val targetAssets = currentLib.items.filter { it.hash in assetHashes && it.folderId != effectiveFolderId }
        if (targetAssets.isEmpty()) return@withContext Result.success(0)

        val targetDirRel = resolveFolderPhysicalRelPath(sanitized, currentLib, effectiveFolderId)
        val now = getCurrentTimeMillis()
        val newPathMap = mutableMapOf<String, String>()

        for (asset in targetAssets) {
            val fileName = asset.relativePath.substringAfterLast('/').substringAfterLast('\\')
            val newRelPath = "$targetDirRel/$fileName"
            movePhysicalFileSafely(asset.relativePath, newRelPath, fallbackProject = sanitized, assetHash = asset.hash)
            newPathMap[asset.hash] = newRelPath
        }

        val updatedItems = currentLib.items.map { item ->
            val newPath = newPathMap[item.hash]
            if (newPath != null) {
                item.copy(folderId = effectiveFolderId, relativePath = newPath, updatedAt = now)
            } else {
                item
            }
        }
        val finalLib = currentLib.copy(items = updatedItems)
        saveLibrary(
            projectName = sanitized,
            library = finalLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )

        if (showToast) {
            val destDesc = if (effectiveFolderId != null) {
                currentLib.getFolderDisplayPath(effectiveFolderId)
            } else "根目录"
            val msg = if (targetAssets.size == 1) {
                "素材已移动至: $destDesc"
            } else {
                "已将 ${targetAssets.size} 个素材移动至: $destDesc"
            }
            Toast.show(msg, ToastType.SUCCESS)
        }
        Result.success(targetAssets.size)
    }

    /**
     * 公用函数 6：彻底删除单个或多个素材并物理清理磁盘文件
     */
    suspend fun deleteAssets(
        projectName: String,
        targetHashes: Set<String>,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false
    ): Result<Int> = withContext(Dispatchers.Default) {
        if (targetHashes.isEmpty()) return@withContext Result.success(0)
        val sanitized = sanitizeProjectName(projectName)
        val currentLib = loadLibrary(sanitized, storage)
        val targets = currentLib.items.filter { it.hash in targetHashes }
        if (targets.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("未找到匹配的素材资产: $targetHashes"))
        }

        for (target in targets) {
            backupAssetFileToTurnTrash(sanitized, target)
            try {
                val tFile = TemplateFile(target.relativePath)
                if (tFile.exists()) {
                    tFile.delete(recursive = false)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "物理删除素材文件异常: ${target.relativePath}: ${e.message}")
            }
        }

        val updatedItems = currentLib.items.filterNot { it.hash in targetHashes }
        val newLib = currentLib.copy(items = updatedItems)
        saveLibrary(
            projectName = sanitized,
            library = newLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )

        resolveActiveVm(sanitized)?.let { vm ->
            vm.state = vm.state.copy(
                selectedAssetHash = if (vm.state.selectedAssetHash in targetHashes) null else vm.state.selectedAssetHash,
                selectedAssetHashes = vm.state.selectedAssetHashes - targetHashes
            )
        }

        if (showToast) {
            val msg = if (targets.size == 1) {
                "已删除素材: ${targets.first().name}"
            } else {
                "已批量删除 ${targets.size} 个素材"
            }
            Toast.show(msg, ToastType.INFO)
        }
        Result.success(targets.size)
    }

    /**
     * 公用函数 7：将图片字节流存入项目素材库并自动登记账本（供聊天气泡手动存入与 SaveImageToAssetLibraryTool 共用）
     * 支持自动按多级 folderPath 查找或创建 UIAssetFolder 树节点、可选去背、重名策略控制及同 Hash 自动去重复用。
     */
    suspend fun saveImageToLibrary(
        projectName: String,
        imageBytes: ByteArray,
        fileName: String,
        folderPath: String = "",
        isTransparent: Boolean = true,
        autoRemoveBg: Boolean = false,
        conflictPolicy: NameConflictPolicy = NameConflictPolicy.AUTO_RENAME,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        showToast: Boolean = false,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<UIAssetItem> = withContext(Dispatchers.Default) {
        if (imageBytes.isEmpty()) {
            val msg = "保存失败: 图像数据为空"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val sanitized = sanitizeProjectName(projectName)
        val processedBytes = if (autoRemoveBg && isTransparent) {
            onProgress?.invoke(0.3f, "正在调用本地 Python 抠图引擎进行背景去除...")
            val mattingService = LocalMattingService(storage)
            val noBg = mattingService.removeBackground(imageBytes) { log ->
                onProgress?.invoke(0.5f, log)
            }
            if (noBg != null && noBg.isNotEmpty()) noBg else imageBytes
        } else {
            imageBytes
        }

        var currentLib = loadLibrary(sanitized, storage)
        val cleanSubPath = folderPath.trim().replace('\\', '/').trim('/')

        // 若指定了子文件夹路径（如 "icons/buttons"），自动逐层查找或创建对应的 UIAssetFolder 节点
        var targetFolderId: String? = null
        if (cleanSubPath.isNotBlank()) {
            val segments = cleanSubPath.split('/').map { AssetFileNameGuard.sanitizeFileName(it.trim()) }.filter { it.isNotBlank() }
            var parentId: String? = null
            val mutableFolders = currentLib.folders.toMutableList()
            for (seg in segments) {
                val existing = mutableFolders.find { it.parentFolderId == parentId && it.name.equals(seg, ignoreCase = true) }
                if (existing != null) {
                    parentId = existing.id
                } else {
                    val created = UIAssetFolder(
                        id = "folder_${getCurrentTimeMillis()}_${seg.hashCode().ushr(1)}",
                        name = seg,
                        parentFolderId = parentId
                    )
                    mutableFolders.add(created)
                    parentId = created.id
                }
            }
            targetFolderId = parentId
            currentLib = currentLib.copy(folders = mutableFolders)
        }

        val ext = if (isTransparent) "png" else "jpg"
        val baseClean = AssetFileNameGuard.sanitizeFileName(fileName.trim().substringBeforeLast('.')).ifBlank { "asset_${getCurrentTimeMillis()}" }
        val desiredFileName = "$baseClean.$ext"
        val targetRelDir = resolveFolderPhysicalRelPath(sanitized, currentLib, targetFolderId)
        val desiredFullPath = "$targetRelDir/$desiredFileName"

        val isTaken = AssetFileNameGuard.isFileNameTakenInDir(storage, targetRelDir, desiredFileName)
        val effectivePath = if (isTaken) {
            when (conflictPolicy) {
                NameConflictPolicy.REJECT -> {
                    val msg = "保存被拒绝：当前目录已存在同名文件（不区分大小写）：$desiredFileName，请修改文件名后重新保存"
                    if (showToast) Toast.show(msg, ToastType.ERROR)
                    return@withContext Result.failure(IllegalArgumentException(msg))
                }
                NameConflictPolicy.AUTO_RENAME -> {
                    "$targetRelDir/${baseClean}_${getCurrentTimeMillis() % 100000}.$ext"
                }
                NameConflictPolicy.OVERWRITE -> desiredFullPath
            }
        } else {
            desiredFullPath
        }

        onProgress?.invoke(0.7f, "正在持久化落盘到素材库...")
        try {
            storage.saveBytesToFile(effectivePath, processedBytes)
        } catch (e: Exception) {
            val msg = "保存图片至磁盘失败: ${e.message}"
            if (showToast) Toast.show(msg, ToastType.ERROR)
            return@withContext Result.failure(e)
        }

        val fileHash = calculateShortFileHash(effectivePath) ?: effectivePath.hashCode().toString(16)
        val existingSameHash = currentLib.items.find { it.hash == fileHash }
        if (existingSameHash != null && existingSameHash.relativePath != effectivePath) {
            try {
                TemplateFile(effectivePath).delete(recursive = false)
            } catch (_: Exception) {}
            if (showToast) {
                Toast.show("素材库已存在相同内容资产，已为您自动定位: ${existingSameHash.name}", ToastType.INFO)
            }
            saveLibrary(sanitized, currentLib, storage, syncActiveWorkspace, selectedFolderId = existingSameHash.folderId, selectedAssetHash = existingSameHash.hash)
            return@withContext Result.success(existingSameHash)
        }

        val (w, h) = try {
            val skiaImg = Image.makeFromEncoded(processedBytes)
            skiaImg.width to skiaImg.height
        } catch (_: Exception) {
            100 to 100
        }

        val newAsset = UIAssetItem(
            hash = fileHash,
            relativePath = effectivePath,
            width = w,
            height = h,
            category = "image",
            folderId = targetFolderId,
            updatedAt = getCurrentTimeMillis()
        )
        val updatedItems = currentLib.items.filterNot { it.relativePath == effectivePath || it.hash == fileHash } + newAsset
        val finalLib = currentLib.copy(items = updatedItems)

        saveLibrary(
            projectName = sanitized,
            library = finalLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace,
            selectedFolderId = targetFolderId,
            selectedAssetHash = newAsset.hash
        )

        if (showToast) {
            Toast.show("已存入项目素材库: ${newAsset.name}", ToastType.SUCCESS)
        }
        onProgress?.invoke(1.0f, "素材入库完成")
        Result.success(newAsset)
    }

    /**
     * 公用函数 8：将图集大图与归一化图元列表并发切图存入 UI Studio 素材库，并构建标准 UIStudioPage 工程
     */
    suspend fun importAssetSheetToStudioProject(
        projectName: String,
        skiaImage: Image,
        canvasWidth: Float,
        canvasHeight: Float,
        items: List<NormalizedAssetItem>,
        repository: TemplateRepository = TemplateRepository(),
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): Pair<ProjectState, UIAssetLibrary> = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val imgWidth = skiaImage.width.toFloat()
        val imgHeight = skiaImage.height.toFloat()
        val srcBitmap = Bitmap.makeFromImage(skiaImage).apply { setImmutable() }

        val sliceSemaphore = Semaphore(8)
        val progressMutex = Mutex()
        var completedCount = 0

        val currentLib = loadLibrary(sanitized, storage)
        val existingItemsByHash = currentLib.items.associateBy { it.hash }.toMutableMap()

        data class SliceItemOutcome(
            val index: Int,
            val block: UIStudioBlock,
            val assetItem: UIAssetItem?
        )

        val outcomes = coroutineScope {
            items.mapIndexed { index, item ->
                async(Dispatchers.Default) {
                    sliceSemaphore.withPermit {
                        val b = item.bounds
                        val localCropRect = SerialRect(
                            left = b.left.coerceIn(0f, imgWidth),
                            top = b.top.coerceIn(0f, imgHeight),
                            right = b.right.coerceIn(0f, imgWidth),
                            bottom = b.bottom.coerceIn(0f, imgHeight)
                        )

                        val sliceBytes = cropImage(
                            srcBitmap = srcBitmap,
                            imageWidth = skiaImage.width,
                            imageHeight = skiaImage.height,
                            bounds = localCropRect,
                            originalWidth = imgWidth,
                            originalHeight = imgHeight,
                            isPng = true
                        )

                        var assetItem: UIAssetItem? = null
                        var boundHash: String? = null
                        if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                            val cleanFileName = AssetFileNameGuard.sanitizeFileName(item.name.ifBlank { item.id }).ifBlank { item.id }
                            val relPath = "templates/$sanitized/assets/image/$cleanFileName.png"
                            storage.saveBytesToFile(relPath, sliceBytes)
                            val shortHash = calculateShortFileHash(relPath) ?: relPath.hashCode().toString(16)
                            boundHash = shortHash
                            assetItem = UIAssetItem(
                                hash = shortHash,
                                relativePath = relPath,
                                width = localCropRect.width.toInt().coerceAtLeast(1),
                                height = localCropRect.height.toInt().coerceAtLeast(1),
                                category = item.type.name.lowercase(),
                                folderId = null,
                                updatedAt = getCurrentTimeMillis()
                            )
                        }

                        val studioBlock = UIStudioBlock(
                            id = item.id,
                            type = item.type,
                            bounds = b,
                            cropRect = b,
                            assetHash = boundHash
                        )

                        val done = progressMutex.withLock {
                            completedCount++
                            completedCount
                        }
                        onProgress?.invoke(done.toFloat() / items.size, "并发切片入库 ($done/${items.size}): ${item.name}")
                        SliceItemOutcome(index, studioBlock, assetItem)
                    }
                }
            }.awaitAll().sortedBy { it.index }
        }

        for (outcome in outcomes) {
            val asset = outcome.assetItem ?: continue
            if (!existingItemsByHash.containsKey(asset.hash)) {
                existingItemsByHash[asset.hash] = asset
            }
        }

        val updatedLibrary = currentLib.copy(items = existingItemsByHash.values.toList())
        saveLibrary(sanitized, updatedLibrary, storage, syncActiveWorkspace = syncActiveWorkspace)

        val mainPage = UIStudioPage(
            id = "MainPage",
            width = canvasWidth,
            height = canvasHeight,
            blocks = outcomes.map { it.block }.bindParents()
        )
        val existingProj = repository.getTemplateByName(sanitized)
        val updatedPages = if (existingProj != null && existingProj.studioPages.isNotEmpty()) {
            val hasMain = existingProj.studioPages.any { it.id.equals("MainPage", ignoreCase = true) }
            if (hasMain) {
                existingProj.studioPages.map { if (it.id.equals("MainPage", ignoreCase = true)) mainPage else it }
            } else {
                listOf(mainPage) + existingProj.studioPages
            }
        } else {
            listOf(mainPage)
        }

        val finalProject = (existingProj ?: ProjectState(projectId = sanitized)).copy(
            projectId = sanitized,
            pages = emptyList(),
            studioPages = updatedPages
        )
        repository.saveTemplate(sanitized, finalProject)
        if (syncActiveWorkspace) {
            resolveActiveVm(sanitized)?.reload(finalProject)
        }

        finalProject to updatedLibrary
    }

    /**
     * 写时复制暂存备份：仅在即将物理删除或覆盖某张素材图片前调用，将其备份至 .turn_trash/{hash}.bak（平时 0 开销）
     */
    suspend fun backupAssetFileToTurnTrash(projectName: String, item: UIAssetItem) {
        val sanitized = sanitizeProjectName(projectName)
        if (item.hash.isBlank()) return
        try {
            val srcFile = TemplateFile(item.relativePath)
            if (srcFile.exists()) {
                val bytes = srcFile.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val trashFile = TemplateFile("templates/$sanitized/.turn_trash/${item.hash}.bak")
                    trashFile.createParentDirs()
                    trashFile.writeBytes(bytes)
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "备份素材至 .turn_trash 警告: ${item.relativePath}: ${e.message}")
        }
    }

    /**
     * 清理指定工程的 .turn_trash 临时备份目录
     */
    suspend fun clearTurnTrash(projectName: String) = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        try {
            val trashDir = TemplateFile("templates/$sanitized/.turn_trash")
            if (trashDir.exists()) {
                trashDir.delete(recursive = true)
            }
        } catch (_: Exception) {}
    }

    /**
     * 公用函数 9：将素材库账本、文件夹物理目录与物理图片增/删/改/重命名/移动 100% 极速还原至指定快照状态
     */
    suspend fun restoreAssetLibrarySnapshot(
        projectName: String,
        snapshotLib: UIAssetLibrary,
        storage: LocalFileStorage = LocalFileStorage(),
        syncActiveWorkspace: Boolean = true
    ) = withContext(Dispatchers.Default) {
        val sanitized = sanitizeProjectName(projectName)
        val currentLib = loadLibrary(sanitized, storage, preferActiveMemory = true)

        val snapshotItemsByHash = snapshotLib.items.associateBy { it.hash }
        val currentItemsByHash = currentLib.items.associateBy { it.hash }

        // 1. 删除本轮新增的素材物理文件（hash 不在初始快照中）
        for (curItem in currentLib.items) {
            if (!snapshotItemsByHash.containsKey(curItem.hash)) {
                try {
                    val f = TemplateFile(curItem.relativePath)
                    if (f.exists()) f.delete(recursive = false)
                } catch (_: Exception) {}
            }
        }

        // 2. 确保快照中的所有原有文件夹物理目录存在
        for (folder in snapshotLib.folders) {
            val relDir = resolveFolderPhysicalRelPath(sanitized, snapshotLib, folder.id)
            try {
                TemplateFile("$relDir/placeholder.tmp").createParentDirs()
            } catch (_: Exception) {}
        }

        // 3. 还原被重命名/跨文件夹移动或被删除/被覆盖的素材物理文件
        for (oldItem in snapshotLib.items) {
            val curItem = currentItemsByHash[oldItem.hash]
            if (curItem != null && curItem.relativePath != oldItem.relativePath) {
                // 本轮被重命名或移动到了其它文件夹：直接移回原路径
                movePhysicalFileSafely(curItem.relativePath, oldItem.relativePath, fallbackProject = sanitized, assetHash = oldItem.hash)
            }
            val targetFile = TemplateFile(oldItem.relativePath)
            val wasModifiedOrDeleted = !targetFile.exists() || (curItem != null && curItem.updatedAt != oldItem.updatedAt)
            if (wasModifiedOrDeleted) {
                // 从 .turn_trash 恢复原图字节
                try {
                    val trashFile = TemplateFile("templates/$sanitized/.turn_trash/${oldItem.hash}.bak")
                    if (trashFile.exists()) {
                        val bytes = trashFile.readBytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            targetFile.createParentDirs()
                            targetFile.writeBytes(bytes)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "从 .turn_trash 恢复素材失败: ${oldItem.relativePath}: ${e.message}")
                }
            }
        }

        // 4. 清理本轮新建但快照中不存在的多余物理文件夹目录
        val snapshotFolderDirs = snapshotLib.folders.map { resolveFolderPhysicalRelPath(sanitized, snapshotLib, it.id) }.toSet()
        for (curFolder in currentLib.folders) {
            val curDir = resolveFolderPhysicalRelPath(sanitized, currentLib, curFolder.id)
            if (curDir !in snapshotFolderDirs && curDir != "templates/$sanitized/assets/image") {
                try {
                    TemplateFile(curDir).delete(recursive = true)
                } catch (_: Exception) {}
            }
        }

        // 5. 原子刷回 asset_library.json 并刷新活跃工作区内存
        saveLibrary(
            projectName = sanitized,
            library = snapshotLib,
            storage = storage,
            syncActiveWorkspace = syncActiveWorkspace
        )
    }

    private suspend fun movePhysicalFileSafely(
        oldRelPath: String,
        newRelPath: String,
        fallbackProject: String? = null,
        assetHash: String? = null
    ): Boolean {
        if (oldRelPath == newRelPath) return true
        return try {
            var resolvedOld = TemplateFile(oldRelPath)
            if (!resolvedOld.exists() && fallbackProject != null) {
                val fileName = oldRelPath.substringAfterLast('/').substringAfterLast('\\')
                val c1 = TemplateFile("templates/$fallbackProject/assets/image/$fileName")
                val c2 = if (assetHash != null) TemplateFile("templates/$fallbackProject/assets/$assetHash/$fileName") else null
                if (c1.exists()) resolvedOld = c1
                else if (c2 != null && c2.exists()) resolvedOld = c2
            }
            if (resolvedOld.exists()) {
                val bytes = resolvedOld.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val newFile = TemplateFile(newRelPath)
                    newFile.createParentDirs()
                    newFile.writeBytes(bytes)
                    if (resolvedOld.getAbsolutePath() != newFile.getAbsolutePath()) {
                        resolvedOld.delete(recursive = false)
                    }
                    true
                } else false
            } else false
        } catch (e: Exception) {
            AppLogger.w(TAG, "物理移动文件失败: $oldRelPath -> $newRelPath: ${e.message}")
            false
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.UIStudioLeftBottomTab
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.ui.uistudio.model.ImportConflictFile
import org.gemini.ui.forge.ui.uistudio.model.ImportConflictState
import org.gemini.ui.forge.ui.uistudio.model.ImportConflictStrategy
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.asset.AssetFileNameGuard
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.blockingAwait
import org.gemini.ui.forge.utils.calculateShortFileHash
import org.gemini.ui.forge.utils.copyLocalFile
import org.gemini.ui.forge.utils.isSupportedImageFile
import org.gemini.ui.forge.utils.listFilesRecursively
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toShortHash
import org.gemini.ui.forge.utils.updateBlockInList
import org.jetbrains.skia.Image
import kotlin.random.Random

/**
 * 素材库域委托：承载素材库加载与物理磁盘自愈扫描、一键去背、素材库持久化、
 * 文件夹与素材 CRUD、哈希检索、外部统一导入管道、素材树交互（展开折叠/重命名/
 * 资源管理器定位）、素材画布挂载以及 AI 生成图片入库等素材资产管理核心操作。
 *
 * 通过构造注入宿主的状态桥接（getState/setState）、协程作用域、JSON 序列化器、
 * 本地文件存储、本地抠图服务与画布编辑域委托，实现跨域协同而零循环依赖。
 */
class AssetLibraryDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val jsonHelper: Json,
    internal val storage: LocalFileStorage,
    private val mattingService: LocalMattingService,
    private val canvasEditor: CanvasEditorDelegate
) {
    /** 宿主状态桥接属性：使迁入方法体内的 state 读写零改动平移 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /**
     * 读取项目本地的 asset_library.json 加载素材库，并自动触发本地物理切片自愈扫描。
     */
    fun loadAssetLibrary(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/asset_library.json"
            var loadedLib: UIAssetLibrary? = null
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        loadedLib = jsonHelper.decodeFromString<UIAssetLibrary>(bytes.decodeToString())
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取素材库失败或不存在: ${e.message}")
            }

            val baseLib = loadedLib ?: UIAssetLibrary()
            // 触发物理切片与图元元数据自愈对齐引擎
            val healedLib = healAssetLibrary(sanitized, baseLib)
            state = state.copy(assetLibrary = healedLib)
            AppLogger.i("UIStudioViewModel", "已加载素材库: ${healedLib.items.size} 个素材, ${healedLib.folders.size} 个文件夹")

            // 若发生了自愈补齐，则自动持久化更新 asset_library.json
            if (healedLib.items.size != baseLib.items.size) {
                saveAssetLibrary()
            }
        }
    }

    /**
     * 素材库本地物理资产自愈扫描引擎。
     * 当本地已存在切片图片但 asset_library.json 未收录或为空时，
     * 自动从项目图元元数据及物理磁盘 assets 目录扫描补齐，并持久化保存。
     */
    private suspend fun healAssetLibrary(sanitized: String, initialLib: UIAssetLibrary): UIAssetLibrary {
        var hasUpdatedExisting = false
        val healedExistingItems = initialLib.items.map { item ->
            val curHash = item.hash
            if (curHash.isNullOrBlank()) {
                val computed = calculateShortFileHash(item.relativePath)
                if (computed != null) {
                    hasUpdatedExisting = true
                    item.copy(hash = computed)
                } else item
            } else if (curHash.length > 16) {
                // 将历史存量 64 位长哈希规整为 16 位短哈希
                hasUpdatedExisting = true
                item.copy(hash = curHash.toShortHash())
            } else item
        }
        val effectiveLib = if (hasUpdatedExisting) initialLib.copy(items = healedExistingItems) else initialLib

        val existingPaths = effectiveLib.items.map { it.relativePath.replace("\\", "/") }.toMutableSet()
        val existingIds = effectiveLib.items.map { it.id }.toMutableSet()
        val existingHashes = effectiveLib.items.mapNotNull { it.hash }.toMutableSet()
        val newItems = mutableListOf<UIAssetItem>()

        // 1. 深度扫描本地物理 assets 目录兜底自愈（新模型下素材唯一凭据是内容哈希，扫描时同步计算补齐 16 位短哈希）
        try {
            val assetsDirRel = "templates/$sanitized/assets"
            val directFiles = storage.listFiles(assetsDirRel)
            for (file in directFiles) {
                if (file.endsWith(".png", ignoreCase = true) || file.endsWith(".webp", ignoreCase = true)) {
                    val relPath = "$assetsDirRel/$file"
                    val tFile = TemplateFile(relPath)
                    val fileBytes = try { tFile.readBytes() ?: readLocalFileBytes(relPath) } catch (_: Exception) { null }
                    if (fileBytes == null || fileBytes.isEmpty()) {
                        AppLogger.w("UIStudioViewModel", "🗑️ 自愈扫描探测到 0 字节损坏坏文件，拒绝收录并自动物理清除: $relPath")
                        try {
                            tFile.delete()
                        } catch (_: Exception) {}
                        continue
                    }
                    if (!existingPaths.contains(relPath)) {
                        val baseId = file.substringBeforeLast(".")
                        val fileHash = calculateShortFileHash(relPath)
                        // 同内容哈希的重复文件跳过入库（哈希是素材唯一凭据）
                        if (fileHash != null && existingHashes.contains(fileHash)) continue
                        if (!existingIds.contains(baseId)) {
                            newItems.add(
                                UIAssetItem(
                                    id = baseId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    hash = fileHash
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(baseId)
                            fileHash?.let { existingHashes.add(it) }
                        }
                    }
                }
            }

            val subDirs = storage.listDirectories(assetsDirRel)
            for (sub in subDirs) {
                val subRel = "$assetsDirRel/$sub"
                val subFiles = storage.listFiles(subRel)
                for (file in subFiles) {
                    if (file.endsWith(".png", ignoreCase = true) || file.endsWith(".webp", ignoreCase = true)) {
                        val relPath = "$subRel/$file"
                        val tFile = TemplateFile(relPath)
                        val fileBytes = try { tFile.readBytes() ?: readLocalFileBytes(relPath) } catch (_: Exception) { null }
                        if (fileBytes == null || fileBytes.isEmpty()) {
                            AppLogger.w("UIStudioViewModel", "🗑️ 自愈扫描探测到 0 字节损坏坏文件，拒绝收录并自动物理清除: $relPath")
                            try {
                                tFile.delete()
                            } catch (_: Exception) {}
                            continue
                        }
                        if (!existingPaths.contains(relPath)) {
                            val baseId = file.substringBeforeLast(".")
                            val effectiveId = if (existingIds.contains(baseId)) "${sub}_$baseId" else baseId
                            val fileHash = calculateShortFileHash(relPath)
                            // 同内容哈希的重复文件跳过入库（哈希是素材唯一凭据）
                            if (fileHash != null && existingHashes.contains(fileHash)) continue
                            newItems.add(
                                UIAssetItem(
                                    id = effectiveId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    hash = fileHash
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(effectiveId)
                            fileHash?.let { existingHashes.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "物理目录自愈扫描部分跳过: ${e.message}")
        }

        return if (newItems.isNotEmpty() || hasUpdatedExisting) {
            val updated = effectiveLib.copy(items = effectiveLib.items + newItems)
            AppLogger.i("UIStudioViewModel", "✨ 自愈补齐素材库完成: 新增 ${newItems.size} 个物理素材，补全哈希 ${if (hasUpdatedExisting) "是" else "否"}！")
            updated
        } else {
            initialLib
        }
    }

    /**
     * 对素材库中指定的单个素材执行一键去除背景生成透明 PNG。
     */
    fun removeAssetBackground(assetId: String) {
        val asset = state.assetLibrary.items.find { it.id == assetId } ?: return
        if (state.isMattingAsset) return

        state = state.copy(
            isMattingAsset = true,
            mattingAssetId = assetId
        )

        scope.launch(Dispatchers.Default) {
            try {
                Toast.show("🪄 正在调用本地引擎去除背景: ${asset.name}...", ToastType.INFO)
                val tFile = TemplateFile(asset.relativePath)
                val rawBytes = tFile.readBytes()
                if (rawBytes == null || rawBytes.isEmpty()) {
                    Toast.show("无法读取素材文件: ${asset.relativePath}", ToastType.ERROR)
                    state = state.copy(isMattingAsset = false, mattingAssetId = null)
                    return@launch
                }

                val transparentBytes = mattingService.removeBackground(rawBytes) { log ->
                    AppLogger.d("UIStudioViewModel", log)
                }

                if (transparentBytes != null && transparentBytes.isNotEmpty()) {
                    // 原地覆盖写入透明 PNG
                    tFile.writeBytes(transparentBytes)
                    val now = getCurrentTimeMillis()
                    // 解析透明处理后的物理分辨率
                    val (newW, newH) = try {
                        val skiaImg = Image.makeFromEncoded(transparentBytes)
                        skiaImg.width to skiaImg.height
                    } catch (_: Exception) {
                        asset.width to asset.height
                    }
                    // 保持 hash 守恒不变，仅更新 updatedAt 与物理宽高以触发 UI 刷新
                    val updatedItems = state.assetLibrary.items.map {
                        if (it.id == assetId) {
                            it.copy(
                                updatedAt = now,
                                width = newW,
                                height = newH
                            )
                        } else it
                    }
                    state = state.copy(
                        assetLibrary = state.assetLibrary.copy(items = updatedItems),
                        isMattingAsset = false,
                        mattingAssetId = null
                    )
                    saveAssetLibrary()
                    Toast.show("✨ 成功去除背景并更新为透明图片！", ToastType.SUCCESS)
                } else {
                    state = state.copy(isMattingAsset = false, mattingAssetId = null)
                    Toast.show("抠图处理失败，请检查本地 Python 环境", ToastType.ERROR)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "去除背景异常", e)
                state = state.copy(isMattingAsset = false, mattingAssetId = null)
                Toast.show("去背异常: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 切换切图时是否自动执行一键去背生成透明图片。
     */
    fun updateAutoRemoveBgOnSlice(enabled: Boolean) {
        state = state.copy(autoRemoveBgOnSlice = enabled)
    }

    /**
     * 手动触发素材库物理全量刷新与双向对齐校验。
     * 1. 物理存在性校验：扫描并剔除磁盘中已被手动删除或为 0 字节损坏坏文件的失效孤儿项；
     * 2. 本地新增自愈：扫描物理磁盘 assets 目录，自动收录未登记的切片；
     * 3. 配置持久化与状态更新：将清洗后的干净配置回写至 asset_library.json，并弹出统计 Toast。
     * @param focusAssetPath 可选：刷新完成后需自动聚焦并高亮选中的素材相对路径
     */
    fun refreshAssetLibrary(focusAssetPath: String? = null) {
        if (state.isRefreshingAssetLibrary) return
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")

        state = state.copy(isRefreshingAssetLibrary = true)

        scope.launch(Dispatchers.Default) {
            try {
                AppLogger.i("UIStudioViewModel", "🔄 开始手动触发素材库物理全量刷新与校验: $sanitized (聚焦目标: $focusAssetPath)")
                val currentLib = state.assetLibrary
                var prunedCount = 0

                // 1. 物理存在性校验：检查每个已登记素材的物理文件是否存在且非 0 字节
                val validItems = mutableListOf<UIAssetItem>()
                val refreshTimestamp = getCurrentTimeMillis()
                for (item in currentLib.items) {
                    val fileBytes = try {
                        val t = TemplateFile(item.relativePath)
                        if (t.exists()) t.readBytes() else readLocalFileBytes(item.relativePath)
                    } catch (e: Exception) {
                        null
                    }
                    val isInvalid = fileBytes == null || fileBytes.isEmpty()
                    if (isInvalid) {
                        prunedCount++
                        AppLogger.w("UIStudioViewModel", "🗑️ 探测到物理文件缺失或为 0 字节损坏坏文件，自动剔除失效素材并清理残卷: ${item.name} (${item.relativePath})")
                        try {
                            TemplateFile(item.relativePath).delete()
                        } catch (_: Exception) {}
                    } else {
                        // 刷新更新时间戳，强制击穿 Compose 内存位图缓存，确保外部修改的文件被重新加载
                        validItems.add(item.copy(updatedAt = refreshTimestamp))
                    }
                }

                // 2. 磁盘物理新增扫描自愈
                val intermediateLib = currentLib.copy(items = validItems)
                val healedLib = healAssetLibrary(sanitized, intermediateLib)
                val addedCount = (healedLib.items.size - validItems.size).coerceAtLeast(0)

                // 3. 选中项与聚焦项状态安全计算
                val focusedItem = if (focusAssetPath != null) {
                    healedLib.items.find {
                        it.relativePath.equals(focusAssetPath, ignoreCase = true) ||
                        it.relativePath.endsWith(focusAssetPath.substringAfterLast('/'), ignoreCase = true)
                    }
                } else null

                val newSelectedAssetId = when {
                    focusedItem != null -> focusedItem.id
                    validItems.any { it.id == state.selectedAssetId } -> state.selectedAssetId
                    else -> null
                }
                val newExpandedFolderIds = if (focusedItem?.folderId != null) {
                    state.expandedFolderIds + focusedItem.folderId
                } else state.expandedFolderIds

                // 4. 更新 UI 状态
                state = state.copy(
                    assetLibrary = healedLib,
                    selectedAssetId = newSelectedAssetId,
                    expandedFolderIds = newExpandedFolderIds,
                    isRefreshingAssetLibrary = false
                )

                // 5. 若有变动（剔除或新增），自动持久化更新 asset_library.json
                if (prunedCount > 0 || addedCount > 0) {
                    saveAssetLibrary()
                }

                // 6. 弹出统计 Toast
                val msg = "素材库已刷新：有效 ${healedLib.items.size} 项 (清理失效 $prunedCount 项，同步新增 $addedCount 项)"
                Toast.show(msg, ToastType.SUCCESS)
                AppLogger.i("UIStudioViewModel", "✅ $msg")
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "刷新素材库失败", e)
                Toast.show("素材库刷新失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isRefreshingAssetLibrary = false)
            }
        }
    }

    /**
     * 将当前素材库状态持久化保存至本地磁盘 templates/{proj}/asset_library.json。
     */
    fun saveAssetLibrary() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/asset_library.json"
        scope.launch(Dispatchers.Default) {
            try {
                val jsonStr = jsonHelper.encodeToString(UIAssetLibrary.serializer(), state.assetLibrary)
                TemplateFile(path).writeBytes(jsonStr.encodeToByteArray())
                AppLogger.i("UIStudioViewModel", "素材库保存成功: ${state.assetLibrary.items.size} 个素材")
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存素材库失败", e)
            }
        }
    }

    /**
     * 在素材库中创建新的分类文件夹。
     */
    fun createAssetFolder(name: String, parentFolderId: String? = state.selectedFolderId) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        val newFolder = UIAssetFolder(
            id = "folder_${getCurrentTimeMillis()}",
            name = trimmed,
            parentFolderId = parentFolderId
        )
        val sanitizedProject = state.project?.projectId?.replace(" ", "_") ?: "default"
        scope.launch(Dispatchers.Default) {
            try {
                val dirRel = "templates/$sanitizedProject/assets/image/$trimmed/placeholder.tmp"
                TemplateFile(dirRel).createParentDirs()
            } catch (_: Exception) {}
        }

        val updatedLib = state.assetLibrary.copy(
            folders = state.assetLibrary.folders + newFolder
        )
        state = state.copy(
            assetLibrary = updatedLib,
            selectedFolderId = newFolder.id,
            isNewFolderDialogVisible = false
        )
        saveAssetLibrary()
        Toast.show("成功创建文件夹: $trimmed", ToastType.SUCCESS)
    }

    /**
     * 将素材文件移动到指定的文件夹（若 targetFolderId == null 则移动到根目录）。
     * 同步物理移动磁盘文件并更新素材与项目图元引用。
     */
    fun moveAssetToFolder(assetId: String, targetFolderId: String?, repo: TemplateRepository) {
        val asset = state.assetLibrary.items.find { it.id == assetId } ?: return
        if (asset.folderId == targetFolderId) return

        val sanitizedProject = state.project?.projectId?.replace(" ", "_") ?: "default"
        val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
        val targetSubDir = if (targetFolder != null) "${targetFolder.name}/" else ""
        val fileName = asset.relativePath.substringAfterLast("/")
        val newRelPath = "templates/$sanitizedProject/assets/image/${targetSubDir}$fileName"

        scope.launch(Dispatchers.Default) {
            // 物理移动文件（具备旧路径失效智能探测自愈）
            try {
                var resolvedOldFile = TemplateFile(asset.relativePath)
                if (!resolvedOldFile.exists()) {
                    val candidate1 = TemplateFile("templates/$sanitizedProject/assets/image/$fileName")
                    val candidate2 = TemplateFile("templates/$sanitizedProject/assets/$assetId/$fileName")
                    if (candidate1.exists()) {
                        resolvedOldFile = candidate1
                    } else if (candidate2.exists()) {
                        resolvedOldFile = candidate2
                    }
                }
                if (resolvedOldFile.exists()) {
                    val bytes = resolvedOldFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val newFile = TemplateFile(newRelPath)
                        newFile.createParentDirs()
                        newFile.writeBytes(bytes)
                        if (resolvedOldFile.getAbsolutePath() != newFile.getAbsolutePath()) {
                            resolvedOldFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "物理移动素材文件失败: ${e.message}")
            }
        }

        // 更新素材库项
        val updatedItems = state.assetLibrary.items.map {
            if (it.id == assetId) {
                it.copy(folderId = targetFolderId, relativePath = newRelPath)
            } else it
        }

        // 哈希引用模型下：素材库内移动文件夹仅更新条目的当前路径账本（relativePath），
        // 页面图元通过 assetHash 引用素材，与物理路径彻底解耦，无需任何图元联动更新
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems)
        )
        saveAssetLibrary()
        val destDesc = targetFolder?.name ?: "根目录"
        Toast.show("素材已移动至: $destDesc", ToastType.SUCCESS)
    }

    /**
     * 删除指定素材文件夹，并将该文件夹下的素材平移至根目录。
     */
    fun deleteAssetFolder(folderId: String) {
        val updatedFolders = state.assetLibrary.folders.filterNot { it.id == folderId || it.parentFolderId == folderId }
        val updatedItems = state.assetLibrary.items.map {
            if (it.folderId == folderId) it.copy(folderId = null) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(folders = updatedFolders, items = updatedItems),
            selectedFolderId = if (state.selectedFolderId == folderId) null else state.selectedFolderId
        )
        saveAssetLibrary()
        Toast.show("已删除文件夹", ToastType.INFO)
    }

    /**
     * 选中素材文件夹。
     */
    fun selectAssetFolder(folderId: String?) {
        state = state.copy(selectedFolderId = folderId, selectedAssetId = null)
    }

    /**
     * 选中单个素材。
     */
    fun selectAsset(assetId: String?) {
        state = state.copy(
            selectedAssetId = assetId,
            selectedBlockId = if (assetId != null) null else state.selectedBlockId
        )
    }

    /**
     * 删除单个素材并清理磁盘物理文件。
     */
    fun deleteAsset(assetId: String) {
        val target = state.assetLibrary.items.find { it.id == assetId } ?: return
        val updatedItems = state.assetLibrary.items.filterNot { it.id == assetId }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems),
            selectedAssetId = null
        )
        saveAssetLibrary()
        scope.launch(Dispatchers.Default) {
            try {
                val tFile = TemplateFile(target.relativePath)
                val deleted = tFile.delete()
                if (deleted) {
                    AppLogger.i("UIStudioViewModel", "🗑️ 物理删除素材文件成功: ${target.relativePath}")
                } else {
                    AppLogger.w("UIStudioViewModel", "⚠️ 物理删除素材文件未成功或文件已不存在: ${target.relativePath}")
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "💥 物理删除素材文件异常: ${target.relativePath}", e)
            }
        }
        Toast.show("已删除素材: ${target.name}", ToastType.INFO)
    }

    // ====== 素材库哈希引用体系与统一导入管道 ======

    /**
     * 通过内容哈希检索素材条目。
     * 这是页面图元 (assetHash) 寻址物理资源的唯一通道：
     * 图元仅持有哈希 → 素材条目持有「当前路径账本」(relativePath) → 物理文件。
     * 条目被移动/改名时账本路径更新，但哈希不变，图元引用永不断链。
     */
    fun findAssetByHash(hash: String?): UIAssetItem? {
        if (hash.isNullOrBlank()) return null
        val target = hash.trim().lowercase()
        return state.assetLibrary.items.firstOrNull { item ->
            val itemHash = item.hash?.trim()?.lowercase() ?: return@firstOrNull false
            itemHash == target || itemHash.startsWith(target) || target.startsWith(itemHash)
        }
    }

    /**
     * 统一外部导入管道：支持多文件、多文件夹、文件与文件夹混合的路径列表。
     * 三条导入入口（弹窗多选、目录浏览、桌面拖放）最终全部收口于此。
     *
     * 处理流程：展开目录 → 过滤图片扩展名 → 逐文件计算 SHA-256 哈希 →
     * 同哈希去重跳过 → 物理拷贝入工程 assets/import 目录（文件名 = 哈希.扩展名）→
     * Skia 解码读取真实尺寸 → 构造素材条目入库并持久化。
     *
     * @param paths 外部文件/文件夹绝对路径列表（单一或多个均可）
     * @param targetFolderId 入库目标文件夹（默认当前选中的素材文件夹）
     */
    fun importExternalPaths(paths: List<String>, targetFolderId: String? = state.selectedFolderId) {
        val proj = state.project ?: run {
            Toast.show("尚未打开任何工程，无法导入素材", ToastType.ERROR)
            return
        }
        if (paths.isEmpty()) return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        scope.launch(Dispatchers.Default) {
            // 1. 展开路径集合：文件直接收集，文件夹递归扫描，统一过滤受支持的图片扩展名
            val candidateFiles = mutableListOf<String>()
            for (rawPath in paths) {
                val cleanPath = rawPath.replace("\\", "/")
                try {
                    // 优先按目录递归展开（非目录或空目录返回空列表），否则按单图片文件兜底收录
                    val nested = listFilesRecursively(cleanPath)
                    if (nested.isNotEmpty()) {
                        candidateFiles.addAll(nested.filter { isSupportedImageFile(it) })
                    } else if (isSupportedImageFile(cleanPath) && TemplateFile(cleanPath).exists()) {
                        candidateFiles.add(cleanPath)
                    }
                } catch (e: Exception) {
                    AppLogger.w("UIStudioViewModel", "导入路径解析跳过: $cleanPath (${e.message})")
                }
            }
            if (candidateFiles.isEmpty()) {
                Toast.show("未在所选路径中发现可导入的图片文件", ToastType.INFO)
                return@launch
            }

            // 2. 逐文件处理：内容去重（保留）→ 文件名改用源文件名 → 冲突分流
            val existingHashes = state.assetLibrary.items.mapNotNull { it.hash?.trim()?.lowercase() }.toMutableSet()
            val importDirRel = "templates/$sanitized/assets/import"
            val readyList = mutableListOf<Triple<String, String, String>>() // sourcePath, desiredFileName, fileHash
            val conflictList = mutableListOf<ImportConflictFile>()
            var skippedCount = 0

            for (filePath in candidateFiles) {
                try {
                    val fileHash = calculateShortFileHash(filePath) ?: continue
                    // ① 内容级去重快速通道保留：内容相同时覆盖/别名均无意义，静默跳过
                    if (existingHashes.any { it == fileHash || it.startsWith(fileHash) || fileHash.startsWith(it) }) {
                        skippedCount++
                        continue
                    }
                    // ② 文件名改用源文件名（原 hash 文件名废弃；清洗 + blank 兜底 hash）
                    val rawName = filePath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                    val safeBase = AssetFileNameGuard.sanitizeFileName(rawName).ifBlank { fileHash }
                    val extension = filePath.substringAfterLast('.', "png").lowercase()
                    val desired = "$safeBase.$extension"

                    // ③ 与目标目录已有条目 + 批内准备列表 + 磁盘物理文件做不区分大小写同名检测
                    val taken = state.assetLibrary.items.any { it.relativePath.equals("$importDirRel/$desired", ignoreCase = true) } ||
                            readyList.any { it.second.equals(desired, ignoreCase = true) } ||
                            AssetFileNameGuard.isFileNameTakenInDir(storage, importDirRel, desired)

                    if (taken) {
                        conflictList.add(ImportConflictFile(sourcePath = filePath, desiredFileName = desired, fileHash = fileHash))
                    } else {
                        readyList.add(Triple(filePath, desired, fileHash))
                        existingHashes.add(fileHash)
                    }
                } catch (e: Exception) {
                    AppLogger.w("UIStudioViewModel", "导入单文件解析失败: $filePath (${e.message})")
                }
            }

            // 无冲突项立即物理拷贝入库
            val newItems = mutableListOf<UIAssetItem>()
            for ((sourcePath, desired, fileHash) in readyList) {
                val relPath = "$importDirRel/$desired"
                val item = importSingleFile(sourcePath, relPath, targetFolderId, fileHash)
                if (item != null) {
                    newItems.add(item)
                }
            }

            // 3. 挂起重名冲突弹窗（若存在同名异内容冲突）
            if (conflictList.isNotEmpty()) {
                state = state.copy(
                    importConflictState = ImportConflictState(
                        conflicts = conflictList,
                        targetFolderId = targetFolderId,
                        projectSanitizedName = sanitized
                    )
                )
            }

            if (newItems.isNotEmpty()) {
                state = state.copy(
                    assetLibrary = state.assetLibrary.copy(items = state.assetLibrary.items + newItems),
                    leftBottomTab = UIStudioLeftBottomTab.ASSETS
                )
                saveAssetLibrary()
            }

            if (newItems.isEmpty() && conflictList.isEmpty()) {
                Toast.show(if (skippedCount > 0) "所选素材均已存在（跳过 $skippedCount 个重复项）" else "导入失败，未新增任何素材", ToastType.INFO)
            } else if (newItems.isNotEmpty()) {
                val dupHint = if (skippedCount > 0) "，跳过重复 $skippedCount 个" else ""
                val conflictHint = if (conflictList.isNotEmpty()) "，发现 ${conflictList.size} 个重名冲突待决策" else ""
                Toast.show("成功导入 ${newItems.size} 个素材$dupHint$conflictHint", ToastType.SUCCESS)
            }
        }
    }

    /**
     * 辅助解码图片真实物理尺寸，失败兜底 100x100
     */
    private suspend fun decodeImageSize(relPath: String): Pair<Int, Int> {
        return try {
            val bytes = readLocalFileBytes(relPath)
            if (bytes != null) {
                val image = Image.makeFromEncoded(bytes)
                image.width.coerceAtLeast(1) to image.height.coerceAtLeast(1)
            } else {
                100 to 100
            }
        } catch (_: Exception) {
            100 to 100
        }
    }

    /**
     * 辅助执行单文件拷贝与条目构建
     */
    private suspend fun importSingleFile(
        sourcePath: String,
        relPath: String,
        targetFolderId: String?,
        fileHash: String
    ): UIAssetItem? {
        if (!copyLocalFile(sourcePath, relPath)) {
            AppLogger.w("UIStudioViewModel", "素材拷贝失败: $sourcePath -> $relPath")
            return null
        }
        val (imgWidth, imgHeight) = decodeImageSize(relPath)
        return UIAssetItem(
            id = "asset_${fileHash.take(8)}_${getCurrentTimeMillis() % 100000}",
            relativePath = relPath,
            width = imgWidth,
            height = imgHeight,
            category = "image",
            folderId = targetFolderId,
            hash = fileHash
        )
    }

    /**
     * 处理导入重名冲突（AppImportConflictDialog 回调入口）。
     * 替换 = 覆盖目标目录下同名文件内容，仅刷新条目宽高与更新时间，hash/id/引用链不动；
     * 保留两者 = 自动追加 _N 序号别名后作为新条目入库；
     * 跳过 = 放弃冲突文件。
     */
    fun resolveImportConflicts(strategy: ImportConflictStrategy) {
        val conflict = state.importConflictState ?: return
        state = state.copy(importConflictState = null)
        scope.launch(Dispatchers.Default) {
            val importDirRel = "templates/${conflict.projectSanitizedName}/assets/import"
            var overwritten = 0
            var added = 0
            val newItems = mutableListOf<UIAssetItem>()
            var currentItems = state.assetLibrary.items

            for (c in conflict.conflicts) {
                try {
                    when (strategy) {
                        ImportConflictStrategy.SKIP -> Unit
                        ImportConflictStrategy.KEEP_BOTH -> {
                            val base = c.desiredFileName.substringBeforeLast('.')
                            val ext = c.desiredFileName.substringAfterLast('.', "")
                            var n = 1
                            var candidate = c.desiredFileName
                            while (
                                currentItems.any { it.relativePath.equals("$importDirRel/$candidate", ignoreCase = true) } ||
                                AssetFileNameGuard.isFileNameTakenInDir(storage, importDirRel, candidate)
                            ) {
                                candidate = if (ext.isBlank()) "${base}_$n" else "${base}_$n.$ext"
                                n++
                            }
                            val relPath = "$importDirRel/$candidate"
                            val item = importSingleFile(c.sourcePath, relPath, conflict.targetFolderId, c.fileHash)
                            if (item != null) {
                                newItems.add(item)
                                currentItems = currentItems + item
                                added++
                            }
                        }
                        ImportConflictStrategy.OVERWRITE -> {
                            val targetItem = currentItems.firstOrNull {
                                it.relativePath.equals("$importDirRel/${c.desiredFileName}", ignoreCase = true)
                            }
                            if (targetItem != null) {
                                val bytes = readLocalFileBytes(c.sourcePath)
                                if (bytes != null) {
                                    storage.saveBytesToFile(targetItem.relativePath, bytes)
                                    val (w, h) = decodeImageSize(targetItem.relativePath)
                                    currentItems = currentItems.map {
                                        if (it.id == targetItem.id) {
                                            it.copy(width = w, height = h, updatedAt = getCurrentTimeMillis())
                                        } else it
                                    }
                                    overwritten++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("UIStudioViewModel", "冲突处理失败: ${c.sourcePath} (${e.message})")
                }
            }

            if (overwritten > 0 || added > 0) {
                state = state.copy(
                    assetLibrary = state.assetLibrary.copy(items = currentItems),
                    leftBottomTab = UIStudioLeftBottomTab.ASSETS
                )
                saveAssetLibrary()
            }

            val msg = when (strategy) {
                ImportConflictStrategy.OVERWRITE -> "冲突处理完成：替换 $overwritten 个文件"
                ImportConflictStrategy.KEEP_BOTH -> "冲突处理完成：别名新增 $added 个文件"
                ImportConflictStrategy.SKIP -> "冲突处理完成：已跳过"
            }
            Toast.show(msg, ToastType.SUCCESS)
        }
    }

    fun openNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = true)
    }

    fun closeNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = false)
    }

    /**
     * 切换素材文件夹的展开/折叠状态。
     */
    fun toggleFolderExpanded(folderId: String) {
        val current = state.expandedFolderIds
        state = state.copy(
            expandedFolderIds = if (current.contains(folderId)) current - folderId else current + folderId
        )
    }

    /**
     * 一键全部展开或全部折叠所有素材文件夹。
     */
    fun toggleAllFoldersExpanded() {
        val folders = state.assetLibrary.folders
        if (folders.isEmpty()) return
        val allExpanded = folders.all { state.expandedFolderIds.contains(it.id) }
        state = state.copy(
            expandedFolderIds = if (allExpanded) emptySet() else folders.map { it.id }.toSet()
        )
    }

    fun setDraggedAssetId(id: String?) { state = state.copy(draggedAssetId = id) }
    fun setHoveredFolderId(id: String?) { state = state.copy(hoveredFolderId = id) }

    fun startRenameFolder(folder: UIAssetFolder) { state = state.copy(renamingFolder = folder) }
    fun cancelRenameFolder() { state = state.copy(renamingFolder = null) }
    fun commitRenameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        val updatedFolders = state.assetLibrary.folders.map {
            if (it.id == folderId) it.copy(name = newName.trim()) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(folders = updatedFolders),
            renamingFolder = null
        )
        saveAssetLibrary()
        Toast.show("文件夹已重命名为: $newName", ToastType.SUCCESS)
    }

    fun startRenameAsset(asset: UIAssetItem) { state = state.copy(renamingAsset = asset) }
    fun cancelRenameAsset() { state = state.copy(renamingAsset = null) }
    suspend fun commitRenameAsset(assetId: String, newName: String): Boolean {
        val target = state.assetLibrary.items.find { it.id == assetId } ?: return false
        val clean = AssetFileNameGuard.sanitizeFileName(newName)
        if (clean.isBlank()) {
            Toast.show("名称不能为空", ToastType.ERROR)
            return false
        }
        val oldFileName = target.relativePath.substringAfterLast('/').substringAfterLast('\\')
        val ext = oldFileName.substringAfterLast('.', "png")
        val newFileName = if (ext.isBlank()) clean else "$clean.$ext"
        val dir = target.relativePath.substringBeforeLast('/', "")

        // 不区分大小写重名守卫：排除自身原文件名（仅大小写微调亦放行）
        if (AssetFileNameGuard.isFileNameTakenInDir(storage, dir, newFileName, excludeFileName = oldFileName)) {
            Toast.show("当前目录已存在同名素材（不区分大小写）：$clean，请修改名称后重试", ToastType.ERROR)
            return false
        }

        val newRelPath = if (dir.isNotBlank()) "$dir/$newFileName" else newFileName
        if (newRelPath != target.relativePath) {
            try {
                val oldFile = TemplateFile(target.relativePath)
                val newFile = TemplateFile(newRelPath)
                if (oldFile.exists()) {
                    val bytes = oldFile.readBytes()
                    if (bytes != null) {
                        newFile.writeBytes(bytes)
                        oldFile.delete()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "重命名素材物理文件失败: ${target.relativePath} -> $newRelPath", e)
                return false
            }
        }
        val updatedItems = state.assetLibrary.items.map {
            if (it.id == assetId) it.copy(relativePath = newRelPath, updatedAt = getCurrentTimeMillis()) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems),
            renamingAsset = null
        )
        saveAssetLibrary()
        Toast.show("素材已重命名为: $clean", ToastType.SUCCESS)
        return true
    }

    fun revealAssetInExplorer(asset: UIAssetItem) {
        val path = TemplateFile(asset.relativePath).getAbsolutePath()
        getPlatform().openInFileExplorer(path)
    }

    /**
     * 获取或计算素材的 16 位紧凑短哈希值
     * 若素材模型中已有 hash 则规整后返回；若为空则即时计算并持久化回填
     */
    suspend fun getOrComputeAssetHash(asset: UIAssetItem): String? {
        val existing = asset.hash
        if (!existing.isNullOrBlank()) {
            if (existing.length > 16) {
                val shortHash = existing.toShortHash()
                val updated = asset.copy(hash = shortHash)
                val updatedItems = state.assetLibrary.items.map { if (it.id == asset.id) updated else it }
                state = state.copy(assetLibrary = state.assetLibrary.copy(items = updatedItems))
                saveAssetLibrary()
                return shortHash
            }
            return existing
        }
        val absPath = TemplateFile(asset.relativePath).getAbsolutePath()
        val calculated = calculateShortFileHash(absPath)
        if (!calculated.isNullOrBlank()) {
            val updated = asset.copy(hash = calculated)
            val updatedItems = state.assetLibrary.items.map { if (it.id == asset.id) updated else it }
            state = state.copy(assetLibrary = state.assetLibrary.copy(items = updatedItems))
            saveAssetLibrary()
            return calculated
        }
        return null
    }

    fun revealFolderInExplorer(folderId: String? = state.selectedFolderId) {
        val proj = state.project?.projectId ?: "default"
        val folder = state.assetLibrary.folders.find { it.id == folderId }
        val subPath = if (folder != null) "templates/$proj/assets/${folder.name}" else "templates/$proj/assets"
        val path = TemplateFile(subPath).getAbsolutePath()
        getPlatform().openInFileExplorer(path)
    }

    fun addAssetToCanvas(
        asset: UIAssetItem,
        logicalX: Float? = null,
        logicalY: Float? = null,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: run {
            Toast.show("当前没有处于活跃的视图界面，请先选择或新建视图", ToastType.INFO)
            return
        }
        val activeContainer = state.activeContainerBlock
        val targetW = asset.width.coerceAtLeast(32)
        val targetH = asset.height.coerceAtLeast(32)
        val effectiveHash = asset.hash?.toShortHash() ?: blockingAwait { calculateShortFileHash(asset.relativePath) }
        if (effectiveHash != null && asset.hash != effectiveHash) {
            val updatedLib = state.assetLibrary.copy(
                items = state.assetLibrary.items.map { if (it.id == asset.id) it.copy(hash = effectiveHash) else it }
            )
            state = state.copy(assetLibrary = updatedLib)
            saveAssetLibrary()
        }
        canvasEditor.saveSnapshot("添加素材: ${asset.name}")

        if (activeContainer != null) {
            // 当前处于父组件打开的独立编辑视口：添加到该父组件内（居中落位在光标松手点）
            val posX = if (logicalX != null) logicalX - (targetW / 2f) else ((activeContainer.bounds.width - targetW).coerceAtLeast(0f) / 2f)
            val posY = if (logicalY != null) logicalY - (targetH / 2f) else ((activeContainer.bounds.height - targetH).coerceAtLeast(0f) / 2f)
            val newBlock = UIStudioBlock(
                id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
                type = UIBlockType.IMAGE,
                bounds = SerialRect(posX, posY, posX + targetW, posY + targetH),
                assetHash = effectiveHash
            )
            val updatedContainer = activeContainer.copy(children = activeContainer.children + newBlock)
            val updatedBlocks = view.blocks.updateBlockInList(activeContainer.id) { updatedContainer }.bindParents()
            canvasEditor.updateViewBlocksAndSave(view.id, updatedBlocks, repo)
            state = state.copy(selectedBlockId = newBlock.id)
            Toast.show("已将素材添加到容器「${activeContainer.id}」: ${asset.name}", ToastType.SUCCESS)
        } else {
            // 主视图界面：添加到页面根级 blocks（居中落位在光标松手点）
            val posX = if (logicalX != null) logicalX - (targetW / 2f) else ((view.width - targetW).coerceAtLeast(0f) / 2f)
            val posY = if (logicalY != null) logicalY - (targetH / 2f) else ((view.height - targetH).coerceAtLeast(0f) / 2f)
            val newBlock = UIStudioBlock(
                id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
                type = UIBlockType.IMAGE,
                bounds = SerialRect(posX, posY, posX + targetW, posY + targetH),
                assetHash = effectiveHash
            )
            val updatedBlocks = (view.blocks + newBlock).bindParents()
            canvasEditor.updateViewBlocksAndSave(view.id, updatedBlocks, repo)
            state = state.copy(selectedBlockId = newBlock.id)
            Toast.show("已将素材添加到视图: ${asset.name}", ToastType.SUCCESS)
        }
    }

    /**
     * 将 AI 聊天生成的纯内存图片字节按需持久化存入项目素材库（assets/image/）。
     * 仅当用户主动触发「存入素材库」或「添加到页面」时调用，彻底告别 assets/generated 垃圾落盘。
     *
     * @param imageBytes 原始或去背后的图片字节数据
     * @param fileName 用户自定义的文件名（如 "gold_chest"）
     * @param subPath 目标子文件夹相对路径（如 "icons/rewards"，若为空则存入 assets/image/ 根目录）
     * @param isPng 是否为透明 PNG 格式
     * @return 成功保存后的素材库相对路径（例如 "templates/MyProj/assets/image/icons/rewards/gold_chest.png"），失败返回 null
     */
    suspend fun saveChatImageToAssetLibrary(
        imageBytes: ByteArray,
        fileName: String,
        subPath: String = "",
        isPng: Boolean = true
    ): String? {
        val proj = state.project?.projectId?.ifBlank { null } ?: return null
        val cleanSubPath = subPath.trim().trim('/', '\\')
        val ext = if (isPng) "png" else "jpg"
        val cleanName = fileName.trim().let {
            if (it.endsWith(".$ext", ignoreCase = true)) it else "$it.$ext"
        }
        val targetRelDir = if (cleanSubPath.isBlank()) {
            "templates/$proj/assets/image"
        } else {
            "templates/$proj/assets/image/$cleanSubPath"
        }
        val targetFullPath = "$targetRelDir/$cleanName"

        val targetDir = targetFullPath.substringBeforeLast('/')
        val targetFileName = targetFullPath.substringAfterLast('/')
        if (AssetFileNameGuard.isFileNameTakenInDir(storage, targetDir, targetFileName)) {
            Toast.show("保存被拒绝：当前目录已存在同名文件（不区分大小写）：$targetFileName，请修改文件名后重新保存", ToastType.ERROR)
            return null
        }

        return try {
            if (imageBytes.isEmpty()) {
                AppLogger.w("UIStudioViewModel", "⚠️ 尝试保存 0 字节图片至素材库，已安全拦截: $targetFullPath")
                Toast.show("保存失败: 图像数据为空", ToastType.ERROR)
                return null
            }
            val savedPath = storage.saveBytesToFile(targetFullPath, imageBytes)
            // 1. 同步在内存素材库中构建并登记该新素材，确保无论哈希如何均立即生效并高亮
            val (w, h) = try {
                val skiaImg = Image.makeFromEncoded(imageBytes)
                skiaImg.width to skiaImg.height
            } catch (_: Exception) {
                100 to 100
            }
            val fileHash = calculateShortFileHash(targetFullPath)
            val baseName = cleanName.substringBeforeLast('.')
            val targetFolder = if (cleanSubPath.isNotBlank()) {
                state.assetLibrary.folders.find { it.name.equals(cleanSubPath, ignoreCase = true) }
            } else null
            val newAssetId = baseName.ifBlank { "ai_img_${getCurrentTimeMillis()}" }
            val newAsset = UIAssetItem(
                id = newAssetId,
                relativePath = targetFullPath,
                width = w,
                height = h,
                category = "image",
                folderId = targetFolder?.id,
                hash = fileHash
            )
            val existingList = state.assetLibrary.items.filterNot { it.relativePath == targetFullPath }
            val updatedItems = existingList + newAsset
            val updatedFolders = state.expandedFolderIds.let {
                if (targetFolder != null) it + targetFolder.id else it
            }
            state = state.copy(
                assetLibrary = state.assetLibrary.copy(items = updatedItems),
                selectedAssetId = newAsset.id,
                expandedFolderIds = updatedFolders
            )
            saveAssetLibrary()
            refreshAssetLibrary(focusAssetPath = targetFullPath)
            Toast.show("已成功存入素材库: $cleanName", ToastType.SUCCESS)
            AppLogger.i("UIStudioViewModel", "✅ AI 聊天图片已成功存入素材库并即时选中聚焦: $targetFullPath (ID: ${newAsset.id})")
            savedPath
        } catch (e: Exception) {
            AppLogger.e("UIStudioViewModel", "保存 AI 聊天图片至素材库失败: $targetFullPath", e)
            Toast.show("保存失败: ${e.message}", ToastType.ERROR)
            null
        }
    }

    /**
     * 将 AI 聊天生成的图片一键存入素材库，并直接作为图元挂载添加到当前激活页面或容器的中心。
     */
    suspend fun addChatImageToCurrentView(
        imageBytes: ByteArray,
        fileName: String,
        width: Float = 256f,
        height: Float = 256f,
        subPath: String = "",
        isPng: Boolean = true,
        repo: TemplateRepository = TemplateRepository()
    ): Boolean {
        val savedRelPath = saveChatImageToAssetLibrary(imageBytes, fileName, subPath, isPng) ?: return false
        val cleanName = fileName.trim().substringBeforeLast('.')
        return try {
            val item = state.assetLibrary.items.find { it.relativePath == savedRelPath }
                ?: UIAssetItem(
                    id = "asset_${getCurrentTimeMillis()}",
                    relativePath = savedRelPath,
                    width = width.toInt().coerceAtLeast(1),
                    height = height.toInt().coerceAtLeast(1),
                    category = "image",
                    hash = calculateShortFileHash(savedRelPath)
                )
            addAssetToCanvas(asset = item, repo = repo)
            true
        } catch (e: Exception) {
            AppLogger.e("UIStudioViewModel", "将 AI 图片添加到视图失败", e)
            Toast.show("添加到视图失败: ${e.message}", ToastType.ERROR)
            false
        }
    }
}

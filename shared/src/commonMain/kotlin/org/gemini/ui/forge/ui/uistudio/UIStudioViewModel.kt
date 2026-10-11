package org.gemini.ui.forge.ui.uistudio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.assetsheet.AssetImportDraft
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIPage
import kotlin.random.Random
import kotlinx.datetime.Clock
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.uistudio.model.ProjectAgentConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.calculateBlockAbsoluteBounds
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.findParentBlockId
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.updateBlockInList

/**
 * UI Studio 独立工作室的视图模型。
 * 负责画布交互、视口变换矩阵、工程生命周期、素材库联动、草稿暂存自愈与切片入库。
 */
class UIStudioViewModel {

    /** 专属后台协程作用域 */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val jsonHelper = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val storage = LocalFileStorage()
    private val mattingService = LocalMattingService(storage)
    private val configManager = ConfigManager()

    /** 界面响应式状态快照 */
    var state by mutableStateOf(UIStudioState())
        private set

    /**
     * 初始化或重新加载 UI Studio 工程。
     *
     * @param project 传入的工程对象，若为 null 则自动创建默认空白工程
     */
    fun loadProject(project: ProjectState?) {
        val effectiveProject = project ?: createDefaultBlankProject("NewUIStudioProject", 1920f, 1080f)
        val firstPage = effectiveProject.pages.firstOrNull()
        val initialWidth = firstPage?.width?.takeIf { it > 0 } ?: 1920f
        val initialHeight = firstPage?.height?.takeIf { it > 0 } ?: 1080f
        val initialOpenTabs = firstPage?.let { listOf(it.id) } ?: emptyList()

        state = state.copy(
            project = effectiveProject,
            currentViewId = firstPage?.id,
            openViewIds = if (state.openViewIds.isEmpty()) initialOpenTabs else state.openViewIds,
            canvasWidth = initialWidth,
            canvasHeight = initialHeight,
            scale = 0.6f,
            panOffset = Offset(100f, 60f),
            selectedBlockId = null
        )

        // 异步加载素材库 (asset_library.json)
        loadAssetLibrary(effectiveProject.projectId)

        // 异步检查并恢复未完成的切片校对草稿
        checkDraft(effectiveProject.projectId)
    }

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
        val existingPaths = initialLib.items.map { it.relativePath.replace("\\", "/") }.toMutableSet()
        val existingIds = initialLib.items.map { it.id }.toMutableSet()
        val newItems = mutableListOf<UIAssetItem>()

        // 1. 优先从当前项目所有页面的图元绑定资产中提取高精度元数据
        val allBlocks = state.project?.pages?.flatMap { it.blocks } ?: emptyList()
        for (block in allBlocks) {
            val tFile = block.currentImageUri ?: block.referenceImage
            val rawUri = tFile?.relativePath ?: ""
            val uri = rawUri.replace("\\", "/")
            if (uri.isNotBlank() && (uri.endsWith(".png", ignoreCase = true) || uri.endsWith(".webp", ignoreCase = true))) {
                if (!existingPaths.contains(uri) && !existingIds.contains(block.id)) {
                    val displayName = block.userPromptZh.ifBlank { block.userPromptEn.ifBlank { block.id } }
                    val item = UIAssetItem(
                        id = block.id,
                        name = displayName,
                        relativePath = uri,
                        width = block.bounds.width.toInt().coerceAtLeast(1),
                        height = block.bounds.height.toInt().coerceAtLeast(1),
                        category = block.type.name.lowercase(),
                        folderId = null,
                        createdAt = getCurrentTimeMillis()
                    )
                    newItems.add(item)
                    existingPaths.add(uri)
                    existingIds.add(block.id)
                }
            }
        }

        // 2. 深度扫描本地物理 assets 目录兜底自愈
        try {
            val assetsDirRel = "templates/$sanitized/assets"
            val directFiles = storage.listFiles(assetsDirRel)
            for (file in directFiles) {
                if (file.endsWith(".png", ignoreCase = true) || file.endsWith(".webp", ignoreCase = true)) {
                    val relPath = "$assetsDirRel/$file"
                    if (!existingPaths.contains(relPath)) {
                        val baseId = file.substringBeforeLast(".")
                        if (!existingIds.contains(baseId)) {
                            newItems.add(
                                UIAssetItem(
                                    id = baseId,
                                    name = baseId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    createdAt = getCurrentTimeMillis()
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(baseId)
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
                        if (!existingPaths.contains(relPath)) {
                            val baseId = file.substringBeforeLast(".")
                            val effectiveId = if (existingIds.contains(baseId)) "${sub}_$baseId" else baseId
                            newItems.add(
                                UIAssetItem(
                                    id = effectiveId,
                                    name = if (sub.isNotBlank()) sub else baseId,
                                    relativePath = relPath,
                                    width = 100,
                                    height = 100,
                                    category = "image",
                                    folderId = null,
                                    createdAt = getCurrentTimeMillis()
                                )
                            )
                            existingPaths.add(relPath)
                            existingIds.add(effectiveId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "物理目录自愈扫描部分跳过: ${e.message}")
        }

        return if (newItems.isNotEmpty()) {
            val updated = initialLib.copy(items = initialLib.items + newItems)
            AppLogger.i("UIStudioViewModel", "✨ 自愈补齐素材库完成: 新增 ${newItems.size} 个物理素材！")
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
                    // 更新素材更新时间以触发 UI 重组刷新
                    val updatedItems = state.assetLibrary.items.map {
                        if (it.id == assetId) it.copy(createdAt = getCurrentTimeMillis()) else it
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

        // 同步递归更新 project 内所有图元（包括容器内嵌套图元）的关联图片路径
        fun updateBlockImages(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                val updatedChildren = updateBlockImages(b.children)
                if (b.id == assetId) {
                    b.copy(
                        currentImageUri = TemplateFile(newRelPath),
                        referenceImage = TemplateFile(newRelPath),
                        children = updatedChildren
                    )
                } else {
                    b.copy(children = updatedChildren)
                }
            }
        }

        val updatedPages = state.project?.pages?.map { page ->
            page.copy(blocks = updateBlockImages(page.blocks))
        }

        val updatedProject = if (updatedPages != null && state.project != null) {
            state.project!!.copy(pages = updatedPages)
        } else state.project

        state = state.copy(
            project = updatedProject,
            assetLibrary = state.assetLibrary.copy(items = updatedItems)
        )
        saveAssetLibrary()
        saveProject(repo)
        val destDesc = targetFolder?.name ?: "根目录"
        Toast.show("素材已移动至: $destDesc", ToastType.SUCCESS)
    }

    /**
     * 递归遍历并更新模块树中的目标图元。
     */
    private fun updateBlockInTreeRecursively(
        blocks: List<UIBlock>,
        targetId: String,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else if (b.children.isNotEmpty()) {
                b.copy(children = updateBlockInTreeRecursively(b.children, targetId, transform))
            } else {
                b
            }
        }
    }

    /**
     * 递归遍历整棵模块树并统一变换所有图元。
     */
    private fun mapAllBlocksRecursively(
        blocks: List<UIBlock>,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            val updated = transform(b)
            if (updated.children.isNotEmpty()) {
                updated.copy(children = mapAllBlocksRecursively(updated.children, transform))
            } else {
                updated
            }
        }
    }

    /**
     * 切换指定组件/图层的锁定状态（递归支持容器内的子图元）。
     */
    fun toggleBlockLock(blockId: String, repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(currentV.blocks, blockId) { b ->
            val newLock = !b.isLocked
            // 若为容器，级联锁定全部子项
            if (b.children.isNotEmpty()) {
                b.copy(isLocked = newLock, children = mapAllBlocksRecursively(b.children) { it.copy(isLocked = newLock) })
            } else {
                b.copy(isLocked = newLock)
            }
        }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
    }

    /**
     * 一键锁定/解锁当前视图的所有组件（全层级递归）：
     * 若存在任一未锁定的组件，则将全部组件锁定；
     * 若所有组件均已锁定，则全部解锁。
     */
    fun toggleAllBlocksLocked(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyUnlocked(list: List<UIBlock>): Boolean = list.any { !it.isLocked || hasAnyUnlocked(it.children) }
        val targetLock = hasAnyUnlocked(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isLocked = targetLock) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetLock) "已锁定当前视图全部组件" else "已解锁当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 一键显示/隐藏当前视图的所有组件（全层级递归）：
     * 若存在任一隐藏的组件，则将全部组件设为显示；
     * 若所有组件均已显示，则全部隐藏。
     */
    fun toggleAllBlocksVisibility(repo: TemplateRepository) {
        val currentV = state.currentView ?: return
        if (currentV.blocks.isEmpty()) return
        fun hasAnyHidden(list: List<UIBlock>): Boolean = list.any { !it.isVisible || hasAnyHidden(it.children) }
        val targetVisible = hasAnyHidden(currentV.blocks)
        val updatedBlocks = mapAllBlocksRecursively(currentV.blocks) { it.copy(isVisible = targetVisible) }.bindParents()
        updateViewBlocksAndSave(currentV.id, updatedBlocks, repo)
        val msg = if (targetVisible) "已显示当前视图全部组件" else "已隐藏当前视图全部组件"
        Toast.show(msg, ToastType.INFO)
    }

    /**
     * 快捷发起将两个组件合并至新建纯父容器的确认弹窗。
     */
    fun groupBlocksIntoContainer(draggedId: String, targetId: String) {
        state = state.copy(pendingCreateContainer = draggedId to targetId)
    }

    /**
     * 展开/折叠指定图层容器。
     */
    fun toggleHierarchyBlockExpanded(blockId: String) {
        val current = state.expandedHierarchyBlockIds
        state = state.copy(
            expandedHierarchyBlockIds = if (current.contains(blockId)) current - blockId else current + blockId
        )
    }

    /**
     * 一键全部展开/全部折叠图层树容器。
     */
    fun toggleAllHierarchyBlocksExpanded() {
        val currentV = state.currentView ?: return
        val allContainerIds = currentV.blocks.filter { it.isPureContainer || it.children.isNotEmpty() }.map { it.id }.toSet()
        val isAllExpanded = state.expandedHierarchyBlockIds.containsAll(allContainerIds) && allContainerIds.isNotEmpty()
        state = state.copy(
            expandedHierarchyBlockIds = if (isAllExpanded) emptySet() else allContainerIds
        )
        val msg = if (isAllExpanded) "已折叠全部容器" else "已展开全部容器"
        Toast.show(msg, ToastType.INFO)
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
        state = state.copy(selectedAssetId = assetId)
    }

    /**
     * 删除单个素材并清理磁盘文件。
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
                TemplateFile(target.relativePath).writeBytes(ByteArray(0))
            } catch (_: Exception) {}
        }
        Toast.show("已删除素材: ${target.name}", ToastType.INFO)
    }

    fun openNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = true)
    }

    fun closeNewFolderDialog() {
        state = state.copy(isNewFolderDialogVisible = false)
    }

    // ====== 视图界面 (Views / Pages) 生命周期管理 ======

    /**
     * 选中并激活指定视图界面，同时确保在顶部标签栏中处于打开状态。
     */
    fun selectView(viewId: String) {
        openViewTab(viewId)
    }

    /**
     * 在中间顶部标签栏中打开并激活指定视图界面。
     */
    fun openViewTab(viewId: String) {
        val page = state.project?.pages?.find { it.id == viewId } ?: return
        val currentOpen = state.openViewIds
        val updatedOpen = if (currentOpen.contains(viewId)) currentOpen else currentOpen + viewId
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = viewId,
            canvasWidth = page.width,
            canvasHeight = page.height,
            activeContainerId = null,
            selectedBlockId = null,
            selectedAssetId = null
        )
    }

    /**
     * 选中并激活指定页面视图标签。
     */
    fun selectViewTab(viewId: String) {
        selectView(viewId)
        state = state.copy(activeContainerId = null)
    }

    /**
     * 打开父容器的专属独立编辑视图标签页。
     */
    fun openContainerTab(containerId: String) {
        val currentTabs = state.openContainerTabs
        val updatedTabs = if (currentTabs.contains(containerId)) currentTabs else currentTabs + containerId
        state = state.copy(
            openContainerTabs = updatedTabs,
            activeContainerId = containerId,
            selectedBlockId = containerId
        )
        Toast.show("已打开容器独立布局视图", ToastType.INFO)
    }

    /**
     * 关闭指定父容器的编辑标签页。
     */
    fun closeContainerTab(containerId: String) {
        val currentTabs = state.openContainerTabs
        if (!currentTabs.contains(containerId)) return
        val updatedTabs = currentTabs.filter { it != containerId }
        val newActiveId = if (state.activeContainerId == containerId) {
            updatedTabs.lastOrNull()
        } else {
            state.activeContainerId
        }
        state = state.copy(
            openContainerTabs = updatedTabs,
            activeContainerId = newActiveId
        )
    }

    /**
     * 切换选中指定父容器编辑标签页。
     */
    fun selectContainerTab(containerId: String) {
        state = state.copy(
            activeContainerId = containerId,
            selectedBlockId = containerId
        )
    }

    /**
     * 关闭指定视图的标签页。
     */
    fun closeViewTab(viewId: String) {
        val currentOpen = state.openViewIds
        if (!currentOpen.contains(viewId)) return
        val updatedOpen = currentOpen.filter { it != viewId }
        val newActiveId = if (state.currentViewId == viewId) {
            val idx = currentOpen.indexOf(viewId)
            if (updatedOpen.isNotEmpty()) {
                val nextIdx = (idx - 1).coerceAtLeast(0)
                updatedOpen.getOrNull(nextIdx) ?: updatedOpen.firstOrNull()
            } else {
                null
            }
        } else {
            state.currentViewId
        }
        val nextPage = state.project?.pages?.find { it.id == newActiveId }
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = newActiveId,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            activeContainerId = null,
            selectedBlockId = null
        )
    }

    /**
     * 一键关闭中间顶部所有打开的视图标签页，彻底清空画布及组件渲染。
     */
    fun closeAllViewTabs() {
        state = state.copy(
            openViewIds = emptyList(),
            currentViewId = null,
            openContainerTabs = emptyList(),
            activeContainerId = null,
            selectedBlockId = null,
            selectedAssetId = null
        )
        Toast.show("已关闭所有视图标签", ToastType.INFO)
    }

    /**
     * 关闭除指定视图之外的其他所有视图标签页。
     */
    fun closeOtherViewTabs(viewId: String) {
        if (!state.openViewIds.contains(viewId)) return
        val nextPage = state.project?.pages?.find { it.id == viewId }
        state = state.copy(
            openViewIds = listOf(viewId),
            currentViewId = viewId,
            openContainerTabs = emptyList(),
            activeContainerId = null,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            selectedBlockId = null
        )
        Toast.show("已关闭其他视图标签", ToastType.INFO)
    }

    /**
     * 切换左侧边栏下半部分的工作区 Tab (素材库 / 图层层级)。
     */
    fun switchLeftBottomTab(tab: UIStudioLeftBottomTab) {
        state = state.copy(leftBottomTab = tab)
    }

    /**
     * 动态修改指定视图界面的画布宽高尺寸并持久化。
     */
    fun updatePageDimensions(viewId: String, newWidth: Float, newHeight: Float, repo: TemplateRepository) {
        val proj = state.project ?: return
        val targetPage = proj.pages.find { it.id == viewId } ?: return
        val clampedW = newWidth.coerceIn(100f, 10000f)
        val clampedH = newHeight.coerceIn(100f, 10000f)
        val updatedPage = targetPage.copy(width = clampedW, height = clampedH).postProcess()
        val updatedPages = proj.pages.map { if (it.id == viewId) updatedPage else it }
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(
            project = updatedProject,
            canvasWidth = if (state.currentViewId == viewId) clampedW else state.canvasWidth,
            canvasHeight = if (state.currentViewId == viewId) clampedH else state.canvasHeight
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("已更新视图尺寸: ${clampedW.toInt()} × ${clampedH.toInt()}", ToastType.SUCCESS)
    }

    /**
     * 清空当前所有选中状态（组件与素材均重置），联动恢复页面主视图属性面板。
     */
    fun clearSelection() {
        if (state.selectedBlockId != null || state.selectedAssetId != null) {
            state = state.copy(
                selectedBlockId = null,
                selectedAssetId = null
            )
        }
    }

    /**
     * 新建一个视图界面（Page）。
     */
    fun createView(name: String, width: Float, height: Float, repo: TemplateRepository) {
        val proj = state.project ?: return
        val viewName = name.trim().ifBlank { "View_${proj.pages.size + 1}" }
        val newPage = UIPage(
            id = "view_${getCurrentTimeMillis()}",
            nameStr = viewName,
            width = width,
            height = height,
            blocks = emptyList()
        )
        val updatedPages = proj.pages + newPage
        val updatedProject = proj.copy(pages = updatedPages)
        val updatedOpen = state.openViewIds + newPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = newPage.id,
            openViewIds = updatedOpen,
            canvasWidth = width,
            canvasHeight = height,
            selectedBlockId = null,
            isNewViewDialogVisible = false
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("成功创建视图: $viewName", ToastType.SUCCESS)
    }

    /**
     * 复制并克隆指定视图界面 (含全部图元深度复制)。
     */
    fun duplicateView(page: UIPage, repo: TemplateRepository) {
        val proj = state.project ?: return
        val newViewName = "${page.nameStr}_副本"
        val timestamp = getCurrentTimeMillis()
        val clonedBlocks = page.blocks.map { block ->
            block.copy(id = "block_${timestamp}_${block.id.takeLast(6)}")
        }
        val duplicatedPage = UIPage(
            id = "view_$timestamp",
            nameStr = newViewName,
            width = page.width,
            height = page.height,
            blocks = clonedBlocks
        )
        val updatedPages = proj.pages + duplicatedPage
        val updatedProject = proj.copy(pages = updatedPages)
        val updatedOpen = state.openViewIds + duplicatedPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = duplicatedPage.id,
            openViewIds = updatedOpen,
            canvasWidth = duplicatedPage.width,
            canvasHeight = duplicatedPage.height,
            selectedBlockId = null
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("已克隆视图: $newViewName", ToastType.SUCCESS)
    }

    /**
     * 删除指定视图界面。
     */
    fun deleteView(viewId: String, repo: TemplateRepository) {
        val proj = state.project ?: return
        if (proj.pages.size <= 1) {
            Toast.show("至少保留一个主视图界面", ToastType.INFO)
            return
        }
        val updatedPages = proj.pages.filterNot { it.id == viewId }
        val nextViewId = if (state.currentViewId == viewId) updatedPages.firstOrNull()?.id else state.currentViewId
        val nextPage = updatedPages.find { it.id == nextViewId }
        val updatedProject = proj.copy(pages = updatedPages)
        state = state.copy(
            project = updatedProject,
            currentViewId = nextViewId,
            canvasWidth = nextPage?.width ?: 1920f,
            canvasHeight = nextPage?.height ?: 1080f,
            selectedBlockId = null,
            deletingView = null
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        Toast.show("已删除视图", ToastType.INFO)
    }

    fun requestDeleteView(page: UIPage) {
        state = state.copy(deletingView = page)
    }

    fun cancelDeleteView() {
        state = state.copy(deletingView = null)
    }

    fun confirmDeleteView(repo: TemplateRepository) {
        val page = state.deletingView ?: return
        deleteView(page.id, repo)
    }

    fun updateViewSearchQuery(query: String) {
        state = state.copy(viewSearchQuery = query)
    }

    fun openNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = true)
    }

    fun closeNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = false)
    }

    fun openAiChatDialog() {
        state = state.copy(isAiChatDialogVisible = true)
        loadProjectAgentConfig()
        loadChatSessions()
    }

    fun closeAiChatDialog() {
        state = state.copy(isAiChatDialogVisible = false)
    }

    fun openSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = true)
    }

    fun closeSystemInstructionDialog() {
        state = state.copy(isSystemInstructionDialogVisible = false)
    }

    fun requestDeleteChatSession(session: VisualChatSession) {
        state = state.copy(deletingSession = session)
    }

    fun cancelDeleteChatSession() {
        state = state.copy(deletingSession = null)
    }

    fun confirmDeleteChatSession() {
        val target = state.deletingSession ?: return
        state = state.copy(deletingSession = null)
        deleteChatSession(target.id)
    }

    fun requestClearChatMessages() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
        clearCurrentChatSessionMessages()
    }

    fun addChatAttachment(path: String) {
        if (path.isNotBlank() && !state.aiChatAttachments.contains(path)) {
            state = state.copy(aiChatAttachments = state.aiChatAttachments + path)
        }
    }

    fun removeChatAttachment(path: String) {
        state = state.copy(aiChatAttachments = state.aiChatAttachments - path)
    }

    fun clearChatHistory() {
        requestClearChatMessages()
    }

    fun loadProjectAgentConfig() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/agent_config.json"
        scope.launch(Dispatchers.Default) {
            try {
                val file = TemplateFile(path)
                if (file.exists()) {
                    val text = file.readBytes()?.decodeToString()
                    if (!text.isNullOrBlank()) {
                        val jsonFormat = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val config = jsonFormat.decodeFromString(ProjectAgentConfig.serializer(), text)
                        state = state.copy(projectAgentConfig = config)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载项目 Agent 配置失败", e)
            }
        }
    }

    fun updateProjectAgentConfig(config: ProjectAgentConfig) {
        saveProjectAgentConfig(config)
    }

    fun updateProjectModel(modelName: String) {
        val updated = state.projectAgentConfig.copy(modelName = modelName)
        saveProjectAgentConfig(updated, notifyUser = false)
        Toast.show("已切换模型: $modelName", ToastType.SUCCESS)
    }

    fun updateProjectModel(model: GeminiModel) {
        val updated = state.projectAgentConfig.copy(modelName = model.modelName)
        saveProjectAgentConfig(updated, notifyUser = false)
        Toast.show("已切换模型: ${model.displayName}", ToastType.SUCCESS)
    }

    fun updateProjectSystemPrompt(systemPrompt: String) {
        val updated = state.projectAgentConfig.copy(systemPrompt = systemPrompt)
        saveProjectAgentConfig(updated, notifyUser = true)
    }

    fun updateProjectApiFlavor(apiFlavor: ApiFlavor) {
        val updated = state.projectAgentConfig.copy(apiFlavor = apiFlavor)
        saveProjectAgentConfig(updated, notifyUser = false)
        val name = when (apiFlavor) {
            ApiFlavor.INTERACTIONS -> "会话 API"
            ApiFlavor.GENERATE_CONTENT -> "原生 API"
        }
        Toast.show("已切换通信协议: $name", ToastType.SUCCESS)
    }

    fun saveProjectAgentConfig(config: ProjectAgentConfig, notifyUser: Boolean = true) {
        state = state.copy(projectAgentConfig = config)
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/agent_config.json"
        scope.launch(Dispatchers.Default) {
            try {
                val jsonFormat = kotlinx.serialization.json.Json {
                    encodeDefaults = true
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
                val text = jsonFormat.encodeToString(ProjectAgentConfig.serializer(), config)
                TemplateFile(path).writeBytes(text.encodeToByteArray())
                if (notifyUser) {
                    Toast.show("项目 Agent 配置已更新", ToastType.SUCCESS)
                }
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存项目 Agent 配置失败", e)
            }
        }
    }

    // ====== 项目专属会话存储与持久化 (templates/{proj}/sessions/{sessionId}.json) ======

    fun loadChatSessions() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val sessionsDirRel = "templates/$sanitized/sessions"
        scope.launch(Dispatchers.Default) {
            try {
                val root = org.gemini.ui.forge.utils.GlobalAppEnv.currentRootPath.trimEnd('/', '\\')
                val fullDirPath = "$root/$sessionsDirRel"
                val files = org.gemini.ui.forge.utils.listFilesInLocalDirectory(fullDirPath)
                val loadedSessions = files.filter { it.endsWith(".json", ignoreCase = true) }.mapNotNull { absPath ->
                    try {
                        val fileName = absPath.substringAfterLast("/").substringAfterLast("\\")
                        val sessionId = fileName.removeSuffix(".json")
                        val sessionFile = TemplateFile("$sessionsDirRel/$sessionId.json")
                        val text = sessionFile.readBytes()?.decodeToString()
                        if (!text.isNullOrBlank()) {
                            jsonHelper.decodeFromString(VisualChatSession.serializer(), text)
                        } else null
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "加载单个会话文件失败: $absPath", e)
                        null
                    }
                }.sortedByDescending { it.updatedAt }

                if (loadedSessions.isNotEmpty()) {
                    val activeId = state.currentChatSessionId ?: loadedSessions.first().id
                    state = state.copy(
                        chatSessions = loadedSessions,
                        currentChatSessionId = activeId
                    )
                    return@launch
                }

                // 首次进入无会话，生成默认初始会话并持久化
                val now = getCurrentTimeMillis()
                val welcomeMsg = VisualChatMessage(
                    id = "msg_${now}_welcome",
                    role = "model",
                    prompt = "🤖 你好！我是当前工程的专属「UI Studio 智能设计助理」。当前视图:「${state.currentView?.nameStr ?: "未选择"}」(${state.currentView?.width?.toInt() ?: 0} × ${state.currentView?.height?.toInt() ?: 0})，素材库已就绪 ${state.assetLibrary.items.size} 个切片资产。你可以向我描述界面排版构想、上传原型草图或让我在当前视图规划组件布局！",
                    timestamp = now
                )
                val initialSession = VisualChatSession(
                    id = "session_${now}",
                    scopeId = sanitized,
                    title = "主界面架构与排版规划",
                    messages = listOf(welcomeMsg),
                    createdAt = now,
                    updatedAt = now
                )
                val newSessions = listOf(initialSession)
                state = state.copy(
                    chatSessions = newSessions,
                    currentChatSessionId = initialSession.id
                )
                saveSession(initialSession)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载项目 Chat 会话失败", e)
            }
        }
    }

    fun saveSession(session: VisualChatSession) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/${session.id}.json"
        scope.launch(Dispatchers.Default) {
            try {
                val text = jsonHelper.encodeToString(VisualChatSession.serializer(), session)
                TemplateFile(path).writeBytes(text.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存会话文件失败: ${session.id}", e)
            }
        }
    }

    fun createNewChatSession(title: String = "新布局讨论") {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val now = getCurrentTimeMillis()
        val welcomeMsg = VisualChatMessage(
            id = "msg_${now}_welcome",
            role = "model",
            prompt = "✨ 已开启新的排版讨论会话。请告诉我你想在视图「${state.currentView?.nameStr ?: "当前视图"}」中实现什么样的 UI 布局或业务功能？",
            timestamp = now
        )
        val newSession = VisualChatSession(
            id = "session_${now}",
            scopeId = sanitized,
            title = title,
            messages = listOf(welcomeMsg),
            createdAt = now,
            updatedAt = now
        )
        val updatedSessions = listOf(newSession) + state.chatSessions
        state = state.copy(
            chatSessions = updatedSessions,
            currentChatSessionId = newSession.id
        )
        saveSession(newSession)
        Toast.show("已创建新会话: $title", ToastType.SUCCESS)
    }

    fun selectChatSession(sessionId: String) {
        state = state.copy(currentChatSessionId = sessionId)
    }

    fun deleteChatSession(sessionId: String) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/$sessionId.json"
        scope.launch(Dispatchers.Default) {
            try {
                TemplateFile(path).delete()
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "删除会话文件失败: $sessionId", e)
            }
        }

        val remaining = state.chatSessions.filter { it.id != sessionId }
        val newSessions = if (remaining.isEmpty()) {
            val now = getCurrentTimeMillis()
            val initial = VisualChatSession(
                id = "session_${now}",
                scopeId = sanitized,
                title = "主界面排版讨论",
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
            saveSession(initial)
            listOf(initial)
        } else remaining
        val nextActiveId = if (state.currentChatSessionId == sessionId) newSessions.first().id else state.currentChatSessionId
        state = state.copy(
            chatSessions = newSessions,
            currentChatSessionId = nextActiveId
        )
        Toast.show("会话已删除", ToastType.INFO)
    }

    fun clearCurrentChatSessionMessages() {
        val activeSession = state.currentChatSession ?: return
        val updated = activeSession.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        val updatedSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        state = state.copy(chatSessions = updatedSessions)
        saveSession(updated)
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }

    fun updateChatSearchQuery(query: String) {
        state = state.copy(chatSearchQuery = query)
    }

    fun sendChatMessage(
        userText: String,
        attachmentsList: List<org.gemini.ui.forge.ui.component.chat.CommonChatAttachment> = emptyList(),
        apiKey: String = ""
    ) {
        if (userText.isBlank() && attachmentsList.isEmpty()) return
        val activeSession = state.currentChatSession ?: run {
            createNewChatSession()
            state.currentChatSession!!
        }

        val rawAttachments = if (attachmentsList.isNotEmpty()) {
            attachmentsList.mapNotNull { it.localUri }
        } else {
            state.aiChatAttachments
        }
        val now = getCurrentTimeMillis()
        val userMsg = VisualChatMessage(
            id = "msg_${now}_user",
            role = "user",
            prompt = userText,
            inputImageUris = rawAttachments,
            timestamp = now
        )

        val updatedMessages = activeSession.messages + userMsg
        val updatedSession = activeSession.copy(
            messages = updatedMessages,
            title = if (activeSession.messages.isEmpty() || activeSession.title == "新布局讨论") userText.take(16) else activeSession.title,
            updatedAt = now
        )
        val updatedSessions = state.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }

        state = state.copy(
            chatSessions = updatedSessions,
            aiChatAttachments = emptyList(),
            isAiChatSending = true
        )
        saveSession(updatedSession)

        scope.launch(Dispatchers.Default) {
            try {
                // 若存在无 localUri 仅有 bytes 的内存附件，协程内异步落盘并补齐持久化 URI
                if (attachmentsList.any { it.localUri.isNullOrBlank() && it.bytes != null && it.bytes.isNotEmpty() }) {
                    val proj = state.project
                    val sanitized = proj?.projectId?.trim()?.replace(" ", "_") ?: "default"
                    val tempDir = "templates/$sanitized/chat_attachments"
                    val persistedUris = attachmentsList.mapNotNull { attach ->
                        if (!attach.localUri.isNullOrBlank()) {
                            attach.localUri
                        } else if (attach.bytes != null && attach.bytes.isNotEmpty()) {
                            try {
                                val fileName = attach.name.ifBlank { "paste_${getCurrentTimeMillis()}.png" }
                                val fileRelPath = "$tempDir/$fileName"
                                val file = TemplateFile(fileRelPath)
                                file.writeBytes(attach.bytes)
                                file.getAbsolutePath()
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }
                    if (persistedUris.isNotEmpty()) {
                        val correctedUserMsg = userMsg.copy(inputImageUris = persistedUris)
                        val correctedMessages = updatedSession.messages.map { if (it.id == userMsg.id) correctedUserMsg else it }
                        val correctedSession = updatedSession.copy(messages = correctedMessages)
                        state = state.copy(chatSessions = state.chatSessions.map { if (it.id == correctedSession.id) correctedSession else it })
                        saveSession(correctedSession)
                    }
                }
                val effectiveApiKey = apiKey.ifBlank { configManager.loadKey("GEMINI_API_KEY") ?: "" }
                val apiFlavor = state.projectAgentConfig.apiFlavor
                val systemPrompt = state.projectAgentConfig.systemPrompt
                val selectedModel = state.projectAgentConfig.modelName.ifBlank { GeminiModel.GEMINI_3_8_FLASH.modelName }
                val view = state.currentView
                val contextInfo = "当前视图: ${view?.nameStr ?: "无"} (${view?.width ?: 0f} x ${view?.height ?: 0f})，素材库共有 ${state.assetLibrary.items.size} 个资产。"

                var replyContent = ""
                if (effectiveApiKey.isNotBlank()) {
                    try {
                        val client = GeminiClient()
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:streamGenerateContent?key=$effectiveApiKey&alt=sse"
                        val fullPrompt = "$systemPrompt\n\n[上下文信息] $contextInfo\n\n[用户指令] $userText"
                        val requestBody = kotlinx.serialization.json.buildJsonObject {
                            put("contents", kotlinx.serialization.json.buildJsonArray {
                                add(kotlinx.serialization.json.buildJsonObject {
                                    put("parts", kotlinx.serialization.json.buildJsonArray {
                                        add(kotlinx.serialization.json.buildJsonObject { put("text", fullPrompt) })
                                    })
                                })
                            })
                            put("generationConfig", kotlinx.serialization.json.buildJsonObject {
                                put("temperature", state.projectAgentConfig.temperature)
                            })
                        }.toString()
                        val sb = StringBuilder()
                        client.streamGenerateContent(
                            url = url,
                            requestBody = requestBody,
                            onChunk = { chunk: String ->
                                sb.append(chunk)
                            }
                        )
                        replyContent = sb.toString()
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "Gemini API 调用异常，生成备选建议", e)
                        replyContent = "💡 基于驱动「${apiFlavor.displayNameZh}」与模型「$selectedModel」建议：\n1. 建议将核心交互组件置于视图中下方，预留 32dp 安全边距；\n2. 严格按 8pt 空间网格对齐；\n3. 推荐使用素材库已有素材保证风格统一。\n(通信说明: ${e.message ?: "未配置外部通道"})"
                    }
                } else {
                    replyContent = "🤖 [驱动: ${apiFlavor.displayNameZh} · 模型: $selectedModel]\n已接收排版意图：\"$userText\"\n\n【排版规划方案】\n1. 目标视图：${view?.nameStr ?: "MainPage"} (${view?.width?.toInt() ?: 0} × ${view?.height?.toInt() ?: 0})\n2. 推荐布局结构：\n   - 顶部导航栏（高度 64dp，贴合顶部，放置返回与标题）\n   - 中部主内容展示区（自适应居中，8pt 边距）\n   - 底部浮动控制栏（居中水平分布）\n3. 素材应用：素材库已有 ${state.assetLibrary.items.size} 个切片资产，可直接拖入画布对应区域完成装配。"
                }

                val assistantMsg = VisualChatMessage(
                    id = "msg_${getCurrentTimeMillis()}_assistant",
                    role = "model",
                    prompt = replyContent,
                    timestamp = getCurrentTimeMillis()
                )

                val sessionAfterReply = (state.chatSessions.find { it.id == activeSession.id } ?: activeSession).let { cur ->
                    cur.copy(
                        messages = cur.messages + assistantMsg,
                        updatedAt = getCurrentTimeMillis()
                    )
                }
                val finalSessions = state.chatSessions.map { if (it.id == sessionAfterReply.id) sessionAfterReply else it }
                state = state.copy(chatSessions = finalSessions, isAiChatSending = false)
                saveSession(sessionAfterReply)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI Chat 处理异常", e)
                state = state.copy(isAiChatSending = false)
            }
        }
    }

    // ====== 边栏尺寸与自适应居中 ======

    fun updateLeftPanelWidth(width: Dp) {
        state = state.copy(leftPanelWidth = width.coerceIn(200.dp, 500.dp))
    }

    fun updateRightPanelWidth(width: Dp) {
        state = state.copy(rightPanelWidth = width.coerceIn(200.dp, 520.dp))
    }

    fun updateViewsPanelHeightRatio(ratio: Float) {
        state = state.copy(viewsPanelHeightRatio = ratio.coerceIn(0.2f, 0.8f))
    }

    /**
     * 自动将当前视图的画布居中并自适应填充满工作区视口 (Fit to Viewport)。
     */
    fun fitToViewport(viewportWidth: Float, viewportHeight: Float) {
        val canvasW = state.canvasWidth
        val canvasH = state.canvasHeight
        if (viewportWidth <= 0f || viewportHeight <= 0f || canvasW <= 0f || canvasH <= 0f) return

        val padding = 64f
        val availW = (viewportWidth - padding).coerceAtLeast(100f)
        val availH = (viewportHeight - padding).coerceAtLeast(100f)

        val scaleX = availW / canvasW
        val scaleY = availH / canvasH
        val targetScale = minOf(scaleX, scaleY).coerceIn(0.1f, 3.0f)

        val panX = (viewportWidth - canvasW * targetScale) / 2f
        val panY = (viewportHeight - canvasH * targetScale) / 2f

        state = state.copy(
            scale = targetScale,
            panOffset = Offset(panX, panY),
            isFitToViewport = true
        )
    }

    /**
     * 检查当前项目目录下是否存在暂存的图元切片校对草稿 (import_draft.json)
     */
    fun checkDraft(projectId: String? = null) {
        val effectiveId = (projectId ?: state.project?.projectId ?: "").trim()
        if (effectiveId.isBlank()) return

        scope.launch {
            try {
                val sanitizedName = effectiveId.replace(" ", "_")
                val draftPath = "templates/$sanitizedName/import_draft.json"
                val file = TemplateFile(draftPath)
                val bytes = file.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val draft = jsonHelper.decodeFromString<AssetImportDraft>(bytes.decodeToString())
                    if (draft.items.isNotEmpty()) {
                        state = state.copy(
                            importDraft = draft,
                            isDraftPromptVisible = true
                        )
                        AppLogger.i("UIStudioViewModel", "📋 发现未完成的切片草稿: ${draft.items.size} 个图元, 图片=${draft.sourceImagePath}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "解析草稿文件失败: ${e.message}")
            }
        }
    }

    /**
     * 恢复并继续未完成的切片草稿。
     */
    fun resumeDraft() {
        val draft = state.importDraft ?: return
        state = state.copy(
            sourceImagePath = draft.sourceImagePath,
            sourceJsonPath = draft.sourceJsonPath,
            verifySliceItems = draft.items,
            isVerifySliceDialogVisible = true,
            isDraftPromptVisible = false
        )
        Toast.show("已成功恢复上次的校对草稿（共 ${draft.items.size} 个图元）", ToastType.SUCCESS)
    }

    /**
     * 丢弃并物理清除草稿文件。
     */
    fun discardDraft() {
        val proj = state.project ?: return
        scope.launch {
            try {
                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val draftPath = "templates/$sanitizedName/import_draft.json"
                TemplateFile(draftPath).writeBytes(ByteArray(0)) // 清空草稿
            } catch (_: Exception) {}
            state = state.copy(
                importDraft = null,
                isDraftPromptVisible = false
            )
            Toast.show("已清除校对草稿", ToastType.INFO)
        }
    }

    /**
     * 将当前校对图元状态持久化落盘至草稿文件 (import_draft.json)，确保中途退出心血不丢。
     */
    fun saveDraft(items: List<NormalizedAssetItem>) {
        val proj = state.project ?: return
        val currentImage = state.sourceImagePath
        if (currentImage.isBlank() || items.isEmpty()) return

        scope.launch {
            try {
                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val draft = AssetImportDraft(
                    sourceImagePath = currentImage,
                    sourceJsonPath = state.sourceJsonPath,
                    canvasWidth = state.canvasWidth,
                    canvasHeight = state.canvasHeight,
                    items = items,
                    savedAtMillis = org.gemini.ui.forge.getCurrentTimeMillis()
                )
                val jsonStr = jsonHelper.encodeToString(AssetImportDraft.serializer(), draft)
                val draftPath = "templates/$sanitizedName/import_draft.json"
                TemplateFile(draftPath).writeBytes(jsonStr.encodeToByteArray())
                AppLogger.i("UIStudioViewModel", "💾 校对草稿已自动落盘暂存 (${items.size} 个图元)")
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存草稿失败: ${e.message}")
            }
        }
    }

    /**
     * 处理画布视口缩放手势。
     *
     * @param factor 缩放变化比率（大于 1 放大，小于 1 缩小）
     * @param pivot 缩放聚焦中心点屏幕绝对坐标
     */
    fun onZoom(factor: Float, pivot: Offset) {
        val oldScale = state.scale
        val newScale = (oldScale * factor).coerceIn(0.1f, 5.0f)
        if (oldScale == newScale) return

        // 以光标为锚点的无损缩放偏移补偿计算
        val scaleRatio = newScale / oldScale
        val newPanX = pivot.x - (pivot.x - state.panOffset.x) * scaleRatio
        val newPanY = pivot.y - (pivot.y - state.panOffset.y) * scaleRatio

        state = state.copy(
            scale = newScale,
            panOffset = Offset(newPanX, newPanY)
        )
    }

    /**
     * 处理画布视口平移拖拽手势。
     *
     * @param delta 平移位移增量向量
     */
    fun onPan(delta: Offset) {
        state = state.copy(
            panOffset = state.panOffset + delta
        )
    }

    /**
     * 重置画布视口至居中默认视野。
     */
    fun resetViewport() {
        state = state.copy(
            scale = 0.6f,
            panOffset = Offset(100f, 60f)
        )
    }

    /**
     * 选中或取消选中指定图元。
     *
     * @param blockId 目标图元 ID，传入 null 表示点击空白处取消选中
     */
    fun selectBlock(blockId: String?) {
        state = state.copy(selectedBlockId = blockId)
    }

    /**
     * 切换左侧素材库面板的展开/折叠显隐状态。
     */
    fun toggleAssetPanel() {
        state = state.copy(isAssetPanelVisible = !state.isAssetPanelVisible)
    }

    /**
     * 异步保存当前 UI Studio 工程至本地磁盘。
     *
     * @param repo 模板仓储服务 [TemplateRepository]
     */
    fun saveProject(repo: TemplateRepository) {
        val proj = state.project ?: return
        if (state.isSaving) return

        state = state.copy(isSaving = true)
        scope.launch {
            try {
                repo.saveTemplate(proj.projectId, proj)
                Toast.show("项目保存成功", ToastType.SUCCESS)
            } catch (e: Exception) {
                Toast.show("保存失败: ${e.message}", ToastType.ERROR)
            } finally {
                state = state.copy(isSaving = false)
            }
        }
    }

    /**
     * 打开导入图集配置弹窗。
     */
    fun openImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = true)
    }

    /**
     * 关闭导入图集配置弹窗。
     */
    fun closeImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = false)
    }

    /**
     * 更新选中的源图片文件路径。
     */
    fun updateSourceImagePath(path: String) {
        state = state.copy(sourceImagePath = path)
    }

    /**
     * 更新选中的源 JSON 坐标文件路径并触发自适应解析。
     */
    fun updateSourceJsonPath(path: String) {
        state = state.copy(sourceJsonPath = path)
        if (path.isNotBlank()) {
            parseJsonFile(path)
        }
    }

    /**
     * 读取指定本地路径的 JSON 文件并进行零预设自适应结构解析。
     *
     * 支持绝对物理路径（如 E:\...）与相对工作区路径，调用跨平台安全读取器 [readLocalFileBytes]。
     */
    fun parseJsonFile(path: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val content = readLocalFileBytes(path)?.decodeToString()
                if (content.isNullOrBlank()) {
                    Toast.show("JSON 文件为空或无法读取", ToastType.ERROR)
                    state = state.copy(isParsingJson = false)
                    return@launch
                }
                parseJsonContent(content)
            } catch (e: Exception) {
                Toast.show("读取文件失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isParsingJson = false)
            }
        }
    }

    /**
     * 直接对文本内容执行零预设自适应解析。
     */
    fun parseJsonContent(content: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val result = AssetSheetNormalizer.normalizeJsonOffline(content)
                state = state.copy(
                    parseResult = result,
                    isParsingJson = false
                )
                if (result.items.isNotEmpty()) {
                    Toast.show("成功识别 ${result.items.size} 个图元组件", ToastType.SUCCESS)
                } else {
                    Toast.show("未能从内容中识别出有效的图元坐标", ToastType.INFO)
                }
            } catch (e: Exception) {
                state = state.copy(isParsingJson = false)
                Toast.show("解析失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 从导入配置弹窗流转进入切片可视化预审校对弹窗（阶段三）。
     */
    fun proceedToVerifyDialog() {
        val result = state.parseResult
        val items = result?.items ?: emptyList()
        state = state.copy(
            isImportSheetDialogVisible = false,
            isVerifySliceDialogVisible = true,
            verifySliceItems = items
        )
        // 自动落盘初始草稿
        saveDraft(items)
    }

    /**
     * 针对纯图场景调用 Gemini 视觉多模态大模型逆向检测图元坐标，并流转进入阶段三切片校对弹窗。
     *
     * @param apiKey 可选自定义 API Key，缺省时自动从本地配置中心读取
     */
    fun startAiDetectAndProceed(apiKey: String? = null) {
        val imagePath = state.sourceImagePath
        if (imagePath.isBlank()) {
            Toast.show("请先选择或输入待识别的图集大图路径", ToastType.INFO)
            return
        }

        state = state.copy(
            isAiDetectingCoords = true,
            aiDetectStatusText = "正在准备上传图集图片并请求 Gemini 视觉大模型..."
        )

        scope.launch {
            try {
                val (result, _) = org.gemini.ui.forge.service.assetsheet.AssetSheetAiDetector.detectAssetSheet(
                    imagePath = imagePath,
                    apiKey = apiKey,
                    projectName = state.project?.projectId,
                    onProgress = { _, message ->
                        state = state.copy(aiDetectStatusText = message)
                    }
                )

                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = "",
                    parseResult = result,
                    isImportSheetDialogVisible = false,
                    isVerifySliceDialogVisible = true,
                    verifySliceItems = result.items
                )

                // 自动保存初始草稿
                saveDraft(result.items)
                Toast.show("AI 成功识别出 ${result.items.size} 个图元组件！", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI 视觉图集逆向识别失败: ${e.message}", e)
                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = ""
                )
                Toast.show("AI 识别失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 关闭可视化预审校对弹窗。
     */
    fun closeVerifySliceDialog() {
        state = state.copy(isVerifySliceDialogVisible = false)
    }

    /**
     * 确认校对切片并执行真正的物理透明切片与入库落盘（阶段四核心实现）。
     */
    fun onConfirmSlice(
        items: List<NormalizedAssetItem>,
        repo: TemplateRepository,
        onProjectUpdated: ((ProjectState) -> Unit)? = null
    ) {
        val proj = state.project ?: return
        val imagePath = state.sourceImagePath
        if (imagePath.isBlank() || items.isEmpty()) {
            Toast.show("未指定原图或图元列表为空", ToastType.INFO)
            return
        }

        state = state.copy(
            isVerifySliceDialogVisible = false,
            verifySliceItems = items,
            isSlicingAssets = true,
            sliceProgress = 0f,
            sliceStatusText = "正在读取图集大图..."
        )

        scope.launch(Dispatchers.Default) {
            try {
                val imageBytes = readLocalFileBytes(imagePath)
                if (imageBytes == null || imageBytes.isEmpty()) {
                    Toast.show("无法读取图集文件: $imagePath", ToastType.ERROR)
                    state = state.copy(isSlicingAssets = false)
                    return@launch
                }

                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val canvasW = state.canvasWidth
                val canvasH = state.canvasHeight

                val targetFolderId = state.selectedFolderId
                val targetFolder = state.assetLibrary.folders.find { it.id == targetFolderId }
                val folderSubDir = if (targetFolder != null) "${targetFolder.name.trim().replace(" ", "_")}/" else ""

                val newAssetItems = mutableListOf<UIAssetItem>()
                val total = items.size

                val shouldRemoveBg = state.autoRemoveBgOnSlice
                for ((idx, item) in items.withIndex()) {
                    state = state.copy(
                        sliceProgress = (idx + 1).toFloat() / total,
                        sliceStatusText = if (shouldRemoveBg) {
                            "正在切片并去背 (${idx + 1}/$total): ${item.name}"
                        } else {
                            "正在切片 (${idx + 1}/$total): ${item.name}"
                        }
                    )

                    // 1. 调用 Skia 执行高质量透明 Alpha 通道图像物理切片
                    val sliceBytes = cropImage(
                        imageBytes = imageBytes,
                        bounds = item.bounds,
                        originalWidth = canvasW,
                        originalHeight = canvasH,
                        isPng = true
                    )

                    val assetId = item.id.ifBlank { "asset_${getCurrentTimeMillis()}_$idx" }
                    val relPath = "templates/$sanitizedName/assets/image/${folderSubDir}${assetId}.png"

                    // 2. 将切片物理写入磁盘素材库目录 (支持自动透明去背)
                    if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                        val finalBytes = if (shouldRemoveBg) {
                            val matted = mattingService.removeBackground(sliceBytes)
                            if (matted != null && matted.isNotEmpty()) matted else sliceBytes
                        } else {
                            sliceBytes
                        }
                        val tFile = TemplateFile(relPath)
                        tFile.writeBytes(finalBytes)
                    }

                    // 3. 构建对应的 UIAssetItem 资产实体并归入素材库
                    val assetItem = UIAssetItem(
                        id = assetId,
                        name = item.name,
                        relativePath = relPath,
                        width = item.bounds.width.toInt().coerceAtLeast(1),
                        height = item.bounds.height.toInt().coerceAtLeast(1),
                        category = item.type.name.lowercase(),
                        folderId = targetFolderId,
                        createdAt = getCurrentTimeMillis()
                    )
                    newAssetItems.add(assetItem)
                }

                // 4. 将切片生成的素材归入素材库 (Asset Library)，同步更新状态并持久化落盘
                val updatedLibrary = state.assetLibrary.copy(
                    items = state.assetLibrary.items.filterNot { old -> newAssetItems.any { it.id == old.id } } + newAssetItems
                )

                // 5. 清理切片校对草稿文件
                try {
                    val draftPath = "templates/$sanitizedName/import_draft.json"
                    TemplateFile(draftPath).writeBytes(ByteArray(0))
                } catch (_: Exception) {}

                state = state.copy(
                    assetLibrary = updatedLibrary,
                    isSlicingAssets = false,
                    importDraft = null,
                    isDraftPromptVisible = false,
                    sliceStatusText = ""
                )

                // 6. 持久化素材库到 asset_library.json
                saveAssetLibrary()

                withContext(Dispatchers.Main) {
                    onProjectUpdated?.invoke(proj)
                }

                Toast.show("🎉 成功切片并入库 ${newAssetItems.size} 个素材到素材库中！", ToastType.SUCCESS)
                AppLogger.i("UIStudioViewModel", "✅ 物理切片与素材库持久化完成: 共 ${newAssetItems.size} 个素材入库")

            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "❌ 切图入库失败", e)
                Toast.show("切片入库失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isSlicingAssets = false)
            }
        }
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
    fun commitRenameAsset(assetId: String, newName: String) {
        if (newName.isBlank()) return
        val updatedItems = state.assetLibrary.items.map {
            if (it.id == assetId) it.copy(name = newName.trim()) else it
        }
        state = state.copy(
            assetLibrary = state.assetLibrary.copy(items = updatedItems),
            renamingAsset = null
        )
        saveAssetLibrary()
        Toast.show("素材已重命名为: $newName", ToastType.SUCCESS)
    }

    fun revealAssetInExplorer(asset: UIAssetItem) {
        val path = TemplateFile(asset.relativePath).getAbsolutePath()
        org.gemini.ui.forge.getPlatform().openInFileExplorer(path)
    }

    fun revealFolderInExplorer(folderId: String? = state.selectedFolderId) {
        val proj = state.project?.projectId ?: "default"
        val folder = state.assetLibrary.folders.find { it.id == folderId }
        val subPath = if (folder != null) "templates/$proj/assets/${folder.name}" else "templates/$proj/assets"
        val path = TemplateFile(subPath).getAbsolutePath()
        org.gemini.ui.forge.getPlatform().openInFileExplorer(path)
    }

    fun addAssetToCanvas(asset: UIAssetItem, repo: TemplateRepository) {
        val view = state.currentView ?: run {
            Toast.show("当前没有处于活跃的视图界面，请先选择或新建视图", ToastType.INFO)
            return
        }
        val centerW = (view.width - asset.width.coerceAtLeast(32)).coerceAtLeast(0f) / 2f
        val centerH = (view.height - asset.height.coerceAtLeast(32)).coerceAtLeast(0f) / 2f
        val newBlock = UIBlock(
            id = "block_${asset.id}_${Random.nextInt(1000, 9999)}",
            type = UIBlockType.IMAGE,
            bounds = SerialRect(centerW, centerH, centerW + asset.width, centerH + asset.height),
            currentImageUri = TemplateFile(asset.relativePath),
            userPromptZh = asset.name,
            userPromptEn = asset.id
        )
        val updatedBlocks = view.blocks + newBlock
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: listOf(view.copy(blocks = updatedBlocks))
        val updatedProject = state.project?.copy(pages = updatedPages) ?: return
        state = state.copy(project = updatedProject, selectedBlockId = newBlock.id)
        saveProject(repo)
        Toast.show("已将素材添加到视图: ${asset.name}", ToastType.SUCCESS)
    }

    fun startRenameView(view: UIPage) { state = state.copy(renamingView = view) }
    fun cancelRenameView() { state = state.copy(renamingView = null) }
    fun commitRenameView(viewId: String, newName: String, repo: TemplateRepository) {
        if (newName.isBlank()) return
        val updatedPages = state.project?.pages?.map {
            if (it.id == viewId) it.copy(nameStr = newName.trim()) else it
        } ?: return
        val updatedProject = state.project?.copy(pages = updatedPages) ?: return
        state = state.copy(project = updatedProject, renamingView = null)
        saveProject(repo)
        Toast.show("视图已重命名为: $newName", ToastType.SUCCESS)
    }

    fun updateBlockBounds(blockId: String, left: Float, top: Float, width: Float, height: Float, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(bounds = SerialRect(left, top, left + width, top + height)) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    /**
     * 实时平移单个图元（高频拖拽中毫秒级更新内存坐标，递归支持子图元）。
     */
    fun moveBlockDelta(blockId: String, dx: Float, dy: Float) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) { block ->
            val b = block.bounds
            val w = b.right - b.left
            val h = b.bottom - b.top
            val newLeft = b.left + dx
            val newTop = b.top + dy
            block.copy(bounds = SerialRect(newLeft, newTop, newLeft + w, newTop + h))
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
    }

    /**
     * 图元拖拽释放后执行持久化刷盘。
     */
    fun commitBlockMove(repo: TemplateRepository = TemplateRepository()) {
        saveProject(repo)
    }

    fun updateBlockType(blockId: String, newType: UIBlockType, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(type = newType)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun updateBlockPrompts(blockId: String, userPromptZh: String, userPromptEn: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(userPromptZh = userPromptZh, userPromptEn = userPromptEn) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun updateBlockPureContainer(blockId: String, isPure: Boolean, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = view.blocks.map {
            if (it.id == blockId) it.copy(isPureContainer = isPure) else it
        }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    fun deleteSelectedBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = removeBlockFromTree(view.blocks, blockId).bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
        state = state.copy(selectedBlockId = null)
        Toast.show("已删除组件", ToastType.INFO)
    }

    /**
     * 递归从模块树中安全移除指定 ID 的图元对象。
     */
    private fun removeBlockFromTree(list: List<UIBlock>, id: String): List<UIBlock> {
        return list.filterNot { it.id == id }.map { block ->
            if (block.children.any { it.id == id }) {
                block.copy(children = block.children.filterNot { it.id == id })
            } else {
                block.copy(children = removeBlockFromTree(block.children, id))
            }
        }
    }

    /**
     * 移动图层层级（支持 BEFORE / AFTER 同级排序及 INSIDE 父子容器嵌套约束）。
     */
    fun moveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        if (draggedId == targetId) return
        val draggedBlock = view.blocks.findBlockById(draggedId) ?: return

        val oldParentId = view.blocks.findParentBlockId(draggedId)
        val oldParent = oldParentId?.let { view.blocks.findBlockById(it) }
        val targetParentId = if (targetId != null) view.blocks.findParentBlockId(targetId) else null
        val isMovingOutOfParent = oldParentId != null && (
            (position == DropPosition.INSIDE && targetId != oldParentId) ||
            (position != DropPosition.INSIDE && targetParentId != oldParentId) ||
            targetId == null
        )

        // 移出最后一个子组件时强校验与弹窗拦截
        if (isMovingOutOfParent && oldParent != null && oldParent.children.size <= 1) {
            state = state.copy(
                pendingDragRemoveLastChild = DragRemoveLastChildContext(
                    draggedId = draggedId,
                    oldParentId = oldParent.id,
                    targetId = targetId,
                    position = position
                )
            )
            return
        }

        executeMoveHierarchyBlock(
            draggedId = draggedId,
            targetId = targetId,
            position = position,
            repo = repo,
            dissolveContainerId = null
        )
    }

    /**
     * 用户确认移除最后一个子组件操作：可选择保留或解散父容器。
     */
    fun confirmDragRemoveLastChild(keepContainer: Boolean, repo: TemplateRepository) {
        val ctx = state.pendingDragRemoveLastChild ?: return
        state = state.copy(pendingDragRemoveLastChild = null)
        executeMoveHierarchyBlock(
            draggedId = ctx.draggedId,
            targetId = ctx.targetId,
            position = ctx.position,
            repo = repo,
            dissolveContainerId = if (keepContainer) null else ctx.oldParentId
        )
    }

    /**
     * 取消最后一个子组件的移出操作，恢复原始层级状态。
     */
    fun cancelDragRemoveLastChild() {
        state = state.copy(pendingDragRemoveLastChild = null)
    }

    private fun executeMoveHierarchyBlock(
        draggedId: String,
        targetId: String?,
        position: DropPosition,
        repo: TemplateRepository,
        dissolveContainerId: String? = null
    ) {
        val view = state.currentView ?: return
        val draggedBlock = view.blocks.findBlockById(draggedId) ?: return

        // 目标为 null：移至根列表末尾
        if (targetId == null) {
            val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
            val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            var newBlocks = (cleanedBlocks + updatedDragged).bindParents()
            if (dissolveContainerId != null) {
                newBlocks = removeBlockFromTree(newBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, newBlocks, repo)
            return
        }

        val targetBlock = view.blocks.findBlockById(targetId) ?: return
        val targetParentId = view.blocks.findParentBlockId(targetId)

        if (position == DropPosition.INSIDE) {
            // 移入目标成组逻辑判定：
            // 1. 若目标本身就是纯容器
            if (targetBlock.isPureContainer || targetBlock.type == UIBlockType.CONTAINER) {
                val targetAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: targetBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - targetAbsBounds.left,
                    top = draggedAbsBounds.top - targetAbsBounds.top,
                    right = draggedAbsBounds.right - targetAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - targetAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                val updatedTarget = targetBlock.copy(children = targetBlock.children + draggedBlock.copy(bounds = localBounds))
                var resultBlocks = cleanedBlocks.updateBlockInList(targetId) { updatedTarget }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入容器: ${targetBlock.id}", ToastType.SUCCESS)
                return
            }

            // 2. 若目标所处已有父容器
            if (targetParentId != null) {
                val parentBlock = view.blocks.findBlockById(targetParentId) ?: return
                val parentAbsBounds = view.blocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
                val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
                val localBounds = SerialRect(
                    left = draggedAbsBounds.left - parentAbsBounds.left,
                    top = draggedAbsBounds.top - parentAbsBounds.top,
                    right = draggedAbsBounds.right - parentAbsBounds.left,
                    bottom = draggedAbsBounds.bottom - parentAbsBounds.top
                )
                val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)
                var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                    p.copy(children = p.children + draggedBlock.copy(bounds = localBounds))
                }.bindParents()
                if (dissolveContainerId != null) {
                    resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
                }
                updateViewBlocksAndSave(view.id, resultBlocks, repo)
                Toast.show("已将组件移入同级容器", ToastType.SUCCESS)
                return
            }

            // 3. 目标为独立根图层且无父容器：触发二次确认弹窗
            state = state.copy(pendingCreateContainer = draggedId to targetId)
            return
        }

        // BEFORE 或 AFTER：同级相邻排序
        val draggedAbsBounds = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: draggedBlock.bounds
        val cleanedBlocks = removeBlockFromTree(view.blocks, draggedId)

        if (targetParentId != null) {
            val parentBlock = cleanedBlocks.findBlockById(targetParentId) ?: return
            val parentAbsBounds = cleanedBlocks.calculateBlockAbsoluteBounds(targetParentId) ?: parentBlock.bounds
            val localBounds = SerialRect(
                left = draggedAbsBounds.left - parentAbsBounds.left,
                top = draggedAbsBounds.top - parentAbsBounds.top,
                right = draggedAbsBounds.right - parentAbsBounds.left,
                bottom = draggedAbsBounds.bottom - parentAbsBounds.top
            )
            val updatedDragged = draggedBlock.copy(bounds = localBounds)
            var resultBlocks = cleanedBlocks.updateBlockInList(targetParentId) { p ->
                val list = p.children.toMutableList()
                val targetIndex = list.indexOfFirst { it.id == targetId }
                val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
                list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
                p.copy(children = list)
            }.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        } else {
            val updatedDragged = draggedBlock.copy(bounds = draggedAbsBounds)
            val list = cleanedBlocks.toMutableList()
            val targetIndex = list.indexOfFirst { it.id == targetId }
            val insertIndex = if (position == DropPosition.BEFORE) targetIndex else targetIndex + 1
            list.add(insertIndex.coerceIn(0, list.size), updatedDragged)
            var resultBlocks = list.bindParents()
            if (dissolveContainerId != null) {
                resultBlocks = removeBlockFromTree(resultBlocks, dissolveContainerId).bindParents()
            }
            updateViewBlocksAndSave(view.id, resultBlocks, repo)
        }
    }

    /**
     * 用户确认创建父容器约束。
     */
    fun confirmCreateContainer(draggedId: String, targetId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val blockA = view.blocks.findBlockById(draggedId) ?: return
        val blockB = view.blocks.findBlockById(targetId) ?: return

        val boundsA = view.blocks.calculateBlockAbsoluteBounds(draggedId) ?: blockA.bounds
        val boundsB = view.blocks.calculateBlockAbsoluteBounds(targetId) ?: blockB.bounds

        val unionLeft = kotlin.math.min(boundsA.left, boundsB.left)
        val unionTop = kotlin.math.min(boundsA.top, boundsB.top)
        val unionRight = kotlin.math.max(boundsA.right, boundsB.right)
        val unionBottom = kotlin.math.max(boundsA.bottom, boundsB.bottom)
        val unionRect = SerialRect(unionLeft, unionTop, unionRight, unionBottom)

        val localA = blockA.copy(bounds = SerialRect(boundsA.left - unionLeft, boundsA.top - unionTop, boundsA.right - unionLeft, boundsA.bottom - unionTop))
        val localB = blockB.copy(bounds = SerialRect(boundsB.left - unionLeft, boundsB.top - unionTop, boundsB.right - unionLeft, boundsB.bottom - unionTop))

        val containerId = "Container_${getCurrentTimeMillis() % 10000}"
        val container = UIBlock(
            id = containerId,
            type = UIBlockType.CONTAINER,
            isPureContainer = true,
            bounds = unionRect,
            children = listOf(localB, localA)
        )

        val remaining = removeBlockFromTree(removeBlockFromTree(view.blocks, draggedId), targetId)
        val targetIdx = view.blocks.indexOfFirst { it.id == targetId }
        val mutable = remaining.toMutableList()
        val insertIdx = if (targetIdx >= 0) targetIdx.coerceAtMost(mutable.size) else mutable.size
        mutable.add(insertIdx, container)
        val resultBlocks = mutable.bindParents()

        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        state = state.copy(pendingCreateContainer = null, selectedBlockId = containerId)
        Toast.show("成功创建父约束容器: $containerId", ToastType.SUCCESS)
    }

    fun cancelCreateContainer() {
        state = state.copy(pendingCreateContainer = null)
    }

    /**
     * 请求将组件移出父容器。若为容器内最后一个组件，触发确认提示。
     */
    fun requestRemoveBlockFromParent(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId) ?: return
        val parent = view.blocks.findBlockById(parentId) ?: return
        if (parent.children.size <= 1) {
            state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, false))
        } else {
            performRemoveBlockFromParent(parentId, blockId, false, repo)
        }
    }

    /**
     * 请求删除组件。若属于容器且为最后一个子组件，触发确认提示。
     */
    fun requestDeleteBlock(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val parentId = view.blocks.findParentBlockId(blockId)
        if (parentId != null) {
            val parent = view.blocks.findBlockById(parentId)
            if (parent != null && parent.children.size <= 1) {
                state = state.copy(pendingRemoveLastChildAction = Triple(parentId, blockId, true))
                return
            }
        }
        deleteSelectedBlock(blockId, repo)
    }

    fun confirmRemoveLastChildAction(repo: TemplateRepository) {
        val action = state.pendingRemoveLastChildAction ?: return
        val (containerId, childId, isDelete) = action
        performRemoveBlockFromParent(containerId, childId, isDelete, repo)
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    fun cancelRemoveLastChildAction() {
        state = state.copy(pendingRemoveLastChildAction = null)
    }

    private fun performRemoveBlockFromParent(
        containerId: String,
        childId: String,
        isDelete: Boolean,
        repo: TemplateRepository
    ) {
        val view = state.currentView ?: return
        val child = view.blocks.findBlockById(childId) ?: return
        val childAbsBounds = view.blocks.calculateBlockAbsoluteBounds(childId) ?: child.bounds
        val parent = view.blocks.findBlockById(containerId) ?: return

        val parentIndexInRoot = view.blocks.indexOfFirst { it.id == containerId }
        val remainingInContainer = parent.children.filterNot { it.id == childId }

        var newBlocks = removeBlockFromTree(view.blocks, childId)
        if (remainingInContainer.isEmpty()) {
            newBlocks = removeBlockFromTree(newBlocks, containerId)
        }

        if (!isDelete) {
            val mutable = newBlocks.toMutableList()
            val targetInsertIndex = if (parentIndexInRoot >= 0) {
                if (remainingInContainer.isNotEmpty()) {
                    val currentParentIdx = mutable.indexOfFirst { it.id == containerId }
                    if (currentParentIdx >= 0) currentParentIdx + 1 else mutable.size
                } else {
                    parentIndexInRoot.coerceIn(0, mutable.size)
                }
            } else {
                mutable.size
            }
            mutable.add(targetInsertIndex, child.copy(bounds = childAbsBounds))
            newBlocks = mutable
        }

        val resultBlocks = newBlocks.bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        val actionText = if (isDelete) "组件已删除" else "组件已移出父容器"
        val containerText = if (remainingInContainer.isEmpty()) "，原父容器已自动解散清理" else ""
        Toast.show("$actionText$containerText", ToastType.SUCCESS)
    }

    /**
     * 解散父容器：将其内部所有子图层平移释放至根列表，并销毁该纯容器。
     */
    fun ungroupContainer(containerId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val container = view.blocks.findBlockById(containerId) ?: return
        val containerAbsBounds = view.blocks.calculateBlockAbsoluteBounds(containerId) ?: container.bounds
        val releasedChildren = container.children.map { child ->
            val absLeft = containerAbsBounds.left + child.bounds.left
            val absTop = containerAbsBounds.top + child.bounds.top
            child.copy(bounds = SerialRect(absLeft, absTop, absLeft + child.bounds.width, absTop + child.bounds.height))
        }
        val withoutContainer = removeBlockFromTree(view.blocks, containerId)
        val resultBlocks = (withoutContainer + releasedChildren).bindParents()
        updateViewBlocksAndSave(view.id, resultBlocks, repo)
        Toast.show("已解散父容器: ${container.id}", ToastType.SUCCESS)
    }

    private fun updateViewBlocksAndSave(viewId: String, newBlocks: List<UIBlock>, repo: TemplateRepository) {
        val updatedPages = state.project?.pages?.map {
            if (it.id == viewId) it.copy(blocks = newBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    /**
     * 切换指定组件的可见性状态 (显示/隐藏，递归支持容器内的子图元)。
     */
    fun toggleBlockVisibility(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) { b ->
            val newVisible = !b.isVisible
            if (b.children.isNotEmpty()) {
                b.copy(isVisible = newVisible, children = mapAllBlocksRecursively(b.children) { it.copy(isVisible = newVisible) })
            } else {
                b.copy(isVisible = newVisible)
            }
        }.bindParents()
        updateViewBlocksAndSave(view.id, updatedBlocks, repo)
    }

    /**
     * 将指定组件置于顶层（移至渲染列表尾部）。
     */
    fun bringBlockToFront(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val target = view.blocks.find { it.id == blockId } ?: return
        val updatedBlocks = view.blocks.filterNot { it.id == blockId } + target
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
        Toast.show("已将组件置于顶层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件置于底层（移至渲染列表头部）。
     */
    fun sendBlockToBack(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val target = view.blocks.find { it.id == blockId } ?: return
        val updatedBlocks = listOf(target) + view.blocks.filterNot { it.id == blockId }
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
        Toast.show("已将组件置于底层", ToastType.SUCCESS)
    }

    /**
     * 将指定组件上移一层。
     */
    fun moveBlockUp(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val idx = view.blocks.indexOfFirst { it.id == blockId }
        if (idx == -1 || idx == view.blocks.lastIndex) return
        val mutable = view.blocks.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(idx + 1, item)
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = mutable) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    /**
     * 将指定组件下移一层。
     */
    fun moveBlockDown(blockId: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val idx = view.blocks.indexOfFirst { it.id == blockId }
        if (idx <= 0) return
        val mutable = view.blocks.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(idx - 1, item)
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = mutable) else it
        } ?: return
        state = state.copy(project = state.project?.copy(pages = updatedPages))
        saveProject(repo)
    }

    /**
     * 重命名组件（递归支持子组件）。
     */
    fun renameBlock(blockId: String, newName: String, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val target = view.blocks.findBlockById(blockId) ?: return
        val trimmed = newName.trim().ifBlank { target.id }
        if (trimmed == blockId) {
            state = state.copy(renamingBlockId = null)
            return
        }
        val updatedBlocks = updateBlockInTreeRecursively(view.blocks, blockId) {
            it.copy(id = trimmed)
        }.bindParents()
        val updatedPages = state.project?.pages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(
            project = state.project?.copy(pages = updatedPages),
            selectedBlockId = if (state.selectedBlockId == blockId) trimmed else state.selectedBlockId,
            renamingBlockId = null
        )
        saveProject(repo)
        Toast.show("组件已重命名为: $trimmed", ToastType.SUCCESS)
    }

    fun startRenameBlock(blockId: String) {
        state = state.copy(renamingBlockId = blockId)
    }

    fun cancelRenameBlock() {
        state = state.copy(renamingBlockId = null)
    }

    fun requestClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearCurrentSession() {
        val current = state.currentChatSession ?: return
        val updated = current.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        state = state.copy(
            isClearingCurrentSession = false,
            chatSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        )
        saveSession(updated)
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }

    /**
     * 创建一个带指定名称和分辨率的默认空白模板工程。
     */
    private fun createDefaultBlankProject(name: String, width: Float, height: Float): ProjectState {
        return ProjectState(
            projectId = name,
            pages = listOf(
                UIPage(
                    id = "page_main",
                    nameStr = "MainPage",
                    width = width,
                    height = height,
                    blocks = emptyList()
                )
            )
        )
    }
}

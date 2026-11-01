package org.gemini.ui.forge.ui.uistudio.delegate

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.ui.uistudio.ViewportState
import org.gemini.ui.forge.ui.uistudio.UIStudioLayoutConfig
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.bindParents

/**
 * UI Studio 视口域委托。
 * 承载画布视口的缩放平移手势、视口历史记忆缓存（viewport_cache.json）、
 * 工作台布局持久化（workspace_layout.json）、边栏宽度与面板高度比例、
 * 图元几何属性（缩放因数 / 不透明度）与画布窗口包围盒等视口相关状态操作。
 *
 * 通过构造注入宿主的状态读写桥接、协程作用域、脏标记回调、JSON 解析器
 * 以及图元树递归更新函数引用，保持与宿主 UIStudioState 的单一数据源。
 */
class ViewportDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val markDirty: () -> Unit,
    private val jsonHelper: Json,
    private val updateBlockInTree: (List<UIStudioBlock>, String, (UIStudioBlock) -> UIStudioBlock) -> List<UIStudioBlock>
) {

    /** 状态桥接属性：使迁移的方法体以 state = state.copy(...) 形式零改动平移。 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    fun updateDragCursorPosition(pos: Offset?) {
        state = state.copy(dragCursorPosition = pos)
    }

    /**
     * 仅且只将缩放值还原为 100% (1.0f)，绝不修改或重置画布的 X/Y 坐标，杜绝坐标跳动。
     */
    fun resetZoomTo100() {
        state = state.copy(
            scale = 1.0f,
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 暂存当前活跃视口（视图界面或父容器专属标签）的缩放比率与平移偏移量到内存缓存中，
     * 并同步持久化到本地磁盘 `templates/{projectId}/viewport_cache.json`。
     * 当切换标签页或关闭标签页时调用，以保证用户再次打开或切回时能够 100% 还原上次调整的视口布局。
     */
    fun cacheCurrentViewport() {
        val currentKey = state.activeContainerId ?: state.currentViewId ?: return
        val currentCache = state.viewViewportCache.toMutableMap()
        currentCache[currentKey] = ViewportState(
            scale = state.scale,
            panX = state.panOffset.x,
            panY = state.panOffset.y,
            isFitToViewport = state.isFitToViewport
        )
        state = state.copy(viewViewportCache = currentCache)
        state.project?.projectId?.let { saveViewportCache(it) }
    }

    /**
     * 还原指定视图界面或父容器标签页的视口布局。
     * - 若缓存中存在此前保存的视口记录，则精确恢复该记录的 scale、panOffset 以及是否自适应居中；
     * - 若该视图属于首次打开（无历史缓存），则严格以 100% (1.0f) 原始比例居中对齐呈现，绝不自动进行非 100% 的自适应缩放。
     *
     * @param targetKey 目标视图 ID 或容器 ID
     */
    internal fun restoreViewportOrDefault(targetKey: String) {
        val cached = state.viewViewportCache[targetKey]
        if (cached != null) {
            state = state.copy(
                scale = cached.scale,
                panOffset = Offset(cached.panX, cached.panY),
                isFitToViewport = cached.isFitToViewport
            )
            if (cached.isFitToViewport) {
                state = state.copy(fitToViewportTrigger = state.fitToViewportTrigger + 1)
            }
        } else {
            // 首次打开默认严格为 100% 原始比例，并在当前舞台居中对齐
            resetZoom()
        }
    }

    /**
     * 从本地磁盘异步加载当前项目的视口历史记忆缓存文件 `viewport_cache.json`。
     */
    fun loadViewportCache(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/viewport_cache.json"
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val map = jsonHelper.decodeFromString<Map<String, ViewportState>>(bytes.decodeToString())
                        state = state.copy(viewViewportCache = map)
                        val activeKey = state.activeContainerId ?: state.currentViewId
                        if (activeKey != null) {
                            withContext(Dispatchers.Main) {
                                restoreViewportOrDefault(activeKey)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取视口缓存失败: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    state = state.copy(isViewportInitialized = true)
                }
            }
        }
    }

    /**
     * 将当前内存中的视口记忆映射持久化写入本地磁盘 `templates/{projectId}/viewport_cache.json`。
     */
    fun saveViewportCache(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/viewport_cache.json"
            try {
                val tFile = TemplateFile(path)
                val jsonStr = jsonHelper.encodeToString(state.viewViewportCache)
                tFile.writeBytes(jsonStr.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存视口缓存失败: ${e.message}")
            }
        }
    }

    /**
     * 从本地磁盘异步加载当前项目的工作台布局记忆配置 `workspace_layout.json`。
     */
    fun loadWorkspaceLayout(projectId: String) {
        scope.launch(Dispatchers.Default) {
            val sanitized = projectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/workspace_layout.json"
            try {
                val tFile = TemplateFile(path)
                if (tFile.exists()) {
                    val bytes = tFile.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val layout = jsonHelper.decodeFromString(UIStudioLayoutConfig.serializer(), bytes.decodeToString())
                        val restoredTab = runCatching { UIStudioRightTab.valueOf(layout.activeRightTabName) }.getOrDefault(UIStudioRightTab.PROPERTY)
                        val propW = layout.propertySidebarWidthDp.dp.coerceAtLeast(180.dp)
                        val aiW = layout.aiChatSidebarWidthDp.dp.coerceAtLeast(280.dp)
                        val activeWidth = if (restoredTab == UIStudioRightTab.PROPERTY) propW else aiW

                        // 仅当上次记录的页面在当前工程 studioPages 中仍然合法存在时，才进行恢复
                        val allStudioPages = state.project?.studioPages ?: emptyList()
                        val validOpenIds = layout.openViewIds.filter { openId ->
                            allStudioPages.any { it.id == openId }
                        }
                        val restoredCurrentViewId = if (validOpenIds.contains(layout.currentViewId)) {
                            layout.currentViewId
                        } else {
                            validOpenIds.lastOrNull()
                        }
                        val targetPage = allStudioPages.find { it.id == restoredCurrentViewId }

                        withContext(Dispatchers.Main) {
                            state = state.copy(
                                leftPanelWidth = layout.leftPanelWidthDp.dp,
                                rightPanelWidth = activeWidth,
                                propertySidebarWidth = propW,
                                aiChatSidebarWidth = aiW,
                                activeRightTab = restoredTab,
                                isRightSidebarVisible = layout.isRightSidebarVisible,
                                viewsPanelHeightRatio = layout.viewsPanelHeightRatio.coerceIn(0.05f, 0.95f),
                                propertyPanelHeightRatio = layout.propertyPanelHeightRatio.coerceIn(0.05f, 0.95f),
                                openViewIds = validOpenIds,
                                currentViewId = restoredCurrentViewId,
                                canvasWidth = targetPage?.width ?: state.canvasWidth,
                                canvasHeight = targetPage?.height ?: state.canvasHeight
                            )
                            if (restoredCurrentViewId != null) {
                                restoreViewportOrDefault(restoredCurrentViewId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "读取工作区布局失败: ${e.message}")
            }
        }
    }

    /**
     * 将当前工作台边栏宽度与面板高度比例持久化写入本地磁盘 `templates/{projectId}/workspace_layout.json`。
     */
    fun saveWorkspaceLayout(projectId: String? = null) {
        val targetProjectId = projectId ?: state.project?.projectId ?: return
        scope.launch(Dispatchers.Default) {
            val sanitized = targetProjectId.trim().replace(" ", "_")
            val path = "templates/$sanitized/workspace_layout.json"
            try {
                val layout = UIStudioLayoutConfig(
                    leftPanelWidthDp = state.leftPanelWidth.value,
                    rightPanelWidthDp = state.rightPanelWidth.value,
                    viewsPanelHeightRatio = state.viewsPanelHeightRatio,
                    propertyPanelHeightRatio = state.propertyPanelHeightRatio,
                    propertySidebarWidthDp = state.propertySidebarWidth.value,
                    aiChatSidebarWidthDp = state.aiChatSidebarWidth.value,
                    activeRightTabName = state.activeRightTab.name,
                    isRightSidebarVisible = state.isRightSidebarVisible,
                    openViewIds = state.openViewIds,
                    currentViewId = state.currentViewId
                )
                val tFile = TemplateFile(path)
                tFile.createParentDirs()
                val jsonStr = jsonHelper.encodeToString(UIStudioLayoutConfig.serializer(), layout)
                tFile.writeBytes(jsonStr.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存工作区布局失败: ${e.message}")
            }
        }
    }

    fun zoomIn() {
        state = state.copy(scale = (state.scale * 1.2f).coerceIn(0.1f, 5.0f), isFitToViewport = false)
        cacheCurrentViewport()
        markDirty()
    }

    fun zoomOut() {
        state = state.copy(scale = (state.scale / 1.2f).coerceIn(0.1f, 5.0f), isFitToViewport = false)
        cacheCurrentViewport()
        markDirty()
    }

    fun updateDensity(density: Float) {
        if (state.density != density && density > 0f) {
            state = state.copy(density = density)
        }
    }

    fun resetZoom(viewportWidth: Float = 0f, viewportHeight: Float = 0f, density: Float? = null) {
        val currentDensity = (density ?: state.density).coerceAtLeast(0.1f)
        val activeContainer = state.activeContainerBlock
        val currentView = state.currentView
        val effectiveWidth = (activeContainer?.bounds?.width ?: currentView?.width ?: state.canvasWidth)
        val effectiveHeight = (activeContainer?.bounds?.height ?: currentView?.height ?: state.canvasHeight)
        val canvasPhysicalW = effectiveWidth * currentDensity
        val canvasPhysicalH = effectiveHeight * currentDensity

        val vw = if (viewportWidth > 0f) viewportWidth else (state.canvasWindowBounds?.let { it.right - it.left } ?: canvasPhysicalW)
        val vh = if (viewportHeight > 0f) viewportHeight else (state.canvasWindowBounds?.let { it.bottom - it.top } ?: canvasPhysicalH)

        val panX = (vw - canvasPhysicalW) / 2f
        val panY = (vh - canvasPhysicalH) / 2f

        state = state.copy(
            scale = 1.0f,
            panOffset = Offset(panX, panY),
            isFitToViewport = false
        )
        cacheCurrentViewport()
    }

    /**
     * 独立调节选中图元的缩放因数 scaleX 与 scaleY（完全不影响 bounds.width 与 bounds.height 固有尺寸）。
     */
    fun updateSelectedBlockScale(scaleX: Float, scaleY: Float, repo: TemplateRepository) {
        val selectedId = state.selectedBlockId ?: return
        updateBlockScale(selectedId, scaleX, scaleY, repo)
    }

    /**
     * 独立调节指定图元的缩放因数 scaleX 与 scaleY（完全不影响 bounds.width 与 bounds.height 固有尺寸）。
     */
    fun updateBlockScale(blockId: String, scaleX: Float, scaleY: Float, repo: TemplateRepository) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTree(view.blocks, blockId) {
            it.copy(scaleX = scaleX, scaleY = scaleY)
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    /**
     * 独立调节选中图元的不透明度 opacity（范围 0.0~1.0f）。
     */
    fun updateSelectedBlockOpacity(opacity: Float, repo: TemplateRepository = TemplateRepository()) {
        val selectedId = state.selectedBlockId ?: return
        updateBlockOpacity(selectedId, opacity, repo)
    }

    /**
     * 独立调节指定图元的不透明度 opacity（范围 0.0~1.0f）。
     */
    fun updateBlockOpacity(blockId: String, opacity: Float, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val clamped = opacity.coerceIn(0.0f, 1.0f)
        val updatedBlocks = updateBlockInTree(view.blocks, blockId) {
            it.copy(opacity = clamped)
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    /**
     * 一键重置图元的变换属性（双轴缩放还原 1.0，透明度还原 1.0）。
     */
    fun resetBlockTransform(blockId: String, repo: TemplateRepository = TemplateRepository()) {
        val view = state.currentView ?: return
        val updatedBlocks = updateBlockInTree(view.blocks, blockId) {
            it.copy(scaleX = 1.0f, scaleY = 1.0f, opacity = 1.0f)
        }.bindParents()
        val updatedPages = state.project?.studioPages?.map {
            if (it.id == view.id) it.copy(blocks = updatedBlocks) else it
        } ?: return
        state = state.copy(project = state.project?.copy(studioPages = updatedPages), isDirty = true)
    }

    fun triggerFitToViewport() {
        state = state.copy(
            isFitToViewport = true,
            fitToViewportTrigger = state.fitToViewportTrigger + 1
        )
    }

    // ====== 边栏尺寸与自适应居中 ======

    fun updateLeftPanelWidth(width: Dp, totalContainerWidth: Dp? = null) {
        val minWidth = 60.dp
        val maxWidth = if (totalContainerWidth != null && totalContainerWidth > minWidth) {
            val intersectWidth = (totalContainerWidth - state.rightPanelWidth).coerceAtLeast(minWidth)
            (intersectWidth * 0.90f).coerceAtLeast(minWidth)
        } else {
            1200.dp
        }
        state = state.copy(leftPanelWidth = width.coerceIn(minWidth, maxWidth))
    }

    fun updateRightPanelWidth(width: Dp, totalContainerWidth: Dp? = null) {
        val minWidth = if (state.activeRightTab == UIStudioRightTab.PROPERTY) 180.dp else 260.dp
        val maxWidth = if (totalContainerWidth != null && totalContainerWidth > minWidth) {
            val leftW = if (state.isAssetPanelVisible) state.leftPanelWidth else 0.dp
            val intersectWidth = (totalContainerWidth - leftW).coerceAtLeast(minWidth)
            (intersectWidth * 0.90f).coerceAtLeast(minWidth)
        } else {
            1200.dp
        }
        val clamped = width.coerceIn(minWidth, maxWidth)
        state = if (state.activeRightTab == UIStudioRightTab.PROPERTY) {
            state.copy(rightPanelWidth = clamped, propertySidebarWidth = clamped)
        } else {
            state.copy(rightPanelWidth = clamped, aiChatSidebarWidth = clamped)
        }
    }

    /**
     * 切换右侧边栏分页 Tab（属性 / AI 助手）。
     * 切换时自动应用该 Tab 记忆的独立宽度，并确保边栏处于可见状态。
     */
    fun selectRightTab(tab: UIStudioRightTab) {
        val targetWidth = if (tab == UIStudioRightTab.PROPERTY) state.propertySidebarWidth else state.aiChatSidebarWidth
        state = state.copy(
            activeRightTab = tab,
            isRightSidebarVisible = true,
            rightPanelWidth = targetWidth
        )
        saveWorkspaceLayout()
    }

    /**
     * 展开 / 折叠右侧边栏。
     */
    fun toggleRightSidebar() {
        state = state.copy(isRightSidebarVisible = !state.isRightSidebarVisible)
        saveWorkspaceLayout()
    }

    /**
     * 显式设置右侧边栏可见性。
     */
    fun setRightSidebarVisible(visible: Boolean) {
        state = state.copy(isRightSidebarVisible = visible)
        saveWorkspaceLayout()
    }

    fun updateViewsPanelHeightRatio(ratio: Float) {
        state = state.copy(viewsPanelHeightRatio = ratio.coerceIn(0.05f, 0.95f))
    }

    /**
     * 自动将当前视图的画布居中并自适应填充满工作区视口 (Fit to Viewport)。
     * 支持传入自定义页面/容器宽高（若未传则优先使用当前激活容器的宽高，其次使用主视图页面宽高）。
     */
    fun fitToViewport(
        viewportWidth: Float,
        viewportHeight: Float,
        customWidth: Float? = null,
        customHeight: Float? = null,
        density: Float? = null
    ) {
        val currentDensity = (density ?: state.density).coerceAtLeast(0.1f)
        val activeContainer = state.activeContainerBlock
        val currentView = state.currentView
        val effectiveWidth = (customWidth ?: activeContainer?.bounds?.width ?: currentView?.width ?: state.canvasWidth)
        val effectiveHeight = (customHeight ?: activeContainer?.bounds?.height ?: currentView?.height ?: state.canvasHeight)
        if (viewportWidth <= 0f || viewportHeight <= 0f || effectiveWidth <= 0f || effectiveHeight <= 0f) return

        val canvasPhysicalW = effectiveWidth * currentDensity
        val canvasPhysicalH = effectiveHeight * currentDensity

        val paddingPx = 64f * currentDensity
        val availW = (viewportWidth - paddingPx).coerceAtLeast(100f)
        val availH = (viewportHeight - paddingPx).coerceAtLeast(100f)

        val scaleX = availW / canvasPhysicalW
        val scaleY = availH / canvasPhysicalH
        val targetScale = minOf(scaleX, scaleY).coerceIn(0.1f, 3.0f)

        val panX = (viewportWidth - canvasPhysicalW * targetScale) / 2f
        val panY = (viewportHeight - canvasPhysicalH * targetScale) / 2f

        state = state.copy(
            scale = targetScale,
            panOffset = Offset(panX, panY),
            isFitToViewport = true
        )
        cacheCurrentViewport()
    }

    /**
     * 更新指定页面的画布参考底色（用于在编辑中直观区隔渲染区域与无限舞台背景）。
     */
    fun updatePageCanvasBackgroundColor(pageId: String, colorHex: String?, repo: TemplateRepository) {
        val proj = state.project ?: return
        val updatedPages = proj.studioPages.map { page ->
            if (page.id == pageId) page.copy(canvasBackgroundColor = colorHex) else page
        }
        val updatedProject = proj.copy(studioPages = updatedPages)
        state = state.copy(project = updatedProject)
        markDirty()
        scope.launch(Dispatchers.Default) {
            try {
                repo.saveTemplate(updatedProject.projectId, updatedProject)
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "保存页面参考背景色失败", e)
            }
        }
    }

    /**
     * 更新右侧属性面板与预览面板的高度比例。
     */
    fun updatePropertyPanelHeightRatio(ratio: Float) {
        state = state.copy(propertyPanelHeightRatio = ratio.coerceIn(0.05f, 0.95f))
    }

    /**
     * 设置素材拖拽悬停至画布的高亮状态。
     */
    fun setCanvasDragHovered(isHovered: Boolean) {
        if (state.isCanvasDragHovered != isHovered) {
            state = state.copy(isCanvasDragHovered = isHovered)
        }
    }

    /**
     * 更新画布在窗口中的绝对物理矩形包围盒。
     */
    fun updateCanvasWindowBounds(bounds: SerialRect?) {
        state = state.copy(canvasWindowBounds = bounds)
    }

    /**
     * 更新素材拖拽悬停时的最近光标绝对坐标。
     */
    fun updateLastDragHoverPos(pos: Offset?) {
        state = state.copy(lastDragHoverPos = pos)
    }

    /**
     * 处理画布视口滚轮/双指缩放手势。
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
            panOffset = Offset(newPanX, newPanY),
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 处理画布视口平移拖拽手势。
     *
     * @param delta 平移位移增量向量
     */
    fun onPan(delta: Offset) {
        state = state.copy(
            panOffset = state.panOffset + delta,
            isFitToViewport = false
        )
        cacheCurrentViewport()
        markDirty()
    }

    /**
     * 重置画布视口至居中 100% (1.0f) 原寸视野。
     */
    fun resetViewport() {
        resetZoom()
    }

    /**
     * 切换左侧素材库面板的展开/折叠显隐状态。
     */
    fun toggleAssetPanel() {
        state = state.copy(isAssetPanelVisible = !state.isAssetPanelVisible)
    }
}

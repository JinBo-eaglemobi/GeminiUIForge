package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.UIStudioLeftBottomTab
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab
import org.gemini.ui.forge.utils.Toast

/**
 * UI Studio 视图页面域委托 (PageManagerDelegate)。
 *
 * 专职承载视图界面 (Page) 的全生命周期管理：视图标签页的打开/切换/关闭、
 * 视图的新建/克隆/删除、画布尺寸调整、选中状态清空，以及 AI 聊天大弹窗的
 * 打开/停靠侧栏/关闭等交互编排。
 *
 * 跨域协作说明：
 * - 视口缓存与恢复 (cacheCurrentViewport / restoreViewportOrDefault 等) 通过构造
 *   注入 [viewport] (ViewportDelegate) 复用视口域能力；
 * - AI 聊天弹窗开启时需要联动加载 Agent 配置、会话列表与技能体系，这些能力属于
 *   尚未迁移的宿主域 (chatSession / aiConfig)，故以函数引用回调注入，保持逐域
 *   拆分的独立性与可组合性。
 */
class PageManagerDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val viewport: ViewportDelegate,
    private val loadProjectAgentConfig: () -> Unit,
    private val loadChatSessions: () -> Unit,
    private val refreshSkills: (Boolean) -> Unit,
    private val startSkillsDirectoryWatcher: () -> Unit,
    private val stopSkillsDirectoryWatcher: () -> Unit,
) {
    /** 桥接属性：与宿主共享同一份 UIStudioState，实现方法体零改动平移 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

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
        viewport.cacheCurrentViewport()
        val page = state.project?.studioPages?.find { it.id == viewId } ?: return
        val currentOpen = state.openViewIds
        val updatedOpen = if (currentOpen.contains(viewId)) currentOpen else currentOpen + viewId
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = viewId,
            canvasWidth = page.width,
            canvasHeight = page.height,
            activeContainerId = null,
            selectedBlockId = null,
            selectedAssetHash = null,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        viewport.restoreViewportOrDefault(viewId)
        viewport.saveWorkspaceLayout()
    }

    /**
     * 选中并激活指定页面视图标签。
     */
    fun selectViewTab(viewId: String) {
        viewport.cacheCurrentViewport()
        selectView(viewId)
        state = state.copy(
            activeContainerId = null,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        viewport.restoreViewportOrDefault(viewId)
        viewport.saveWorkspaceLayout()
    }

    /**
     * 自动扫描并自愈工程中的组件与视图页面绑定关系 (UIStudioPage & Component 1:1 Invariant)。
     * 若图元含有子元素或被标记为复合组件，但尚未绑定独立子页面，自动为其在 studioPages 中注册对应子页面。
     */
    fun healStudioPagesAndComponents(proj: ProjectState): ProjectState {
        val originalPages = proj.studioPages.toList()
        var hasChanges = false
        val pageIds = originalPages.map { it.id }.toMutableSet()
        val newGeneratedPages = mutableListOf<UIStudioPage>()

        fun processBlock(block: UIStudioBlock): UIStudioBlock {
            val hasChildren = block.children.isNotEmpty()
            if (hasChildren) {
                val matchedPage = originalPages.find { it.id == block.targetPageId }
                    ?: newGeneratedPages.find { it.id == block.targetPageId }
                val targetPageId = if (matchedPage != null) {
                    matchedPage.id
                } else {
                    var newPageId = "page_${block.id}"
                    var counter = 1
                    while (pageIds.contains(newPageId)) {
                        newPageId = "page_${block.id}_$counter"
                        counter++
                    }
                    pageIds.add(newPageId)
                    val newPage = UIStudioPage(
                        id = newPageId,
                        nameStr = "组件_${block.id}",
                        width = block.bounds.width.coerceAtLeast(100f),
                        height = block.bounds.height.coerceAtLeast(100f),
                        blocks = block.children
                    )
                    newGeneratedPages.add(newPage)
                    hasChanges = true
                    newPageId
                }

                val updatedChildren = block.children.map { processBlock(it) }
                return block.copy(
                    targetPageId = targetPageId,
                    children = updatedChildren
                )
            }
            return block
        }

        val updatedOriginalPages = originalPages.map { page ->
            val updatedBlocks = page.blocks.map { processBlock(it) }
            if (updatedBlocks != page.blocks) {
                hasChanges = true
                page.copy(blocks = updatedBlocks)
            } else {
                page
            }
        }

        return if (hasChanges || newGeneratedPages.isNotEmpty()) {
            proj.copy(studioPages = updatedOriginalPages + newGeneratedPages)
        } else {
            proj
        }
    }

    /**
     * 为指定复合图元确保存在一个引用的独立视图页面 (UIStudioPage)。
     * 如果已有引用页面则直接返回；否则自动在 studioPages 中新建平级页面并绑定引用。
     */
    fun ensurePageForBlock(block: UIStudioBlock, parentPage: UIStudioPage, repo: TemplateRepository = TemplateRepository()): UIStudioPage {
        val proj = state.project ?: return parentPage
        val existing = proj.studioPages.find { it.id == block.targetPageId }
        if (existing != null) {
            return existing
        }

        val newPageId = "page_${block.id}_${getCurrentTimeMillis().toString().takeLast(4)}"
        val newPage = UIStudioPage(
            id = newPageId,
            nameStr = "组件_${block.id}",
            width = block.bounds.width.coerceAtLeast(100f),
            height = block.bounds.height.coerceAtLeast(100f),
            blocks = block.children
        )
        val updatedBlocks = parentPage.blocks.map { if (it.id == block.id) it.copy(targetPageId = newPageId) else it }
        val updatedParent = parentPage.copy(blocks = updatedBlocks)
        val updatedPages = proj.studioPages.map { if (it.id == parentPage.id) updatedParent else it } + newPage
        val updatedProj = proj.copy(studioPages = updatedPages)
        state = state.copy(project = updatedProj, isDirty = true)
        scope.launch { repo.saveTemplate(updatedProj.projectId, updatedProj) }
        return newPage
    }

    /**
     * 打开父容器/复合组件引用的独立编辑视图标签页。
     * 架构统一：直接以一等公民 UIStudioPage 打开并激活对应平级页面！
     */
    fun openContainerTab(containerId: String) {
        val proj = state.project
        if (proj != null) {
            val currentView = state.currentView
            val targetBlock = currentView?.blocks?.findBlockById(containerId) as? UIStudioBlock
            val targetPageId = targetBlock?.targetPageId ?: containerId
            val matchedPage = proj.studioPages.find { it.id == targetPageId }
            if (matchedPage != null) {
                openViewTab(matchedPage.id)
                Toast.show("已打开平级视图: ${matchedPage.nameStr}", ToastType.INFO)
                return
            }

            if (targetBlock != null && currentView != null) {
                val page = ensurePageForBlock(targetBlock, currentView)
                openViewTab(page.id)
                Toast.show("已打开平级视图: ${page.nameStr}", ToastType.INFO)
                return
            }
        }

        viewport.cacheCurrentViewport()
        val currentTabs = state.openContainerTabs
        val updatedTabs = if (currentTabs.contains(containerId)) currentTabs else currentTabs + containerId
        state = state.copy(
            openContainerTabs = updatedTabs,
            activeContainerId = containerId,
            selectedBlockId = containerId,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        viewport.restoreViewportOrDefault(containerId)
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
        val targetKey = newActiveId ?: state.currentViewId
        if (targetKey != null) {
            viewport.restoreViewportOrDefault(targetKey)
        } else {
            viewport.resetZoom()
        }
    }

    /**
     * 切换选中指定父容器编辑标签页。
     */
    fun selectContainerTab(containerId: String) {
        viewport.cacheCurrentViewport()
        state = state.copy(
            activeContainerId = containerId,
            selectedBlockId = containerId,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        viewport.restoreViewportOrDefault(containerId)
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
        val nextPage = state.project?.studioPages?.find { it.id == newActiveId }
        state = state.copy(
            openViewIds = updatedOpen,
            currentViewId = newActiveId,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            activeContainerId = null,
            selectedBlockId = null,
            leftBottomTab = if (newActiveId != null) UIStudioLeftBottomTab.HIERARCHY else state.leftBottomTab
        )
        if (newActiveId != null) {
            viewport.restoreViewportOrDefault(newActiveId)
        }
        viewport.saveWorkspaceLayout()
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
            selectedAssetHash = null
        )
        viewport.saveWorkspaceLayout()
        Toast.show("已关闭所有视图标签", ToastType.INFO)
    }

    /**
     * 关闭除指定视图之外的其他所有视图标签页。
     */
    fun closeOtherViewTabs(viewId: String) {
        if (!state.openViewIds.contains(viewId)) return
        val nextPage = state.project?.studioPages?.find { it.id == viewId }
        state = state.copy(
            openViewIds = listOf(viewId),
            currentViewId = viewId,
            openContainerTabs = emptyList(),
            activeContainerId = null,
            canvasWidth = nextPage?.width ?: state.canvasWidth,
            canvasHeight = nextPage?.height ?: state.canvasHeight,
            selectedBlockId = null,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        viewport.saveWorkspaceLayout()
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
        val targetPage = proj.studioPages.find { it.id == viewId } ?: return
        val clampedW = newWidth.coerceIn(100f, 10000f)
        val clampedH = newHeight.coerceIn(100f, 10000f)
        val updatedPage = targetPage.copy(width = clampedW, height = clampedH)
        val updatedPages = proj.studioPages.map { if (it.id == viewId) updatedPage else it }
        val updatedProject = proj.copy(studioPages = updatedPages)
        state = state.copy(
            project = updatedProject,
            canvasWidth = if (state.currentViewId == viewId) clampedW else state.canvasWidth,
            canvasHeight = if (state.currentViewId == viewId) clampedH else state.canvasHeight,
            isDirty = true
        )
    }

    /**
     * 清空当前所有选中状态（组件与素材均重置），联动恢复页面主视图属性面板。
     */
    fun clearSelection() {
        if (state.selectedBlockId != null || state.selectedAssetHash != null) {
            state = state.copy(
                selectedBlockId = null,
                selectedAssetHash = null
            )
        }
    }

    /**
     * 新建一个视图界面（Page）。
     */
    fun createView(name: String, width: Float, height: Float, repo: TemplateRepository) {
        val proj = state.project ?: return
        val viewName = name.trim().ifBlank { "View_${proj.studioPages.size + 1}" }
        val newPage = UIStudioPage(
            id = "view_${getCurrentTimeMillis()}",
            nameStr = viewName,
            width = width,
            height = height,
            blocks = emptyList()
        )
        val updatedPages = proj.studioPages + newPage
        val updatedProject = proj.copy(studioPages = updatedPages)
        val updatedOpen = state.openViewIds + newPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = newPage.id,
            openViewIds = updatedOpen,
            canvasWidth = width,
            canvasHeight = height,
            selectedBlockId = null,
            isNewViewDialogVisible = false,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        viewport.saveWorkspaceLayout()
        Toast.show("成功创建视图: $viewName", ToastType.SUCCESS)
    }

    /**
     * 复制并克隆指定视图界面 (含全部图元深度复制)。
     */
    fun duplicateView(page: UIStudioPage, repo: TemplateRepository) {
        val proj = state.project ?: return
        val newViewName = "${page.nameStr}_副本"
        val timestamp = getCurrentTimeMillis()
        val clonedBlocks = page.blocks.map { block ->
            block.copy(id = "block_${timestamp}_${block.id.takeLast(6)}")
        }
        val duplicatedPage = UIStudioPage(
            id = "view_$timestamp",
            nameStr = newViewName,
            width = page.width,
            height = page.height,
            blocks = clonedBlocks
        )
        val updatedPages = proj.studioPages + duplicatedPage
        val updatedProject = proj.copy(studioPages = updatedPages)
        val updatedOpen = state.openViewIds + duplicatedPage.id
        state = state.copy(
            project = updatedProject,
            currentViewId = duplicatedPage.id,
            openViewIds = updatedOpen,
            canvasWidth = duplicatedPage.width,
            canvasHeight = duplicatedPage.height,
            selectedBlockId = null,
            leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        viewport.saveWorkspaceLayout()
        Toast.show("已克隆视图: $newViewName", ToastType.SUCCESS)
    }

    /**
     * 删除指定视图界面。
     */
    fun deleteView(viewId: String, repo: TemplateRepository) {
        val proj = state.project ?: return
        if (proj.studioPages.size <= 1) {
            Toast.show("至少保留一个主视图界面", ToastType.INFO)
            return
        }
        val updatedPages = proj.studioPages.filterNot { it.id == viewId }
        val updatedOpen = state.openViewIds.filterNot { it == viewId }
        val nextViewId = if (state.currentViewId == viewId) updatedPages.firstOrNull()?.id else state.currentViewId
        val nextPage = updatedPages.find { it.id == nextViewId }
        val updatedProject = proj.copy(studioPages = updatedPages)
        state = state.copy(
            project = updatedProject,
            openViewIds = updatedOpen,
            currentViewId = nextViewId,
            canvasWidth = nextPage?.width ?: 1920f,
            canvasHeight = nextPage?.height ?: 1080f,
            selectedBlockId = null,
            deletingView = null,
            leftBottomTab = if (nextViewId != null) UIStudioLeftBottomTab.HIERARCHY else state.leftBottomTab
        )
        scope.launch {
            repo.saveTemplate(updatedProject.projectId, updatedProject)
        }
        viewport.saveWorkspaceLayout()
        Toast.show("已删除视图", ToastType.INFO)
    }

    /**
     * 请求删除指定视图界面 (唤起二次确认弹窗)。
     */
    fun requestDeleteView(page: UIStudioPage) {
        state = state.copy(deletingView = page)
    }

    /**
     * 取消删除视图确认弹窗。
     */
    fun cancelDeleteView() {
        state = state.copy(deletingView = null)
    }

    /**
     * 确认执行删除当前待删除视图。
     */
    fun confirmDeleteView(repo: TemplateRepository) {
        val page = state.deletingView ?: return
        deleteView(page.id, repo)
    }

    /**
     * 更新视图搜索框文本。
     */
    fun updateViewSearchQuery(query: String) {
        state = state.copy(viewSearchQuery = query)
    }

    /**
     * 打开新建视图弹窗。
     */
    fun openNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = true)
    }

    /**
     * 关闭新建视图弹窗。
     */
    fun closeNewViewDialog() {
        state = state.copy(isNewViewDialogVisible = false)
    }

    /**
     * 打开 AI 聊天全景大弹窗，并联动加载 Agent 配置、会话列表与技能体系。
     */
    fun openAiChatDialog() {
        state = state.copy(
            isAiChatDialogVisible = true,
            activeRightTab = UIStudioRightTab.PROPERTY
        )
        loadProjectAgentConfig()
        loadChatSessions()
        refreshSkills(false)
        startSkillsDirectoryWatcher()
    }

    /**
     * 将全景大弹窗收纳并无缝转换至右侧边栏 AI 助手模式 (单模态互斥切换)
     */
    fun dockAiChatToSidebar() {
        closeAiChatDialog()
        viewport.setRightSidebarVisible(true)
        viewport.selectRightTab(UIStudioRightTab.AI_CHAT)
    }

    /**
     * 关闭 AI 聊天全景大弹窗，并停止技能目录监视。
     */
    fun closeAiChatDialog() {
        state = state.copy(
            isAiChatDialogVisible = false,
            isSystemInstructionDialogVisible = false,
            isClearingCurrentSession = false,
            pendingLayoutResult = null
        )
        stopSkillsDirectoryWatcher()
    }
}

package org.gemini.ui.forge.ui.uistudio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.assetsheet.AssetSheetParseResult
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.uistudio.model.ProjectAgentConfig
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary

/**
 * UI Studio 左侧边栏下半部分 Tab 分类枚举。
 */
enum class UIStudioLeftBottomTab {
    ASSETS,     // 素材库 (Asset Library)
    HIERARCHY   // 图层层级 (Hierarchy)
}

/**
 * UI Studio 界面响应式状态数据模型。
 *
 * @property project 当前正在编辑的模板工程实体
 * @property canvasWidth 画布逻辑宽度（像素）
 * @property canvasHeight 画布逻辑高度（像素）
 * @property scale 当前画布的视图缩放比率 (0.1 ~ 5.0)
 * @property panOffset 当前画布相对中心原点的二维视口平移位移向量
 * @property selectedBlockId 当前在画布中处于选中状态的图元 ID（为空表示未选中任何图元）
 * @property isAssetPanelVisible 左侧素材库面板是否处于展开显示状态
 * @property isSaving 是否处于工程保存中状态
 * @property isImportSheetDialogVisible 导入图集配置弹窗是否可见
 * @property sourceImagePath 待导入的原始大图文件物理路径
 * @property sourceJsonPath 待导入的坐标 JSON 文件物理路径
 * @property parseResult 坐标 JSON 经自适应解析引擎标准化后的结果
 * @property isParsingJson 是否正在执行 JSON 解析运算
 * @property assetLibrary 项目素材库数据（含文件夹体系与素材列表）
 * @property selectedFolderId 素材库当前选中的文件夹 ID（null 表示全部或根目录）
 * @property selectedAssetId 素材库当前选中的素材 ID
 * @property isNewFolderDialogVisible 新建素材文件夹弹窗是否可见
 * @property currentViewId 当前活跃选中的视图界面（Page）ID
 * @property viewSearchQuery 视图界面搜索查找关键字
 * @property isNewViewDialogVisible 新建视图界面弹窗是否可见
 * @property isAiChatDialogVisible AI Chat 智能排版会话弹窗是否可见
 * @property leftPanelWidth 左侧边栏宽度
 * @property rightPanelWidth 右侧属性面板宽度
 * @property viewsPanelHeightRatio 左侧栏上半部分（ViewsPanel）所占高度比率 (0.2 ~ 0.8)
 * @property isFitToViewport 是否处于自适应居中模式
 */
data class UIStudioState(
    val project: ProjectState? = null,
    val canvasWidth: Float = 1920f,
    val canvasHeight: Float = 1080f,
    val scale: Float = 0.6f,
    val panOffset: Offset = Offset.Zero,
    val selectedBlockId: String? = null,
    val isAssetPanelVisible: Boolean = true,
    val isSaving: Boolean = false,
    val isImportSheetDialogVisible: Boolean = false,
    val sourceImagePath: String = "",
    val sourceJsonPath: String = "",
    val parseResult: AssetSheetParseResult? = null,
    val isParsingJson: Boolean = false,
    val isVerifySliceDialogVisible: Boolean = false,
    val verifySliceItems: List<org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem> = emptyList(),
    val importDraft: org.gemini.ui.forge.model.assetsheet.AssetImportDraft? = null,
    val isDraftPromptVisible: Boolean = false,
    val isSlicingAssets: Boolean = false,
    val sliceProgress: Float = 0f,
    val sliceStatusText: String = "",
    val isAiDetectingCoords: Boolean = false,
    val aiDetectStatusText: String = "",
    val assetLibrary: UIAssetLibrary = UIAssetLibrary(),
    val selectedFolderId: String? = null,
    val selectedAssetId: String? = null,
    val isNewFolderDialogVisible: Boolean = false,
    val currentViewId: String? = null,
    val openViewIds: List<String> = emptyList(),
    val leftBottomTab: UIStudioLeftBottomTab = UIStudioLeftBottomTab.ASSETS,
    val renamingBlockId: String? = null,
    val viewSearchQuery: String = "",
    val isNewViewDialogVisible: Boolean = false,
    val isAiChatDialogVisible: Boolean = false,
    val leftPanelWidth: Dp = 280.dp,
    val rightPanelWidth: Dp = 280.dp,
    val viewsPanelHeightRatio: Float = 0.45f,
    val isFitToViewport: Boolean = true,
    val isMattingAsset: Boolean = false,
    val mattingAssetId: String? = null,
    val autoRemoveBgOnSlice: Boolean = true,
    val expandedFolderIds: Set<String> = emptySet(),
    val renamingFolder: UIAssetFolder? = null,
    val renamingAsset: UIAssetItem? = null,
    val deletingFolder: UIAssetFolder? = null,
    val deletingAsset: UIAssetItem? = null,
    val renamingView: UIPage? = null,
    val deletingView: UIPage? = null,
    val projectAgentConfig: ProjectAgentConfig = ProjectAgentConfig(),
    val isSystemInstructionDialogVisible: Boolean = false,
    val deletingSession: VisualChatSession? = null,
    val isClearingCurrentSession: Boolean = false,
    val chatSessions: List<VisualChatSession> = emptyList(),
    val currentChatSessionId: String? = null,
    val chatSearchQuery: String = "",
    val streamingAiText: String = "",
    val aiChatAttachments: List<String> = emptyList(),
    val isAiChatSending: Boolean = false,
    val pendingCreateContainer: Pair<String, String>? = null,
    val pendingRemoveLastChildAction: Triple<String, String, Boolean>? = null,
    val pendingDragRemoveLastChild: DragRemoveLastChildContext? = null,
    val openContainerTabs: List<String> = emptyList(),
    val activeContainerId: String? = null,
    val expandedHierarchyBlockIds: Set<String> = emptySet(),
    val draggedAssetId: String? = null,
    val hoveredFolderId: String? = null
) {
    /**
     * 当前处于激活独立编辑状态的父容器组件 [UIBlock]（若处于整页模式则为 null）。
     */
    val activeContainerBlock: UIBlock?
        get() {
            val cid = activeContainerId ?: return null
            return currentView?.blocks?.findBlockById(cid)
        }
    /**
     * 获取当前处于活跃对话状态的会话单元 [VisualChatSession]。
     */
    val currentChatSession: VisualChatSession?
        get() {
            if (chatSessions.isEmpty()) return null
            val id = currentChatSessionId ?: return chatSessions.firstOrNull()
            return chatSessions.find { it.id == id } ?: chatSessions.firstOrNull()
        }
    /**
     * 获取当前处于活跃编辑状态的视图界面 [UIPage]。
     * 若所有标签已全部关闭或未指定活跃视图，则严格返回 null，清空画布渲染。
     */
    val currentView: UIPage?
        get() {
            val pages = project?.pages ?: return null
            if (pages.isEmpty() || openViewIds.isEmpty()) return null
            val viewId = currentViewId ?: return null
            return pages.find { it.id == viewId }
        }

    /**
     * 获取当前处于选中状态的图元对象 [UIBlock]。
     */
    val selectedBlock: UIBlock?
        get() {
            val id = selectedBlockId ?: return null
            return currentView?.blocks?.find { it.id == id }
        }

    /**
     * 获取当前处于选中状态的素材资产 [UIAssetItem]。
     */
    val selectedAsset: UIAssetItem?
        get() = assetLibrary.items.find { it.id == selectedAssetId }
}

/**
 * 拖拽将最后一个子组件移出父容器时的上下文信息。
 */
data class DragRemoveLastChildContext(
    val draggedId: String,
    val oldParentId: String,
    val targetId: String?,
    val position: org.gemini.ui.forge.model.ui.DropPosition
)


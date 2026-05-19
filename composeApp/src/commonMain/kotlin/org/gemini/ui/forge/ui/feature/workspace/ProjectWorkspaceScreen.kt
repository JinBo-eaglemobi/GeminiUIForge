package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.*
import org.gemini.ui.forge.ui.component.CanvasArea
import org.gemini.ui.forge.ui.component.HierarchySidebar
import org.jetbrains.compose.resources.stringResource

/**
 * 统一项目工作区主页面。
 * 将原有的“布局编辑器”与“资产生成器”合并为单一工作流。
 * 核心结构为：左侧图层树、中间交互式画布、右侧属性配置面板。
 */
@Composable
fun ProjectWorkspaceScreen(
    initialProject: ProjectState,
    initialProjectName: String,
    templateRepo: TemplateRepository,
    cloudAssetManager: CloudAssetManager,
    configManager: ConfigManager,
    effectiveApiKey: String,
    initialPromptLang: PromptLanguage,
    saveEvent: kotlinx.coroutines.flow.SharedFlow<Unit>,
    shortcutEvent: kotlinx.coroutines.flow.SharedFlow<org.gemini.ui.forge.model.app.ShortcutAction>,
    onSaveRequest: (String, ProjectState) -> Unit,
    onDirtyChanged: (Boolean) -> Unit
) {
    // 实例化核心服务与 ViewModel
    val aiService = remember { AIGenerationService(cloudAssetManager, configManager) }
    val viewModel: ProjectWorkspaceViewModel = viewModel(key = initialProjectName) {
        ProjectWorkspaceViewModel(initialProject, initialProjectName, initialPromptLang, templateRepo, cloudAssetManager, aiService, onDirtyChanged)
    }
    val state by viewModel.state.collectAsState()

    // 交互辅助状态
    var showVisualRefine by remember { mutableStateOf(false) }
    var refineTargetId by remember { mutableStateOf<String?>(null) }
    var showReferenceArea by remember { mutableStateOf(false) }
    var referenceAreaTargetId by remember { mutableStateOf<String?>(null) }
    var showHistoricalDialog by remember { mutableStateOf(false) }
    var historicalImages by remember { mutableStateOf<List<TemplateFile>>(emptyList()) }
    var blockToDelete by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 生命周期与全局事件监听
    LaunchedEffect(saveEvent) { saveEvent.collect { onSaveRequest(initialProjectName, state.project) } }
    LaunchedEffect(viewModel.requestSaveEvent) { viewModel.requestSaveEvent.collect { onSaveRequest(initialProjectName, state.project) } }
    LaunchedEffect(shortcutEvent) { 
        shortcutEvent.collect { action ->
            org.gemini.ui.forge.utils.AppLogger.d("WorkspaceScreen", "📌 收到快捷键: ${action.name}")
            if (action == org.gemini.ui.forge.model.app.ShortcutAction.DELETE) {
                if (state.selectedBlockId != null) {
                    blockToDelete = state.selectedBlockId
                } else {
                    org.gemini.ui.forge.utils.Toast.show("请先选择要删除的模块", org.gemini.ui.forge.ui.component.ToastType.INFO)
                }
            } else {
                viewModel.shortcutManager.handleAction(action) 
            }
        } 
    }
    LaunchedEffect(initialProject) { viewModel.reload(initialProject) }

    // --- 对话框组件集成 ---

    // AI 任务执行进度与日志弹窗
    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = "AI 任务执行中",
            currentStatus = state.currentTaskStatus,
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = state.isGenerationLogVisible,
            onToggleLogVisibility = { viewModel.updateState { it.copy(isGenerationLogVisible = !it.isGenerationLogVisible) } },
            onActionClick = { if (state.isGenerating) viewModel.assetGen.cancelGeneration() else viewModel.updateState { it.copy(showAITaskDialog = false) } },
            onDismiss = { viewModel.updateState { it.copy(showAITaskDialog = false) } }
        )
    }

    // 视觉区域重塑引导对话框
    if (showVisualRefine) {
        VisualRefineDialog(
            blockId = refineTargetId,
            imageUri = state.currentPage?.sourceImageUri,
            pageWidth = state.currentPage?.width ?: 1080f,
            pageHeight = state.currentPage?.height ?: 1920f,
            initialInstruction = if (refineTargetId != null) state.defaultRefineInstructionUpdate else state.defaultRefineInstructionNew,
            onDismiss = { showVisualRefine = false },
            onConfirm = { rect, instr, useChat, _, _, _ ->
                showVisualRefine = false
                viewModel.layoutEditor.onRefineArea(refineTargetId, rect, instr, effectiveApiKey, useChat) { }
            }
        )
    }

    // 历史资产选择对话框
    if (showHistoricalDialog) {
        val targetBlock = state.selectedBlock
        AssetSelectionDialog(
            title = "历史生成记录",
            candidates = historicalImages,
            onDismiss = { showHistoricalDialog = false },
            onImageSelected = { viewModel.assetManager.onImageSelected(it); showHistoricalDialog = false },
            onDeleteImages = { uris -> 
                viewModel.assetManager.deleteHistoricalImages(uris)
                historicalImages = historicalImages.filterNot { it in uris }
            },
            onClearAll = { 
                targetBlock?.id?.let { id ->
                    viewModel.assetManager.clearAllHistory(id)
                    historicalImages = emptyList()
                }
            }
        )
    }

    // 批量生成引导对话框
    if (state.showBatchGenDialog) {
        fun findAllMissing(blocks: List<UIBlock>): List<UIBlock> {
            val res = mutableListOf<UIBlock>()
            for (b in blocks) { if (b.currentImageUri == null) res.add(b); res.addAll(findAllMissing(b.children)) }
            return res
        }
        BatchAssetGenDialog(
            blocks = findAllMissing(state.currentPage?.blocks ?: emptyList()),
            onCancel = { viewModel.updateState { it.copy(showBatchGenDialog = false) } },
            onStartGen = { viewModel.assetGen.startBatchGeneration(effectiveApiKey, it) }
        )
    }

    // 历史记录面板
    if (state.showHistoryPanel) {
        HistoryPanelDialog(
            undoStack = state.undoStack,
            redoStack = state.redoStack,
            onJump = { viewModel.historyManager.jumpToHistory(it) },
            onReset = { viewModel.historyManager.clearAllHistoryAndReset(); viewModel.historyManager.toggleHistoryPanel(false) },
            onDismiss = { viewModel.historyManager.toggleHistoryPanel(false) }
        )
    }

    // --- 主 UI 布局渲染 ---
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var leftWeight by remember { mutableStateOf(0.2f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.25f) }

        Row(Modifier.fillMaxSize()) {
            // [左] 图层树面板
            Surface(Modifier.weight(leftWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                HierarchySidebar(
                    blocks = state.currentPage?.blocks ?: emptyList(),
                    selectedBlockId = state.selectedBlockId,
                    onBlockClicked = { viewModel.onBlockClicked(it) },
                    onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                    onMoveBlock = { src, target, pos -> viewModel.layoutEditor.moveBlock(src, target, pos) },
                    onToggleVisibility = { id, visible -> viewModel.layoutEditor.toggleBlockVisibility(id, visible) },
                    onToggleAllVisibility = { visible -> viewModel.layoutEditor.toggleAllBlocksVisibility(visible) },
                    onAddCustomBlock = { _, type, _, _ -> viewModel.layoutEditor.addBlock(type) },
                    onRenameBlock = { old, new -> viewModel.layoutEditor.renameBlock(old, new) },
                    renameRequestEvent = viewModel.requestRenameEvent
                )
            }

            // 侧边栏缩放条
            VerticalSplitter(onDrag = { delta ->
                val deltaWeight = delta / totalWidthPx
                leftWeight = (leftWeight + deltaWeight).coerceIn(0.1f, 0.4f)
                centerWeight = 1.0f - leftWeight - rightWeight
            })

            // [中] 核心画布区域
            Box(Modifier.weight(centerWeight).fillMaxHeight()) {
                CanvasArea(
                    blocks = state.currentPage?.blocks ?: emptyList(),
                    pageWidth = state.currentPage?.width ?: 1080f,
                    pageHeight = state.currentPage?.height ?: 1920f,
                    selectedBlockId = state.selectedBlockId,
                    editingGroupId = state.editingGroupId,
                    isVisualMode = state.isVisualMode,
                    onToggleVisualMode = { viewModel.updateState { s -> s.copy(isVisualMode = !s.isVisualMode) } },
                    isHideOutlines = state.isHideOutlines,
                    onToggleHideOutlines = { viewModel.updateState { s -> s.copy(isHideOutlines = !s.isHideOutlines) } },
                    referenceMode = state.referenceMode,
                    onReferenceModeChange = { mode -> viewModel.updateState { s -> s.copy(referenceMode = mode) } },
                    referenceUri = state.referenceImageUri?.getAbsolutePath(),
                    referenceOpacity = state.referenceOpacity,
                    onReferenceOpacityChange = { opacity -> viewModel.updateState { s -> s.copy(referenceOpacity = opacity) } },
                    onBlockClicked = { viewModel.onBlockClicked(it) },
                    onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                    onBlockDragStart = { viewModel.historyManager.saveSnapshot("拖动模块位置") },
                    onBlockDragged = { id, dx, dy -> viewModel.layoutEditor.moveBlockBy(id, dx, dy) },
                    onExitGroupEdit = { viewModel.updateState { s -> s.copy(editingGroupId = null) } },
                    stageBackgroundColor = state.stageBackgroundColor
                )

                // 悬浮历史入口
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    FilledTonalIconButton(
                        onClick = { viewModel.historyManager.toggleHistoryPanel(true) },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).size(40.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(Icons.Default.History, "操作历史", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 属性面板缩放条
            VerticalSplitter(onDrag = { delta ->
                val deltaWeight = delta / totalWidthPx
                rightWeight = (rightWeight - deltaWeight).coerceIn(0.2f, 0.4f)
                centerWeight = 1.0f - leftWeight - rightWeight
            })

            // [右] 属性面板
            Surface(Modifier.weight(rightWeight).fillMaxHeight()) {
                UnifiedPropertyPanel(
                    state = state,
                    viewModel = viewModel,
                    apiKey = effectiveApiKey,
                    onRefineClick = { refineTargetId = it; showVisualRefine = true },
                    onSetReferenceAreaClick = { id -> referenceAreaTargetId = id; showReferenceArea = true },
                    onShowHistory = { id -> 
                        coroutineScope.launch {
                            historicalImages = viewModel.assetManager.loadHistoricalImages(id)
                            showHistoricalDialog = true
                        }
                    },
                    onDeleteRequest = { blockToDelete = it }
                )
            }
        }
        
        // 删除确认对话框
        if (blockToDelete != null) {
            AlertDialog(
                onDismissRequest = { blockToDelete = null },
                title = { Text("确认删除") },
                text = { Text("是否永久删除模块 $blockToDelete 及其所有子模块？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.layoutEditor.deleteBlock(blockToDelete!!)
                            blockToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { blockToDelete = null }) { Text("取消") }
                }
            )
        }
        
        // 参考区域截图对话框
        if (showReferenceArea && state.currentPage?.sourceImageUri != null) {
            ReferenceAreaCropDialog(
                blockId = referenceAreaTargetId ?: "",
                imageUri = state.currentPage!!.sourceImageUri!!,
                pageWidth = state.currentPage!!.width,
                pageHeight = state.currentPage!!.height,
                onDismiss = { showReferenceArea = false },
                onConfirm = { rect ->
                    showReferenceArea = false
                    referenceAreaTargetId?.let { blockId ->
                        viewModel.layoutEditor.onSetReferenceArea(blockId, rect)
                    }
                }
            )
        }
    }
}

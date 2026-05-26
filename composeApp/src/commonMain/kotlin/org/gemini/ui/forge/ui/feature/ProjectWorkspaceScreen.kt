package org.gemini.ui.forge.ui.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.CanvasArea
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.gemini.ui.forge.state.app.AppState
import org.gemini.ui.forge.state.app.AppGlobalState
import org.gemini.ui.forge.ui.component.HierarchySidebar
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.dialog.AppConfirmDialog
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.*
import org.gemini.ui.forge.ui.feature.workspace.UnifiedPropertyPanel
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * 统一项目工作区主页面。
 * 将原有的“布局编辑器”与“资产生成器”合并为单一工作流。
 * 核心结构为：左侧图层树、中间交互式画布、右侧属性配置面板。
 */
@Composable
fun ProjectWorkspaceScreen(
    appViewModel: AppViewModel,
    appState: AppState,
    globalState: AppGlobalState,
    templateRepo: TemplateRepository,
    configManager: ConfigManager
) {
    // 实例化核心 ViewModel
    val viewModel: ProjectWorkspaceViewModel = viewModel(key = appState.projectName) {
        ProjectWorkspaceViewModel(
            initialProject = appState.project,
            initialProjectName = appState.projectName,
            initialLang = globalState.promptLangPref,
            templateRepo = templateRepo,
            cloudAssetManager = appViewModel.cloudAssetManager,
            aiService = appViewModel.aiService,
            onDirtyChanged = { appViewModel.setDirty(it) }
        )
    }
    val state by viewModel.state.collectAsState()

    // 交互辅助状态
    var showImageEditorForBlock by remember { mutableStateOf<Pair<UIBlock, TemplateFile>?>(null) }
    var showProjectSettingsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 键盘修饰键状态（多选判定）
    var isShiftPressed by remember { mutableStateOf(false) }
    var isCtrlPressed by remember { mutableStateOf(false) }

    // 生命周期与全局事件监听
    LaunchedEffect(appViewModel.saveEvent) {
        appViewModel.saveEvent.collect {
            appViewModel.saveProject(appState.projectName, state.project)
        }
    }
    LaunchedEffect(viewModel.requestSaveEvent) {
        viewModel.requestSaveEvent.collect {
            appViewModel.saveProject(appState.projectName, state.project)
        }
    }
    LaunchedEffect(appViewModel.projectSettingsEvent) {
        appViewModel.projectSettingsEvent.collect {
            showProjectSettingsDialog = true
        }
    }
    LaunchedEffect(appViewModel.shortcutEvent) { 
        appViewModel.shortcutEvent.collect { action ->
            AppLogger.d("WorkspaceScreen", "📌 收到快捷键: ${action.name}")
            when (action) {
                ShortcutAction.DELETE -> {
                    if (state.selectedBlockIds.size > 1) {
                        viewModel.historyManager.saveSnapshot("批量删除模块")
                        state.selectedBlockIds.forEach { viewModel.layoutEditor.deleteBlock(it) }
                        viewModel.onBlockClicked(null)
                    } else {
                        state.selectedBlockId?.let { selectedId ->
                            viewModel.showDeleteConfirmation(selectedId)
                        } ?: run {
                            Toast.show("请先选择要删除的模块", ToastType.INFO)
                        }
                    }
                }
                ShortcutAction.MOVE_UP -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 0f, -1f)
                ShortcutAction.MOVE_UP_FAST -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 0f, -10f)
                ShortcutAction.MOVE_DOWN -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 0f, 1f)
                ShortcutAction.MOVE_DOWN_FAST -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 0f, 10f)
                ShortcutAction.MOVE_LEFT -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, -1f, 0f)
                ShortcutAction.MOVE_LEFT_FAST -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, -10f, 0f)
                ShortcutAction.MOVE_RIGHT -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 1f, 0f)
                ShortcutAction.MOVE_RIGHT_FAST -> viewModel.layoutEditor.moveBlocksBy(state.selectedBlockIds, 10f, 0f)
                else -> viewModel.shortcutManager.handleAction(action)
            }
        } 
    }
    LaunchedEffect(appState.project) { viewModel.reload(appState.project) }

    // --- 对话框组件集成 ---

    // 项目设置弹窗
    if (showProjectSettingsDialog) {
        ProjectSettingsDialog(
            initialPath = state.resourceConfigPath,
            onDismiss = { showProjectSettingsDialog = false },
            onConfirm = { path ->
                viewModel.updateResourceConfigPath(path)
                showProjectSettingsDialog = false
            }
        )
    }

    // AI 任务执行进度与日志弹窗
    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = "AI 任务执行中",
            currentStatus = state.currentTaskStatus,
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = state.isGenerationLogVisible,
            onToggleLogVisibility = { viewModel.updateState { it.copy(isGenerationLogVisible = !it.isGenerationLogVisible) } },
            onActionClick = { 
                if (state.isGenerating) {
                    viewModel.assetGen.cancelGeneration() 
                } else {
                    viewModel.updateState { it.copy(showAITaskDialog = false) }
                    state.selectedBlock?.id?.let { blockId ->
                        viewModel.showHistoricalDialog(blockId)
                    }
                }
            },
            onDismiss = { 
                viewModel.updateState { it.copy(showAITaskDialog = false) }
                state.selectedBlock?.id?.let { blockId ->
                    viewModel.showHistoricalDialog(blockId)
                }
            }
        )
    }

    // 视觉区域重塑引导对话框
    if (state.showVisualRefine) {
        VisualRefineDialog(
            viewModel = viewModel,
            state = state,
            apiKey = globalState.effectiveApiKey
        )
    }

    // 历史资产选择对话框
    if (state.showHistoricalDialog) {
        val targetBlock = state.selectedBlock
        AssetSelectionDialog(
            title = "历史生成记录",
            candidates = state.historicalImages,
            targetWidth = targetBlock?.bounds?.width ?: 0f,
            targetHeight = targetBlock?.bounds?.height ?: 0f,
            onDismiss = { viewModel.hideHistoricalDialog() },
            onImageSelected = { selectedFile -> 
                val targetId = state.historicalTargetBlockId ?: targetBlock?.id
                if (targetId != null) {
                    viewModel.assetManager.onHistoricalImageSelected(targetId, selectedFile)
                } else {
                    viewModel.assetManager.onImageSelected(selectedFile)
                }
                viewModel.hideHistoricalDialog() 
            },
            onCropRequested = { selectedFile ->
                viewModel.assetManager.onImageSelected(selectedFile)
                viewModel.hideHistoricalDialog()
                if (targetBlock != null) {
                    showImageEditorForBlock = targetBlock to selectedFile
                }
            },
            onDeleteImages = { uris -> 
                viewModel.assetManager.deleteHistoricalImages(uris)
                viewModel.updateState { it.copy(historicalImages = it.historicalImages.filterNot { img -> img in uris }) }
            },
            onClearAll = { 
                targetBlock?.id?.let { id ->
                    viewModel.assetManager.clearAllHistory(id)
                    viewModel.updateState { it.copy(historicalImages = emptyList()) }
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
            onStartGen = { viewModel.assetGen.startBatchGeneration(globalState.effectiveApiKey, it) }
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
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    isShiftPressed = event.keyboardModifiers.isShiftPressed
                    isCtrlPressed = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                }
            }
        }
    ) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var leftWeight by remember { mutableStateOf(0.2f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.25f) }

        Row(Modifier.fillMaxSize()) {
            // [左] 图层树面板
            Surface(Modifier.weight(leftWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                HierarchySidebar(
                    state = state,
                    viewModel = viewModel
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
                    state = state,
                    viewModel = viewModel
                )

                // 悬浮历史入口
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    FilledTonalIconButton(
                        onClick = { viewModel.historyManager.toggleHistoryPanel(true) },
                        modifier = Modifier.align(Alignment.TopStart).size(40.dp),
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
                    apiKey = globalState.effectiveApiKey
                )
            }
        }
        
        // 删除确认对话框
        val pendingId = state.pendingDeleteBlockId
        if (state.showDeleteBlockConfirmation && pendingId != null) {
            AppConfirmDialog(
                title = "确认删除",
                message = "是否永久删除模块 $pendingId 及其所有子模块？此操作无法撤销。",
                confirmText = "确认删除",
                isDestructive = true,
                onConfirm = {
                    viewModel.layoutEditor.deleteBlock(pendingId)
                    viewModel.hideDeleteConfirmation()
                },
                onDismiss = { viewModel.hideDeleteConfirmation() }
            )
        }
        
        // 参考区域截图对话框
        if (state.showReferenceArea && state.currentPage?.sourceImageUri != null) {
            ReferenceAreaCropDialog(
                blockId = state.referenceAreaTargetId ?: "",
                imageUri = state.currentPage!!.sourceImageUri!!,
                pageWidth = state.currentPage!!.width,
                pageHeight = state.currentPage!!.height,
                onDismiss = { viewModel.hideReferenceArea() },
                onConfirm = { rect ->
                    val blockId = state.referenceAreaTargetId
                    viewModel.hideReferenceArea()
                    blockId?.let { id ->
                        viewModel.layoutEditor.onSetReferenceArea(id, rect)
                    }
                }
            )
        }

        // 历史记录应用不合规时弹出的切图/烘焙对话框
        if (showImageEditorForBlock != null) {
            val (block, file) = showImageEditorForBlock!!
            ImageEditorDialog(
                block = block,
                initialImageUri = file.getAbsolutePath(),
                onDismiss = { showImageEditorForBlock = null },
                onConfirm = { bytes, mode, config, cropBytes ->
                    viewModel.assetManager.bakeBlockImage(
                        block.id,
                        mode,
                        config,
                        block.bounds.width.toInt(),
                        block.bounds.height.toInt(),
                        block.bounds.width.toInt(),
                        block.bounds.height.toInt(),
                        bytes,
                        cropBytes
                    )
                    showImageEditorForBlock = null
                }
            )
        }
    }
}

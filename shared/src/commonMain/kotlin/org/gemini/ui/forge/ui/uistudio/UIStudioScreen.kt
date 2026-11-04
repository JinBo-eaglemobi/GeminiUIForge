package org.gemini.ui.forge.ui.uistudio

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import org.gemini.ui.forge.ui.component.dialog.AppDraggableAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.chat.UIProjectAiStudioDialog
import org.gemini.ui.forge.ui.uistudio.component.*
import org.gemini.ui.forge.ui.uistudio.dialog.*
import org.gemini.ui.forge.ui.uistudio.verify.AssetSliceVerifyDialog
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import geminiuiforge.composeapp.generated.resources.*

/**
 * 全新独立的 UI Studio 主工作台容器界面。
 *
 * 遵循一文件一 Composable 规范，聚合三栏双向自由拖拽工作台布局：
 * - 顶部 TopBar 控制条
 * - 左侧复合面板 LeftSidebar（上：视图界面列表 Views | 中：水平手柄 | 下：素材库 Assets）
 * - 左侧纵向拖拽分割线 VerticalSplitter（支持自由拖拽左侧宽度）
 * - 中部核心交互画布 CanvasArea（自适应占据剩余空间，默认自适应居中，悬浮 AI Chat 按钮）
 * - 右侧纵向拖拽分割线 VerticalSplitter（支持自由拖拽右侧宽度）
 * - 右侧图元属性 Inspector 面板 PropertyPanel
 */
@Composable
fun UIStudioScreen(
    appViewModel: AppViewModel,
    templateRepository: TemplateRepository = remember { TemplateRepository() },
    modifier: Modifier = Modifier
) {
    val projectState = appViewModel.state.value.project
    val viewModel = remember(projectState.projectId) {
        UIStudioViewModel()
    }
    val state = viewModel.state
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val focusRequester = remember { FocusRequester() }
    var pendingSliceItems by remember { mutableStateOf<List<NormalizedAssetItem>?>(null) }
    var isSaveLocationDialogOpen by remember { mutableStateOf(false) }
    var customSliceSubPath by remember { mutableStateOf("") }

    val currentTargetFolderDisplayName = remember(customSliceSubPath, state.selectedFolderId, state.assetLibrary.folders) {
        if (customSliceSubPath.isNotBlank()) {
            customSliceSubPath
        } else {
            val f = state.assetLibrary.folders.find { it.id == state.selectedFolderId }
            f?.name ?: "根目录"
        }
    }

    DisposableEffect(viewModel) {
        org.gemini.ui.forge.service.mcp.McpUiActionPipeline.registerActiveUiStudioViewModel(viewModel)
        onDispose {
            org.gemini.ui.forge.service.mcp.McpUiActionPipeline.unregisterActiveUiStudioViewModel(viewModel)
        }
    }

    LaunchedEffect(projectState) {
        viewModel.loadProject(projectState)
        viewModel.checkDraft()
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // 收集全局快捷键中枢分发的事件
    LaunchedEffect(Unit) {
        appViewModel.shortcutEvent.collect { action ->
            when (action) {
                ShortcutAction.UNDO -> viewModel.canvasEditor.undo()
                ShortcutAction.REDO -> viewModel.canvasEditor.redo()
                ShortcutAction.SAVE -> viewModel.saveProject(templateRepository)
                ShortcutAction.COPY -> viewModel.canvasEditor.copySelectedBlock()
                ShortcutAction.PASTE -> viewModel.canvasEditor.pasteBlock(templateRepository)
                ShortcutAction.CUT -> viewModel.canvasEditor.cutSelectedBlock(templateRepository)
                ShortcutAction.DELETE -> {
                    val selId = viewModel.state.selectedBlockId
                    if (selId != null) viewModel.canvasEditor.deleteSelectedBlock(selId, templateRepository)
                }
                ShortcutAction.MOVE_UP -> viewModel.canvasEditor.nudgeSelectedBlock(0f, -1f)
                ShortcutAction.MOVE_UP_FAST -> viewModel.canvasEditor.nudgeSelectedBlock(0f, -10f)
                ShortcutAction.MOVE_DOWN -> viewModel.canvasEditor.nudgeSelectedBlock(0f, 1f)
                ShortcutAction.MOVE_DOWN_FAST -> viewModel.canvasEditor.nudgeSelectedBlock(0f, 10f)
                ShortcutAction.MOVE_LEFT -> viewModel.canvasEditor.nudgeSelectedBlock(-1f, 0f)
                ShortcutAction.MOVE_LEFT_FAST -> viewModel.canvasEditor.nudgeSelectedBlock(-10f, 0f)
                ShortcutAction.MOVE_RIGHT -> viewModel.canvasEditor.nudgeSelectedBlock(1f, 0f)
                ShortcutAction.MOVE_RIGHT_FAST -> viewModel.canvasEditor.nudgeSelectedBlock(10f, 0f)
                ShortcutAction.RENAME -> {
                    val selId = viewModel.state.selectedBlockId
                    if (selId != null) viewModel.canvasEditor.startRenameBlock(selId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            UIStudioTopBar(
                viewModel = viewModel,
                templateRepo = templateRepository,
                onBackToHome = {
                    viewModel.requestExit(onDirectExit = {
                        appViewModel.navigateTo(AppScreen.HOME)
                    })
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val isCtrl = event.isCtrlPressed || event.isMetaPressed
                val isShift = event.isShiftPressed
                val isMac = hostOs.isMacOS || hostOs == OS.Ios
                val isDeleteKey = if (isMac) event.key == Key.Delete || event.key == Key.Backspace else event.key == Key.Delete

                when {
                    // Ctrl+Z / Cmd+Z: 撤销
                    isCtrl && !isShift && event.key == Key.Z -> {
                        viewModel.canvasEditor.undo()
                        true
                    }
                    // Ctrl+Y / Ctrl+Shift+Z / Cmd+Shift+Z: 重做
                    (isCtrl && event.key == Key.Y) || (isCtrl && isShift && event.key == Key.Z) -> {
                        viewModel.canvasEditor.redo()
                        true
                    }
                    // Ctrl+S / Cmd+S: 保存
                    isCtrl && event.key == Key.S -> {
                        viewModel.saveProject(templateRepository)
                        true
                    }
                    // Ctrl+C: 复制组件
                    isCtrl && event.key == Key.C -> {
                        viewModel.canvasEditor.copySelectedBlock()
                        true
                    }
                    // Ctrl+V: 粘贴组件
                    isCtrl && event.key == Key.V -> {
                        viewModel.canvasEditor.pasteBlock(templateRepository)
                        true
                    }
                    // Ctrl+X: 剪切组件
                    isCtrl && event.key == Key.X -> {
                        viewModel.canvasEditor.cutSelectedBlock(templateRepository)
                        true
                    }
                    // Ctrl+D: 快速克隆组件
                    isCtrl && event.key == Key.D -> {
                        viewModel.canvasEditor.duplicateSelectedBlock(templateRepository)
                        true
                    }
                    // F2: 重命名组件
                    event.key == Key.F2 -> {
                        val selId = viewModel.state.selectedBlockId
                        if (selId != null) {
                            viewModel.canvasEditor.startRenameBlock(selId)
                            true
                        } else false
                    }
                    // Delete: 删除图元（冒泡到此处说明无文本输入框消费此按键）
                    isDeleteKey && !isCtrl -> {
                        val selId = viewModel.state.selectedBlockId
                        if (selId != null && viewModel.state.renamingBlockId == null && viewModel.state.renamingView == null) {
                            viewModel.canvasEditor.deleteSelectedBlock(selId, templateRepository)
                            true
                        } else false
                    }
                    // 方向键平移组件
                    event.key == Key.DirectionUp -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.canvasEditor.nudgeSelectedBlock(0f, if (isShift) -10f else -1f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionDown -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.canvasEditor.nudgeSelectedBlock(0f, if (isShift) 10f else 1f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionLeft -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.canvasEditor.nudgeSelectedBlock(if (isShift) -10f else -1f, 0f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionRight -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.canvasEditor.nudgeSelectedBlock(if (isShift) 10f else 1f, 0f)
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val totalContainerWidth = maxWidth

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { containerCoords = it }
            ) {
                // 左侧复合侧栏（上：视图 Views | 下：素材库 Assets，可上下拖拽高度）
                if (state.isAssetPanelVisible) {
                    UIStudioLeftSidebar(
                        viewModel = viewModel,
                        templateRepo = templateRepository,
                        modifier = Modifier
                            .width(state.leftPanelWidth)
                            .fillMaxHeight()
                    )

                    // 左侧纵向拖拽分割线（官方 draggable 封装，毫秒级无损平滑响应）
                    VerticalSplitter(
                        onDrag = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp() }
                            viewModel.viewport.updateLeftPanelWidth(
                                viewModel.state.leftPanelWidth + deltaDp,
                                totalContainerWidth = totalContainerWidth
                            )
                        },
                        onDragEnd = {
                            viewModel.viewport.saveWorkspaceLayout()
                        }
                    )
                }

                // 中部核心交互画布与顶部多标签页
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 顶部多页面标签栏
                    UIStudioPageTabBar(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 核心交互画布
                    UIStudioCanvasArea(
                        viewModel = viewModel,
                        assetPathResolver = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.relativePath },
                        assetUpdatedAtResolver = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.updatedAt },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // 右侧纵向拖拽分割线与右侧边栏（受 isRightSidebarVisible 状态控制）
                if (state.isRightSidebarVisible) {
                    VerticalSplitter(
                        onDrag = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp() }
                            viewModel.viewport.updateRightPanelWidth(
                                viewModel.state.rightPanelWidth - deltaDp,
                                totalContainerWidth = totalContainerWidth
                            )
                        },
                        onDragEnd = {
                            viewModel.viewport.saveWorkspaceLayout()
                        }
                    )

                    // 右侧复合边栏（属性检查面板 / AI 对话面板，可自由拖拽宽度与上下高度）
                    UIStudioRightSidebar(
                        viewModel = viewModel,
                        templateRepo = templateRepository,
                        modifier = Modifier
                            .width(state.rightPanelWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // 挂载新建视图界面弹窗
        if (state.isNewViewDialogVisible) {
            NewViewDialog(
                defaultWidth = state.canvasWidth,
                defaultHeight = state.canvasHeight,
                onDismiss = { viewModel.pageManager.closeNewViewDialog() },
                onConfirm = { name, w, h ->
                    viewModel.pageManager.createView(name, w, h, templateRepository)
                }
            )
        }

        // 挂载新建素材文件夹弹窗
        if (state.isNewFolderDialogVisible) {
            NewFolderDialog(
                onDismiss = { viewModel.assetLibrary.closeNewFolderDialog() },
                onConfirm = { name ->
                    viewModel.assetLibrary.createAssetFolder(name)
                }
            )
        }

        // 挂载项目级 AI Studio 智能工作台全景弹窗 (0.92f 视口规格)
        if (state.isAiChatDialogVisible) {
            UIProjectAiStudioDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.pageManager.closeAiChatDialog() }
            )
        }

        // 挂载未完成切片草稿恢复对话框
        if (state.isDraftPromptVisible) {
            val draft = state.importDraft
            if (draft != null) {
                AssetImportDraftDialog(
                    draft = draft,
                    onResume = { viewModel.resumeDraft() },
                    onDiscard = { viewModel.discardDraft() }
                )
            }
        }

        // 挂载导入图集配置与解析预览弹窗 (阶段二)
        if (state.isImportSheetDialogVisible) {
            ImportAssetSheetDialog(
                viewModel = viewModel,
                onProceedToVerify = {
                    viewModel.sliceImport.proceedToVerifyDialog()
                }
            )
        }

        // 挂载切图前交互式预审与微调弹窗 (阶段三)
        if (state.isVerifySliceDialogVisible) {
            AssetSliceVerifyDialog(
                visible = state.isVerifySliceDialogVisible,
                imagePath = state.sourceImagePath,
                initialItems = state.verifySliceItems,
                autoRemoveBg = state.autoRemoveBgOnSlice,
                onToggleAutoRemoveBg = { viewModel.assetLibrary.updateAutoRemoveBgOnSlice(it) },
                onDismiss = { viewModel.sliceImport.closeVerifySliceDialog() },
                onConfirmSlice = { items ->
                    pendingSliceItems = items
                },
                onSaveDraft = { items -> viewModel.saveDraft(items) },
                mattingSourceImage = { bytes, onProgress -> viewModel.sliceImport.mattingSourceImage(bytes, onProgress) },
                targetFolderDisplayName = currentTargetFolderDisplayName,
                onSelectTargetFolder = { isSaveLocationDialogOpen = true }
            )
        }

        // 挂载确认切片前置保存位置弹窗 (v2：支持顶栏随时点击修改与确认时二次弹窗)
        if (isSaveLocationDialogOpen || pendingSliceItems != null) {
            SaveChatImageToAssetDialog(
                isOpen = true,
                initialFileName = "",
                folders = state.assetLibrary.folders,
                showFileNameField = false,
                titleText = stringResource(Res.string.slice_save_dialog_title),
                showRemoveBgOption = !viewModel.sliceImport.isPreMattingApplied,
                initialRemoveBgChecked = true,
                onDismissRequest = {
                    isSaveLocationDialogOpen = false
                    pendingSliceItems = null
                },
                onConfirmSaveWithOptions = { _, subPath, removeBgAfterSlice ->
                    customSliceSubPath = subPath
                    val selectedFolderId = if (subPath.isNotBlank()) {
                        state.assetLibrary.folders.find { it.name.equals(subPath, ignoreCase = true) }?.id
                    } else null

                    val pending = pendingSliceItems
                    if (pending != null) {
                        viewModel.sliceImport.onConfirmSlice(
                            items = pending,
                            repo = templateRepository,
                            onProjectUpdated = { updatedProject ->
                                appViewModel.loadProject(updatedProject.projectId, updatedProject)
                            },
                            targetSubPath = subPath,
                            targetFolderId = selectedFolderId,
                            removeBgAfterSlice = removeBgAfterSlice
                        )
                        pendingSliceItems = null
                    }
                    isSaveLocationDialogOpen = false
                }
            )
        }

        // 挂载切片中全景进度遮罩 (阶段四)
        if (state.isSlicingAssets) {
            AssetSlicingProgressOverlay(
                progress = state.sliceProgress,
                statusText = state.sliceStatusText,
                stage = state.sliceCurrentStage,
                taskItems = state.sliceTaskItems,
                currentItemName = state.sliceCurrentItemName,
                startTimeMillis = state.sliceStartTimeMillis
            )
        }

        // 顶层全局悬浮拖拽光标跟随镜像 (Drag Ghost Badge)
        val draggedAsset = remember(state.draggedAssetHash, state.assetLibrary.items) {
            state.assetLibrary.items.find { it.hash == state.draggedAssetHash }
        }
        UIStudioDragGhostBadge(
            asset = draggedAsset,
            cursorPos = state.dragCursorPosition
        )

        // 挂载最大工具轮询续轮确认弹窗
        if (state.showMaxToolRoundsContinueDialog) {
            AppDraggableAlertDialog(
                onDismissRequest = { viewModel.aiAgent.cancelToolLoop() },
                modifier = Modifier.width(LocalAppSpacing.current.dialogAlertWidth),
                title = { Text("工具轮询上限提醒") },
                text = {
                    Text(
                        "AI Agent 已达到当前单次最大轮询上限（${state.currentToolLoopRound} 轮），当前仍在全力推演中。\n\n是否继续追加 ${state.maxToolLoopRoundLimit} 轮继续推演？"
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.aiAgent.continueToolLoop() }) {
                        Text("继续执行 (+${state.maxToolLoopRoundLimit}轮)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.aiAgent.cancelToolLoop() }) {
                        Text("停止推演")
                    }
                }
            )
        }

        // 挂载退出未保存确认弹窗 (三选：保存并退出 / 不保存退出 / 取消)
        if (state.showExitConfirmDialog) {
            AppDraggableAlertDialog(
                onDismissRequest = { viewModel.dismissExitDialog() },
                modifier = Modifier.width(LocalAppSpacing.current.dialogConfigWidth),
                title = { Text("提醒") },
                text = { Text("项目有未保存的修改或视口调整，是否保存后退出？") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.saveProject(templateRepository)
                        viewModel.dismissExitDialog()
                        appViewModel.navigateTo(AppScreen.HOME)
                    }) {
                        Text("保存并退出")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            viewModel.dismissExitDialog()
                            appViewModel.navigateTo(AppScreen.HOME)
                        }) {
                            Text("不保存退出")
                        }
                        TextButton(onClick = { viewModel.dismissExitDialog() }) {
                            Text("取消")
                        }
                    }
                }
            )
        }

        // 挂载 AI 生成界面排版应用确认弹窗 (当 AI 全景弹窗关闭时的全局宿主兜底)
        val pendingLayout = state.pendingLayoutResult
        if (pendingLayout != null && !state.isAiChatDialogVisible) {
            ApplyLayoutConfirmDialog(
                layoutResult = pendingLayout,
                currentViewName = state.currentView?.nameStr ?: "当前视图",
                existingViewNames = state.project?.studioPages?.map { it.nameStr } ?: emptyList(),
                onDismiss = { viewModel.aiLayout.cancelApplyLayout() },
                onConfirm = { replaceCurrent, customName ->
                    viewModel.aiLayout.confirmApplyLayout(replaceCurrent, customName)
                }
            )
        }
    }
}

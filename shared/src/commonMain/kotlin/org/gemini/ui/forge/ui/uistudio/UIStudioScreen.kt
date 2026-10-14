package org.gemini.ui.forge.ui.uistudio

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
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
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.chat.UIProjectAiStudioDialog
import org.gemini.ui.forge.ui.uistudio.component.*
import org.gemini.ui.forge.ui.uistudio.dialog.*
import org.gemini.ui.forge.ui.uistudio.verify.AssetSliceVerifyDialog
import org.gemini.ui.forge.viewmodel.AppViewModel

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
                ShortcutAction.UNDO -> viewModel.undo()
                ShortcutAction.REDO -> viewModel.redo()
                ShortcutAction.SAVE -> viewModel.saveProject(templateRepository)
                ShortcutAction.COPY -> viewModel.copySelectedBlock()
                ShortcutAction.PASTE -> viewModel.pasteBlock(templateRepository)
                ShortcutAction.CUT -> viewModel.cutSelectedBlock(templateRepository)
                ShortcutAction.DELETE -> {
                    val selId = viewModel.state.selectedBlockId
                    if (selId != null) viewModel.deleteSelectedBlock(selId, templateRepository)
                }
                ShortcutAction.MOVE_UP -> viewModel.nudgeSelectedBlock(0f, -1f)
                ShortcutAction.MOVE_UP_FAST -> viewModel.nudgeSelectedBlock(0f, -10f)
                ShortcutAction.MOVE_DOWN -> viewModel.nudgeSelectedBlock(0f, 1f)
                ShortcutAction.MOVE_DOWN_FAST -> viewModel.nudgeSelectedBlock(0f, 10f)
                ShortcutAction.MOVE_LEFT -> viewModel.nudgeSelectedBlock(-1f, 0f)
                ShortcutAction.MOVE_LEFT_FAST -> viewModel.nudgeSelectedBlock(-10f, 0f)
                ShortcutAction.MOVE_RIGHT -> viewModel.nudgeSelectedBlock(1f, 0f)
                ShortcutAction.MOVE_RIGHT_FAST -> viewModel.nudgeSelectedBlock(10f, 0f)
                ShortcutAction.RENAME -> {
                    val selId = viewModel.state.selectedBlockId
                    if (selId != null) viewModel.startRenameBlock(selId)
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
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isCtrl = event.isCtrlPressed || event.isMetaPressed
                val isShift = event.isShiftPressed

                when {
                    // Ctrl+Z / Cmd+Z: 撤销
                    isCtrl && !isShift && event.key == Key.Z -> {
                        viewModel.undo()
                        true
                    }
                    // Ctrl+Y / Ctrl+Shift+Z / Cmd+Shift+Z: 重做
                    (isCtrl && event.key == Key.Y) || (isCtrl && isShift && event.key == Key.Z) -> {
                        viewModel.redo()
                        true
                    }
                    // Ctrl+S / Cmd+S: 保存
                    isCtrl && event.key == Key.S -> {
                        viewModel.saveProject(templateRepository)
                        true
                    }
                    // Ctrl+C: 复制组件
                    isCtrl && event.key == Key.C -> {
                        viewModel.copySelectedBlock()
                        true
                    }
                    // Ctrl+V: 粘贴组件
                    isCtrl && event.key == Key.V -> {
                        viewModel.pasteBlock(templateRepository)
                        true
                    }
                    // Ctrl+X: 剪切组件
                    isCtrl && event.key == Key.X -> {
                        viewModel.cutSelectedBlock(templateRepository)
                        true
                    }
                    // Ctrl+D: 快速克隆组件
                    isCtrl && event.key == Key.D -> {
                        viewModel.duplicateSelectedBlock(templateRepository)
                        true
                    }
                    // F2: 重命名组件
                    event.key == Key.F2 -> {
                        val selId = viewModel.state.selectedBlockId
                        if (selId != null) {
                            viewModel.startRenameBlock(selId)
                            true
                        } else false
                    }
                    // Delete / Backspace: 删除图元（无输入框获焦时）
                    (event.key == Key.Delete || event.key == Key.Backspace) && !isCtrl -> {
                        val selId = viewModel.state.selectedBlockId
                        if (selId != null && viewModel.state.renamingBlockId == null && viewModel.state.renamingView == null) {
                            viewModel.deleteSelectedBlock(selId, templateRepository)
                            true
                        } else false
                    }
                    // 方向键平移组件
                    event.key == Key.DirectionUp -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.nudgeSelectedBlock(0f, if (isShift) -10f else -1f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionDown -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.nudgeSelectedBlock(0f, if (isShift) 10f else 1f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionLeft -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.nudgeSelectedBlock(if (isShift) -10f else -1f, 0f)
                            true
                        } else false
                    }
                    event.key == Key.DirectionRight -> {
                        if (viewModel.state.selectedBlockId != null) {
                            viewModel.nudgeSelectedBlock(if (isShift) 10f else 1f, 0f)
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
                            viewModel.updateLeftPanelWidth(
                                viewModel.state.leftPanelWidth + deltaDp,
                                totalContainerWidth = totalContainerWidth
                            )
                        },
                        onDragEnd = {
                            viewModel.saveWorkspaceLayout()
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // 右侧纵向拖拽分割线（官方 draggable 封装，毫秒级无损平滑响应）
                VerticalSplitter(
                    onDrag = { deltaPx ->
                        val deltaDp = with(density) { deltaPx.toDp() }
                        viewModel.updateRightPanelWidth(
                            viewModel.state.rightPanelWidth - deltaDp,
                            totalContainerWidth = totalContainerWidth
                        )
                    },
                    onDragEnd = {
                        viewModel.saveWorkspaceLayout()
                    }
                )

                // 右侧复合边栏（属性检查面板 + 独立高保真实时预览面板，可自由拖拽宽度与上下高度）
                UIStudioRightSidebar(
                    viewModel = viewModel,
                    templateRepo = templateRepository,
                    modifier = Modifier
                        .width(state.rightPanelWidth)
                        .fillMaxHeight()
                )
            }
        }

        // 挂载新建视图界面弹窗
        if (state.isNewViewDialogVisible) {
            NewViewDialog(
                defaultWidth = state.canvasWidth,
                defaultHeight = state.canvasHeight,
                onDismiss = { viewModel.closeNewViewDialog() },
                onConfirm = { name, w, h ->
                    viewModel.createView(name, w, h, templateRepository)
                }
            )
        }

        // 挂载新建素材文件夹弹窗
        if (state.isNewFolderDialogVisible) {
            NewFolderDialog(
                onDismiss = { viewModel.closeNewFolderDialog() },
                onConfirm = { name ->
                    viewModel.createAssetFolder(name)
                }
            )
        }

        // 挂载项目级 AI Studio 智能工作台全景弹窗 (0.92f 视口规格)
        if (state.isAiChatDialogVisible) {
            UIProjectAiStudioDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeAiChatDialog() }
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
                    viewModel.proceedToVerifyDialog()
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
                onToggleAutoRemoveBg = { viewModel.updateAutoRemoveBgOnSlice(it) },
                onDismiss = { viewModel.closeVerifySliceDialog() },
                onConfirmSlice = { items ->
                    viewModel.onConfirmSlice(
                        items = items,
                        repo = templateRepository,
                        onProjectUpdated = { updatedProject ->
                            appViewModel.loadProject(updatedProject.projectId, updatedProject)
                        }
                    )
                },
                onSaveDraft = { items -> viewModel.saveDraft(items) }
            )
        }

        // 挂载切片中全景进度遮罩 (阶段四)
        if (state.isSlicingAssets) {
            AssetSlicingProgressOverlay(
                progress = state.sliceProgress,
                statusText = state.sliceStatusText
            )
        }

        // 顶层全局悬浮拖拽光标跟随镜像 (Drag Ghost Badge)
        val draggedAsset = remember(state.draggedAssetId, state.assetLibrary.items) {
            state.assetLibrary.items.find { it.id == state.draggedAssetId }
        }
        UIStudioDragGhostBadge(
            asset = draggedAsset,
            cursorPos = state.dragCursorPosition
        )

        // 挂载退出未保存确认弹窗 (三选：保存并退出 / 不保存退出 / 取消)
        if (state.showExitConfirmDialog) {
            AlertDialog(
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
    }
}

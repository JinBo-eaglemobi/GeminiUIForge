package org.gemini.ui.forge.ui.feature.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.viewmodel.TemplateEditorViewModel
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.AITaskProgressDialog
import org.gemini.ui.forge.ui.dialog.VisualRefineDialog
import org.gemini.ui.forge.ui.component.CanvasArea
import org.gemini.ui.forge.ui.component.HierarchySidebar
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

import org.gemini.ui.forge.ui.dialog.ReferenceAreaCropDialog

/**
 * 模板编辑器主页面。
 * 负责初始化 ViewModel、管理 UI 三栏布局结构、处理 AI 交互弹窗以及维护画布/属性面板的同步。
 */
@Composable
fun TemplateEditorScreen(
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

    val aiService = AIGenerationService(cloudAssetManager, configManager)

    // 1. 初始化 ViewModel，其生命周期与当前 Screen 绑定
    val viewModel: TemplateEditorViewModel = viewModel(key = initialProjectName) {
        TemplateEditorViewModel(
            initialProject,
            initialProjectName,
            initialPromptLang,
            templateRepo,
            cloudAssetManager,
            aiService,
            onDirtyChanged = onDirtyChanged
        )
    }
    val state by viewModel.state.collectAsState()

    // 监听全局保存事件
    LaunchedEffect(saveEvent) {
        saveEvent.collect {
            onSaveRequest(initialProjectName, state.project)
        }
    }

    // 监听全局快捷键事件
    LaunchedEffect(shortcutEvent) {
        shortcutEvent.collect { action ->
            viewModel.handleShortcutAction(action)
        }
    }

    // 监听传入的 initialProject 变化（比如从首页重新进入时强制刷新 ViewModel 状态）
    LaunchedEffect(initialProject) {
        viewModel.reload(initialProject)
    }

    // --- 内部 UI 状态控制 ---
    var showVisualRefine by remember { mutableStateOf(false) }
    var refineTargetId by remember { mutableStateOf<String?>(null) }
    var showReferenceArea by remember { mutableStateOf(false) }
    var referenceAreaTargetId by remember { mutableStateOf<String?>(null) }
    var showAILogs by remember { mutableStateOf(true) }

    // 当 AI 开始生成时，自动展开日志面板
    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            showAILogs = true
        }
    }

    // 渲染 AI 任务执行进度弹窗
    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = if (state.generationLogs.any { it.contains("优化") || it.contains("润色") }) "智能优化提示词中..." else "正在执行任务...",
            currentStatus = state.aiStatus,
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = showAILogs,
            onToggleLogVisibility = { showAILogs = !showAILogs },
            onActionClick = {
                if (state.isGenerating) viewModel.cancelAITask() else viewModel.closeAITaskDialog()
            },
            onDismiss = { viewModel.closeAITaskDialog() }
        )
    }

    // 渲染 AI 视觉重塑框选对话框
    if (showVisualRefine) {
        val defaultInstruction =
            if (refineTargetId != null) state.defaultRefineInstructionUpdate else state.defaultRefineInstructionNew
        VisualRefineDialog(
            imageUri = state.currentPage?.sourceImageUri,
            pageWidth = state.currentPage?.width ?: 1080f,
            pageHeight = state.currentPage?.height ?: 1920f,
            initialInstruction = defaultInstruction,
            onDismiss = { showVisualRefine = false },
            onConfirm = { rect, instruction, useChatContext, _, _, _ ->
                showVisualRefine = false
                viewModel.onRefineArea(refineTargetId, rect, instruction, effectiveApiKey, useChatContext) { success ->
                    if (success) {
                        /* ViewModel 内部已处理状态更新，这里可以增加额外的 UI 反馈 */
                    }
                }
            }
        )
    }

    if (showReferenceArea) {
        ReferenceAreaCropDialog(
            imageUri = state.currentPage?.sourceImageUri,
            pageWidth = state.currentPage?.width ?: 1080f,
            pageHeight = state.currentPage?.height ?: 1920f,
            onDismiss = { showReferenceArea = false },
            onConfirm = { rect ->
                showReferenceArea = false
                referenceAreaTargetId?.let { id ->
                    viewModel.onSetReferenceArea(id, rect)
                }
            }
        )
    }

    // 全局模块删除确认对话框
    if (state.showDeleteBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteBlock() },
            title = { Text(stringResource(Res.string.action_delete_block)) },
            text = { 
                Text("确定要删除模块 \"${state.pendingDeleteBlockId}\" 吗？此操作将同时删除其所有子模块，且无法撤销。") 
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteBlock() }
                ) {
                    Text(
                        stringResource(Res.string.dialog_action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteBlock() }) {
                    Text(stringResource(Res.string.dialog_action_cancel))
                }
            }
        )
    }

    // --- 主界面布局 ---
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var leftWeight by remember { mutableStateOf(0.2f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.25f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // [左栏] 工具栏与图层树
            Surface(
                modifier = Modifier.weight(leftWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            stringResource(Res.string.editor_tools_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 全局区域重塑
                        Button(
                            onClick = { refineTargetId = null; showVisualRefine = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.medium
                        ) {
                            Icon(Icons.Default.Crop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_refine_area))
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(stringResource(Res.string.prop_pages), style = MaterialTheme.typography.labelMedium)
                        state.project.pages.forEach { page ->
                            val selected = state.selectedPageId == page.id
                            TextButton(
                                onClick = { viewModel.onPageSelected(page.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.medium
                            ) {
                                Text(
                                    page.nameStr,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }

                    // 图层树
                    HierarchySidebar(
                        blocks = state.currentPage?.blocks ?: emptyList(),
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = { viewModel.onBlockClicked(it) },
                        onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                        onMoveBlock = { d, t, p -> viewModel.moveBlock(d, t, p) },
                        onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) },
                        onRenameBlock = { old, new -> viewModel.renameBlock(old, new) },
                        onToggleVisibility = { id, visible -> viewModel.toggleBlockVisibility(id, visible) },
                        onToggleAllVisibility = { visible -> viewModel.toggleAllBlocksVisibility(visible) },
                        renameRequestEvent = viewModel.requestRenameEvent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.1f..0.3f) {
                    leftWeight += dw
                    centerWeight -= dw
                }
            })

            // [中栏] 交互式画布
            Box(modifier = Modifier.weight(centerWeight).fillMaxHeight()) {
                CanvasArea(
                    pageWidth = state.currentPage?.width ?: 1080f,
                    pageHeight = state.currentPage?.height ?: 1920f,
                    blocks = state.currentPage?.blocks ?: emptyList(),
                    stageBackgroundColor = state.stageBackgroundColor,
                    selectedBlockId = state.selectedBlockId,
                    onBlockClicked = { viewModel.onBlockClicked(it) },
                    onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                    onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                    editingGroupId = state.editingGroupId,
                    onExitGroupEdit = { viewModel.exitGroupEditMode() },
                    referenceUri = state.currentPage?.sourceImageUri?.getAbsolutePath(),
                    isReadOnly = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) {
                    rightWeight -= dw
                    centerWeight += dw
                }
            })

            // [右栏] 属性编辑面板
            Surface(
                modifier = Modifier.weight(rightWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                EditorPropertyPanel(
                    state = state,
                    viewModel = viewModel,
                    apiKey = effectiveApiKey,
                    currentLang = state.currentLang,
                    onSwitchLang = { viewModel.switchLang(it) },
                    onRefineClick = { id -> refineTargetId = id; showVisualRefine = true },
                    onSetReferenceAreaClick = { id -> referenceAreaTargetId = id; showReferenceArea = true }
                )
            }
        }
    }
}
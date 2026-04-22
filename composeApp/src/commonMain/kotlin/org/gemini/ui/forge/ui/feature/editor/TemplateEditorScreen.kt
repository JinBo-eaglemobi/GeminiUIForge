package org.gemini.ui.forge.ui.feature.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.state.TemplateEditorViewModel
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.service.AIGenerationService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.TemplateEditorState
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.AITaskProgressDialog
import org.gemini.ui.forge.ui.dialog.VisualRefineDialog
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar

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
    effectiveApiKey: String,
    initialPromptLang: PromptLanguage,
    onProjectUpdated: (ProjectState) -> Unit
) {

    val aiService = AIGenerationService(cloudAssetManager)

    // 1. 初始化 ViewModel，其生命周期与当前 Screen 绑定
    val viewModel: TemplateEditorViewModel = viewModel(key = initialProjectName) {
        TemplateEditorViewModel(initialProject, initialProjectName, initialPromptLang, templateRepo, cloudAssetManager, aiService)
    }
    val state by viewModel.state.collectAsState()

    // 监听传入的 initialProject 变化（比如从首页重新进入时强制刷新 ViewModel 状态）
    LaunchedEffect(initialProject) {
        viewModel.reload(initialProject)
    }

    // 监听内部 state.project 变化，向外层同步状态，以保证外部顶部导航栏“保存”时拿到最新数据
    LaunchedEffect(state.project) {
        onProjectUpdated(state.project)
    }

    // --- 内部 UI 状态控制 ---
    var showVisualRefine by remember { mutableStateOf(false) }
    var refineTargetId by remember { mutableStateOf<String?>(null) }
    var showAILogs by remember { mutableStateOf(false) }

    // 当 AI 开始生成时，自动展开日志面板
    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            showAILogs = true
        }
    }

    // 渲染 AI 任务执行进度弹窗
    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = if (state.generationLogs.any { it.contains("优化") || it.contains("润色") }) "智能优化提示词中..." else "正在执行区域重构...",
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
            onConfirm = { rect, instruction, _, _, _ ->
                showVisualRefine = false
                viewModel.onRefineArea(refineTargetId, rect, instruction, effectiveApiKey) { success ->
                    if (success) {
                        /* ViewModel 内部已处理状态更新，这里可以增加额外的 UI 反馈 */
                    }
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
                PropertyPanel(
                    state = state,
                    viewModel = viewModel,
                    apiKey = effectiveApiKey,
                    currentLang = state.currentLang,
                    onSwitchLang = { viewModel.switchLang(it) },
                    onRefineClick = { id -> refineTargetId = id; showVisualRefine = true }
                )
            }
        }
    }
}

/**
 * 属性面板。
 * 直接接收 ViewModel 以便在内部处理用户对具体属性（ID、坐标、提示词等）的修改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(
    state: TemplateEditorState,
    viewModel: TemplateEditorViewModel,
    apiKey: String,
    currentLang: PromptLanguage,
    onSwitchLang: (PromptLanguage) -> Unit,
    onRefineClick: (String) -> Unit
) {
    val selectedBlock = state.selectedBlock

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.editor_properties),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedBlock == null) {
            state.currentPage?.let { page ->
                Text(
                    "页面属性 (${page.nameStr})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = AppShapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableInfoItem(
                                label = "宽度 (W)",
                                value = page.width.toInt().toString(),
                                onValueChange = { 
                                    val w = it.toFloatOrNull() ?: page.width
                                    viewModel.updatePageSize(w, page.height)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            EditableInfoItem(
                                label = "高度 (H)",
                                value = page.height.toInt().toString(),
                                onValueChange = { 
                                    val h = it.toFloatOrNull() ?: page.height
                                    viewModel.updatePageSize(page.width, h)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.stageBackgroundColor,
                            onValueChange = { viewModel.updateStageBackgroundColor(it) },
                            label = { Text("临时背景色 (HEX)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.small,
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true
                        )
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // ID 编辑
            OutlinedTextField(
                value = selectedBlock.id,
                onValueChange = { if (it.isNotBlank()) viewModel.renameBlock(selectedBlock.id, it) },
                label = { Text(stringResource(Res.string.prop_block_id)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                shape = AppShapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // 物理参数编辑组
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "物理坐标与尺寸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditableInfoItem(
                            label = "X",
                            value = selectedBlock.bounds.left.toInt().toString(),
                            onValueChange = { newValue ->
                                val x = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    x,
                                    selectedBlock.bounds.top,
                                    x + selectedBlock.bounds.width,
                                    selectedBlock.bounds.bottom
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "Y",
                            value = selectedBlock.bounds.top.toInt().toString(),
                            onValueChange = { newValue ->
                                val y = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    y,
                                    selectedBlock.bounds.right,
                                    y + selectedBlock.bounds.height
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditableInfoItem(
                            label = "W",
                            value = selectedBlock.bounds.width.toInt().toString(),
                            onValueChange = { newValue ->
                                val w = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    selectedBlock.bounds.top,
                                    selectedBlock.bounds.left + w,
                                    selectedBlock.bounds.bottom
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "H",
                            value = selectedBlock.bounds.height.toInt().toString(),
                            onValueChange = { newValue ->
                                val h = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    selectedBlock.bounds.top,
                                    selectedBlock.bounds.right,
                                    selectedBlock.bounds.top + h
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 组件类型切换
            var expanded by remember { mutableStateOf(false) }
            Text(
                stringResource(Res.string.prop_type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = stringResource(selectedBlock.type.getDisplayNameRes()),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = AppShapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    UIBlockType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.getDisplayNameRes())) },
                            onClick = { viewModel.updateBlockType(selectedBlock.id, type); expanded = false }
                        )
                    }
                }
            }

            // 提示词多语言编辑
            Text(
                text = stringResource(Res.string.editor_prompt_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = currentLang == lang,
                        onClick = { onSwitchLang(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(lang.displayName) }
                    )
                }
            }

            val displayPrompt =
                if (currentLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
            OutlinedTextField(
                value = displayPrompt,
                onValueChange = { viewModel.onUserPromptChanged(selectedBlock.id, it, currentLang) },
                label = { Text("${stringResource(Res.string.label_description_content)} (${currentLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                maxLines = 10,
                enabled = !state.isGenerating,
                shape = AppShapes.medium
            )

            // AI 辅助工具
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.optimizePrompt(selectedBlock.id, apiKey, currentLang) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating && displayPrompt.isNotBlank(),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.prop_optimize_prompt))
                }

                OutlinedButton(
                    onClick = { onRefineClick(selectedBlock.id) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating,
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.action_refine_area))
                }
            }

            Spacer(Modifier.height(24.dp))

            // 块删除
            var showDeleteConfirmDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { showDeleteConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                enabled = !state.isGenerating
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_delete_block))
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text(stringResource(Res.string.action_delete_block)) },
                    text = { Text("确定要删除模块 \"${selectedBlock.id}\" 吗？此操作将同时删除其所有子模块，且无法撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                viewModel.deleteBlock(selectedBlock.id)
                            }
                        ) {
                            Text(stringResource(Res.string.dialog_action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text(stringResource(Res.string.dialog_action_cancel))
                        }
                    }
                )
            }
        }
    }
}

/**
 * 封装的数字输入项
 */
@Composable
private fun EditableInfoItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isNotEmpty() && newValue.toFloatOrNull() != null) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        maxLines = 1
    )
}
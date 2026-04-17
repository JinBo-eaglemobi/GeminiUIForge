package org.gemini.ui.forge.ui.feature.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.TemplateEditorState
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.component.AITaskProgressDialog
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar
import androidx.compose.ui.focus.onFocusChanged

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
    aiService: AIGenerationService,
    effectiveApiKey: String,
    currentEditingPromptLang: PromptLanguage,
    onSwitchEditingLanguage: (PromptLanguage) -> Unit,
    onProjectUpdated: (ProjectState) -> Unit,
    onSaveTemplate: () -> Unit
) {
    // 1. 初始化 ViewModel，其生命周期与当前 Screen 绑定
    val viewModel: TemplateEditorViewModel = viewModel(key = initialProjectName) {
        TemplateEditorViewModel(initialProject, initialProjectName, templateRepo, cloudAssetManager, aiService)
    }
    val state by viewModel.state.collectAsState()

    // 状态状态同步：当 ViewModel 内部的项目状态发生变化时，同步给外部以便 App 保存
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
        val defaultInstruction = if (refineTargetId != null) state.defaultRefineInstructionUpdate else state.defaultRefineInstructionNew
        VisualRefineDialog(
            imageUri = state.currentPage?.sourceImageUri ?: "",
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
            Surface(modifier = Modifier.weight(leftWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(Res.string.editor_tools_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                        
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
                                Text(page.nameStr, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
                    selectedBlockId = state.selectedBlockId,
                    onBlockClicked = { viewModel.onBlockClicked(it) },
                    onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                    onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                    editingGroupId = state.editingGroupId,
                    onExitGroupEdit = { viewModel.exitGroupEditMode() },
                    referenceUri = state.currentPage?.sourceImageUri,
                    isReadOnly = false,
                    modifier = Modifier.fillMaxSize()
                )
                
                // 快捷保存
                SmallFloatingActionButton(
                    onClick = onSaveTemplate,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Save, null)
                }
            }
            
            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) {
                    rightWeight -= dw
                    centerWeight += dw
                }
            })

            // [右栏] 属性编辑面板
            Surface(modifier = Modifier.weight(rightWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                PropertyPanel(
                    state = state,
                    viewModel = viewModel,
                    apiKey = effectiveApiKey,
                    currentLang = currentEditingPromptLang,
                    onSwitchLang = onSwitchEditingLanguage,
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
        Text(stringResource(Res.string.editor_properties), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

        if (selectedBlock == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    Text("物理坐标与尺寸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditableInfoItem(
                            label = "X",
                            value = selectedBlock.bounds.left.toInt().toString(),
                            onValueChange = { newValue ->
                                val x = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(selectedBlock.id, x, selectedBlock.bounds.top, x + selectedBlock.bounds.width, selectedBlock.bounds.bottom)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "Y",
                            value = selectedBlock.bounds.top.toInt().toString(),
                            onValueChange = { newValue ->
                                val y = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(selectedBlock.id, selectedBlock.bounds.left, y, selectedBlock.bounds.right, y + selectedBlock.bounds.height)
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
                                viewModel.updateBlockBounds(selectedBlock.id, selectedBlock.bounds.left, selectedBlock.bounds.top, selectedBlock.bounds.left + w, selectedBlock.bounds.bottom)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "H",
                            value = selectedBlock.bounds.height.toInt().toString(),
                            onValueChange = { newValue ->
                                val h = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(selectedBlock.id, selectedBlock.bounds.left, selectedBlock.bounds.top, selectedBlock.bounds.right, selectedBlock.bounds.top + h)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 组件类型切换
            var expanded by remember { mutableStateOf(false) }
            Text(stringResource(Res.string.prop_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
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
            Text(text = stringResource(Res.string.editor_prompt_lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
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

            val displayPrompt = if (currentLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
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
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Button(
                onClick = { viewModel.deleteBlock(selectedBlock.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                enabled = !state.isGenerating
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_delete_block))
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
    var textValue by remember(value) { mutableStateOf(value) }
    Column(modifier) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { 
                textValue = it
                if (it.isNotEmpty() && it.toFloatOrNull() != null) { onValueChange(it) }
            },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.small
        )
    }
}

/**
 * 视觉框选重塑对话框
 */
@Composable
fun VisualRefineDialog(
    imageUri: String,
    pageWidth: Float,
    pageHeight: Float,
    initialInstruction: String,
    onDismiss: () -> Unit,
    onConfirm: (SerialRect, String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit
) {
    var instruction by remember { mutableStateOf(initialInstruction) }
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(Res.string.action_refine_area), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.05f)).clip(RoundedCornerShape(8.dp))) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val containerW = maxWidth
                        val containerH = maxHeight
                        val imageAspectRatio = pageWidth / pageHeight
                        val containerAspectRatio = containerW.value / containerH.value

                        val displayW: Float; val displayH: Float
                        if (imageAspectRatio > containerAspectRatio) {
                            displayW = containerW.value
                            displayH = displayW / imageAspectRatio
                        } else {
                            displayH = containerH.value
                            displayW = displayH * imageAspectRatio
                        }

                        val offsetX = (containerW.value - displayW) / 2
                        val offsetY = (containerH.value - displayH) / 2

                        AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val lx = ((offset.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth
                                    val ly = ((offset.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight
                                    selectedRect = SerialRect(lx, ly, lx, ly)
                                },
                                onDrag = { change, _ ->
                                    val lx = ((change.position.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth
                                    val ly = ((change.position.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight
                                    selectedRect = selectedRect?.copy(right = lx, bottom = ly)
                                }
                            )
                        }) {
                            selectedRect?.let { rect ->
                                val left = with(density) { (offsetX + (rect.left / pageWidth) * displayW).dp.toPx() }
                                val top = with(density) { (offsetY + (rect.top / pageHeight) * displayH).dp.toPx() }
                                val right = with(density) { (offsetX + (rect.right / pageWidth) * displayW).dp.toPx() }
                                val bottom = with(density) { (offsetY + (rect.bottom / pageHeight) * displayH).dp.toPx() }
                                val rectTopLeft = Offset(kotlin.math.min(left, right), kotlin.math.min(top, bottom))
                                val rectSize = Size(kotlin.math.abs(right - left), kotlin.math.abs(bottom - top))
                                drawRect(color = Color.Cyan, topLeft = rectTopLeft, size = rectSize, style = Stroke(width = 2.dp.toPx()))
                                drawRect(color = Color.Cyan.copy(alpha = 0.2f), topLeft = rectTopLeft, size = rectSize)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = instruction, onValueChange = { instruction = it }, label = { Text("重塑指令") }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = AppShapes.medium)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { selectedRect?.let { onConfirm(it, instruction, {}, {}, {}) } }, enabled = selectedRect != null, shape = AppShapes.medium) { Text("确认重塑") }
                }
            }
        }
    }
}

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
import org.gemini.ui.forge.state.EditorState
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.component.AITaskProgressDialog
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun TemplateEditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String?) -> Unit,
    onBlockBoundsChanged: (String, Float, Float, Float, Float) -> Unit,
    onBlockTypeChanged: (String, UIBlockType) -> Unit,
    onPromptChanged: (String, String) -> Unit,
    onOptimizePrompt: (String, (String) -> Unit) -> Unit,
    onRefineArea: (String, SerialRect, String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    onRefineCustomArea: (SerialRect, String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    onSwitchEditingLanguage: (PromptLanguage) -> Unit,
    onBlockDoubleClicked: (String) -> Unit,
    onExitGroupEdit: () -> Unit,
    onAddBlock: (UIBlockType) -> Unit,
    onAddCustomBlock: (String, UIBlockType, Float, Float) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlock: (String, String?, org.gemini.ui.forge.model.ui.DropPosition) -> Unit,
    onBlockDragged: (String, Float, Float) -> Unit,
    onRenameBlock: (String, String) -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onToggleAllVisibility: (Boolean) -> Unit = {},
    onCancelAITask: () -> Unit = {},
    onToggleAILog: () -> Unit = {},
    onCloseAITaskDialog: () -> Unit = {},
    onSaveTemplate: () -> Unit
) {
    var showVisualRefine by remember { mutableStateOf(false) }
    var refineTargetId by remember { mutableStateOf<String?>(null) } 

    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = if (state.generationLogs.any { it.contains("优化") || it.contains("润色") }) "智能优化提示词中..." else "正在执行区域重构...",
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = state.isGenerationLogVisible,
            onToggleLogVisibility = onToggleAILog,
            onActionClick = {
                if (state.isGenerating) onCancelAITask() else onCloseAITaskDialog()
            },
            onDismiss = onCloseAITaskDialog
        )
    }

    if (showVisualRefine) {
        val defaultInstruction = if (refineTargetId != null) {
            state.defaultRefineInstructionUpdate
        } else {
            state.defaultRefineInstructionNew
        }

        VisualRefineDialog(
            imageUri = state.currentPage?.sourceImageUri ?: "",
            pageWidth = state.currentPage?.width ?: 1080f,
            pageHeight = state.currentPage?.height ?: 1920f,
            initialInstruction = defaultInstruction,
            onDismiss = { showVisualRefine = false },
            onConfirm = { rect, instruction, _, _, _ ->
                showVisualRefine = false
                if (refineTargetId != null) {
                    onRefineArea(refineTargetId!!, rect, instruction, {}, {}, {})
                } else {
                    onRefineCustomArea(rect, instruction, {}, {}, {})
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var leftWeight by remember { mutableStateOf(0.2f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.25f) }

        Row(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.weight(leftWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(Res.string.editor_tools_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                        
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
                                onClick = { onPageSelected(page.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.medium
                            ) {
                                Text(page.nameStr, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }

                    HierarchySidebar(
                        blocks = state.currentPage?.blocks ?: emptyList(),
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = onBlockClicked,
                        onBlockDoubleClicked = onBlockDoubleClicked,
                        onMoveBlock = onMoveBlock,
                        onAddCustomBlock = onAddCustomBlock,
                        onRenameBlock = onRenameBlock,
                        onToggleVisibility = onToggleVisibility,
                        onToggleAllVisibility = onToggleAllVisibility,
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

            Box(modifier = Modifier.weight(centerWeight).fillMaxHeight()) {
                CanvasArea(
                    pageWidth = state.currentPage?.width ?: 1080f,
                    pageHeight = state.currentPage?.height ?: 1920f,
                    blocks = state.currentPage?.blocks ?: emptyList(),
                    selectedBlockId = state.selectedBlockId,
                    onBlockClicked = onBlockClicked,
                    onBlockDoubleClicked = onBlockDoubleClicked,
                    onBlockDragged = onBlockDragged,
                    editingGroupId = state.editingGroupId,
                    onExitGroupEdit = onExitGroupEdit,
                    referenceUri = state.currentPage?.sourceImageUri,
                    isReadOnly = false,
                    modifier = Modifier.fillMaxSize()
                )
                
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

            Surface(modifier = Modifier.weight(rightWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                PropertyPanel(
                    selectedBlock = state.selectedBlock,
                    currentLang = state.currentEditingPromptLang,
                    onSwitchLang = onSwitchEditingLanguage,
                    onBlockTypeChanged = onBlockTypeChanged,
                    onBlockBoundsChanged = onBlockBoundsChanged,
                    onRenameBlock = onRenameBlock,
                    onPromptChanged = onPromptChanged,
                    onOptimizePrompt = onOptimizePrompt,
                    onRefineClick = { id -> refineTargetId = id; showVisualRefine = true },
                    onDeleteBlock = onDeleteBlock,
                    isGenerating = state.isGenerating
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(
    selectedBlock: UIBlock?,
    currentLang: PromptLanguage,
    onSwitchLang: (PromptLanguage) -> Unit,
    onBlockTypeChanged: (String, UIBlockType) -> Unit,
    onBlockBoundsChanged: (String, Float, Float, Float, Float) -> Unit,
    onRenameBlock: (String, String) -> Unit,
    onPromptChanged: (String, String) -> Unit,
    onOptimizePrompt: (String, (String) -> Unit) -> Unit,
    onRefineClick: (String) -> Unit,
    onDeleteBlock: (String) -> Unit,
    isGenerating: Boolean
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(Res.string.editor_properties), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

        if (selectedBlock == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 模块 ID 编辑
            OutlinedTextField(
                value = selectedBlock.id,
                onValueChange = { if (it.isNotBlank()) onRenameBlock(selectedBlock.id, it) },
                label = { Text(stringResource(Res.string.prop_block_id)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                shape = AppShapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium
            )

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
                                val newLeft = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                val currentWidth = selectedBlock.bounds.width
                                onBlockBoundsChanged(selectedBlock.id, newLeft, selectedBlock.bounds.top, newLeft + currentWidth, selectedBlock.bounds.bottom)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "Y",
                            value = selectedBlock.bounds.top.toInt().toString(),
                            onValueChange = { newValue ->
                                val newTop = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                val currentHeight = selectedBlock.bounds.height
                                onBlockBoundsChanged(selectedBlock.id, selectedBlock.bounds.left, newTop, selectedBlock.bounds.right, newTop + currentHeight)
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
                                val newWidth = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                onBlockBoundsChanged(selectedBlock.id, selectedBlock.bounds.left, selectedBlock.bounds.top, selectedBlock.bounds.left + newWidth, selectedBlock.bounds.bottom)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "H",
                            value = selectedBlock.bounds.height.toInt().toString(),
                            onValueChange = { newValue ->
                                val newHeight = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                onBlockBoundsChanged(selectedBlock.id, selectedBlock.bounds.left, selectedBlock.bounds.top, selectedBlock.bounds.right, selectedBlock.bounds.top + newHeight)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

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
                            onClick = { onBlockTypeChanged(selectedBlock.id, type); expanded = false }
                        )
                    }
                }
            }

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
                onValueChange = { onPromptChanged(selectedBlock.id, it) },
                label = { Text("${stringResource(Res.string.label_description_content)} (${currentLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                maxLines = 10,
                enabled = !isGenerating,
                shape = AppShapes.medium
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOptimizePrompt(selectedBlock.id) {} },
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating && displayPrompt.isNotBlank(),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.prop_optimize_prompt))
                }
                
                OutlinedButton(
                    onClick = { onRefineClick(selectedBlock.id) },
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating,
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.action_refine_area))
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { onDeleteBlock(selectedBlock.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                enabled = !isGenerating
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_delete_block))
            }
        }
    }
}

@Composable
private fun EditableInfoItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { 
                textValue = it
                if (it.isNotEmpty() && it.toFloatOrNull() != null) {
                    onValueChange(it)
                }
            },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            shape = AppShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

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

                        val displayW: Float
                        val displayH: Float
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
                OutlinedTextField(value = instruction, onValueChange = { instruction = it }, label = { Text("重塑指令 (例如：优化细节、增加阴影、重新识别结构)") }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = AppShapes.medium)
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

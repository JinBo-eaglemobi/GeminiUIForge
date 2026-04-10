package org.gemini.ui.forge.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.viewmodel.EditorState
import org.gemini.ui.forge.viewmodel.PromptLanguage
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min

@Composable
fun TemplateEditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String) -> Unit,
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
    onMoveBlock: (String, String?, org.gemini.ui.forge.domain.DropPosition) -> Unit,
    onBlockDragged: (String, Float, Float) -> Unit,
    onRenameBlock: (String, String) -> Unit,
    onSaveTemplate: () -> Unit
) {
    var showVisualRefine by remember { mutableStateOf(false) }
    var refineTargetId by remember { mutableStateOf<String?>(null) } // 如果为 null 代表是自由框选

    // 视觉选择重塑对话框
    if (showVisualRefine) {
        val defaultInstruction = if (refineTargetId != null) {
            "请基于我提供的局部高清图，重新识别并分析这个特定组件的细节。请务必将分析出的 UI 结构（子组件、样式、提示词）更新到我当前选定的这个模块中，不要创建冗余的新模块，也不要修改其他无关区域。"
        } else {
            "这段区域是你之前未能识别到的 UI 部分。请重新分析该区域中的视觉元素，提取出准确的 UI 功能组件，并将它们作为新模块正确添加到当前页面的布局结构中。"
        }

        VisualRefineDialog(
            imageUri = state.currentPage?.sourceImageUri ?: "",
            pageWidth = state.currentPage?.width ?: 1080f,
            pageHeight = state.currentPage?.height ?: 1920f,
            initialInstruction = defaultInstruction,
            onDismiss = { showVisualRefine = false },
            onConfirm = { rect, instruction, onLog, onChunk, onDone ->
                if (refineTargetId != null) {
                    onRefineArea(refineTargetId!!, rect, instruction, onLog, onChunk, onDone)
                } else {
                    onRefineCustomArea(rect, instruction, onLog, onChunk, onDone)
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
            // 工具栏
            Surface(modifier = Modifier.weight(leftWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(Res.string.editor_tools_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                        
                        Button(
                            onClick = { refineTargetId = null; showVisualRefine = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Crop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_refine_area))
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(stringResource(Res.string.prop_pages), style = MaterialTheme.typography.labelMedium)
                        state.project.pages.forEach { page ->
                            val selected = state.selectedPageId == page.id
                            TextButton(onClick = { onPageSelected(page.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(page.nameStr, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }

                    // 层级列表
                    HierarchySidebar(
                        blocks = state.currentPage?.blocks ?: emptyList(),
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = onBlockClicked,
                        onMoveBlock = onMoveBlock,
                        onAddCustomBlock = onAddCustomBlock,
                        onRenameBlock = onRenameBlock,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.15f..0.3f) { leftWeight += dw; centerWeight -= dw }
            })

            // 画布
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
                    modifier = Modifier.fillMaxSize()
                )
            }

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) { rightWeight -= dw; centerWeight += dw }
            })

            // 属性面板
            Surface(modifier = Modifier.weight(rightWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(Res.string.editor_properties), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val block = state.selectedBlock
                    if (block == null) {
                        Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BlockPropertiesEditor(
                            block = block,
                            currentEditingLang = state.currentEditingPromptLang,
                            onSwitchEditingLang = onSwitchEditingLanguage,
                            onBoundsChanged = onBlockBoundsChanged,
                            onTypeChanged = { onBlockTypeChanged(block.id, it) },
                            onPromptChanged = { onPromptChanged(block.id, it) },
                            onOptimizePrompt = { onOptimizePrompt(block.id, it) },
                            onOpenRefine = { refineTargetId = block.id; showVisualRefine = true },
                            onDelete = { onDeleteBlock(block.id) }
                        )
                    }
                }
            }
        }
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
    var selectionRect by remember { mutableStateOf<SerialRect?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(true) } // 默认开启日志侧边栏
    val logs = remember { mutableStateListOf<String>() }
    var streamText by remember { mutableStateOf("") }

    val imageBitmapState = produceState<ImageBitmap?>(null, imageUri) {
        value = imageUri.decodeBase64ToBitmap()
    }
    val imageBitmap = imageBitmapState.value

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // 顶栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("AI 视觉引导重塑工作间", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    
                    // 日志开关
                    FilledTonalIconToggleButton(checked = showLogs, onCheckedChange = { showLogs = it }) {
                        Icon(Icons.Default.Terminal, null)
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onDismiss, enabled = !isProcessing) { Icon(Icons.Default.Close, null) }
                }

                Spacer(Modifier.height(16.dp))

                // 核心工作区：左侧图片选区，右侧大控制台
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 左侧：图片选区
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                        if (imageBitmap != null) {
                            ImageSelector(
                                bitmap = imageBitmap,
                                logicalWidth = pageWidth,
                                logicalHeight = pageHeight,
                                enabled = !isProcessing && !isDone, // 正在处理或已完成时冻结
                                onSelectionChanged = { selectionRect = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }

                    // 右侧：持久化日志控制台
                    if (showLogs) {
                        Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                            Text("处理日志 (可滚动选择)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            LogTerminal(
                                logs = logs, 
                                streamText = streamText, 
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 指令与操作区
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        label = { Text("修正建议或功能描述") },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing && !isDone
                    )

                    Spacer(Modifier.width(16.dp))

                    if (isDone) {
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Text("分析完成，返回编辑器")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (selectionRect != null) {
                                    isProcessing = true
                                    logs.clear()
                                    streamText = ""
                                    onConfirm(selectionRect!!, instruction, { logs.add(it) }, { streamText += it }) { success ->
                                        isProcessing = false
                                        if (success) isDone = true
                                    }
                                }
                            },
                            enabled = selectionRect != null && instruction.isNotBlank() && !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在分析...")
                            } else {
                                Text("开始 AI 重塑")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageSelector(
    bitmap: ImageBitmap,
    logicalWidth: Float,
    logicalHeight: Float,
    enabled: Boolean = true,
    onSelectionChanged: (SerialRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()
        val scale = min(maxWidth.value / imgWidth, maxHeight.value / imgHeight)
        val drawWidth = imgWidth * scale
        val drawHeight = imgHeight * scale
        val offsetX = (maxWidth.value - drawWidth) / 2
        val offsetY = (maxHeight.value - drawHeight) / 2

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(enabled) {
            if (!enabled) return@pointerInput 
            
            detectDragGestures(
                onDragStart = { startOffset = it },
                onDrag = { change, _ ->
                    if (!enabled) return@detectDragGestures
                    currentOffset = change.position
                    change.consume()
                    
                    val start = startOffset
                    val end = currentOffset
                    if (start != null && end != null) {
                        fun toLogical(o: Offset): Offset {
                            val px = (o.x / density.density - offsetX) / scale
                            val py = (o.y / density.density - offsetY) / scale
                            val lx = (px / imgWidth) * logicalWidth
                            val ly = (py / imgHeight) * logicalHeight
                            return Offset(lx.coerceIn(0f, logicalWidth), ly.coerceIn(0f, logicalHeight))
                        }
                        val l1 = toLogical(start)
                        val l2 = toLogical(end)
                        onSelectionChanged(SerialRect(
                            left = min(l1.x, l2.x),
                            top = min(l1.y, l2.y),
                            right = maxOf(l1.x, l2.x),
                            bottom = maxOf(l1.y, l2.y)
                        ))
                    }
                },
                onDragEnd = {},
                onDragCancel = { startOffset = null; currentOffset = null }
            )
        }) {
            drawImage(
                image = bitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset((offsetX * density.density).toInt(), (offsetY * density.density).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize((drawWidth * density.density).toInt(), (drawHeight * density.density).toInt())
            )

            val start = startOffset
            val end = currentOffset
            if (start != null && end != null) {
                val rectLeft = min(start.x, end.x)
                val rectTop = min(start.y, end.y)
                val rectWidth = abs(end.x - start.x)
                val rectHeight = abs(end.y - start.y)
                
                val color = if (enabled) Color.Cyan else Color.LightGray.copy(alpha = 0.5f)
                
                drawRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight)
                )
                drawRect(
                    color = color,
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun LogTerminal(logs: List<String>, streamText: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.5f))) {
        SelectionContainer {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(logs.size) { if(logs.isNotEmpty()) listState.animateScrollToItem(logs.size) }
                
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { Text(it, color = Color.Green, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    if (streamText.isNotEmpty()) {
                        item { Text("[流] 当前长度: ${streamText.length}", color = Color.Cyan, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                VerticalScrollbar(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(listState))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockPropertiesEditor(
    block: UIBlock,
    currentEditingLang: PromptLanguage,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    onBoundsChanged: (String, Float, Float, Float, Float) -> Unit,
    onTypeChanged: (UIBlockType) -> Unit,
    onPromptChanged: (String) -> Unit,
    onOptimizePrompt: ((String) -> Unit) -> Unit,
    onOpenRefine: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedType by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    OutlinedTextField(value = block.id, onValueChange = {}, readOnly = true, label = { Text(stringResource(Res.string.prop_block_id)) }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))

    ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
        OutlinedTextField(value = stringResource(block.type.getDisplayNameRes()), onValueChange = {}, readOnly = true, label = { Text(stringResource(Res.string.prop_type)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
            UIBlockType.entries.forEach { DropdownMenuItem(text = { Text(stringResource(it.getDisplayNameRes())) }, onClick = { onTypeChanged(it); expandedType = false }) }
        }
    }

    Spacer(Modifier.height(16.dp))

    val displayPrompt = if (currentEditingLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(value = displayPrompt, onValueChange = {}, readOnly = true, label = { Text("提示词 (${currentEditingLang.displayName})") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Box(Modifier.matchParentSize().clickable { showPromptDialog = true })
    }

    if (showPromptDialog) {
        var tempPrompt by remember { mutableStateOf(displayPrompt) }
        var isOptimizing by remember { mutableStateOf(false) }
        LaunchedEffect(currentEditingLang, block) { tempPrompt = if (currentEditingLang == PromptLanguage.EN) block.userPromptEn else block.userPromptZh }
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Column { Text("编辑详细描述"); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) { PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { i, l -> SegmentedButton(currentEditingLang == l, { onSwitchEditingLang(l) }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l.displayName) } } } } },
            text = { OutlinedTextField(tempPrompt, { tempPrompt = it; onPromptChanged(it) }, label = { Text("描述内容") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), enabled = !isOptimizing) },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ isOptimizing = true; onPromptChanged(tempPrompt); onOptimizePrompt { tempPrompt = it; isOptimizing = false } }, enabled = !isOptimizing && tempPrompt.isNotBlank()) { if (isOptimizing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("AI 优化") }; Button({ showPromptDialog = false }) { Text("完成") } } }
        )
    }

    Spacer(Modifier.height(16.dp))
    var xStr by remember(block.id, block.bounds.left) { mutableStateOf(block.bounds.left.toInt().toString()) }
    var yStr by remember(block.id, block.bounds.top) { mutableStateOf(block.bounds.top.toInt().toString()) }
    var wStr by remember(block.id, block.bounds.width) { mutableStateOf(block.bounds.width.toInt().toString()) }
    var hStr by remember(block.id, block.bounds.height) { mutableStateOf(block.bounds.height.toInt().toString()) }

    fun sub() {
        val x = xStr.toFloatOrNull() ?: block.bounds.left
        val y = yStr.toFloatOrNull() ?: block.bounds.top
        val w = wStr.toFloatOrNull()?.coerceAtLeast(10f) ?: block.bounds.width
        val h = hStr.toFloatOrNull()?.coerceAtLeast(10f) ?: block.bounds.height
        onBoundsChanged(block.id, x, y, x + w, y + h)
    }

    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(xStr, { xStr = it; sub() }, label = { Text("X") }, modifier = Modifier.weight(1f))
        OutlinedTextField(yStr, { yStr = it; sub() }, label = { Text("Y") }, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(wStr, { wStr = it; sub() }, label = { Text("宽") }, modifier = Modifier.weight(1f))
        OutlinedTextField(hStr, { hStr = it; sub() }, label = { Text("高") }, modifier = Modifier.weight(1f))
    }

    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onOpenRefine, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Text("AI 组件重塑")
    }
    Spacer(Modifier.height(8.dp))
    Button(onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Text("删除此组件")
    }
}

@Composable
private fun VerticalSplitter(onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerHoverIcon(org.gemini.ui.forge.ResizeHorizontalIcon)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { onDrag(it) }
            )
    )
}

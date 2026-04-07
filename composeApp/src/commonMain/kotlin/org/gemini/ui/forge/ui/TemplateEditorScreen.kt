package org.gemini.ui.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.viewmodel.EditorState
import org.gemini.ui.forge.viewmodel.PromptLanguage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter

@Composable
fun TemplateEditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String) -> Unit,
    onBlockBoundsChanged: (String, Float, Float, Float, Float) -> Unit,
    onBlockTypeChanged: (String, UIBlockType) -> Unit,
    onPromptChanged: (String, String) -> Unit,
    onOptimizePrompt: (String, (String) -> Unit) -> Unit,
    onRefineArea: (String, String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit, // 增加了 Log/Chunk 回调
    onRefineCustomArea: (SerialRect, String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    onSwitchEditingLanguage: (PromptLanguage) -> Unit,
    onAddBlock: (UIBlockType) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onSaveTemplate: () -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var showCustomRefineDialog by remember { mutableStateOf(false) }
    var pendingRect by remember { mutableStateOf<SerialRect?>(null) }

    if (showCustomRefineDialog && pendingRect != null) {
        var instruction by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        val logs = remember { mutableStateListOf<String>() }
        var streamText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showCustomRefineDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            title = { Text("框选区域重塑 (AI 流式分析)") },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("AI 将分析您框选的区域，并将其作为新组件合并到当前模板中。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        label = { Text("描述该区域 (例如：顶部状态栏和返回按钮)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    LogTerminal(logs = logs, streamText = streamText, modifier = Modifier.weight(1f))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        logs.clear()
                        streamText = ""
                        onRefineCustomArea(pendingRect!!, instruction, { logs.add(it) }, { streamText += it }) { success ->
                            isProcessing = false
                            if (success) {
                                showCustomRefineDialog = false
                                isSelectionMode = false
                            }
                        }
                    },
                    enabled = !isProcessing && instruction.isNotBlank()
                ) { Text("确认并开始分析") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRefineDialog = false }, enabled = !isProcessing) { Text("取消") }
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
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text("编辑工具", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    FilterChip(
                        selected = isSelectionMode,
                        onClick = { isSelectionMode = !isSelectionMode },
                        label = { Text("框选重塑模式") },
                        leadingIcon = { Icon(Icons.Default.Crop, null, Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("页面列表:", style = MaterialTheme.typography.labelMedium)
                    state.project.pages.forEach { page ->
                        val selected = state.selectedPageId == page.id
                        TextButton(onClick = { onPageSelected(page.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(page.nameStr, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onSaveTemplate, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                        Text(stringResource(Res.string.action_save_layout))
                    }
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
                    isSelectionMode = isSelectionMode,
                    onAreaSelected = { pendingRect = it; showCustomRefineDialog = true },
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
                            onRefineArea = { inst, log, chunk, done -> onRefineArea(block.id, inst, log, chunk, done) },
                            onDelete = { onDeleteBlock(block.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTerminal(logs: List<String>, streamText: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Black, shape = RoundedCornerShape(4.dp)) {
        SelectionContainer {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(logs.size) { if(logs.isNotEmpty()) listState.animateScrollToItem(logs.size) }
                
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { Text(it, color = Color.Green, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    if (streamText.isNotEmpty()) {
                        item { Text("[流] 长度:${streamText.length} | 预览: ...${streamText.takeLast(40)}", color = Color.Cyan, style = MaterialTheme.typography.bodySmall) }
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
    onRefineArea: (String, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    var expandedType by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showRefineDialog by remember { mutableStateOf(false) }

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

    if (showRefineDialog) {
        var inst by remember { mutableStateOf("") }
        var isProc by remember { mutableStateOf(false) }
        val logs = remember { mutableStateListOf<String>() }
        var streamText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!isProc) showRefineDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            title = { Text("组件重塑 (流式)") },
            text = {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(inst, { inst = it }, label = { Text("修正指令") }, modifier = Modifier.fillMaxWidth(), enabled = !isProc)
                    Spacer(Modifier.height(16.dp))
                    LogTerminal(logs, streamText, Modifier.weight(1f))
                }
            },
            confirmButton = { Button({ isProc = true; logs.clear(); streamText = ""; onRefineArea(inst, { logs.add(it) }, { streamText += it }) { isProc = false; if(it) showRefineDialog = false } }, enabled = !isProc && inst.isNotBlank()) { Text("开始") } },
            dismissButton = { TextButton({ showRefineDialog = false }, enabled = !isProc) { Text("取消") } }
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
    OutlinedButton({ showRefineDialog = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
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
                state = rememberDraggableState { onDrag(it) },
                orientation = Orientation.Horizontal
            )
    )
}

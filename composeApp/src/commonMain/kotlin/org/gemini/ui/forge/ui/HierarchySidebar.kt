package org.gemini.ui.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester

@Composable
fun HierarchySidebar(
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    onMoveBlock: (String, String?, org.gemini.ui.forge.domain.DropPosition) -> Unit = { _, _, _ -> },
    onAddCustomBlock: (String, UIBlockType, Float, Float) -> Unit = { _, _, _, _ -> },
    onRenameBlock: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredBlockId by remember { mutableStateOf<String?>(null) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    
    // 拖拽跟随状态
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    
    // 功能栏状态
    var showAddDialog by remember { mutableStateOf(false) }
    var locateTrigger by remember { mutableStateOf(0L) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }

    // 自动滚动到选中项 (包括新添加的项)
    LaunchedEffect(selectedBlockId) {
        if (selectedBlockId != null) {
            delay(100) // 稍作延迟等待组件展开和布局完成
            locateTrigger = org.gemini.ui.forge.getCurrentTimeMillis()
        }
    }

    if (showAddDialog) {
        AddLayerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { id, type, w, h ->
                onAddCustomBlock(id, type, w, h)
                showAddDialog = false
            }
        )
    }

    if (showRenameDialog != null) {
        RenameLayerDialog(
            initialId = showRenameDialog!!,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newId ->
                onRenameBlock(showRenameDialog!!, newId)
                showRenameDialog = null
            }
        )
    }

    var dropPosition by remember { mutableStateOf(org.gemini.ui.forge.domain.DropPosition.INSIDE) }

    Box(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .onGloballyPositioned { listCoordinates = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val windowOffset = listCoordinates?.localToWindow(offset) ?: offset
                        val hit = itemBounds.entries.find { it.value.contains(windowOffset) }
                        if (hit != null) {
                            draggedBlockId = hit.key
                            dragPosition = offset
                        }
                    },
                    onDrag = { change, _ ->
                        dragPosition = change.position
                        val windowOffset = listCoordinates?.localToWindow(change.position) ?: change.position
                        val hit = itemBounds.entries.find { it.value.contains(windowOffset) }
                        if (hit != null) {
                            hoveredBlockId = hit.key
                            val rect = hit.value
                            val y = windowOffset.y
                            dropPosition = when {
                                y < rect.top + rect.height * 0.25f -> org.gemini.ui.forge.domain.DropPosition.BEFORE
                                y > rect.bottom - rect.height * 0.25f -> org.gemini.ui.forge.domain.DropPosition.AFTER
                                else -> org.gemini.ui.forge.domain.DropPosition.INSIDE
                            }
                        } else {
                            hoveredBlockId = null
                            dropPosition = org.gemini.ui.forge.domain.DropPosition.INSIDE
                        }
                    },
                    onDragEnd = {
                        if (draggedBlockId != null) {
                            if (hoveredBlockId != draggedBlockId) {
                                onMoveBlock(draggedBlockId!!, hoveredBlockId, dropPosition)
                            }
                        }
                        draggedBlockId = null
                        hoveredBlockId = null
                        dragPosition = null
                    },
                    onDragCancel = {
                        draggedBlockId = null
                        hoveredBlockId = null
                        dragPosition = null
                    }
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("图层层级", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                Spacer(Modifier.weight(1f))
                
                // 定位按钮
                IconButton(onClick = { locateTrigger = org.gemini.ui.forge.getCurrentTimeMillis() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Layer", modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                // 添加按钮
                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.AddBox, contentDescription = "Add Layer", modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                // 重命名按钮
                IconButton(onClick = { showRenameDialog = selectedBlockId }, modifier = Modifier.size(24.dp), enabled = selectedBlockId != null) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename Layer", modifier = Modifier.size(16.dp))
                }
            }
            
            // 拖拽到根节点的高亮提示
            if (draggedBlockId != null && hoveredBlockId == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.hierarchy_move_to_top), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                }
            }            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    blocks.forEach { block ->
                        HierarchyItem(
                            block = block,
                            depth = 0,
                            isSelected = block.id == selectedBlockId,
                            isDragged = block.id == draggedBlockId,
                            isHovered = block.id == hoveredBlockId,
                            selectedBlockId = selectedBlockId,
                            draggedBlockId = draggedBlockId,
                            hoveredBlockId = hoveredBlockId,
                            dropPosition = dropPosition,
                            locateTrigger = locateTrigger,
                            onBlockClicked = onBlockClicked,
                            onBoundsCalculated = { id, rect -> itemBounds[id] = rect }
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }

        // 渲染跟随鼠标的拖拽阴影
        if (draggedBlockId != null && dragPosition != null) {
            val draggedBlock = findBlockById(blocks, draggedBlockId!!)
            if (draggedBlock != null) {
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(dragPosition!!.x.roundToInt() - 20, dragPosition!!.y.roundToInt() - 20) }
                        .alpha(0.85f),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = draggedBlock.type.getIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(draggedBlock.type.getDisplayNameRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
    for (block in blocks) {
        if (block.id == id) return block
        val found = findBlockById(block.children, id)
        if (found != null) return found
    }
    return null
}

private fun isDescendantOfLocal(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { isDescendantOfLocal(it, targetId) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HierarchyItem(
    block: UIBlock,
    depth: Int,
    isSelected: Boolean,
    isDragged: Boolean,
    isHovered: Boolean,
    dropPosition: org.gemini.ui.forge.domain.DropPosition,
    locateTrigger: Long,
    onBlockClicked: (String) -> Unit,
    onBoundsCalculated: (String, Rect) -> Unit,
    selectedBlockId: String?,
    draggedBlockId: String?,
    hoveredBlockId: String?
) {
    var expanded by remember { mutableStateOf(true) }
    val hasChildren = block.children.isNotEmpty()
    
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(selectedBlockId) {
        if (selectedBlockId != null && hasChildren) {
            if (isDescendantOfLocal(block, selectedBlockId)) {
                expanded = true
            }
        }
    }

    LaunchedEffect(locateTrigger) {
        if (locateTrigger > 0L && isSelected) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isHovered && isDragged.not() && draggedBlockId != null && dropPosition == org.gemini.ui.forge.domain.DropPosition.BEFORE) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF03A9F4)))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onGloballyPositioned { coords ->
                    onBoundsCalculated(block.id, coords.boundsInWindow())
                }
                .background(
                    when {
                        isHovered && draggedBlockId != null && dropPosition == org.gemini.ui.forge.domain.DropPosition.INSIDE -> 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        isDragged -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    }
                )
                .clickable { onBlockClicked(block.id) }
                .padding(start = (8 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(20.dp))
            }

            Spacer(Modifier.width(4.dp))
            
            Icon(
                imageVector = block.type.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.width(6.dp))

            Column {
                Text(
                    text = stringResource(block.type.getDisplayNameRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = block.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (isHovered && isDragged.not() && draggedBlockId != null && dropPosition == org.gemini.ui.forge.domain.DropPosition.AFTER && (!hasChildren || !expanded)) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF03A9F4)))
        }

        if (hasChildren && expanded) {
            block.children.forEach { child ->
                HierarchyItem(
                    block = child,
                    depth = depth + 1,
                    isSelected = child.id == selectedBlockId,
                    isDragged = child.id == draggedBlockId,
                    isHovered = child.id == hoveredBlockId,
                    dropPosition = dropPosition,
                    locateTrigger = locateTrigger,
                    onBlockClicked = onBlockClicked,
                    onBoundsCalculated = onBoundsCalculated,
                    selectedBlockId = selectedBlockId,
                    draggedBlockId = draggedBlockId,
                    hoveredBlockId = hoveredBlockId
                )
            }
            if (isHovered && isDragged.not() && draggedBlockId != null && dropPosition == org.gemini.ui.forge.domain.DropPosition.AFTER) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF03A9F4)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, UIBlockType, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(UIBlockType.PANEL) }
    var widthStr by remember { mutableStateOf("200") }
    var heightStr by remember { mutableStateOf("200") }
    var expandedType by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加新图层") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("图层名称/ID (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
                    OutlinedTextField(
                        value = stringResource(selectedType.getDisplayNameRes()),
                        onValueChange = {}, readOnly = true,
                        label = { Text("模块类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        UIBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(type.getDisplayNameRes())) },
                                onClick = { selectedType = type; expandedType = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = widthStr, onValueChange = { widthStr = it }, label = { Text("宽度") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = heightStr, onValueChange = { heightStr = it }, label = { Text("高度") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = widthStr.toFloatOrNull() ?: 200f
                val h = heightStr.toFloatOrNull() ?: 200f
                onConfirm(name, selectedType, w, h)
            }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameLayerDialog(
    initialId: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newId by remember { mutableStateOf(initialId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名图层") },
        text = {
            OutlinedTextField(
                value = newId,
                onValueChange = { newId = it },
                label = { Text("新 ID/名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(newId) }) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

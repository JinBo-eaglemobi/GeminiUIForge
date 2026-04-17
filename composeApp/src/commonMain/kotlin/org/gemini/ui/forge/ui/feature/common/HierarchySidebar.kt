package org.gemini.ui.forge.ui.feature.common

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.getIcon
import org.gemini.ui.forge.ui.dialog.AddLayerDialog
import org.gemini.ui.forge.ui.dialog.RenameLayerDialog

@Composable
fun HierarchySidebar(
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String?) -> Unit,
    onBlockDoubleClicked: (String) -> Unit = {},
    onMoveBlock: (String, String?, DropPosition) -> Unit = { _, _, _ -> },
    onAddCustomBlock: (String, UIBlockType, Float, Float) -> Unit = { _, _, _, _ -> },
    onRenameBlock: (String, String) -> Unit = { _, _ -> },
    onToggleVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onToggleAllVisibility: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false
) {
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredBlockId by remember { mutableStateOf<String?>(null) }
    var pressedBlockId by remember { mutableStateOf<String?>(null) }
    var dragShadowIcon by remember { mutableStateOf<ImageVector?>(null) }
    var dragShadowLabel by remember { mutableStateOf<String?>(null) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var isAutoTrackEnabled by remember { mutableStateOf(true) }
    var locateTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedBlockId, isAutoTrackEnabled) {
        if (isAutoTrackEnabled && selectedBlockId != null) {
            delay(100)
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

    if (showRenameDialog != null && selectedBlockId != null) {
        RenameLayerDialog(
            initialId = selectedBlockId,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newId ->
                onRenameBlock(selectedBlockId, newId)
                showRenameDialog = null
            }
        )
    }

    var dropPosition by remember { mutableStateOf(DropPosition.INSIDE) }

    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .onGloballyPositioned { listCoordinates = it }
            .pointerInput(blocks, isReadOnly) {
                if (isReadOnly) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val windowOffset = listCoordinates?.localToWindow(down.position) ?: down.position
                    val allIds = mutableSetOf<String>()
                    fun walk(l: List<UIBlock>) {
                        l.forEach { walk(it.children); allIds.add(it.id) }
                    }
                    walk(blocks)
                    val hit = itemBounds.entries.toList().asReversed()
                        .filter { it.key in allIds }
                        .find { it.value.contains(windowOffset) }
                    pressedBlockId = hit?.key
                }
            }
            .pointerInput(blocks, isReadOnly) {
                if (isReadOnly) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val sourceId = pressedBlockId
                        if (sourceId != null) {
                            draggedBlockId = sourceId
                            val blockObj = findBlockById(blocks, sourceId)
                            if (blockObj != null) {
                                dragShadowIcon = blockObj.type.getIcon()
                                dragShadowLabel = blockObj.id
                            }
                            dragPosition = offset
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = change.position
                        val windowOffset = listCoordinates?.localToWindow(change.position) ?: change.position
                        val hit = itemBounds.entries.toList().asReversed().find { it.value.contains(windowOffset) }
                        if (hit != null) {
                            hoveredBlockId = hit.key
                            val rect = hit.value
                            val margin = rect.height * 0.15f
                            val y = windowOffset.y
                            dropPosition = when {
                                y < rect.top + margin -> DropPosition.BEFORE
                                y > rect.bottom - margin -> DropPosition.AFTER
                                else -> DropPosition.INSIDE
                            }
                        } else {
                            hoveredBlockId = null
                            dropPosition = DropPosition.INSIDE
                        }
                    },
                    onDragEnd = {
                        if (draggedBlockId != null && hoveredBlockId != draggedBlockId) {
                            onMoveBlock(draggedBlockId!!, hoveredBlockId, dropPosition)
                        }
                        draggedBlockId = null
                        pressedBlockId = null
                        dragShadowIcon = null
                        dragShadowLabel = null
                        dragPosition = null
                    },
                    onDragCancel = {
                        draggedBlockId = null
                        pressedBlockId = null
                        dragShadowIcon = null
                        dragShadowLabel = null
                        dragPosition = null
                    }
                )
            }
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Layers,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("图层层级", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconToggleButton(
                    checked = isAutoTrackEnabled,
                    onCheckedChange = { isAutoTrackEnabled = it },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoTrackEnabled) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                        contentDescription = "Auto Track",
                        modifier = Modifier.size(18.dp),
                        tint = if (isAutoTrackEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                fun checkAllVisible(list: List<UIBlock>): Boolean {
                    return list.all { it.isVisible && checkAllVisible(it.children) }
                }

                val allVisible = blocks.isNotEmpty() && checkAllVisible(blocks)
                IconButton(onClick = { onToggleAllVisibility(!allVisible) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (allVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "All Vis",
                        modifier = Modifier.size(18.dp),
                        tint = if (allVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isReadOnly) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Default.AddBox, contentDescription = "Add", modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { if (selectedBlockId != null) showRenameDialog = selectedBlockId },
                        modifier = Modifier.size(28.dp),
                        enabled = selectedBlockId != null
                    ) {
                        Icon(
                            Icons.Default.DriveFileRenameOutline,
                            contentDescription = "Rename",
                            modifier = Modifier.size(18.dp),
                            tint = if (selectedBlockId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.4f
                            )
                        )
                    }
                }
            }
            if (!isReadOnly && draggedBlockId != null && hoveredBlockId == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(Res.string.hierarchy_move_to_top),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    blocks.forEach { block ->
                        key(block.id) {
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
                                onBlockDoubleClicked = onBlockDoubleClicked,
                                onBoundsCalculated = { id, rect -> itemBounds[id] = rect },
                                onToggleVisibility = onToggleVisibility
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
        if (dragShadowLabel != null && dragPosition != null) {
            Surface(
                modifier = Modifier.offset {
                    IntOffset(
                        dragPosition!!.x.roundToInt() - 20,
                        dragPosition!!.y.roundToInt() - 20
                    )
                }.alpha(0.85f),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dragShadowIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = dragShadowLabel!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HierarchyItem(
    block: UIBlock,
    depth: Int,
    isSelected: Boolean,
    isDragged: Boolean,
    isHovered: Boolean,
    dropPosition: DropPosition,
    locateTrigger: Long,
    onBlockClicked: (String?) -> Unit,
    onBlockDoubleClicked: (String) -> Unit,
    onBoundsCalculated: (String, Rect) -> Unit,
    selectedBlockId: String?,
    draggedBlockId: String?,
    hoveredBlockId: String?,
    onToggleVisibility: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val hasChildren = block.children.isNotEmpty()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(selectedBlockId) {
        if (selectedBlockId != null && hasChildren && isDescendantOfLocal(block, selectedBlockId)) {
            expanded = true
        }
    }
    LaunchedEffect(locateTrigger) { if (locateTrigger > 0L && isSelected) bringIntoViewRequester.bringIntoView() }

    val indicatorColor = Color(0xFF03A9F4)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onGloballyPositioned { coords -> onBoundsCalculated(block.id, coords.boundsInWindow()) }
                .drawWithContent {
                    drawContent()
                    if (isHovered && isDragged.not() && draggedBlockId != null) {
                        when (dropPosition) {
                            DropPosition.BEFORE -> drawLine(
                                color = indicatorColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 4f
                            )

                            DropPosition.AFTER -> if (!hasChildren || !expanded) drawLine(
                                color = indicatorColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 4f
                            )

                            else -> {}
                        }
                    }
                }
                .background(
                    when {
                        isHovered && draggedBlockId != null && dropPosition == DropPosition.INSIDE -> MaterialTheme.colorScheme.secondaryContainer.copy(
                            alpha = 0.8f
                        )

                        isDragged -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = { onBlockClicked(block.id) },
                    onDoubleClick = { onBlockDoubleClicked(block.id) }
                )
                .padding(start = (8 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else Spacer(Modifier.width(20.dp))
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = block.type.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            IconButton(onClick = { onToggleVisibility(block.id, !block.isVisible) }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (block.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Vis",
                    modifier = Modifier.size(16.dp),
                    tint = if (block.isVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    )
                )
            }
        }
        if (hasChildren && expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        onBlockDoubleClicked = onBlockDoubleClicked,
                        onBoundsCalculated = onBoundsCalculated,
                        selectedBlockId = selectedBlockId,
                        draggedBlockId = draggedBlockId,
                        hoveredBlockId = hoveredBlockId,
                        onToggleVisibility = onToggleVisibility
                    )
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
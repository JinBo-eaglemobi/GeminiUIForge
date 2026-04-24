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
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
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

/**
 * UI 图层层级面板组件
 * 负责渲染应用模块的层级结构（类似 Photoshop / Figma 的图层面板）。
 * 支持选中、重命名、隐藏/显示控制，以及长按拖拽调整图层层级（排序及父子嵌套关系）。
 *
 * @param blocks UI 模块数据列表（树形结构）
 * @param selectedBlockId 当前全局被选中的模块 ID
 * @param onBlockClicked 单击选中图层的回调
 * @param onBlockDoubleClicked 双击图层的回调（通常用于触发进入隔离编辑模式）
 * @param onMoveBlock 拖拽移动图层结束时的回调，参数：源图层ID, 目标图层ID(可为空代表移至顶层), 拖拽释放位置(Before/After/Inside)
 * @param onAddCustomBlock 顶部工具栏添加新图层的回调
 * @param onRenameBlock 触发图层重命名的回调
 * @param onToggleVisibility 切换单个图层显示/隐藏状态的回调
 * @param onToggleAllVisibility 批量切换所有图层显示/隐藏状态的回调
 * @param modifier 外部修饰符
 * @param isReadOnly 是否为只读模式（如果是，将禁用拖拽、重命名、添加等操作）
 */
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
    renameRequestEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = kotlinx.coroutines.flow.MutableSharedFlow(),
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false
) {
    // ---- 拖拽与高亮状态 ----
    var draggedBlockId by remember { mutableStateOf<String?>(null) } // 当前正在拖拽的源图层 ID
    var hoveredBlockId by remember { mutableStateOf<String?>(null) } // 当前被拖拽到的目标上方图层 ID
    var pressedBlockId by remember { mutableStateOf<String?>(null) } // 刚被按下但还未触发拖拽的图层 ID（用于防止误触）
    
    // ---- 拖拽时的浮动视觉提示状态 ----
    var dragShadowIcon by remember { mutableStateOf<ImageVector?>(null) }
    var dragShadowLabel by remember { mutableStateOf<String?>(null) }
    
    // ---- 坐标映射及碰撞检测缓存 ----
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) } // 面板容器的全局坐标，用于坐标转换
    val itemBounds = remember { mutableMapOf<String, Rect>() } // 缓存每个图层项渲染后在其 Window 中的位置边界
    var dragPosition by remember { mutableStateOf<Offset?>(null) } // 当前拖拽手指所处的实时位置
    
    // ---- 对话框及面板属性状态 ----
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var isAutoTrackEnabled by remember { mutableStateOf(true) } // 是否开启自动定位功能
    var locateTrigger by remember { mutableStateOf(0L) } // 触发器时间戳：通知内部组件执行自动滚动定位

    // 监听外部重命名请求
    LaunchedEffect(renameRequestEvent) {
        renameRequestEvent.collect {
            if (selectedBlockId != null && !isReadOnly) {
                showRenameDialog = selectedBlockId
            }
        }
    }

    // 监听 selectedBlockId 变化：当在外侧画布被选中时，延迟通知列表滚动定位到该图层
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
                VerticalScrollbarAdapter(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    scrollState = scrollState
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

/**
 * 递归查询指定 ID 对应的 UI 节点对象。
 * 供拖拽开始时收集节点图标和文案信息等。
 */
private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
    for (block in blocks) {
        if (block.id == id) return block
        val found = findBlockById(block.children, id)
        if (found != null) return found
    }
    return null
}

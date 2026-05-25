package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.ui.dialog.AddLayerDialog
import org.gemini.ui.forge.ui.dialog.RenameLayerDialog
import kotlin.time.Duration.Companion.milliseconds
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * UI 图层层级面板组件
 * 负责渲染应用模块的层级结构（类似 Photoshop / Figma 的图层面板）。
 * 支持选中、重命名、隐藏/显示控制，以及长按拖拽调整图层层级（排序及父子嵌套关系）。
 *
 * @param state 项目工作区状态
 * @param viewModel 项目工作区视图模型
 * @param modifier 外部修饰符
 */
@Composable
fun HierarchySidebar(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val blocks = state.currentPage?.blocks ?: emptyList()
    val selectedBlockId = state.selectedBlockId
    val selectedBlockIds = state.selectedBlockIds
    val isReadOnly = false
    val renameRequestEvent = viewModel.requestRenameEvent

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
    var expandCollapseTrigger by remember { mutableStateOf(0L to true) } // 新增：一键展开/折叠触发器 (时间戳 to 是否展开)

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
            delay(100.milliseconds)
            locateTrigger = getCurrentTimeMillis()
        }
    }

    if (showAddDialog) {
        AddLayerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { id, type, w, h ->
                viewModel.layoutEditor.addBlock(type)
                showAddDialog = false
            }
        )
    }

    if (showRenameDialog != null && selectedBlockId != null) {
        RenameLayerDialog(
            initialId = selectedBlockId,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newId ->
                viewModel.layoutEditor.renameBlock(selectedBlockId, newId)
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
                                val blockObj = blocks.findBlockById(sourceId)
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
                                viewModel.layoutEditor.moveBlock(draggedBlockId!!, hoveredBlockId, dropPosition)
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
                    modifier = Modifier.size(28.dp).tip("画布选中项自动追踪")
                ) {
                    Icon(
                        imageVector = if (isAutoTrackEnabled) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                        contentDescription = "Auto Track",
                        modifier = Modifier.size(18.dp),
                        tint = if (isAutoTrackEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { expandCollapseTrigger = getCurrentTimeMillis() to true },
                    modifier = Modifier.size(28.dp).tip(stringResource(Res.string.hierarchy_expand_all))
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand All",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { expandCollapseTrigger = getCurrentTimeMillis() to false },
                    modifier = Modifier.size(28.dp).tip(stringResource(Res.string.hierarchy_collapse_all))
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Collapse All",
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!isReadOnly) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(28.dp).tip("添加自定义模块")
                    ) { Icon(Icons.Default.AddBox, contentDescription = "Add", modifier = Modifier.size(18.dp)) }      
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { if (selectedBlockId != null) showRenameDialog = selectedBlockId },
                        modifier = Modifier.size(28.dp).tip("修改模块 ID"),
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
                Spacer(Modifier.width(4.dp))
                fun checkAllVisible(list: List<UIBlock>): Boolean {
                    return list.all { it.isVisible && checkAllVisible(it.children) }
                }

                val allVisible = blocks.isNotEmpty() && checkAllVisible(blocks)
                IconButton(
                    onClick = { viewModel.layoutEditor.toggleAllBlocksVisibility(!allVisible) }, 
                    modifier = Modifier.size(28.dp).tip("一键显示/隐藏所有图层")
                ) {        
                    Icon(
                        imageVector = if (allVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,       
                        contentDescription = "All Vis",
                        modifier = Modifier.size(18.dp),
                        tint = if (allVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            val interactionContext = remember(
                                selectedBlockId,
                                selectedBlockIds,
                                draggedBlockId,
                                hoveredBlockId,
                                dropPosition,
                                locateTrigger,
                                expandCollapseTrigger,
                                itemBounds
                            ) {
                                HierarchyInteractionContext(
                                    selectedBlockId = selectedBlockId,
                                    selectedBlockIds = selectedBlockIds,
                                    draggedBlockId = draggedBlockId,
                                    hoveredBlockId = hoveredBlockId,
                                    dropPosition = dropPosition,
                                    locateTrigger = locateTrigger,
                                    expandCollapseTrigger = expandCollapseTrigger,
                                    itemBounds = itemBounds
                                )
                            }
                            HierarchyItem(
                                block = block,
                                depth = 0,
                                viewModel = viewModel,
                                context = interactionContext
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
                    Spacer(Modifier.width(6.6.dp))
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

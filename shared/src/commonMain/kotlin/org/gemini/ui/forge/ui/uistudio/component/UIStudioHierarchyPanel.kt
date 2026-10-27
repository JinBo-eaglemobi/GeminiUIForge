package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.ui.component.getIcon
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.component.dialog.AppDraggableAlertDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.dialog.UIStudioRenameDialog
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*

/**
 * UI Studio 视图图层层级大纲面板组件 (Hierarchy Panel)。
 *
 * 遵循一文件一 Composable 铁律。
 * 树形展示当前激活视图页面内的所有组件，支持紧凑搜索过滤、长按拖拽调序/父子嵌套、
 * 纯容器约束弹窗确认、可见性控制与右键操作。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param assetPathResolver 哈希→素材相对路径解析器（素材库唯一账本）
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioHierarchyPanel(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    assetPathResolver: (String?) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val currentView = state.currentView
    var searchQuery by remember { mutableStateOf("") }

    // 拖拽与高亮碰撞状态
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredBlockId by remember { mutableStateOf<String?>(null) }
    var dropPosition by remember { mutableStateOf(DropPosition.INSIDE) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<String, Rect>() }

    // 重命名弹窗
    val renamingBlockId = state.renamingBlockId
    if (renamingBlockId != null) {
        val targetBlock = currentView?.blocks?.find { it.id == renamingBlockId }
        val initialName = targetBlock?.id ?: ""
        UIStudioRenameDialog(
            title = "重命名组件",
            initialName = initialName,
            onConfirm = { newName ->
                viewModel.renameBlock(renamingBlockId, newName, templateRepo)
            },
            onDismiss = { viewModel.cancelRenameBlock() }
        )
    }

    // 拖拽放入触发创建父容器确认弹窗
    val pendingContainer = state.pendingCreateContainer
    if (pendingContainer != null) {
        val (draggedId, targetId) = pendingContainer
        AppConfirmDialog(
            title = "创建父容器",
            message = "是否将组件「$draggedId」与「$targetId」放入新建的纯父容器中？",
            confirmText = "创建容器",
            onConfirm = { viewModel.confirmCreateContainer(draggedId, targetId, templateRepo) },
            onDismiss = { viewModel.cancelCreateContainer() }
        )
    }

    // 移出/删除容器最后一个子组件解散父容器确认弹窗
    val pendingRemoveAction = state.pendingRemoveLastChildAction
    if (pendingRemoveAction != null) {
        val (containerId, childId, isDelete) = pendingRemoveAction
        val actionText = if (isDelete) "删除" else "移出"
        AppConfirmDialog(
            title = "解散空父容器提示",
            message = "组件「$childId」是容器「$containerId」中的最后一个子图层。${actionText}后该父容器将自动解散删除，是否继续？",
            confirmText = "确认$actionText",
            isDestructive = isDelete,
            onConfirm = { viewModel.confirmRemoveLastChildAction(templateRepo) },
            onDismiss = { viewModel.cancelRemoveLastChildAction() }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.small)
                    .onGloballyPositioned { listCoordinates = it }
            ) {
                // 顶部工具栏：紧凑搜索框 + 一键锁定/解锁 + 一键显示/隐藏 + 一键折叠/展开 (严格与下方行项 X 坐标 1:1 对齐)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                ) {
                    UIStudioSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "搜索当前页面组件...",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 1. 一键全部锁定 / 解锁组件 (对齐下方行项的锁定开关)
                    val isAnyUnlocked = currentView?.blocks?.any { !it.isLocked } == true
                    IconButton(
                        onClick = { viewModel.toggleAllBlocksLocked(templateRepo) },
                        modifier = Modifier
                            .size(20.dp)
                            .tip(if (isAnyUnlocked) "锁定全部组件" else "解锁全部组件")
                    ) {
                        Icon(
                            imageVector = if (isAnyUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "锁定全部",
                            tint = if (isAnyUnlocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // 2. 一键全部显示 / 隐藏组件 (对齐下方行项的眼睛开关)
                    val isAnyHidden = currentView?.blocks?.any { !it.isVisible } == true
                    IconButton(
                        onClick = { viewModel.toggleAllBlocksVisibility(templateRepo) },
                        modifier = Modifier
                            .size(20.dp)
                            .tip(if (isAnyHidden) "显示全部组件" else "隐藏全部组件")
                    ) {
                        Icon(
                            imageVector = if (isAnyHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "显示全部",
                            tint = if (isAnyHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.extraSmall))

                if (currentView == null) {
                    // 未打开任何视图的空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = stringResource(Res.string.uistudio_hierarchy_empty_no_view),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val contextBlocks = remember(currentView, state.activeContainerId) {
                        val activeContainer = state.activeContainerId?.let { cid -> currentView.blocks.findBlockById(cid) as? UIStudioBlock }
                        activeContainer?.children ?: currentView.blocks.filter { it.parent == null }
                    }
                    val filteredBlocks = if (searchQuery.isBlank()) {
                        contextBlocks
                    } else {
                        contextBlocks.filter {
                            it.id.contains(searchQuery, ignoreCase = true) ||
                            it.type.name.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filteredBlocks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyText = when {
                                searchQuery.isNotBlank() -> "未匹配到相关组件"
                                state.activeContainerId != null -> stringResource(Res.string.uistudio_hierarchy_empty_container)
                                else -> stringResource(Res.string.uistudio_hierarchy_empty_page)
                            }
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(filteredBlocks, key = { it.id }) { block ->
                                UIStudioHierarchyItem(
                                    block = block,
                                    depth = 0,
                                    isAncestorHidden = false,
                                    viewModel = viewModel,
                                    templateRepo = templateRepo,
                                    draggedBlockId = draggedBlockId,
                                    hoveredBlockId = hoveredBlockId,
                                    dropPosition = dropPosition,
                                    assetPathResolver = assetPathResolver,
                                    onPositioned = { id, rect ->
                                        itemBounds[id] = rect
                                    },
                                    onStartDrag = { id, windowPos ->
                                        draggedBlockId = id
                                        dragPosition = listCoordinates?.windowToLocal(windowPos) ?: windowPos
                                    },
                                    onDrag = { windowPos ->
                                        dragPosition = listCoordinates?.windowToLocal(windowPos) ?: windowPos
                                        val allIds = contextBlocks.map { it.id }.toSet()

                                        // 直接使用窗口物理坐标与 itemBounds 进行 1:1 精准命中碰撞检测
                                        val hit = itemBounds.entries.toList().asReversed()
                                            .filter { it.key in allIds && it.key != draggedBlockId }
                                            .find { it.value.contains(windowPos) }

                                        if (hit != null) {
                                            hoveredBlockId = hit.key
                                            val rect = hit.value
                                            val margin = rect.height * 0.25f
                                            val y = windowPos.y
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
                                        val dragged = draggedBlockId
                                        val hovered = hoveredBlockId
                                        if (dragged != null && hovered != null && dragged != hovered) {
                                            viewModel.moveHierarchyBlock(dragged, hovered, dropPosition, templateRepo)
                                        } else if (dragged != null && hovered == null) {
                                            // 拖拽至列表空白处：视为移出父容器至根级末尾，自动检查并触发最后一个子组件保留/解散弹窗
                                            viewModel.moveHierarchyBlock(dragged, null, DropPosition.AFTER, templateRepo)
                                        }
                                        draggedBlockId = null
                                        hoveredBlockId = null
                                        dragPosition = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 拖拽悬浮预览阴影卡片 (跟随光标像素坐标精准吸附)
            val currentDragPos = dragPosition
            val currentDraggedId = draggedBlockId
            if (currentDragPos != null && currentDraggedId != null && currentView != null) {
                val draggedBlock = currentView.blocks.findBlockById(currentDraggedId)
                if (draggedBlock != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (currentDragPos.x - 40f).roundToInt().coerceAtLeast(0),
                                    (currentDragPos.y - 18f).roundToInt().coerceAtLeast(0)
                                )
                            }
                            .shadow(8.dp, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = draggedBlock.type.getIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = draggedBlock.id,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            // 拖拽移出最后一个子组件确认弹窗 (支持保留父组件或解散删除)
            state.pendingDragRemoveLastChild?.let { ctx ->
                AppDraggableAlertDialog(
                    onDismissRequest = { viewModel.cancelDragRemoveLastChild() },
                    title = {
                        Text(
                            text = "移出组件并处理父容器",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        Text(
                            text = "组件「${ctx.draggedId}」是父容器「${ctx.oldParentId}」内的最后一个子组件。\n\n移出后，您希望保留该空的父容器，还是将其一并解散删除？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.confirmDragRemoveLastChild(keepContainer = true, templateRepo)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("保留父组件")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.cancelDragRemoveLastChild() }) {
                                Text("取消")
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.confirmDragRemoveLastChild(keepContainer = false, templateRepo)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("解散并删除")
                            }
                        }
                    }
                )
            }
        }
    }
}

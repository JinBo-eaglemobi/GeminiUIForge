package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.Color

/**
 * 大纲图层层级树中所有节点共享的交互上下文。
 *
 * 聚合了本地拖拽、多选、悬停、一键展开和视口滚动信号。
 */
data class HierarchyInteractionContext(
    val selectedBlockId: String?,
    val selectedBlockIds: Set<String> = emptySet(),
    val draggedBlockId: String?,
    val hoveredBlockId: String?,
    val dropPosition: DropPosition,
    val locateTrigger: Long,
    val expandCollapseTrigger: Pair<Long, Boolean> = 0L to true,
    val itemBounds: MutableMap<String, androidx.compose.ui.geometry.Rect>
)

/**
 * 递归渲染的单一图层树节点组件。
 *
 * @param block UI 模块数据
 * @param depth 当前嵌套层级（控制缩进宽度）
 * @param viewModel 项目工作区全局控制器
 * @param context 树中所有子节点共享的本地交互上下文
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HierarchyItem(
    block: UIBlock,
    depth: Int,
    viewModel: org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel,
    context: HierarchyInteractionContext
) {
    // --- 内部化解构 Context 属性以实现声明式渲染 ---
    val selectedBlockId = context.selectedBlockId
    val selectedBlockIds = context.selectedBlockIds
    val draggedBlockId = context.draggedBlockId
    val hoveredBlockId = context.hoveredBlockId
    val dropPosition = context.dropPosition
    val locateTrigger = context.locateTrigger
    val expandCollapseTrigger = context.expandCollapseTrigger
    val itemBounds = context.itemBounds

    val isSelected = block.id == selectedBlockId || selectedBlockIds.contains(block.id)
    val isDragged = block.id == draggedBlockId
    val isHovered = block.id == hoveredBlockId

    var expanded by remember { mutableStateOf(true) }
    val hasChildren = block.children.isNotEmpty()
    var showContextMenu by remember { mutableStateOf(false) }

    // 针对每个图层项目，申请一个视口定位请求器
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isMultiSelectActive by remember { mutableStateOf(false) }

    // 自动展开包含选中图层的父级组
    LaunchedEffect(selectedBlockId) {
        if (selectedBlockId != null && hasChildren && isDescendantOfLocal(block, selectedBlockId)) {
            expanded = true
        }
    }

    // 当自动定位触发，且自身为选中图层时，请求滚动到视口中
    LaunchedEffect(locateTrigger) { if (locateTrigger > 0L && isSelected) bringIntoViewRequester.bringIntoView() }

    // 监听一键展开/折叠触发器
    LaunchedEffect(expandCollapseTrigger) {
        if (expandCollapseTrigger.first > 0L) {
            expanded = expandCollapseTrigger.second
        }
    }

    val indicatorColor = Color(0xFF03A9F4)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                // 报告自己的布局坐标给外层容器，用于碰撞检测
                .onGloballyPositioned { coords -> itemBounds[block.id] = coords.boundsInWindow() }
                .drawWithContent {
                    drawContent()
                    // 若被拖拽至该目标之上，渲染反馈指示器
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            isMultiSelectActive = event.keyboardModifiers.isShiftPressed ||
                                    event.keyboardModifiers.isCtrlPressed ||
                                    event.keyboardModifiers.isMetaPressed

                            // ★ 鼠标右键点击弹出上下文菜单
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                viewModel.onBlockClicked(block.id, false)
                                showContextMenu = true
                            }
                        }
                    }
                }
                .combinedClickable(
                    onClick = { viewModel.onBlockClicked(block.id, isMultiSelectActive) },
                    onDoubleClick = { viewModel.onBlockDoubleClicked(block.id) }
                )
                .padding(start = (8 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图层右键上下文操作菜单
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名图层") },
                    leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.onBlockClicked(block.id, false)
                        viewModel.layoutEditor.triggerRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("复制模块") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.onBlockClicked(block.id, false)
                        viewModel.layoutEditor.copy()
                    }
                )
                DropdownMenuItem(
                    text = { Text("剪切模块") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.onBlockClicked(block.id, false)
                        viewModel.layoutEditor.cut()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (hasChildren) "进入此组编辑" else "进入父组编辑") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.onBlockDoubleClicked(block.id)
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (block.isVisible) "隐藏图层" else "显示图层") },
                    leadingIcon = { Icon(if (block.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.layoutEditor.toggleBlockVisibility(block.id, !block.isVisible)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("删除模块", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showContextMenu = false
                        viewModel.showDeleteConfirmation(block.id)
                    }
                )
            }
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
            // 隐藏/显示眼睛图标，直接对 viewModel 发起行为控制
            IconButton(onClick = { viewModel.layoutEditor.toggleBlockVisibility(block.id, !block.isVisible) }, modifier = Modifier.size(24.dp)) {
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

        // 递归子节点渲染，极简状态透传
        if (hasChildren && expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                block.children.forEach { child ->
                    HierarchyItem(
                        block = child,
                        depth = depth + 1,
                        viewModel = viewModel,
                        context = context
                    )
                }
            }
        }
    }
}

private fun isDescendantOfLocal(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { isDescendantOfLocal(it, targetId) }
}
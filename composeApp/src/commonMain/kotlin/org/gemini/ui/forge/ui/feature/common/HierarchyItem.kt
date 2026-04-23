package org.gemini.ui.forge.ui.feature.common

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.getIcon
import org.jetbrains.compose.resources.stringResource

/**
 * 递归渲染的单一图层组件
 *
 * @param block UI 模块数据
 * @param depth 当前嵌套层级（控制缩进宽度）
 * @param isSelected 自身是否被选中
 * @param isDragged 自身是否正在被拖拽（渲染时如果命中，自身会降低不透明度隐藏）
 * @param isHovered 自身是否为当前被拖拽到的目标位置节点
 * @param dropPosition 当前的目标拖拽模式（作为前节点/作为后节点/作为内部子节点）
 * @param locateTrigger 发起自动定位到被选中项的强制时间戳信号
 * @param onBlockClicked 单击事件
 * @param onBlockDoubleClicked 双击事件
 * @param onBoundsCalculated 报告自身边界的回调，用于长按和拖拽碰撞检测
 * @param selectedBlockId 全局选中的 ID（用于传递给子组件）
 * @param draggedBlockId 全局拖拽中的源 ID（用于传递给子组件）
 * @param hoveredBlockId 全局悬停目标 ID（用于传递给子组件）
 * @param onToggleVisibility 隐藏/显示功能开关
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HierarchyItem(
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

    // 针对每个图层项目，申请一个视口定位请求器
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // 自动展开包含选中图层的父级组
    LaunchedEffect(selectedBlockId) {
        if (selectedBlockId != null && hasChildren && isDescendantOfLocal(block, selectedBlockId)) {
            expanded = true
        }
    }

    // 当自动定位触发，且自身为选中图层时，请求滚动到视口中
    LaunchedEffect(locateTrigger) { if (locateTrigger > 0L && isSelected) bringIntoViewRequester.bringIntoView() }

    val indicatorColor = Color(0xFF03A9F4)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                // 报告自己的布局坐标给外层容器，用于碰撞检测
                .onGloballyPositioned { coords -> onBoundsCalculated(block.id, coords.boundsInWindow()) }
                .drawWithContent {
                    drawContent()
                    // 若被拖拽至该目标之上，渲染反馈指示器：上面边缘高亮，下面边缘高亮
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
                // 如果自身被拖拽中，变淡显示；如果是目标且置入内部，增加底层高亮
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
            // 有子节点时提供展开/收起按钮
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

            // 模块的特定组件图标
            Icon(
                imageVector = block.type.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 图层组件类型名称
                Text(
                    text = stringResource(block.type.getDisplayNameRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                // 图层唯一 ID
                Text(
                    text = block.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            // 隐藏/显示眼睛图标
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

        // 递归子节点渲染
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

/**
 * 递归判断 targetId 是否为 currentBlock 的后代节点。
 */
private fun isDescendantOfLocal(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { isDescendantOfLocal(it, targetId) }
}
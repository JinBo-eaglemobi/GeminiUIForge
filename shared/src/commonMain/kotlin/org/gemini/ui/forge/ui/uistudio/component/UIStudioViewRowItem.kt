package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.contextMenuClickable
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 单个视图列表行组件。
 *
 * 支持左键单击切换、双端统一上下文菜单（PC 端鼠标右键 / 移动端长按）、快捷重命名与删除。
 *
 * @param page 当前视图页面数据
 * @param isSelected 当前是否被选中激活
 * @param canDelete 当前视图是否允许被删除（总视图数 > 1）
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板仓储
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioViewRowItem(
    page: UIStudioPage,
    isSelected: Boolean,
    canDelete: Boolean,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicTitleStyle = if (spacing.isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val dynamicSubtitleStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val dynamicMenuStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    var showContextMenu by remember { mutableStateOf(false) }
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isBeingDragged = viewModel.state.draggedPageId == page.id

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minMenuWidth = maxOf(200.dp, maxWidth)
        val maxMenuWidth = maxOf(260.dp, maxWidth)

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                isBeingDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            border = if (isSelected || isBeingDragged) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { rowCoords = it }
                .contextMenuClickable(
                    onClick = { viewModel.pageManager.selectView(page.id) },
                    onContextMenu = {
                        showContextMenu = true
                    }
                )
                .pointerInput(page.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!down.pressed) return@awaitEachGesture
                        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture

                        var isDragging = false
                        var totalDistance = 0f

                        drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            totalDistance += dragAmount.getDistance()
                            if (!isDragging && totalDistance > 6f) {
                                isDragging = true
                                viewModel.pageManager.setDraggedPageId(page.id)
                            }
                            if (isDragging) {
                                rowCoords?.localToWindow(change.position)?.let { winPos ->
                                    viewModel.viewport.updateDragCursorPosition(winPos)
                                }
                                change.consume()
                            }
                        }

                        if (isDragging) {
                            viewModel.pageManager.setDraggedPageId(null)
                            viewModel.viewport.updateDragCursorPosition(null)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = page.id,
                            style = dynamicTitleStyle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${page.width.toInt()} × ${page.height.toInt()} · ${page.blocks.size} 个图元",
                            style = dynamicSubtitleStyle,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.canvasEditor.startRenameView(page) },
                        modifier = Modifier
                            .size(24.dp)
                            .tip("重命名视图 (F2)")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename View",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (canDelete) {
                        IconButton(
                            onClick = { viewModel.pageManager.requestDeleteView(page) },
                            modifier = Modifier
                                .size(24.dp)
                                .tip("删除该视图 (Delete)")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete View",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 统一右键 (PC) / 长按 (移动端) 上下文菜单，自适应宽度 + 明确悬浮立体质感
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .widthIn(min = minMenuWidth, max = maxMenuWidth)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp))
                .padding(vertical = 4.dp)
        ) {
            DropdownMenuItem(
                text = { Text("切换至此视图", style = dynamicMenuStyle, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(spacing.iconCompactSize)) },
                onClick = {
                    showContextMenu = false
                    viewModel.pageManager.selectView(page.id)
                }
            )
            DropdownMenuItem(
                text = { Text("重命名视图 (F2)", style = dynamicMenuStyle, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(spacing.iconCompactSize)) },
                onClick = {
                    showContextMenu = false
                    viewModel.canvasEditor.startRenameView(page)
                }
            )
            DropdownMenuItem(
                text = { Text("克隆/复制此视图", style = dynamicMenuStyle, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(spacing.iconCompactSize)) },
                onClick = {
                    showContextMenu = false
                    viewModel.pageManager.duplicateView(page, templateRepo)
                }
            )
            DropdownMenuItem(
                text = { Text("作为组件实例插入当前视图", style = dynamicMenuStyle, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(spacing.iconCompactSize)) },
                onClick = {
                    showContextMenu = false
                    viewModel.canvasEditor.insertPageInstance(page.id, templateRepo)
                }
            )
            if (canDelete) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.extraSmall, horizontal = spacing.small),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                DropdownMenuItem(
                    text = { Text("删除视图 (Delete)", style = dynamicMenuStyle, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(spacing.iconCompactSize)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.pageManager.requestDeleteView(page)
                    }
                )
            }
        }
    }
}

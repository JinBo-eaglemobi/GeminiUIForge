package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    var showContextMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minMenuWidth = maxOf(200.dp, maxWidth)
        val maxMenuWidth = maxOf(260.dp, maxWidth)

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .contextMenuClickable(
                    onClick = { viewModel.selectView(page.id) },
                    onContextMenu = {
                        // 弹窗菜单不是选中功能，仅唤出菜单，不修改当前激活视图
                        showContextMenu = true
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.nameStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${page.width.toInt()} × ${page.height.toInt()} · ${page.blocks.size} 个图元",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.startRenameView(page) },
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
                            onClick = { viewModel.requestDeleteView(page) },
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
                text = { Text("切换至此视图", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.selectView(page.id)
                }
            )
            DropdownMenuItem(
                text = { Text("重命名视图 (F2)", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.startRenameView(page)
                }
            )
            DropdownMenuItem(
                text = { Text("克隆/复制此视图", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.duplicateView(page, templateRepo)
                }
            )
            if (canDelete) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                DropdownMenuItem(
                    text = { Text("删除视图 (Delete)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        viewModel.requestDeleteView(page)
                    }
                )
            }
        }
    }
}

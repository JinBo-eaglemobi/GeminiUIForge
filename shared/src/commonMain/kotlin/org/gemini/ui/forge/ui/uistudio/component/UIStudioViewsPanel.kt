package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.dialog.UIStudioRenameDialog

/**
 * UI Studio 左上侧视图界面管理面板
 *
 * 展示当前工程的所有视图界面 (UIStudioPage)，支持新建视图、搜索过滤、切换活跃视图与删除视图。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioViewsPanel(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val allPages = state.project?.studioPages ?: emptyList()
    val filteredPages = if (state.viewSearchQuery.isBlank()) {
        allPages
    } else {
        allPages.filter { it.nameStr.contains(state.viewSearchQuery, ignoreCase = true) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            // 顶部 Header 栏：标题 + 计数 + 新建按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Views",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.extraSmall))
                    Text(
                        text = "视图界面",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(spacing.extraSmall))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${allPages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { viewModel.openNewViewDialog() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .tip("新建视图界面 (UIStudioPage)")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("新建", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 独立紧凑自定义搜索框（彻底解决 M3 原生 OutlinedTextField 文字截断缺陷）
            UIStudioSearchField(
                value = state.viewSearchQuery,
                onValueChange = { viewModel.updateViewSearchQuery(it) },
                placeholder = "搜索视图..."
            )

            // 视图列表
            if (filteredPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.viewSearchQuery.isBlank()) "暂无视图，点击右上角「新建」" else "未找到匹配视图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.F2 -> {
                                        val currentPage = allPages.find { it.id == state.currentViewId }
                                        if (currentPage != null) {
                                            viewModel.startRenameView(currentPage)
                                            true
                                        } else false
                                    }
                                    Key.Delete, Key.Backslash -> {
                                        val currentPage = allPages.find { it.id == state.currentViewId }
                                        if (currentPage != null && allPages.size > 1) {
                                            viewModel.requestDeleteView(currentPage)
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredPages, key = { it.id }) { page ->
                        UIStudioViewRowItem(
                            page = page,
                            isSelected = page.id == state.currentViewId,
                            canDelete = allPages.size > 1,
                            viewModel = viewModel,
                            templateRepo = templateRepo
                        )
                    }
                }
            }
        }
    }

    // 挂载重命名视图弹窗
    state.renamingView?.let { page ->
        UIStudioRenameDialog(
            title = "重命名视图",
            initialName = page.nameStr,
            onConfirm = { newName ->
                viewModel.commitRenameView(page.id, newName, templateRepo)
            },
            onDismiss = { viewModel.cancelRenameView() }
        )
    }

    // 挂载删除视图确认弹窗（破坏性操作二次确认安全门禁）
    state.deletingView?.let { page ->
        AppConfirmDialog(
            title = "删除视图",
            message = "确定要删除视图「${page.nameStr}」吗？该视图下的所有图元将被物理移除且不可撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = { viewModel.confirmDeleteView(templateRepo) },
            onDismiss = { viewModel.cancelDeleteView() }
        )
    }
}

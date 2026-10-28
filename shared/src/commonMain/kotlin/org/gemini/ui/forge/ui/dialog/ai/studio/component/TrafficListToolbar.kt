package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.ui.component.AppSearchField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.component.AppMenu
import org.gemini.ui.forge.ui.component.AppMenuItemRow
import org.gemini.ui.forge.ui.component.AppMenuTier
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 报文档案列表紧凑工具栏：搜索 / 方向过滤 / 排序 / 清空
 *
 * 严格遵循一文件一 Composable 红线与 UI 人机工程学规范，标准高度 40dp。
 */
@Composable
fun TrafficListToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    directionFilter: TrafficDirection?,
    onDirectionFilterChange: (TrafficDirection?) -> Unit,
    sortOrder: TrafficSortOrder,
    onSortOrderChange: (TrafficSortOrder) -> Unit,
    isEnabled: Boolean,
    onClearRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isSortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 搜索框（弹性占满左侧余宽，34dp 紧凑高度与右侧药丸完美齐平）
        AppSearchField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = stringResource(Res.string.traffic_search_hint),
            enabled = isEnabled,
            height = 34.dp,
            modifier = Modifier.weight(1f)
        )

        // 2. 方向过滤药丸分段（全部 / REQ / RESP）
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier.height(34.dp)
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全部
                val isAllSelected = directionFilter == null
                Box(
                    modifier = Modifier
                        .clip(AppShapes.extraSmall)
                        .background(if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = isEnabled) { onDirectionFilterChange(null) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.traffic_filter_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // REQ
                val isReqSelected = directionFilter == TrafficDirection.REQ
                Box(
                    modifier = Modifier
                        .clip(AppShapes.extraSmall)
                        .background(if (isReqSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = isEnabled) { onDirectionFilterChange(TrafficDirection.REQ) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.traffic_filter_req),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReqSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // RESP
                val isRespSelected = directionFilter == TrafficDirection.RESP
                Box(
                    modifier = Modifier
                        .clip(AppShapes.extraSmall)
                        .background(if (isRespSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = isEnabled) { onDirectionFilterChange(TrafficDirection.RESP) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.traffic_filter_resp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRespSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. 排序下拉菜单
        Box {
            OutlinedButton(
                onClick = { isSortMenuOpen = true },
                enabled = isEnabled,
                shape = AppShapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(34.dp)
                    .tip(stringResource(Res.string.traffic_sort_menu_title))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(spacing.extraSmall))
                Text(
                    text = stringResource(sortOrder.labelRes),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            AppMenu(
                expanded = isSortMenuOpen,
                onDismissRequest = { isSortMenuOpen = false },
                tier = AppMenuTier.COMPACT,
                title = stringResource(Res.string.traffic_sort_menu_title)
            ) {
                TrafficSortOrder.entries.forEach { order ->
                    AppMenuItemRow(
                        title = stringResource(order.labelRes),
                        isSelected = (sortOrder == order),
                        onClick = {
                            onSortOrderChange(order)
                            isSortMenuOpen = false
                        }
                    )
                }
            }
        }

        // 4. 清空档案按钮（红色警示色）
        IconButton(
            onClick = onClearRequested,
            enabled = isEnabled,
            modifier = Modifier
                .size(34.dp)
                .tip(stringResource(Res.string.traffic_clear_menu_label))
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Clear traffic",
                tint = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.state.MentionState

/**
 * AI 输入框「@」智能联想浮层面板 (一文件一 Composable)
 */
@Composable
fun MentionDropdownPanel(
    state: MentionState,
    candidates: List<MentionItem>,
    onItemSelected: (MentionItem) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 480.dp,
    onDeleteItem: ((MentionItem) -> Unit)? = null
) {
    if (!state.isMentionMenuVisible) return

    val listState = rememberLazyListState()

    // 候选集更新时自动重置/约束聚焦索引，确保 PC 端始终默认选中第 1 项
    LaunchedEffect(candidates) {
        if (candidates.isNotEmpty()) {
            state.selectedIndex = state.selectedIndex.coerceIn(0, candidates.size - 1)
        } else {
            state.selectedIndex = 0
        }
    }

    // 自动跟随键盘聚焦上下移动滚动条
    LaunchedEffect(state.selectedIndex) {
        if (state.selectedIndex in candidates.indices) {
            listState.animateScrollToItem(state.selectedIndex)
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        if (candidates.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Text(
                    text = if (state.searchQuery.isBlank()) "暂无工程上下文可用" else "未找到与 \"${state.searchQuery}\" 相关的实体",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 事先计算各分类的不变起始偏移快照，彻底杜绝 LazyColumn 闭包可变变量延迟累加陷阱
                val grouped = candidates.groupBy { it.category }
                var runningOffset = 0
                val groupOffsets = grouped.map { (cat, items) ->
                    val start = runningOffset
                    runningOffset += items.size
                    Triple(cat, items, start)
                }

                groupOffsets.forEach { (category, items, startIndex) ->
                    item(key = "header_${category.name}") {
                        MentionCategoryHeader(
                            title = "${category.titleZh} (${items.size})"
                        )
                    }

                    items(items.size, key = { "item_${items[it].id}" }) { idx ->
                        val item = items[idx]
                        val itemGlobalIndex = startIndex + idx
                        val isFocused = itemGlobalIndex == state.selectedIndex

                        MentionItemRow(
                            item = item,
                            isFocused = isFocused,
                            onClick = { onItemSelected(item) },
                            onDelete = onDeleteItem?.let { del -> { del(item) } }
                        )
                    }
                }
            }
        }
    }
}

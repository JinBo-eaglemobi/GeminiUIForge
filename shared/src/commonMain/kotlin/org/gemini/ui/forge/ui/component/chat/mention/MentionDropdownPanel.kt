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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.mention_empty_no_context
import geminiuiforge.composeapp.generated.resources.mention_empty_not_found
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.state.MentionState
import kotlin.math.roundToInt

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
    onDeleteItem: ((MentionItem) -> Unit)? = null,
    hoverPreviewConfig: MentionHoverPreviewConfig? = null
) {
    if (!state.isMentionMenuVisible) return

    val listState = rememberLazyListState()

    var hovered by remember { mutableStateOf<Pair<MentionItem, IntOffset>?>(null) }
    var panelBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }

    val tokens = remember(state.searchQuery) {
        state.searchQuery.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }

    // 候选集更新时自动重置/约束聚焦索引，确保 PC 端始终默认选中第 1 项
    LaunchedEffect(candidates) {
        hovered = null
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

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    panelBoundsInWindow = IntRect(
                        left = pos.x.roundToInt(),
                        top = pos.y.roundToInt(),
                        right = (pos.x + coords.size.width).roundToInt(),
                        bottom = (pos.y + coords.size.height).roundToInt()
                    )
                }
        ) {
            if (candidates.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(
                        text = if (state.searchQuery.isBlank())
                            stringResource(Res.string.mention_empty_no_context)
                        else stringResource(Res.string.mention_empty_not_found, state.searchQuery),
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

                            var rowRelativeTop by remember { mutableStateOf(0) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { rowCoords ->
                                        val rowPos = rowCoords.positionInWindow()
                                        rowRelativeTop = (rowPos.y - panelBoundsInWindow.top).roundToInt()
                                    }
                            ) {
                                MentionItemRow(
                                    item = item,
                                    isFocused = isFocused,
                                    highlightTokens = tokens,
                                    onClick = { onItemSelected(item) },
                                    onDelete = onDeleteItem?.let { del -> { del(item) } },
                                    onHoverEvent = if (hoverPreviewConfig != null) {
                                        { enter ->
                                            hovered = if (enter) {
                                                item to IntOffset(0, rowRelativeTop)
                                            } else {
                                                if (hovered?.first?.id == item.id) null else hovered
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }

        // PC 端悬停预览 Popup 单例浮层
        if (hoverPreviewConfig != null && hovered != null) {
            MentionHoverPreviewPopup(
                hovered = hovered,
                panelBoundsInWindow = panelBoundsInWindow,
                config = hoverPreviewConfig
            )
        }
    }
}

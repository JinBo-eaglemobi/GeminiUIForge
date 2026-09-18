package org.gemini.ui.forge.ui.dialog.ai.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 格式化等宽代码审查展示组件 (FormattedCodeViewer)
 *
 * 具备优雅的行号排版、特定关键字/图元ID自动高亮与平滑自动滚动、以及一键复制能力。
 *
 * @param code 待展示的 Pretty JSON 代码字符串
 * @param highlightKeyword 目标高亮行匹配关键词（如当前选中的图元 ID）
 * @param title 顶部小标题（如 "当前代码 (Current)"）
 * @param titleContainerColor 标题容器底色
 * @param modifier 修饰符
 */
@Composable
fun FormattedCodeViewer(
    code: String,
    modifier: Modifier = Modifier,
    highlightKeyword: String? = null,
    title: String? = null,
    titleContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val copy = rememberClipboardAction()
    val lines = remember(code) { code.lines() }
    val listState = rememberLazyListState()

    // 监听高亮关键字变化，自动平滑滚动至目标代码行
    LaunchedEffect(highlightKeyword, lines) {
        if (!highlightKeyword.isNullOrBlank()) {
            val targetIndex = lines.indexOfFirst { it.contains(highlightKeyword) }
            if (targetIndex >= 0) {
                // 滚动至目标行并留出上方 2 行视野缓冲
                listState.animateScrollToItem(maxOf(0, targetIndex - 2))
            }
        }
    }

    Surface(
        modifier = modifier
            .clip(AppShapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), AppShapes.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = AppShapes.medium
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 顶部小工具栏
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(titleContainerColor.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { copy(code, "已复制完整代码到剪贴板") },
                        modifier = Modifier.size(24.dp).tip("复制完整代码")
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // 2. 带行号的代码主体展示区域
            SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isHighlighted = !highlightKeyword.isNullOrBlank() && line.contains(highlightKeyword)
                        val rowBg = if (isHighlighted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 行号 (右对齐)
                            Text(
                                text = "${index + 1}".padStart(4, ' '),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.width(36.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            // 代码正文
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

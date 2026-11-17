package org.gemini.ui.forge.ui.dialog.ai.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.code_block_copied_toast
import geminiuiforge.composeapp.generated.resources.code_block_copy_tip
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

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
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    val lines = remember(code) { code.lines() }
    val listState = rememberLazyListState()
    val copiedToast = stringResource(Res.string.code_block_copied_toast)
    val copyTip = stringResource(Res.string.code_block_copy_tip)

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
                        .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppIconButton(
                        onClick = { copy(code, copiedToast) },
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = copyTip,
                        tooltip = copyTip,
                        tier = AppIconButtonTier.COMPACT
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // 2. 带行号的代码主体展示区域（接入统一纵向滚动条适配器）
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = spacing.extraSmall,
                                bottom = spacing.extraSmall,
                                end = spacing.small
                            )
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
                                    .padding(horizontal = spacing.small, vertical = 1.dp),
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
                                    modifier = Modifier.width(spacing.codeLineNumberWidth)
                                )
                                Spacer(Modifier.width(spacing.small))
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

                VerticalScrollbarAdapter(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    scrollState = listState
                )
            }
        }
    }
}

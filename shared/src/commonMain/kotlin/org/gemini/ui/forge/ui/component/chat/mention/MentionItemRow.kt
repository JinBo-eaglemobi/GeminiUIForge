package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.mention_clear_history_tip
import geminiuiforge.composeapp.generated.resources.mention_press_enter_tip
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.utils.MentionFuzzyMatcher

/**
 * 联想浮层单个条目行项组件 (一文件一 Composable)
 */
@Composable
fun MentionItemRow(
    item: MentionItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    highlightTokens: List<String> = emptyList(),
    onHoverEvent: ((Boolean) -> Unit)? = null
) {
    val bgColor = if (isFocused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    val borderModifier = if (isFocused) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            shape = RoundedCornerShape(6.dp)
        )
    } else {
        Modifier
    }

    val hoverModifier = if (onHoverEvent != null) {
        Modifier.pointerInput(onHoverEvent) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter -> onHoverEvent(true)
                        PointerEventType.Exit -> onHoverEvent(false)
                    }
                }
            }
        }
    } else {
        Modifier
    }

    val clearTip = stringResource(Res.string.mention_clear_history_tip)
    val enterTip = stringResource(Res.string.mention_press_enter_tip)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(borderModifier)
            .then(hoverModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 1. 左侧图标
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        // 2. 中段标题与副标题
        val primaryColor = MaterialTheme.colorScheme.primary
        val annotatedTitle = remember(item.title, highlightTokens, primaryColor) {
            if (highlightTokens.isEmpty()) {
                AnnotatedString(item.title)
            } else {
                val hitSet = sortedSetOf<Int>()
                highlightTokens.forEach { token ->
                    MentionFuzzyMatcher.matchField(token, item.title)?.let { hitSet.addAll(it.hitIndices) }
                }
                buildAnnotatedString {
                    append(item.title)
                    hitSet.forEach { idx ->
                        if (idx in item.title.indices) {
                            addStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                start = idx,
                                end = idx + 1
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = annotatedTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. 历史引用删除按钮（仅 DOC 类别显示）
        if (item.category == MentionCategory.DOC && onDelete != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDelete() }
                    .tip(clearTip)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = clearTip,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 4. 聚焦态快捷提示
        if (isFocused) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = enterTip,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

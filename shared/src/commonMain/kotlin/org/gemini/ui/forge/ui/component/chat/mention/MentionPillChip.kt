package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.ui.component.tip

/**
 * 单个已挂载工程上下文药丸胶囊 (一文件一 Composable)
 */
@Composable
fun MentionPillChip(
    item: MentionItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when (item.category) {
        MentionCategory.VIEW -> MaterialTheme.colorScheme.secondaryContainer
        MentionCategory.BLOCK -> MaterialTheme.colorScheme.tertiaryContainer
        MentionCategory.ASSET -> MaterialTheme.colorScheme.primaryContainer
        MentionCategory.DOC -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (item.category) {
        MentionCategory.VIEW -> MaterialTheme.colorScheme.onSecondaryContainer
        MentionCategory.BLOCK -> MaterialTheme.colorScheme.onTertiaryContainer
        MentionCategory.ASSET -> MaterialTheme.colorScheme.onPrimaryContainer
        MentionCategory.DOC -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val tipText = buildString {
        append("[${item.category.titleZh}] ${item.title}")
        if (!item.subtitle.isNullOrBlank()) {
            append(" · ${item.subtitle}")
        }
        if (onClick != null) {
            append(" (点击预览)")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
        modifier = modifier.tip(tipText)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            ) {
                if (item.icon != null) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除引用",
                    modifier = Modifier.size(11.dp),
                    tint = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

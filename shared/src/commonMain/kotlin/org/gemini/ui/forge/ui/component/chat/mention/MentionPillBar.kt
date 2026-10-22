package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.MentionItem

/**
 * 输入框上方已挂载工程上下文药丸排列栏 (一文件一 Composable)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MentionPillBar(
    mentions: List<MentionItem>,
    onRemove: (MentionItem) -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: ((MentionItem) -> Unit)? = null
) {
    if (mentions.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        mentions.forEach { item ->
            MentionPillChip(
                item = item,
                onRemove = { onRemove(item) },
                onClick = onItemClick?.let { click -> { click(item) } }
            )
        }
    }
}

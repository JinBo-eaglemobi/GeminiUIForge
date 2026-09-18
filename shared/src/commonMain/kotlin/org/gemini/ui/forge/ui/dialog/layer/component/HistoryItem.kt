package org.gemini.ui.forge.ui.dialog.layer.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.model.history.HistoryEntry
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 历史记录条目组件 (HistoryItem)
 *
 * 展示操作标签及精确的变更时间戳。
 *
 * @param entry 历史记录实体
 * @param isRedo 是否属于重做队列 (未来状态)
 * @param onClick 点击跳转回溯回调
 */
@Composable
fun HistoryItem(
    entry: HistoryEntry,
    isRedo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = formatTimestamp(entry.timestamp, "HH:mm:ss")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .tip("点击跳转回溯至该历史快照状态 ($formattedTime)"),
        color = Color.Transparent,
        shape = AppShapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(8.dp).background(
                    if (isRedo) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    shape = CircleShape
                )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRedo) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

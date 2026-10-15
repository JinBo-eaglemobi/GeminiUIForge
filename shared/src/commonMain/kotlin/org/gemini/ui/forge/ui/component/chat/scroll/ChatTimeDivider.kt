package org.gemini.ui.forge.ui.component.chat.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.formatTimestamp

/**
 * 聊天会话智能时间分组分割标签 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 居中微胶囊呈现，不破坏聊天视觉流；
 * 2. 格式化输出消息间隔时间（跨天显示日期，当天显示时分）；
 * 3. 低饱和度柔和视觉，与 Material 3 背景优雅融合。
 */
@Composable
fun ChatTimeDivider(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(timestamp) {
        formatTimestamp(timestamp, "MM-dd HH:mm")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

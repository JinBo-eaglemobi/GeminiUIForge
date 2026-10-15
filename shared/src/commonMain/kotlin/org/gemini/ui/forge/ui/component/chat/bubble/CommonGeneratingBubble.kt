package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.markdown.AppMarkdown
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用生成中打字/思考状态气泡组件 (一文件一 Composable)
 */
@Composable
fun CommonGeneratingBubble(
    streamingText: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp).tip("AI 思考与生成中")
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(spacing.small))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                if (streamingText.isNotBlank()) {
                    AppMarkdown(
                        content = streamingText
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 正在深度思考与组织回复中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

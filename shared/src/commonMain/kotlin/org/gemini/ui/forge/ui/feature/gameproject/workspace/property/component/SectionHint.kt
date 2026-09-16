package org.gemini.ui.forge.ui.feature.gameproject.workspace.property.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 分区空态提示组件。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun SectionHint(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)
    )
}

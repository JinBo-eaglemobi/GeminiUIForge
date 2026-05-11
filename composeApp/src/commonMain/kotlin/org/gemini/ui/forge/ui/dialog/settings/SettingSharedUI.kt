package org.gemini.ui.forge.ui.dialog.settings


import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing


/**
 * 设置区块标题组件
 *
 * 用于在设置对话框中统一展示各个区块的标题样式。
 *
 * @param title 标题文本内容
 */
@Composable
fun SettingSectionTitle(title: String) {
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = LocalAppSpacing.current.small)
    )
}
package org.gemini.ui.forge.ui.feature.gameproject.workspace.property.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.gameproject.DebugTreeNode

/**
 * 调试树节点行组件。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun DebugTreeRow(
    node: DebugTreeNode,
    depth: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable { onSelect(); if (node.item.isNotEmpty()) onToggle() }
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
            )
            .padding(start = (6 + depth * 12).dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = node.text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

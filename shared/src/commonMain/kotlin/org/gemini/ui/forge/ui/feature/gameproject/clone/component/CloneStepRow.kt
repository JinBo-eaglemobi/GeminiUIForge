package org.gemini.ui.forge.ui.feature.gameproject.clone.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 步骤进行状态
 */
enum class CloneStepState { PENDING, ACTIVE, DONE, FAILED }

/**
 * 单个步骤行组件：图标 + 文案。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CloneStepRow(
    label: String,
    state: CloneStepState,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        when (state) {
            CloneStepState.DONE -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            CloneStepState.ACTIVE -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            CloneStepState.FAILED -> Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            CloneStepState.PENDING -> Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(12.dp)
            ) {}
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                CloneStepState.DONE, CloneStepState.ACTIVE -> MaterialTheme.colorScheme.onSurface
                CloneStepState.FAILED -> MaterialTheme.colorScheme.error
                CloneStepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (state == CloneStepState.ACTIVE) FontWeight.Bold else FontWeight.Normal
        )
    }
}

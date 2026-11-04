package org.gemini.ui.forge.ui.uistudio.verify.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.verify.model.MattingStepItem
import org.gemini.ui.forge.ui.uistudio.verify.model.MattingStepStatus

/**
 * AI 原图去背 Task 单个大步骤行项组件 (一文件一 Composable)
 */
@Composable
fun MattingTaskStepRow(
    step: MattingStepItem,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val isRunning = step.status == MattingStepStatus.RUNNING
    val isCompleted = step.status == MattingStepStatus.COMPLETED

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        border = if (isRunning) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 左侧状态图标
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step.status) {
                    MattingStepStatus.PENDING -> {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "等待中",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    MattingStepStatus.RUNNING -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    MattingStepStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. 中间步骤文本与动态子信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${step.stepIndex}. ${step.title}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = when {
                        isRunning -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.outline
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 细粒度子进度条 (如有)
                if (isRunning && step.progressRatio != null) {
                    LinearProgressIndicator(
                        progress = { step.progressRatio },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                // 动态状态输出 (如有)
                if (isRunning && step.detail.isNotBlank()) {
                    Text(
                        text = step.detail,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. 右侧该步骤独立耗时药丸
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    isCompleted -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            ) {
                Text(
                    text = when (step.status) {
                        MattingStepStatus.PENDING -> "--"
                        MattingStepStatus.RUNNING -> "⏱️ ${((step.durationSeconds * 10).toInt() / 10f)}s"
                        MattingStepStatus.COMPLETED -> "${((step.durationSeconds * 10).toInt() / 10f)}s"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    color = when {
                        isRunning -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

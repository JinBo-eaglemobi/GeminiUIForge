package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.SliceItemStatus
import org.gemini.ui.forge.ui.uistudio.SliceTaskItemState

/**
 * 图集切片任务单项状态卡片
 *
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun AssetSlicingItemCard(
    item: SliceTaskItemState,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    val cardBg = if (isCurrent) {
        colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val borderColor = if (isCurrent) {
        colorScheme.primary
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isCurrent) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(cardBg)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 状态指示图标/转圈
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (item.status) {
                SliceItemStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "排队中",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                SliceItemStatus.SLICING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.secondary
                    )
                }
                SliceItemStatus.SLICED -> {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "已切片",
                        tint = colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                SliceItemStatus.MATTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary
                    )
                }
                SliceItemStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已完成",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
                SliceItemStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "异常",
                        tint = colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 类型胶囊微标
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isCurrent) colorScheme.primary.copy(alpha = 0.15f) else colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 1.dp)
        ) {
            Text(
                text = item.category.uppercase().take(6).ifBlank { "ASSET" },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (isCurrent) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // 资源名称
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isCurrent) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 尺寸规格
        Text(
            text = "${item.width} × ${item.height}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // 状态文字说明
        val (statusText, statusColor) = when (item.status) {
            SliceItemStatus.PENDING -> "等待中" to colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            SliceItemStatus.SLICING -> "切片中" to colorScheme.secondary
            SliceItemStatus.SLICED -> "已切片" to colorScheme.secondary
            SliceItemStatus.MATTING -> "去背中" to colorScheme.primary
            SliceItemStatus.SUCCESS -> "已就绪" to Color(0xFF4CAF50)
            SliceItemStatus.FAILED -> "失败" to colorScheme.error
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = statusColor
        )
    }
}

package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.SliceItemStatus
import org.gemini.ui.forge.ui.uistudio.SliceStage
import org.gemini.ui.forge.ui.uistudio.SliceTaskItemState

/**
 * 格式化耗时毫秒数为 mm:ss.S 格式
 */
private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    val tenths = (millis % 1000) / 100
    val mm = if (m < 10) "0$m" else "$m"
    val ss = if (s < 10) "0$s" else "$s"
    return "$mm:$ss.$tenths"
}

/**
 * 图集切片物理落盘与全量资源全景流转进度遮罩
 *
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun AssetSlicingProgressOverlay(
    progress: Float,
    statusText: String,
    stage: SliceStage,
    taskItems: List<SliceTaskItemState>,
    currentItemName: String,
    startTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    // 动态秒表计时器 (每 80ms 刷新一次已用耗时与预估剩余)
    var nowMillis by remember { mutableStateOf(getCurrentTimeMillis()) }
    LaunchedEffect(startTimeMillis) {
        while (isActive) {
            nowMillis = getCurrentTimeMillis()
            delay(80)
        }
    }

    val elapsedMillis = if (startTimeMillis > 0L) (nowMillis - startTimeMillis).coerceAtLeast(0L) else 0L
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200)
    )

    val totalItems = taskItems.size
    val successCount = taskItems.count { it.status == SliceItemStatus.SUCCESS }

    // 预估剩余时间推算
    val remainingText = remember(animatedProgress, elapsedMillis, totalItems, successCount) {
        if (animatedProgress >= 0.99f || stage == SliceStage.FINISHED) {
            "已完成"
        } else if (animatedProgress > 0.05f && elapsedMillis > 1000L) {
            val estimatedTotal = (elapsedMillis / animatedProgress).toLong()
            val remainSec = ((estimatedTotal - elapsedMillis).coerceAtLeast(0L) / 1000f)
            "剩余 ~${(remainSec * 10).toInt() / 10f}s"
        } else {
            "估算中..."
        }
    }

    // 处理速率 (张/秒)
    val speedText = remember(elapsedMillis, successCount) {
        val elapsedSec = elapsedMillis / 1000f
        if (elapsedSec > 0.5f && successCount > 0) {
            val speed = (successCount / elapsedSec)
            "${(speed * 10).toInt() / 10f} 项/s"
        } else {
            "--"
        }
    }

    // 列表状态与自动滚动跟随
    val listState = rememberLazyListState()
    LaunchedEffect(currentItemName) {
        if (currentItemName.isNotBlank() && taskItems.isNotEmpty()) {
            val index = taskItems.indexOfFirst { it.name == currentItemName }
            if (index >= 0) {
                listState.animateScrollToItem((index - 2).coerceAtLeast(0))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .heightIn(min = 360.dp, max = 560.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface,
            tonalElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // 顶部 Header: 标题 + 阶段微标 + 实时耗时胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Text(
                                text = "图集切片与智能入库",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colorScheme.onSurface
                            )

                            // 阶段状态胶囊
                            val (stageLabel, stageIcon, stageColor) = when (stage) {
                                SliceStage.PREPARING -> Triple("准备图集", Icons.Default.Schedule, colorScheme.outline)
                                SliceStage.SLICING -> Triple("极速切片中", Icons.Default.ContentCut, colorScheme.secondary)
                                SliceStage.MATTING -> Triple("AI 批量去背", Icons.Default.AutoAwesome, colorScheme.primary)
                                SliceStage.REGISTERING -> Triple("登记入库", Icons.Default.Schedule, colorScheme.tertiary)
                                SliceStage.FINISHED -> Triple("完成", Icons.Default.DoneAll, Color(0xFF4CAF50))
                            }

                            Surface(
                                shape = CircleShape,
                                color = stageColor.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, stageColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = stageIcon,
                                        contentDescription = null,
                                        tint = stageColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = stageLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = stageColor
                                    )
                                }
                            }
                        }

                        Text(
                            text = "微秒级多核裁剪与常驻单进程 AI 批量去背景",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // 右上角实时耗时与剩余指标胶囊
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "已用时间",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatElapsed(elapsedMillis),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colorScheme.primary
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(1.dp),
                                color = colorScheme.outlineVariant
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "预估剩余",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = remainingText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                // 核心指标行: 进度条 + 百分比 + 已就绪计数 + 速率
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = colorScheme.primary
                            )

                            Text(
                                text = "($successCount / $totalItems 项就绪)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "速率: $speedText",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 整切待处理与流转资源全景列表 (All Slicing Items)
                Text(
                    text = "整切资源流转清单 (${totalItems} 项)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.small),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(taskItems, key = { _, item -> item.id }) { _, item ->
                            val isCurrent = item.name == currentItemName || (item.status == SliceItemStatus.SLICING || item.status == SliceItemStatus.MATTING)
                            AssetSlicingItemCard(
                                item = item,
                                isCurrent = isCurrent
                            )
                        }
                    }
                }

                // 底部状态描述行
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary
                        )

                        Text(
                            text = statusText.ifBlank { "正在全力处理中..." },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

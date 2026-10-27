package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.ui.uistudio.component.UIStudioChildPreviewThumbnail

/**
 * 悬停预览浮层中渲染视图画布微缩或容器子图元微缩排版的组件 (一文件一 Composable)
 */
@Composable
fun MentionHoverCanvasPreview(
    title: String,
    subtitle: String?,
    blocks: List<UIStudioBlock>,
    canvasWidth: Float,
    canvasHeight: Float,
    config: MentionHoverPreviewConfig,
    modifier: Modifier = Modifier
) {
    val safeWidth = canvasWidth.coerceAtLeast(1f)
    val safeHeight = canvasHeight.coerceAtLeast(1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. 微缩画布视口
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 6.dp)
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            ) {
                val availW = maxWidth.value
                val availH = maxHeight.value
                val scale = minOf(availW / safeWidth, availH / safeHeight)

                Box(
                    modifier = Modifier
                        .size((safeWidth * scale).dp, (safeHeight * scale).dp)
                        .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                ) {
                    blocks.forEach { child ->
                        if (child.isVisible) {
                            UIStudioChildPreviewThumbnail(
                                child = child,
                                scale = scale,
                                inheritedOpacity = child.effectiveOpacity,
                                assetPathResolver = config.resolveAssetPathByHash
                            )
                        }
                    }
                }
            }
        }

        // 2. 下方信息卡片
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${safeWidth.toInt()} × ${safeHeight.toInt()} px",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

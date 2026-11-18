package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import kotlin.math.roundToInt

/**
 * 画布右上角悬浮视口缩放与自适应居中工具栏。
 * 包含缩小、当前缩放比（点击重置 100%）、放大、自适应居中。
 */
@Composable
fun UIStudioCanvasToolbar(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier,
    containerSize: IntSize = IntSize.Zero
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val zoomPercent = (state.scale * 100f).roundToInt()
    val dynamicTextStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Surface(
        color = Color(0xDD1E293B),
        shape = AppShapes.medium,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .shadow(elevation = 6.dp, shape = AppShapes.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = 2.dp)
        ) {
            // 1. 缩小
            AppIconButton(
                imageVector = Icons.Default.Remove,
                contentDescription = "缩小",
                onClick = { viewModel.viewport.zoomOut() },
                tier = AppIconButtonTier.COMPACT,
                tint = Color.White.copy(alpha = 0.9f),
                tooltip = "缩小画布 (滚轮向下)"
            )

            // 2. 缩放百分比胶囊（点击还原 100% 原寸，不影响画布坐标）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(AppShapes.small)
                    .clickable {
                        viewModel.viewport.resetZoomTo100()
                    }
                    .padding(horizontal = spacing.small, vertical = spacing.extraSmall)
                    .tip("还原为 100% 原始比例 (保持当前坐标)")
            ) {
                Text(
                    text = "$zoomPercent%",
                    style = dynamicTextStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF38BDF8)
                )
            }

            // 3. 放大
            AppIconButton(
                imageVector = Icons.Default.Add,
                contentDescription = "放大",
                onClick = { viewModel.viewport.zoomIn() },
                tier = AppIconButtonTier.COMPACT,
                tint = Color.White.copy(alpha = 0.9f),
                tooltip = "放大画布 (滚轮向上)"
            )

            // 分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(spacing.iconCompactSize)
                    .padding(horizontal = 2.dp)
            ) {
                VerticalDivider(color = Color.White.copy(alpha = 0.2f))
            }

            // 4. 一键还原 100% 缩放值
            AppIconButton(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "还原 100%",
                onClick = { viewModel.viewport.resetZoomTo100() },
                tier = AppIconButtonTier.COMPACT,
                tint = Color(0xFF38BDF8),
                tooltip = "一键还原 100% 缩放值 (保持当前坐标)"
            )

            // 5. 一键自适应居中
            AppIconButton(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "居中",
                onClick = { viewModel.viewport.triggerFitToViewport() },
                tier = AppIconButtonTier.COMPACT,
                tint = Color(0xFFA78BFA),
                tooltip = "一键自适应视口居中"
            )
        }
    }
}

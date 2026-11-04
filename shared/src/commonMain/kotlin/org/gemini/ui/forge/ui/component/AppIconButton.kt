package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全局统一标准图标按钮组件。
 *
 * 遵循 8pt/4pt 人机工程学空间网格系统与严格的尺寸比例守恒定律：
 * - 外部通过 [tier] 统一锁定容器尺寸与内部图标尺寸（[AppIconButtonTier]），杜绝随手手写导致的大小参差不齐；
 * - 自动通过 [tooltip] 挂载 PC 端悬浮提示气泡（[Modifier.tip]），内置视口边界防遮挡自适应；
 * - 完整支持 [AppIconButtonVariant] 四种视觉形态（幽灵、微底板、描边、实色填充）；
 * - 内置 [isLoading] 加载态防重点击与平滑指示器，以及 [enabled] 禁用态优雅变暗。
 *
 * @param onClick 点击事件回调。
 * @param modifier 外部修饰符。注意：尺寸由 [tier] 自动接管，外部严禁擅自覆盖 width / height / size。
 * @param imageVector 适量图标（与 [painter]、[content] 三选一）。
 * @param painter 绘图画笔（与 [imageVector]、[content] 三选一）。
 * @param contentDescription 无障碍与屏幕阅读器描述文本。
 * @param tooltip 悬浮操作提示文案。非空时自动挂载气泡提示。
 * @param tier 尺寸分级，默认为 [AppIconButtonTier.COMPACT]（28dp 容器 / 16dp 图标）。
 * @param variant 视觉变体，默认为 [AppIconButtonVariant.GHOST]（常态透明，悬停微高亮）。
 * @param tint 图标前景色。为 null 时自适应遵循主题与变体样式。
 * @param enabled 是否可交互。
 * @param isLoading 是否处于加载中状态。为 true 时阻断点击并呈现旋转进度圈。
 * @param shape 按钮圆角形状。为 null 时遵循当前 [tier] 的标准人机工程学圆角。
 * @param content 自定义图标插槽。用于需要图标旋转动画（graphicsLayer）或状态淡入淡出（Crossfade）的复杂场景。
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    contentDescription: String? = null,
    tooltip: String? = null,
    tier: AppIconButtonTier = AppIconButtonTier.COMPACT,
    variant: AppIconButtonVariant = AppIconButtonVariant.GHOST,
    tint: Color? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape? = null,
    content: (@Composable () -> Unit)? = null
) {
    val spacing = LocalAppSpacing.current
    val buttonSize = tier.buttonSize(spacing)
    val iconSize = tier.iconSize(spacing)
    val buttonShape = shape ?: RoundedCornerShape(tier.defaultCornerRadius)

    // 解析变体颜色配置
    val effectiveColors = resolveIconButtonColors(
        variant = variant,
        tint = tint,
        enabled = enabled
    )

    // 构建边框修饰（仅描边型启用）
    val borderModifier = if (variant == AppIconButtonVariant.OUTLINED) {
        val borderColor = if (enabled) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        }
        Modifier.border(BorderStroke(1.dp, borderColor), buttonShape)
    } else {
        Modifier
    }

    val effectiveTint = tint ?: when (variant) {
        AppIconButtonVariant.FILLED -> MaterialTheme.colorScheme.onPrimary
        AppIconButtonVariant.FILLED_TONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        AppIconButtonVariant.GHOST, AppIconButtonVariant.OUTLINED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = effectiveColors,
        modifier = modifier
            .size(buttonSize)
            .clip(buttonShape)
            .then(borderModifier)
            .tip(tooltip)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(buttonSize)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize * 0.9f),
                    strokeWidth = 2.dp,
                    color = effectiveTint
                )
            } else if (content != null) {
                // 自定义图标插槽（支持旋转动画、状态切换等）
                content()
            } else if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = effectiveTint,
                    modifier = Modifier.size(iconSize)
                )
            } else if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = contentDescription,
                    tint = effectiveTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * 根据变体与启用状态解析 [IconButtonColors]。
 */
@Composable
private fun resolveIconButtonColors(
    variant: AppIconButtonVariant,
    tint: Color?,
    enabled: Boolean
): IconButtonColors {
    val colorScheme = MaterialTheme.colorScheme

    return when (variant) {
        AppIconButtonVariant.GHOST, AppIconButtonVariant.OUTLINED -> {
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = tint ?: colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        AppIconButtonVariant.FILLED_TONAL -> {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.7f),
                contentColor = tint ?: colorScheme.onSurfaceVariant,
                disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        AppIconButtonVariant.FILLED -> {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = colorScheme.primary,
                contentColor = tint ?: colorScheme.onPrimary,
                disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

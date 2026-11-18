package org.gemini.ui.forge.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 桌面与移动双模式自适应内嵌悬浮胶囊分段图标单选组 (InspectorSegmentedIconGroup)。
 *
 * 视觉与人机规范：
 * - 严格遵循同心圆角公式（外轨道圆角 = 内胶囊圆角 + 2dp 内边距），营造精致悬浮凹槽质感；
 * - 选中态采用柔和强调胶囊 + 微描边 + 高对比图标高亮，未选中态支持鼠标悬停微亮反馈；
 * - 高度、圆角与图标尺寸 100% 动态取自 [LocalAppSpacing]，每个分段内置手型光标与 [Modifier.tip]。
 */
@Composable
fun InspectorSegmentedIconGroup(
    options: List<InspectorSegmentedIconOption>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val insetPadding = 2.dp
    val innerRadius = spacing.inspectorFieldCornerRadius
    val outerShape = RoundedCornerShape(innerRadius + insetPadding)
    val innerShape = RoundedCornerShape(innerRadius)
    val iconSize = if (spacing.isCompact) spacing.iconCompactSize else spacing.iconLargeSize

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(insetPadding),
        modifier = modifier
            .height(spacing.inspectorFieldHeight)
            .clip(outerShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), outerShape)
            .padding(insetPadding)
    ) {
        options.forEach { option ->
            val isSelected = option.key.equals(selectedKey, ignoreCase = true)
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            val targetBg = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else -> Color.Transparent
            }
            val bgColor by animateColorAsState(targetBg, animationSpec = tween(150))
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else if (isHovered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                },
                animationSpec = tween(150)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(innerShape)
                    .background(bgColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = innerShape
                            )
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelected(option.key) }
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .tip(option.tooltip)
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.tooltip,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

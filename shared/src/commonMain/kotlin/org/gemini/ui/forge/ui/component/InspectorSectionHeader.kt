package org.gemini.ui.forge.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 桌面与移动双模式自适应属性折叠栏标题组件 (InspectorSectionHeader)。
 * - 极简扁平流式排版；
 * - 行高、间距与图标尺寸动态取自 [LocalAppSpacing]，字号动态响应 [MaterialTheme.typography]；
 * - 左侧折叠箭头动画 + 纯净标题文案 + 右侧可选操作工具槽位。
 */
@Composable
fun InspectorSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val spacing = LocalAppSpacing.current
    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f)
    val dynamicTitleStyle = if (spacing.isCompact) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleMedium
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.inspectorSectionHeaderHeight)
                .clickable { onToggle() }
                .padding(vertical = spacing.inspectorFieldPaddingVertical)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowRight,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(spacing.iconCompactSize)
                        .rotate(arrowRotation)
                )
                Text(
                    text = title,
                    style = dynamicTitleStyle.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            if (trailingContent != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing),
                    content = trailingContent
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            modifier = Modifier.padding(bottom = spacing.inspectorRowSpacing)
        )
    }
}

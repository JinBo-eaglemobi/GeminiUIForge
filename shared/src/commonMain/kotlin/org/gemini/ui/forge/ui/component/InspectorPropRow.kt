package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 属性检查器全局唯一通用两列行容器 (InspectorPropRow)。
 *
 * 强制对齐基准：
 * - 左侧固定标题列宽度 [LocalAppSpacing.current.inspectorLabelWidth]，所有主标签严格左对齐；
 * - 右侧插槽区域统一占据剩余全部宽度 `Modifier.weight(1f)`，确保内部所有文本输入框、下拉框、
 *   颜色选择器、分段图标组与单/双数值输入框的左边缘 100% 垂直共线，彻底消除锯齿错位；
 * - 尺寸、间距与字号 100% 动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun InspectorPropRow(
    label: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp? = null,
    tooltip: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    val spacing = LocalAppSpacing.current
    val resolvedLabelWidth = labelWidth ?: spacing.inspectorLabelWidth
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (tooltip != null) Modifier.tip(tooltip) else Modifier)
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = dynamicBodyStyle.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.width(resolvedLabelWidth)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.weight(1f),
            content = content
        )
    }
}

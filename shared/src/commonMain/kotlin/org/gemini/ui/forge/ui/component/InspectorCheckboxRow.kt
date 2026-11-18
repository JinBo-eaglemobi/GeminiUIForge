package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 桌面与移动双模式自适应属性布尔复选行组件 (InspectorCheckboxRow)。
 * 高度、间距、选框尺寸与文字字号 100% 动态读取 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun InspectorCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.inspectorCheckboxRowHeight)
            .clickable { onCheckedChange(!checked) }
            .then(if (tooltip != null) Modifier.tip(tooltip) else Modifier)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.size(spacing.iconCompactSize)
        )
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = dynamicBodyStyle.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

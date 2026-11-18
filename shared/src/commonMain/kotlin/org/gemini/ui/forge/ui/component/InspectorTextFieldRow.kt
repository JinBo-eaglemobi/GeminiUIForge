package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 桌面与移动双模式自适应单行紧凑文本输入行组件 (InspectorTextFieldRow)。
 * - 左侧标签宽度取自 [LocalAppSpacing.current.inspectorLabelWidth]；
 * - 右侧直接复用全局封装 [SelectAllOutlinedTextField]；
 * - 字号与高度 100% 随 [MaterialTheme.typography] 与 [LocalAppSpacing] 动态自适应，绝不写死固定值。
 */
@Composable
fun InspectorTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCommit: ((String) -> Unit)? = null,
    labelWidth: Dp? = null,
    placeholder: String = "",
    tooltip: String? = null
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

        var tempText by remember(value) { mutableStateOf(value) }

        SelectAllOutlinedTextField(
            value = tempText,
            onValueChange = {
                tempText = it
                onValueChange(it)
            },
            singleLine = true,
            placeholder = if (placeholder.isNotEmpty()) {
                {
                    Text(
                        text = placeholder,
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            } else null,
            onEnterConfirm = { onCommit?.invoke(tempText.trim()) },
            onBlurConfirm = { onCommit?.invoke(tempText.trim()) },
            modifier = Modifier.weight(1f)
        )
    }
}

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
 * 桌面与移动双模式自适应独立成对属性行组件 (InspectorSimpleDualPropRow)。
 * 专门用于语义独立的对称属性（如 "行数" / "列数"、"行距" / "列距"、"项宽" / "项高"），
 * 所有尺度与字号 100% 动态读取 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun InspectorSimpleDualPropRow(
    label1: String,
    value1: String,
    onValueChange1: (String) -> Unit,
    label2: String,
    value2: String,
    onValueChange2: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFloat: Boolean = true,
    step: Float = 1f,
    minVal: Float? = null,
    maxVal: Float? = null,
    label1Width: Dp? = null,
    label2Width: Dp? = null,
    fieldWidth: Dp? = null,
    tooltip1: String? = null,
    tooltip2: String? = null
) {
    val spacing = LocalAppSpacing.current
    val resolvedLabel1Width = label1Width ?: spacing.inspectorSimpleLabel1Width
    val resolvedLabel2Width = label2Width ?: spacing.inspectorDualTag2Width
    val resolvedFieldWidth = fieldWidth ?: spacing.inspectorNumberFieldWidth
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (tooltip1 != null) Modifier.tip(tooltip1) else Modifier)
        ) {
            Text(
                text = label1,
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(resolvedLabel1Width - spacing.inspectorControlGap)
            )
            NumberOutlinedTextField(
                value = value1,
                onValueChange = onValueChange1,
                isFloat = isFloat,
                step = step,
                minVal = minVal,
                maxVal = maxVal,
                contentPadding = PaddingValues(
                    horizontal = spacing.inspectorFieldPaddingHorizontal,
                    vertical = spacing.inspectorFieldPaddingVertical
                ),
                modifier = Modifier.widthIn(max = resolvedFieldWidth).weight(1f, fill = false)
            )
        }

        Spacer(modifier = Modifier.width(spacing.inspectorDualGroupGap))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (tooltip2 != null) Modifier.tip(tooltip2) else Modifier)
        ) {
            Text(
                text = label2,
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(resolvedLabel2Width)
            )
            NumberOutlinedTextField(
                value = value2,
                onValueChange = onValueChange2,
                isFloat = isFloat,
                step = step,
                minVal = minVal,
                maxVal = maxVal,
                contentPadding = PaddingValues(
                    horizontal = spacing.inspectorFieldPaddingHorizontal,
                    vertical = spacing.inspectorFieldPaddingVertical
                ),
                modifier = Modifier.widthIn(max = resolvedFieldWidth).weight(1f, fill = false)
            )
        }
    }
}

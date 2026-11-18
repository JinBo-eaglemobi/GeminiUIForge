package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 桌面与移动双模式自适应双属性数值行组件 (InspectorDualPropRow)。
 * 所有标签宽度、输入框宽度、间距与字号均动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]，
 * 在桌面紧凑模式与移动端触控模式下全自动缩放并严格保持 0 误差垂直共线对齐。
 */
@Composable
fun InspectorDualPropRow(
    categoryLabel: String,
    tag1: String,
    value1: String,
    onValueChange1: (String) -> Unit,
    tag2: String,
    value2: String,
    onValueChange2: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFloat: Boolean = true,
    step: Float = 1f,
    minVal: Float? = null,
    maxVal: Float? = null,
    categoryWidth: Dp? = null,
    tagWidth: Dp? = null,
    fieldWidth: Dp? = null,
    tooltip1: String? = null,
    tooltip2: String? = null
) {
    val spacing = LocalAppSpacing.current
    val resolvedCategoryWidth = categoryWidth ?: spacing.inspectorLabelWidth
    val resolvedTagWidth = tagWidth ?: spacing.inspectorTagWidth
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
        Text(
            text = categoryLabel,
            maxLines = 1,
            softWrap = false,
            style = dynamicBodyStyle.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.width(resolvedCategoryWidth)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (tooltip1 != null) Modifier.tip(tooltip1) else Modifier)
        ) {
            Text(
                text = tag1,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = dynamicBodyStyle.copy(
                    color = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.width(resolvedTagWidth)
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
                text = tag2,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = dynamicBodyStyle.copy(
                    color = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.width(resolvedTagWidth)
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

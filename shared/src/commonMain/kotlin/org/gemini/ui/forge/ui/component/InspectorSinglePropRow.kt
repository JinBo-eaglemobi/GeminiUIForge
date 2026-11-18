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
 * 桌面与移动双模式自适应单属性数值行组件 (InspectorSinglePropRow)。
 * 所有尺寸与字号均动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun InspectorSinglePropRow(
    categoryLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
    unitSuffix: String = "",
    isFloat: Boolean = true,
    step: Float = 1f,
    minVal: Float? = null,
    maxVal: Float? = null,
    categoryWidth: Dp? = null,
    tagWidth: Dp? = null,
    fieldWidth: Dp? = null,
    tooltip: String? = null
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
            modifier = if (tooltip != null) Modifier.tip(tooltip) else Modifier
        ) {
            if (tag.isNotBlank()) {
                Text(
                    text = tag,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    style = dynamicBodyStyle.copy(
                        color = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.width(resolvedTagWidth)
                )
            } else {
                Spacer(modifier = Modifier.width(resolvedTagWidth))
            }
            NumberOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                isFloat = isFloat,
                step = step,
                minVal = minVal,
                maxVal = maxVal,
                contentPadding = PaddingValues(
                    horizontal = spacing.inspectorFieldPaddingHorizontal,
                    vertical = spacing.inspectorFieldPaddingVertical
                ),
                modifier = Modifier.width(resolvedFieldWidth)
            )
            if (unitSuffix.isNotBlank()) {
                Text(
                    text = unitSuffix,
                    style = dynamicBodyStyle.copy(
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorSimpleDualPropRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 进度条组件 (PROGRESS_BAR) 专属属性配置面板。
 * 遵循 UIFactory 进度条规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun ProgressBarPropertiesSection(
    properties: BlockProperties.ProgressBarProperties,
    onPropertiesChanged: (BlockProperties.ProgressBarProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 当前值与最大值 (独立对仗成对属性)
        InspectorSimpleDualPropRow(
            label1 = "当前值",
            value1 = properties.value.toString(),
            onValueChange1 = {
                val v = it.toDoubleOrNull() ?: properties.value
                onPropertiesChanged(properties.copy(value = v))
            },
            label2 = "最大值",
            value2 = properties.max.toString(),
            onValueChange2 = {
                val m = it.toDoubleOrNull() ?: properties.max
                onPropertiesChanged(properties.copy(max = m))
            },
            isFloat = true,
            tooltip1 = "进度条当前进度数值",
            tooltip2 = "进度条满格上限数值"
        )

        // 2. 标题显示格式 (titleType)
        val titleTypeOptions = listOf(
            "Percent" to "百分比 (50%)",
            "ValueAndMax" to "当前/最大 (50/100)",
            "Value" to "纯当前值 (50)",
            "None" to "不显示文案 (None)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "标题格式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            val currentLabel = titleTypeOptions.find { it.first == properties.titleType }?.second ?: properties.titleType
            DropdownInputField(
                value = currentLabel,
                onValueChange = {},
                items = titleTypeOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(titleType = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.titleType },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 进度条组件 (PROGRESS_BAR) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun ProgressBarPropertiesSection(
    properties: BlockProperties.ProgressBarProperties,
    onPropertiesChanged: (BlockProperties.ProgressBarProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 当前值与最大值
        InspectorPropRow(label = "当前值") {
            NumberOutlinedTextField(
                value = properties.value.toString(),
                onValueChange = {
                    val v = it.toDoubleOrNull() ?: properties.value
                    onPropertiesChanged(properties.copy(value = v))
                },
                isFloat = true,
                modifier = Modifier.weight(1f).tip("进度条当前进度数值")
            )
            NumberOutlinedTextField(
                value = properties.max.toString(),
                onValueChange = {
                    val m = it.toDoubleOrNull() ?: properties.max
                    onPropertiesChanged(properties.copy(max = m))
                },
                prefixTag = "最大",
                isFloat = true,
                modifier = Modifier.weight(1f).tip("进度条满格上限数值")
            )
        }

        // 2. 标题显示格式 (titleType)
        val titleTypeOptions = listOf(
            "Percent" to "百分比 (50%)",
            "ValueAndMax" to "当前/最大 (50/100)",
            "Value" to "纯当前值 (50)",
            "None" to "不显示文案 (None)"
        )
        InspectorPropRow(label = "标题格式") {
            val currentLabel = titleTypeOptions.find { it.first == properties.titleType }?.second ?: properties.titleType
            DropdownInputField(
                value = currentLabel,
                onValueChange = {},
                items = titleTypeOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(titleType = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.titleType },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

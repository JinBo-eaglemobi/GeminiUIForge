package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 滑动条组件 (SLIDER) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun SliderPropertiesSection(
    properties: BlockProperties.SliderProperties,
    onPropertiesChanged: (BlockProperties.SliderProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 最小值与最大值
        InspectorPropRow(label = "最小值") {
            NumberOutlinedTextField(
                value = properties.min.toString(),
                onValueChange = {
                    val m = it.toDoubleOrNull() ?: properties.min
                    onPropertiesChanged(properties.copy(min = m))
                },
                isFloat = true,
                modifier = Modifier.weight(1f).tip("滑块可调节的下限值")
            )
            NumberOutlinedTextField(
                value = properties.max.toString(),
                onValueChange = {
                    val m = it.toDoubleOrNull() ?: properties.max
                    onPropertiesChanged(properties.copy(max = m))
                },
                prefixTag = "最大",
                isFloat = true,
                modifier = Modifier.weight(1f).tip("滑块可调节的上限值")
            )
        }

        // 2. 当前值与步长
        InspectorPropRow(label = "当前值") {
            NumberOutlinedTextField(
                value = properties.value.toString(),
                onValueChange = {
                    val v = it.toDoubleOrNull() ?: properties.value
                    onPropertiesChanged(properties.copy(value = v))
                },
                isFloat = true,
                modifier = Modifier.weight(1f).tip("滑块当前所处的数值")
            )
            NumberOutlinedTextField(
                value = properties.step.toString(),
                onValueChange = {
                    val s = it.toDoubleOrNull() ?: properties.step
                    onPropertiesChanged(properties.copy(step = s))
                },
                prefixTag = "步长",
                isFloat = true,
                modifier = Modifier.weight(1f).tip("每次滑动的最小数值单位")
            )
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 3. 仅取整数开关
        InspectorCheckboxRow(
            label = "仅取整数刻度 (滑动自动吸附)",
            checked = properties.wholeNumbers,
            onCheckedChange = { onPropertiesChanged(properties.copy(wholeNumbers = it)) },
            tooltip = "开启后滑动手柄自动吸附到最近整数刻度"
        )
    }
}

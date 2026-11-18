package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorSimpleDualPropRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 滑动条组件 (SLIDER) 专属属性配置面板。
 * 遵循 UIFactory 滑动条规范，所有尺寸与间距动态取自 [LocalAppSpacing]。
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
        // 1. 最小值与最大值 (独立对仗成对属性)
        InspectorSimpleDualPropRow(
            label1 = "最小值",
            value1 = properties.min.toString(),
            onValueChange1 = {
                val m = it.toDoubleOrNull() ?: properties.min
                onPropertiesChanged(properties.copy(min = m))
            },
            label2 = "最大值",
            value2 = properties.max.toString(),
            onValueChange2 = {
                val m = it.toDoubleOrNull() ?: properties.max
                onPropertiesChanged(properties.copy(max = m))
            },
            isFloat = true,
            tooltip1 = "滑块可调节的下限值",
            tooltip2 = "滑块可调节的上限值"
        )

        // 2. 当前值与步长
        InspectorSimpleDualPropRow(
            label1 = "当前值",
            value1 = properties.value.toString(),
            onValueChange1 = {
                val v = it.toDoubleOrNull() ?: properties.value
                onPropertiesChanged(properties.copy(value = v))
            },
            label2 = "步长",
            value2 = properties.step.toString(),
            onValueChange2 = {
                val s = it.toDoubleOrNull() ?: properties.step
                onPropertiesChanged(properties.copy(step = s))
            },
            isFloat = true,
            tooltip1 = "滑块当前所处的数值",
            tooltip2 = "每次滑动的最小数值单位"
        )

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

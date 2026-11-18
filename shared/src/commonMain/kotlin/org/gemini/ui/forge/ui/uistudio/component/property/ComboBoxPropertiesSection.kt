package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorSimpleDualPropRow
import org.gemini.ui.forge.ui.component.InspectorTextFieldRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 下拉选择框组件 (COMBO_BOX) 专属属性配置面板。
 * 遵循 UIFactory 下拉框规范，所有尺寸与间距动态取自 [LocalAppSpacing]。
 */
@Composable
fun ComboBoxPropertiesSection(
    properties: BlockProperties.ComboBoxProperties,
    onPropertiesChanged: (BlockProperties.ComboBoxProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 选项列表 (以逗号分隔编辑)
        val itemsText = properties.items.joinToString(", ")
        InspectorTextFieldRow(
            label = "候选项表",
            value = itemsText,
            onValueChange = { input ->
                val list = input.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
                onPropertiesChanged(properties.copy(items = list))
            },
            placeholder = "选项1, 选项2, 选项3...",
            tooltip = "输入各项选项文本，以中英文逗号隔开"
        )

        // 2. 默认选中索引 (selectedIndex) 与 展开最大可见条数 (visibleItemCount)
        InspectorSimpleDualPropRow(
            label1 = "默认选中",
            value1 = properties.selectedIndex.toString(),
            onValueChange1 = {
                val idx = it.toIntOrNull() ?: 0
                onPropertiesChanged(properties.copy(selectedIndex = idx))
            },
            label2 = "可见行数",
            value2 = properties.visibleItemCount.toString(),
            onValueChange2 = {
                val count = it.toIntOrNull() ?: 5
                onPropertiesChanged(properties.copy(visibleItemCount = count))
            },
            isFloat = false,
            label2Width = spacing.inspectorDualTagLongWidth,
            tooltip1 = "从 0 开始的默认选中下标索引",
            tooltip2 = "下拉菜单展开时最多可见的行数"
        )
    }
}

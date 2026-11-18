package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 下拉选择框组件 (COMBO_BOX) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐。
 */
@Composable
fun ComboBoxPropertiesSection(
    properties: BlockProperties.ComboBoxProperties,
    onPropertiesChanged: (BlockProperties.ComboBoxProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 选项列表 (以逗号分隔编辑)
        val itemsText = properties.items.joinToString(", ")
        InspectorPropRow(label = "候选项表", tooltip = "输入各项选项文本，以中英文逗号隔开") {
            SelectAllOutlinedTextField(
                value = itemsText,
                onValueChange = { input ->
                    val list = input.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
                    onPropertiesChanged(properties.copy(items = list))
                },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "选项1, 选项2, 选项3...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 默认选中索引 (selectedIndex) 与 展开最大可见条数 (visibleItemCount)
        InspectorPropRow(label = "默认选中") {
            NumberOutlinedTextField(
                value = properties.selectedIndex.toString(),
                onValueChange = {
                    val idx = it.toIntOrNull() ?: 0
                    onPropertiesChanged(properties.copy(selectedIndex = idx))
                },
                isFloat = false,
                modifier = Modifier.weight(1f).tip("从 0 开始的默认选中下标索引")
            )
            NumberOutlinedTextField(
                value = properties.visibleItemCount.toString(),
                onValueChange = {
                    val count = it.toIntOrNull() ?: 5
                    onPropertiesChanged(properties.copy(visibleItemCount = count))
                },
                prefixTag = "可见",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("下拉菜单展开时最多可见的行数")
            )
        }
    }
}

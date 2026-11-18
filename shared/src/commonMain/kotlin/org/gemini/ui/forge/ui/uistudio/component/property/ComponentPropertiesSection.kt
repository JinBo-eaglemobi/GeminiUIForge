package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 复合组件与容器 (COMPONENT) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun ComponentPropertiesSection(
    properties: BlockProperties.ViewProperties,
    onPropertiesChanged: (BlockProperties.ViewProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 排版模型 (layoutType)
        val layoutOptions = listOf(
            "none" to "无布局 / 绝对定位 (none)",
            "vertical" to "一维垂直流式 (vertical)",
            "horizontal" to "一维横向流式 (horizontal)"
        )
        InspectorPropRow(label = "排版模型") {
            val currentLayoutLabel = layoutOptions.find { it.first == properties.layoutType }?.second ?: properties.layoutType
            DropdownInputField(
                value = currentLayoutLabel,
                onValueChange = {},
                items = layoutOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(layoutType = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.layoutType },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 间距与背景色
        InspectorPropRow(label = "间距背景") {
            NumberOutlinedTextField(
                value = properties.gap.toString(),
                onValueChange = { onPropertiesChanged(properties.copy(gap = it.toIntOrNull() ?: 0)) },
                prefixTag = "间距",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("子组件固定间距 (px)")
            )
            ColorPickerField(
                label = "底色",
                hexColor = properties.backgroundColor,
                onColorChanged = { onPropertiesChanged(properties.copy(backgroundColor = it)) },
                showFloatingLabel = false,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. 交叉轴与主轴对齐 (流式模式下生效)
        if (properties.layoutType != "none") {
            val alignOptions = listOf(
                "flex-start" to "交叉轴: 起始",
                "center" to "交叉轴: 居中",
                "flex-end" to "交叉轴: 末端",
                "stretch" to "交叉轴: 拉伸"
            )
            val justifyOptions = listOf(
                "flex-start" to "主轴: 起始",
                "center" to "主轴: 居中",
                "flex-end" to "主轴: 尾部",
                "space-between" to "主轴: 两端贴边",
                "space-around" to "主轴: 环绕均分",
                "space-evenly" to "主轴: 等距均分"
            )
            InspectorPropRow(label = "轴线对齐") {
                DropdownInputField(
                    value = alignOptions.find { it.first == properties.alignItems }?.second ?: properties.alignItems,
                    onValueChange = {},
                    items = alignOptions,
                    onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(alignItems = key)) },
                    itemLabel = { it.second },
                    isSelected = { it.first == properties.alignItems },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                DropdownInputField(
                    value = justifyOptions.find { it.first == properties.justifyContent }?.second ?: properties.justifyContent,
                    onValueChange = {},
                    items = justifyOptions,
                    onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(justifyContent = key)) },
                    itemLabel = { it.second },
                    isSelected = { it.first == properties.justifyContent },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. 横向流特有：自动折行
            if (properties.layoutType == "horizontal") {
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                InspectorCheckboxRow(
                    label = "超出宽度自动折行 (flexWrap)",
                    checked = properties.flexWrap,
                    onCheckedChange = { onPropertiesChanged(properties.copy(flexWrap = it)) },
                    tooltip = "开启后子项在水平累加超出父容器宽度时自动折行到下一行"
                )
            }
        }

        // 5. 滚动控制与溢出状态
        val scrollOptions = listOf(
            -1 to "无滚动",
            0 to "横向滚动",
            1 to "纵向滚动",
            2 to "双向滚动"
        )
        val overflowOptions = listOf(
            0 to "内容可见",
            1 to "容器裁切",
            2 to "滚动视口"
        )
        InspectorPropRow(label = "滚动裁切") {
            DropdownInputField(
                value = scrollOptions.find { it.first == properties.scrollType }?.second ?: "默认",
                onValueChange = {},
                items = scrollOptions,
                onItemSelected = { (key, _) ->
                    val autoOverflow = if (key >= 0) 2 else 0
                    onPropertiesChanged(properties.copy(scrollType = key, overflow = autoOverflow))
                },
                itemLabel = { it.second },
                isSelected = { it.first == properties.scrollType },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
            DropdownInputField(
                value = overflowOptions.find { it.first == properties.overflow }?.second ?: "默认",
                onValueChange = {},
                items = overflowOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(overflow = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.overflow },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 按钮组件 (BUTTON) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐。
 */
@Composable
fun ButtonPropertiesSection(
    properties: BlockProperties.ButtonProperties,
    onPropertiesChanged: (BlockProperties.ButtonProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 按钮文案 (text)
        InspectorPropRow(label = "按钮文案", tooltip = "按钮默认显示的文本内容") {
            SelectAllOutlinedTextField(
                value = properties.text,
                onValueChange = { onPropertiesChanged(properties.copy(text = it)) },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "按钮显示的文字内容...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 按压反馈效果 (downEffect)
        val effectOptions = listOf(
            0 to "无效果 (None)",
            1 to "滤镜变暗 (Darken)",
            2 to "向心缩放 (Scale)"
        )
        InspectorPropRow(label = "按压反馈") {
            val currentEffectLabel = effectOptions.find { it.first == properties.downEffect }?.second ?: "滤镜变暗 (Darken)"
            DropdownInputField(
                value = currentEffectLabel,
                onValueChange = {},
                items = effectOptions,
                onItemSelected = { (key, _) ->
                    val defaultValue = if (key == 2) 0.92f else 0.8f
                    onPropertiesChanged(properties.copy(downEffect = key, downEffectValue = defaultValue))
                },
                itemLabel = { it.second },
                isSelected = { it.first == properties.downEffect },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )

            NumberOutlinedTextField(
                value = properties.downEffectValue.toString(),
                onValueChange = {
                    val f = it.toFloatOrNull() ?: properties.downEffectValue
                    onPropertiesChanged(properties.copy(downEffectValue = f))
                },
                prefixTag = "系数",
                isFloat = true,
                modifier = Modifier.weight(1f).tip("变暗系数(如0.8)或向心缩放比例(如0.92)")
            )
        }

        // 3. 交互模式 (Common / Check / Radio)
        val modeOptions = listOf(
            "Common" to "普通按钮 (Common)",
            "Check" to "复选按钮 (Check)",
            "Radio" to "单选按钮 (Radio)"
        )
        InspectorPropRow(label = "交互模式") {
            val currentModeLabel = modeOptions.find { it.first == properties.mode }?.second ?: properties.mode
            DropdownInputField(
                value = currentModeLabel,
                onValueChange = {},
                items = modeOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(mode = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.mode },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. 若为单选模式，显示关联控制器与对应页面 ID
        if (properties.mode == "Radio") {
            InspectorPropRow(label = "控制器名", tooltip = "单选互斥的分组控制器标识") {
                SelectAllOutlinedTextField(
                    value = properties.relatedController,
                    onValueChange = { onPropertiesChanged(properties.copy(relatedController = it)) },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "如 buttonGroup...",
                            maxLines = 1,
                            softWrap = false,
                            style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            InspectorPropRow(label = "关联页面", tooltip = "选中该单选项时对应激活的页面编号") {
                SelectAllOutlinedTextField(
                    value = properties.pageId,
                    onValueChange = { onPropertiesChanged(properties.copy(pageId = it)) },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "如 tab_0...",
                            maxLines = 1,
                            softWrap = false,
                            style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

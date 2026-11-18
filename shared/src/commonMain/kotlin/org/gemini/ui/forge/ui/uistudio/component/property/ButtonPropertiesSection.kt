package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorTextFieldRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 按钮组件 (BUTTON) 专属属性配置面板。
 * 遵循 UIFactory 按钮规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun ButtonPropertiesSection(
    properties: BlockProperties.ButtonProperties,
    onPropertiesChanged: (BlockProperties.ButtonProperties) -> Unit,
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
        // 1. 按钮文案 (text)
        InspectorTextFieldRow(
            label = "按钮文案",
            value = properties.text,
            onValueChange = { onPropertiesChanged(properties.copy(text = it)) },
            placeholder = "按钮显示的文字内容...",
            tooltip = "按钮默认显示的文本内容"
        )

        // 2. 按压反馈效果 (downEffect)
        val effectOptions = listOf(
            0 to "无效果 (None)",
            1 to "滤镜变暗 (Darken)",
            2 to "向心缩放 (Scale)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "按压反馈",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.weight(1f)
            ) {
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
                    modifier = Modifier.weight(1.2f)
                )

                NumberOutlinedTextField(
                    value = properties.downEffectValue.toString(),
                    onValueChange = {
                        val f = it.toFloatOrNull() ?: properties.downEffectValue
                        onPropertiesChanged(properties.copy(downEffectValue = f))
                    },
                    isFloat = true,
                    contentPadding = PaddingValues(
                        horizontal = spacing.inspectorFieldPaddingHorizontal,
                        vertical = spacing.inspectorFieldPaddingVertical
                    ),
                    modifier = Modifier.weight(0.8f).tip("变暗系数(如0.8)或向心缩放比例(如0.92)")
                )
            }
        }

        // 3. 交互模式 (Common / Check / Radio)
        val modeOptions = listOf(
            "Common" to "普通按钮 (Common)",
            "Check" to "复选按钮 (Check)",
            "Radio" to "单选按钮 (Radio)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "交互模式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            val currentModeLabel = modeOptions.find { it.first == properties.mode }?.second ?: properties.mode
            DropdownInputField(
                value = currentModeLabel,
                onValueChange = {},
                items = modeOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(mode = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.mode },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. 若为单选模式，显示关联控制器与对应页面 ID
        if (properties.mode == "Radio") {
            InspectorTextFieldRow(
                label = "控制器名",
                value = properties.relatedController,
                onValueChange = { onPropertiesChanged(properties.copy(relatedController = it)) },
                placeholder = "如 buttonGroup...",
                tooltip = "单选互斥的分组控制器标识"
            )
            InspectorTextFieldRow(
                label = "关联页面",
                value = properties.pageId,
                onValueChange = { onPropertiesChanged(properties.copy(pageId = it)) },
                placeholder = "如 tab_0...",
                tooltip = "选中该单选项时对应激活的页面编号"
            )
        }
    }
}

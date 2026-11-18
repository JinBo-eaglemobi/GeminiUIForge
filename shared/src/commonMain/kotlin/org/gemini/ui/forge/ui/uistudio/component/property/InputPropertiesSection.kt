package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 输入框组件 (INPUT) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun InputPropertiesSection(
    properties: BlockProperties.InputProperties,
    onPropertiesChanged: (BlockProperties.InputProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 占位提示文案 (hintText)
        InspectorPropRow(label = "占位文案", tooltip = "输入框为空时显示的占位文案") {
            SelectAllOutlinedTextField(
                value = properties.hintText,
                onValueChange = { onPropertiesChanged(properties.copy(hintText = it)) },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "输入框为空时的占位提示...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 颜色与字号
        InspectorPropRow(label = "颜色字号") {
            ColorPickerField(
                label = "色值",
                hexColor = properties.textColor,
                onColorChanged = { onPropertiesChanged(properties.copy(textColor = it)) },
                showFloatingLabel = false,
                modifier = Modifier.weight(1f)
            )
            NumberOutlinedTextField(
                value = properties.textSize.toString(),
                onValueChange = { onPropertiesChanged(properties.copy(textSize = it.toIntOrNull() ?: 14)) },
                prefixTag = "字号",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("输入框字符大小 (px)")
            )
        }

        // 3. 最大允许输入字符长度 (maxLength)
        InspectorPropRow(label = "最大字符", tooltip = "最大字符数限制，留空或 -1 表示不限制") {
            NumberOutlinedTextField(
                value = if (properties.maxLength < 0) "" else properties.maxLength.toString(),
                onValueChange = {
                    val len = it.toIntOrNull() ?: -1
                    onPropertiesChanged(properties.copy(maxLength = len))
                },
                isFloat = false,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 4. 密码模式与单行控制 (紧凑复选行)
        InspectorCheckboxRow(
            label = "开启密码隐藏模式 (掩码圆点)",
            checked = properties.isPassword,
            onCheckedChange = { onPropertiesChanged(properties.copy(isPassword = it)) },
            tooltip = "开启后输入字符自动替换为掩码圆点"
        )
        InspectorCheckboxRow(
            label = "单行输入限制 (禁止多行换行)",
            checked = properties.singleLine,
            onCheckedChange = { onPropertiesChanged(properties.copy(singleLine = it)) },
            tooltip = "限制为单行输入框"
        )
    }
}

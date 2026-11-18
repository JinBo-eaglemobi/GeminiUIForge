package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 输入框组件 (INPUT) 专属属性配置面板。
 * 遵循 UIFactory 输入框规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun InputPropertiesSection(
    properties: BlockProperties.InputProperties,
    onPropertiesChanged: (BlockProperties.InputProperties) -> Unit,
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
        // 1. 占位提示文案 (hintText)
        InspectorTextFieldRow(
            label = "占位文案",
            value = properties.hintText,
            onValueChange = { onPropertiesChanged(properties.copy(hintText = it)) },
            placeholder = "输入框为空时的占位提示...",
            tooltip = "输入框为空时显示的占位文案"
        )

        // 2. 颜色与字号
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "颜色字号",
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
                    isFloat = false,
                    contentPadding = PaddingValues(
                        horizontal = spacing.inspectorFieldPaddingHorizontal,
                        vertical = spacing.inspectorFieldPaddingVertical
                    ),
                    modifier = Modifier.width(spacing.inspectorNumberFieldWidth).tip("输入框字符大小 (px)")
                )
            }
        }

        // 3. 最大允许输入字符长度 (maxLength)
        InspectorSinglePropRow(
            categoryLabel = "最大字符",
            value = if (properties.maxLength < 0) "" else properties.maxLength.toString(),
            onValueChange = {
                val len = it.toIntOrNull() ?: -1
                onPropertiesChanged(properties.copy(maxLength = len))
            },
            isFloat = false,
            tooltip = "最大字符数限制，留空或 -1 表示不限制"
        )

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

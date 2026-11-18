package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 文本组件 (TEXT) 专属属性配置面板。
 * 遵循 UIFactory 文本规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun TextPropertiesSection(
    properties: BlockProperties.TextProperties,
    onPropertiesChanged: (BlockProperties.TextProperties) -> Unit,
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
        // 1. 文本内容输入框
        InspectorTextFieldRow(
            label = "文本内容",
            value = properties.text,
            onValueChange = { onPropertiesChanged(properties.copy(text = it)) },
            placeholder = "要展示的文字内容...",
            tooltip = "显示的文本内容，支持换行符 \\n"
        )

        // 2. 文本加粗、倾斜与排版对齐工具栏
        TextStyleToolbar(
            isBold = properties.isBold,
            onBoldChanged = { onPropertiesChanged(properties.copy(isBold = it)) },
            isItalic = properties.isItalic,
            onItalicChanged = { onPropertiesChanged(properties.copy(isItalic = it)) },
            horizontalAlign = properties.horizontalAlign,
            onHorizontalAlignChanged = { onPropertiesChanged(properties.copy(horizontalAlign = it)) },
            verticalAlign = properties.verticalAlign,
            onVerticalAlignChanged = { onPropertiesChanged(properties.copy(verticalAlign = it)) }
        )

        // 3. 文本颜色与字号
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
                    modifier = Modifier.width(spacing.inspectorNumberFieldWidth).tip("文字像素大小 (px)")
                )
            }
        }

        // 4. 文字描边颜色与描边粗细
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "描边样式",
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
                    label = "描边色",
                    hexColor = properties.strokeColor,
                    onColorChanged = { onPropertiesChanged(properties.copy(strokeColor = it)) },
                    showFloatingLabel = false,
                    modifier = Modifier.weight(1f)
                )
                NumberOutlinedTextField(
                    value = properties.strokeWidth.toString(),
                    onValueChange = {
                        val w = it.toFloatOrNull() ?: 0f
                        onPropertiesChanged(properties.copy(strokeWidth = w))
                    },
                    isFloat = true,
                    contentPadding = PaddingValues(
                        horizontal = spacing.inspectorFieldPaddingHorizontal,
                        vertical = spacing.inspectorFieldPaddingVertical
                    ),
                    modifier = Modifier.width(spacing.inspectorNumberFieldWidth).tip("文字外描边像素粗细 (px)")
                )
            }
        }

        // 5. 行间距与字符间距
        InspectorSimpleDualPropRow(
            label1 = "行间距",
            value1 = properties.leading.toString(),
            onValueChange1 = { onPropertiesChanged(properties.copy(leading = it.toIntOrNull() ?: 4)) },
            label2 = "字间距",
            value2 = properties.letterSpacing.toString(),
            onValueChange2 = { onPropertiesChanged(properties.copy(letterSpacing = it.toIntOrNull() ?: 0)) },
            isFloat = false,
            tooltip1 = "多行文本行距 (px)",
            tooltip2 = "字符水平间距 (px)"
        )

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 6. UBB 富文本与单行限制开关
        InspectorCheckboxRow(
            label = "启用 UBB 富文本标记",
            checked = properties.isUbb,
            onCheckedChange = { onPropertiesChanged(properties.copy(isUbb = it)) },
            tooltip = "开启后支持 [color=#ff0000]红色[/color] 与 [b]加粗[/b] 语法"
        )
        InspectorCheckboxRow(
            label = "单行文本限制 (超出不自动折行)",
            checked = properties.singleLine,
            onCheckedChange = { onPropertiesChanged(properties.copy(singleLine = it)) },
            tooltip = "限制为单行文本，超出边界不自动折行"
        )
    }
}

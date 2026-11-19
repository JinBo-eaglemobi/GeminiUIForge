package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 文本组件 (TEXT) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun TextPropertiesSection(
    properties: BlockProperties.TextProperties,
    onPropertiesChanged: (BlockProperties.TextProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 文本内容输入框（上下结构或多行舒展容器，彻底杜绝单行水平挤压）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                text = "文本内容",
                style = dynamicBodyStyle.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            SelectAllOutlinedTextField(
                value = properties.text,
                onValueChange = { onPropertiesChanged(properties.copy(text = it)) },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                streamlined = false,
                placeholder = {
                    Text(
                        text = "输入文本内容，支持换行...",
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 150.dp)
            )
        }

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
                modifier = Modifier.weight(1f).tip("文字像素大小 (px)")
            )
        }

        // 4. 文字描边颜色与描边粗细
        InspectorPropRow(label = "描边样式") {
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
                prefixTag = "粗细",
                isFloat = true,
                modifier = Modifier.weight(1f).tip("文字外描边像素粗细 (px)")
            )
        }

        // 5. 行间距与字符间距
        InspectorPropRow(label = "行间距") {
            NumberOutlinedTextField(
                value = properties.leading.toString(),
                onValueChange = { onPropertiesChanged(properties.copy(leading = it.toIntOrNull() ?: 4)) },
                isFloat = false,
                modifier = Modifier.weight(1f).tip("多行文本行距 (px)")
            )
            NumberOutlinedTextField(
                value = properties.letterSpacing.toString(),
                onValueChange = { onPropertiesChanged(properties.copy(letterSpacing = it.toIntOrNull() ?: 0)) },
                prefixTag = "字距",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("字符水平间距 (px)")
            )
        }

        // 6. 自动大小 (FairyGUI 标准 5 项枚举)
        val autoSizeOptions = listOf(
            "none" to "(无)",
            "both" to "宽度和高度",
            "height" to "高度",
            "shrink" to "自动收缩",
            "ellipsis" to "显示省略号"
        )
        InspectorPropRow(label = "自动大小", tooltip = "对齐 FGUI autoSize 属性：默认自适应宽度和高度") {
            DropdownInputField(
                value = autoSizeOptions.find { it.first == properties.autoSize }?.second ?: "宽度和高度",
                onValueChange = {},
                items = autoSizeOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(autoSize = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.autoSize },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 7. UBB 富文本与单行限制开关
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

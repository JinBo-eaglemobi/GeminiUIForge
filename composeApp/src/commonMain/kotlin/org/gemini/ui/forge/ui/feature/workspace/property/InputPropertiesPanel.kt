package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.ColorPickerField
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.TextStyleToolbar
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 输入框组件的专属属性面板
 */
@Composable
fun InputPropertiesPanel(
    selectedBlock: UIBlock,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    val props = selectedBlock.properties as? BlockProperties.InputProperties ?: BlockProperties.InputProperties()
    Text("输入框配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    SelectAllOutlinedTextField(
        value = props.hintText,
        onValueChange = { onPropertiesChanged(props.copy(hintText = it)) },
        label = { Text("默认提示文案 (Hint)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    TextStyleToolbar(
        isBold = props.isBold,
        onBoldChanged = { onPropertiesChanged(props.copy(isBold = it)) },
        isItalic = props.isItalic,
        onItalicChanged = { onPropertiesChanged(props.copy(isItalic = it)) },
        horizontalAlign = props.horizontalAlign,
        onHorizontalAlignChanged = { onPropertiesChanged(props.copy(horizontalAlign = it)) },
        verticalAlign = props.verticalAlign,
        onVerticalAlignChanged = { onPropertiesChanged(props.copy(verticalAlign = it)) }
    )
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
        ColorPickerField(
            label = "文本颜色",
            hexColor = props.textColor,
            onColorChanged = { onPropertiesChanged(props.copy(textColor = it)) },
            modifier = Modifier.weight(1.5f)
        )
        NumberOutlinedTextField(
            value = props.textSize.toString(),
            onValueChange = { onPropertiesChanged(props.copy(textSize = it.toIntOrNull() ?: props.textSize)) },
            label = { Text("字号") },
            modifier = Modifier.weight(1f),
            isFloat = false
        )
    }
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
        ColorPickerField(
            label = "描边颜色",
            hexColor = props.strokeColor,
            onColorChanged = { onPropertiesChanged(props.copy(strokeColor = it)) },
            modifier = Modifier.weight(1.5f)
        )
        NumberOutlinedTextField(
            value = props.strokeWidth.toString(),
            onValueChange = { onPropertiesChanged(props.copy(strokeWidth = it.toFloatOrNull() ?: props.strokeWidth)) },
            label = { Text("描边宽") },
            modifier = Modifier.weight(1f),
            isFloat = true
        )
    }
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    NumberOutlinedTextField(
        value = if (props.maxLength == -1) "" else props.maxLength.toString(),
        onValueChange = { 
            val maxLen = if (it.isBlank() || it == "0") -1 else it.toIntOrNull() ?: props.maxLength
            onPropertiesChanged(props.copy(maxLength = maxLen)) 
        },
        label = { Text("最大输入长度 (-1或清空表示不限)") },
        modifier = Modifier.fillMaxWidth(),
        isFloat = false
    )
}

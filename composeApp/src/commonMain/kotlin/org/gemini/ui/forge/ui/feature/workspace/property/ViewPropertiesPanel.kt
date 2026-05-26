package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.ColorPickerField
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 视图组件的专属属性面板
 */
@Composable
fun ViewPropertiesPanel(
    selectedBlock: UIBlock,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    val props = selectedBlock.properties as? BlockProperties.ViewProperties ?: BlockProperties.ViewProperties()
    Text("视图配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    ColorPickerField(
        label = "背景色 (Hex)",
        hexColor = props.backgroundColor,
        onColorChanged = { onPropertiesChanged(props.copy(backgroundColor = it)) }
    )
}

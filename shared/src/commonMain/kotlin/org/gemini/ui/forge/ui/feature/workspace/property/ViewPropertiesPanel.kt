package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.prop_view_clip_overflow
import geminiuiforge.composeapp.generated.resources.prop_view_clip_overflow_desc
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.ColorPickerField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 视图组件的专属属性面板
 */
@Composable
fun ViewPropertiesPanel(
    selectedBlock: UIBlock,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    val props = selectedBlock.properties as? BlockProperties.ViewProperties ?: BlockProperties.ViewProperties()
    val spacing = LocalAppSpacing.current

    Text("视图配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(spacing.small))
    ColorPickerField(
        label = "背景色 (Hex)",
        hexColor = props.backgroundColor,
        onColorChanged = { onPropertiesChanged(props.copy(backgroundColor = it)) }
    )
    Spacer(Modifier.height(spacing.small))
    // 内容溢出隐藏/裁剪控制开关
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .clickable {
                onPropertiesChanged(props.copy(clipOverflow = !props.clipOverflow))
            }
            .padding(vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.prop_view_clip_overflow),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(Res.string.prop_view_clip_overflow_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = props.clipOverflow,
            onCheckedChange = { checked ->
                onPropertiesChanged(props.copy(clipOverflow = checked))
            }
        )
    }
}

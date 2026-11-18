package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorTextFieldRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 动态装载器组件 (LOADER) 专属属性配置面板。
 * 遵循 UIFactory 装载器规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun LoaderPropertiesSection(
    properties: BlockProperties.LoaderProperties,
    onPropertiesChanged: (BlockProperties.LoaderProperties) -> Unit,
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
        // 1. 默认资源地址 (url)
        InspectorTextFieldRow(
            label = "资源路径",
            value = properties.url,
            onValueChange = { onPropertiesChanged(properties.copy(url = it)) },
            placeholder = "图片切片或资源名称...",
            tooltip = "默认要装载的图片切片或外部资源名称"
        )

        // 2. 图像填充拉伸模式 (fill)
        val fillOptions = listOf(
            "none" to "原始尺寸 (none)",
            "scale" to "等比缩放留白 (scale)",
            "scalefree" to "自由拉伸填满 (scalefree)",
            "scalenoborder" to "等比裁剪铺满 (scalenoborder)",
            "scalematchheight" to "按高度等比缩放 (matchheight)",
            "scalematchwidth" to "按宽度等比缩放 (matchwidth)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "填充模式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            val currentLabel = fillOptions.find { it.first == properties.fill }?.second ?: properties.fill
            DropdownInputField(
                value = currentLabel,
                onValueChange = {},
                items = fillOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(fill = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.fill },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 3. 视口边缘强裁剪 (clip)
        InspectorCheckboxRow(
            label = "视口边缘强裁剪 (超限纹理切除)",
            checked = properties.clip,
            onCheckedChange = { onPropertiesChanged(properties.copy(clip = it)) },
            tooltip = "开启后基于 bounds 宽高应用视口强裁剪，超限纹理自动切除"
        )
    }
}

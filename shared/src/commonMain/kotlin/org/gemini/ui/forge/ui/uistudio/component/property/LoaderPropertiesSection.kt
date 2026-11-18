package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 动态装载器组件 (LOADER) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐。
 */
@Composable
fun LoaderPropertiesSection(
    properties: BlockProperties.LoaderProperties,
    onPropertiesChanged: (BlockProperties.LoaderProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 默认资源地址 (url)
        InspectorPropRow(label = "资源路径", tooltip = "默认要装载的图片切片或外部资源名称") {
            SelectAllOutlinedTextField(
                value = properties.url,
                onValueChange = { onPropertiesChanged(properties.copy(url = it)) },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "图片切片或资源名称...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 图像填充拉伸模式 (fill)
        val fillOptions = listOf(
            "none" to "原始尺寸 (none)",
            "scale" to "等比缩放留白 (scale)",
            "scalefree" to "自由拉伸填满 (scalefree)",
            "scalenoborder" to "等比裁剪铺满 (scalenoborder)",
            "scalematchheight" to "按高度等比缩放 (matchheight)",
            "scalematchwidth" to "按宽度等比缩放 (matchwidth)"
        )
        InspectorPropRow(label = "填充模式") {
            val currentLabel = fillOptions.find { it.first == properties.fill }?.second ?: properties.fill
            DropdownInputField(
                value = currentLabel,
                onValueChange = {},
                items = fillOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(fill = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.fill },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
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

package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.ImageResizeMode
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorDualPropRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 图片组件 (IMAGE) 专属属性配置面板。
 * 遵循 UIFactory 图片规范，所有尺寸与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun ImagePropertiesSection(
    properties: BlockProperties.ImageProperties,
    onPropertiesChanged: (BlockProperties.ImageProperties) -> Unit,
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
        // 1. 缩放拉伸模式 (resizeMode)
        val modeOptions = listOf(
            ImageResizeMode.STRETCH to "强制拉伸填满 (STRETCH)",
            ImageResizeMode.FIT_WITH_PADDING to "等比缩放留白 (FIT_WITH_PADDING)",
            ImageResizeMode.NINE_PATCH to "九宫格切边拉伸 (NINE_PATCH)",
            ImageResizeMode.CROP_TO_FILL to "等比铺满裁剪 (CROP_TO_FILL)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "缩放模式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            val currentModeLabel = modeOptions.find { it.first == properties.resizeMode }?.second ?: properties.resizeMode.name
            DropdownInputField(
                value = currentModeLabel,
                onValueChange = {},
                items = modeOptions,
                onItemSelected = { (mode, _) -> onPropertiesChanged(properties.copy(resizeMode = mode)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.resizeMode },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. 若选择九宫格模式，展示四周不拉伸边距
        if (properties.resizeMode == ImageResizeMode.NINE_PATCH) {
            InspectorDualPropRow(
                categoryLabel = "横向九宫",
                tag1 = "左",
                value1 = properties.ninePatchConfig.left.toString(),
                onValueChange1 = {
                    val v = it.toIntOrNull() ?: 0
                    onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(left = v)))
                },
                tag2 = "右",
                value2 = properties.ninePatchConfig.right.toString(),
                onValueChange2 = {
                    val v = it.toIntOrNull() ?: 0
                    onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(right = v)))
                },
                isFloat = false,
                tooltip1 = "左侧固定像素边距",
                tooltip2 = "右侧固定像素边距"
            )

            InspectorDualPropRow(
                categoryLabel = "纵向九宫",
                tag1 = "顶",
                value1 = properties.ninePatchConfig.top.toString(),
                onValueChange1 = {
                    val v = it.toIntOrNull() ?: 0
                    onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(top = v)))
                },
                tag2 = "底",
                value2 = properties.ninePatchConfig.bottom.toString(),
                onValueChange2 = {
                    val v = it.toIntOrNull() ?: 0
                    onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(bottom = v)))
                },
                isFloat = false,
                tooltip1 = "顶部固定像素边距",
                tooltip2 = "底部固定像素边距"
            )
        }
    }
}

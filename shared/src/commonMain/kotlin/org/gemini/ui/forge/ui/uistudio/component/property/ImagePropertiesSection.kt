package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.ImageResizeMode
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 图片组件 (IMAGE) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐与 50%/50% 对称栅格对齐。
 */
@Composable
fun ImagePropertiesSection(
    properties: BlockProperties.ImageProperties,
    onPropertiesChanged: (BlockProperties.ImageProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

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
        InspectorPropRow(label = "缩放模式") {
            val currentModeLabel = modeOptions.find { it.first == properties.resizeMode }?.second ?: properties.resizeMode.name
            DropdownInputField(
                value = currentModeLabel,
                onValueChange = {},
                items = modeOptions,
                onItemSelected = { (mode, _) -> onPropertiesChanged(properties.copy(resizeMode = mode)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.resizeMode },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 若选择九宫格模式，展示四周不拉伸边距
        if (properties.resizeMode == ImageResizeMode.NINE_PATCH) {
            InspectorPropRow(label = "横向九宫") {
                NumberOutlinedTextField(
                    value = properties.ninePatchConfig.left.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull() ?: 0
                        onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(left = v)))
                    },
                    prefixTag = "左",
                    isFloat = false,
                    modifier = Modifier.weight(1f).tip("左侧固定像素边距")
                )
                NumberOutlinedTextField(
                    value = properties.ninePatchConfig.right.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull() ?: 0
                        onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(right = v)))
                    },
                    prefixTag = "右",
                    isFloat = false,
                    modifier = Modifier.weight(1f).tip("右侧固定像素边距")
                )
            }

            InspectorPropRow(label = "纵向九宫") {
                NumberOutlinedTextField(
                    value = properties.ninePatchConfig.top.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull() ?: 0
                        onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(top = v)))
                    },
                    prefixTag = "顶",
                    isFloat = false,
                    modifier = Modifier.weight(1f).tip("顶部固定像素边距")
                )
                NumberOutlinedTextField(
                    value = properties.ninePatchConfig.bottom.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull() ?: 0
                        onPropertiesChanged(properties.copy(ninePatchConfig = properties.ninePatchConfig.copy(bottom = v)))
                    },
                    prefixTag = "底",
                    isFloat = false,
                    modifier = Modifier.weight(1f).tip("底部固定像素边距")
                )
            }
        }
    }
}

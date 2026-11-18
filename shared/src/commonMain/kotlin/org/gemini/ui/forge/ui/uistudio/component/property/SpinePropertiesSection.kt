package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorSinglePropRow
import org.gemini.ui.forge.ui.component.InspectorTextFieldRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 骨骼动画组件 (SPINE) 专属属性配置面板。
 * 遵循 UIFactory 骨骼动画规范，所有尺寸与间距动态取自 [LocalAppSpacing]。
 */
@Composable
fun SpinePropertiesSection(
    properties: BlockProperties.SpineProperties,
    onPropertiesChanged: (BlockProperties.SpineProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 默认动画名称 (animationName)
        InspectorTextFieldRow(
            label = "默认动作",
            value = properties.animationName,
            onValueChange = { onPropertiesChanged(properties.copy(animationName = it)) },
            placeholder = "如 idle, win, spin...",
            tooltip = "骨骼动画初始化时默认播放的动作名"
        )

        // 2. 皮肤名称 (skinName)
        InspectorTextFieldRow(
            label = "皮肤名称",
            value = properties.skinName,
            onValueChange = { onPropertiesChanged(properties.copy(skinName = it)) },
            placeholder = "默认为 default...",
            tooltip = "骨骼皮肤名称，默认为 default"
        )

        // 3. 播放速度 (timeScale)
        InspectorSinglePropRow(
            categoryLabel = "播放倍速",
            value = properties.timeScale.toString(),
            onValueChange = {
                val s = it.toFloatOrNull() ?: properties.timeScale
                onPropertiesChanged(properties.copy(timeScale = s))
            },
            isFloat = true,
            tooltip = "动画播放速度倍率，1.0 为原速"
        )

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 4. 是否循环播放 (loop)
        InspectorCheckboxRow(
            label = "动作自动无限循环 (loop)",
            checked = properties.loop,
            onCheckedChange = { onPropertiesChanged(properties.copy(loop = it)) },
            tooltip = "开启后动作播放完毕自动无限循环"
        )
    }
}

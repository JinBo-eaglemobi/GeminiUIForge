package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.component.InspectorPropRow
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 骨骼动画组件 (SPINE) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现 0 误差左对齐。
 */
@Composable
fun SpinePropertiesSection(
    properties: BlockProperties.SpineProperties,
    onPropertiesChanged: (BlockProperties.SpineProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 默认动画名称 (animationName)
        InspectorPropRow(label = "默认动作", tooltip = "骨骼动画初始化时默认播放的动作名") {
            SelectAllOutlinedTextField(
                value = properties.animationName,
                onValueChange = { onPropertiesChanged(properties.copy(animationName = it)) },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "如 idle, win, spin...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 皮肤名称 (skinName)
        InspectorPropRow(label = "皮肤名称", tooltip = "骨骼皮肤名称，默认为 default") {
            SelectAllOutlinedTextField(
                value = properties.skinName,
                onValueChange = { onPropertiesChanged(properties.copy(skinName = it)) },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "默认为 default...",
                        maxLines = 1,
                        softWrap = false,
                        style = dynamicBodyStyle.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. 播放速度 (timeScale)
        InspectorPropRow(label = "播放倍速", tooltip = "动画播放速度倍率，1.0 为原速") {
            NumberOutlinedTextField(
                value = properties.timeScale.toString(),
                onValueChange = {
                    val s = it.toFloatOrNull() ?: properties.timeScale
                    onPropertiesChanged(properties.copy(timeScale = s))
                },
                isFloat = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

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

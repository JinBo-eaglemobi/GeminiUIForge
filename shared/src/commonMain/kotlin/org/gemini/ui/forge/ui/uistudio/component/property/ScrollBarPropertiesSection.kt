package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.InspectorCheckboxRow
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 滚动条组件 (SCROLL_BAR) 专属属性配置面板。
 * 遵循 UIFactory 滚动条规范，所有尺寸与间距动态取自 [LocalAppSpacing]。
 */
@Composable
fun ScrollBarPropertiesSection(
    properties: BlockProperties.ScrollBarProperties,
    onPropertiesChanged: (BlockProperties.ScrollBarProperties) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        InspectorCheckboxRow(
            label = "固定滑块手柄尺寸 (不随内容等比伸缩)",
            checked = properties.fixedGripSize,
            onCheckedChange = { onPropertiesChanged(properties.copy(fixedGripSize = it)) },
            tooltip = "开启后滑块手柄尺寸保持固定，不随滚动内容比例动态伸缩"
        )

        InspectorCheckboxRow(
            label = "显示两端步进微调箭头",
            checked = properties.arrowButton,
            onCheckedChange = { onPropertiesChanged(properties.copy(arrowButton = it)) },
            tooltip = "是否在滚动条两端显示方向步进微调按钮"
        )
    }
}

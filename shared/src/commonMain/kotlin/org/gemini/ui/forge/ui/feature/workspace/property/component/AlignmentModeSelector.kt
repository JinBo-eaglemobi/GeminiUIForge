package org.gemini.ui.forge.ui.feature.workspace.property.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.DetectionEngineRegistry
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 模块物理校对算法引擎切换器组件 (AlignmentModeSelector)
 *
 * 在未选全页面卡片、单模块属性卡片、多选批量属性卡片三处 100% 统一复用。
 *
 * @param currentMode 当前激活的对齐算法引擎
 * @param onModeSelected 模式切换回调
 * @param modifier 修饰符
 */
@Composable
fun AlignmentModeSelector(
    currentMode: DetectionEngineMode,
    onModeSelected: (DetectionEngineMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "校对算法引擎:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DetectionEngineRegistry.getAvailableAlignerModes().forEach { mode ->
                val isSelected = currentMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.shortName, style = MaterialTheme.typography.labelSmall) },
                    shape = AppShapes.small,
                    modifier = Modifier.tip("${mode.displayName}: ${mode.description}")
                )
            }
        }
    }
}

package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.gemini.ui.forge.manager.MattingPreset
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/** 预设结构化分组定义 */
private enum class PresetCategoryGroup(
    val title: String,
    val icon: ImageVector,
    val filterPredicate: (String) -> Boolean
) {
    EXTRACTION("元素剥离与光效提取", Icons.Default.Grain, { id ->
        id == "extract_button" || id == "extract_glowing_effect_icon" || id == "extract_symbol_icon"
    }),
    INPAINT_CLEAN("底图擦除与面板修复", Icons.Default.CleaningServices, { id ->
        id == "clean_background_inpaint" || id == "remove_text_keep_frame" || id == "clean_dialog_panel"
    }),
    STATE_TRANSFORM("状态衍生与背景处理", Icons.Default.FilterDrama, { id ->
        id == "high_contrast_solid_bg" || id == "state_disabled_gray" || id == "state_active_glowing"
    });

    companion object {
        fun of(presetId: String): PresetCategoryGroup {
            return entries.firstOrNull { it.filterPredicate(presetId) } ?: EXTRACTION
        }
    }
}

/**
 * 视觉工作室专业场景预设下拉菜单（结构化分类版）
 */
@Composable
fun StudioPresetDropdownMenu(
    presets: List<MattingPreset>,
    selectedPresetId: String?,
    onPresetSelected: (MattingPreset) -> Unit,
    maxMenuWidth: Dp = 680.dp,
    maxMenuHeight: Dp? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }
    val currentSelected = remember(selectedPresetId, presets) {
        presets.find { it.id == selectedPresetId }
    }

    Box(modifier = modifier) {
        // 触发胶囊按钮
        OutlinedButton(
            onClick = { isExpanded = true },
            shape = AppShapes.small,
            modifier = Modifier
                .height(32.dp)
                .tip("选择专业场景提示词方案（已按提取、修复、状态分组排布）"),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(spacing.extraSmall))
            Text(
                text = currentSelected?.nameZh ?: "场景预设 ▾",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (currentSelected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            offset = DpOffset(0.dp, 4.dp),
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .widthIn(min = 380.dp, max = maxMenuWidth)
                .heightIn(max = maxMenuHeight ?: 520.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 3 大分类分别渲染
                PresetCategoryGroup.entries.forEach { group ->
                    val groupPresets = presets.filter { group.filterPredicate(it.id) }
                    if (groupPresets.isNotEmpty()) {
                        // 分类标题行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = group.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 该分类下的条目
                        groupPresets.forEach { preset ->
                            val isSelected = preset.id == selectedPresetId
                            SingleLinePresetItem(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = {
                                    onPresetSelected(preset)
                                    isExpanded = false
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单行预设条目组件
 */
@Composable
private fun SingleLinePresetItem(
    preset: MattingPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(AppShapes.small)
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        },
        shape = AppShapes.small,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 场景名称
            Text(
                text = preset.nameZh,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(Modifier.width(spacing.small))
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.width(spacing.small))

            // 详细描述（允许两行弹性展示，完整展现丰富内容）
            Text(
                text = preset.descriptionZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Spacer(Modifier.width(spacing.small))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

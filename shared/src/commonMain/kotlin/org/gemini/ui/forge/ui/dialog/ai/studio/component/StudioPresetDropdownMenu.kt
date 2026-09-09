package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * 视觉工作室专业场景预设下拉菜单（单行清爽版）
 *
 * 核心特性：
 * 1. 纯净单行排版：分类胶囊 + 场景名称 + 详细描述单行横向铺开，绝不分行堆叠，视觉整洁利落；
 * 2. 视口自适应宽度：根据界面宽度自适应展开至 560dp ~ 880dp；
 * 3. 纵向规整单列展示 8 大场景。
 */
@Composable
fun StudioPresetDropdownMenu(
    presets: List<MattingPreset>,
    selectedPresetId: String?,
    onPresetSelected: (MattingPreset) -> Unit,
    adaptiveWidth: Dp = 680.dp,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }
    val currentSelected = remember(selectedPresetId, presets) {
        presets.find { it.id == selectedPresetId }
    }

    val menuWidth = adaptiveWidth.coerceIn(560.dp, 880.dp)

    Box(modifier = modifier) {
        // 1. 触发胶囊按钮
        OutlinedButton(
            onClick = { isExpanded = true },
            shape = AppShapes.small,
            modifier = Modifier
                .height(32.dp)
                .tip("点击展开专业场景提示词预设方案库（支持 8 大高质感场景）"),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(spacing.extraSmall))
            Text(
                text = currentSelected?.nameZh ?: "💡 选择专业场景预设 (8 大场景)...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (currentSelected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(spacing.extraSmall))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        // 2. 单列单行卡片式下拉弹窗
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            offset = DpOffset(0.dp, 4.dp),
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = 420.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🎯 专业场景预设方案库 (点击一键填入，可继续自由编辑)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )

                presets.forEach { preset ->
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
            }
        }
    }
}

/**
 * 单行预设条目组件（一行完整展示分类、名称与描述）
 */
@Composable
private fun SingleLinePresetItem(
    preset: MattingPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val categoryTag = getCategoryTag(preset.id)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(AppShapes.small)
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        shape = AppShapes.small,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 分类微胶囊
            Surface(
                color = categoryTag.containerColor,
                shape = AppShapes.small
            ) {
                Text(
                    text = categoryTag.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryTag.contentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(spacing.small))

            // 2. 场景名称
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

            // 3. 详细描述（单行展示）
            Text(
                text = preset.descriptionZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 4. 选中图标
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

/** 场景分类标签辅助模型 */
private data class PresetCategoryTag(val label: String, val containerColor: Color, val contentColor: Color)

@Composable
private fun getCategoryTag(presetId: String): PresetCategoryTag {
    return when {
        presetId.startsWith("extract") -> PresetCategoryTag(
            label = "提取",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        )
        presetId.startsWith("clean") || presetId.startsWith("remove") -> PresetCategoryTag(
            label = "修复",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.tertiary
        )
        presetId.contains("contrast") || presetId.contains("bg") -> PresetCategoryTag(
            label = "抠图",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary
        )
        else -> PresetCategoryTag(
            label = "状态",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
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
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ModelStudioGroup
import org.gemini.ui.forge.model.ModelStudioMeta
import org.gemini.ui.forge.model.StudioModelResolver
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 视觉工作室会话级模型与并发生成数量配置行（动态能力自适应 + Bento 卡片化美学版）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSessionConfigRow(
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    generationCount: Int,
    onCountSelected: (Int) -> Unit,
    maxMenuWidth: Dp = 480.dp,
    maxMenuHeight: Dp? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isModelMenuExpanded by remember { mutableStateOf(false) }

    // ★ 动态能力驱动：直接从枚举全量动态探测解析，自动过滤无关模型，新模型秒级就绪
    val studioModels = remember { StudioModelResolver.resolveStudioModels() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 1. 模型切换下拉选择框
        Box {
            OutlinedButton(
                onClick = { isModelMenuExpanded = true },
                shape = AppShapes.small,
                modifier = Modifier
                    .height(32.dp)
                    .tip("切换当前会话专属的生图/多模态模型"),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(spacing.extraSmall))
                Text(
                    text = selectedModel.displayName.replace("Preview", "").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 卡片化双分组下拉菜单（自适应内容，限制最大宽与最大高）
            DropdownMenu(
                expanded = isModelMenuExpanded,
                onDismissRequest = { isModelMenuExpanded = false },
                offset = DpOffset(0.dp, 4.dp),
                properties = PopupProperties(focusable = true),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = maxMenuWidth)
                    .heightIn(max = maxMenuHeight ?: 520.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 菜单顶部标题行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 选择生图 / 多模态大模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "共 ${studioModels.size} 个模型",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 按体系双分组动态渲染
                    ModelStudioGroup.entries.forEach { group ->
                        val groupModels = studioModels.filter { it.group == group }
                        if (groupModels.isNotEmpty()) {
                            // 分组标题
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = group.icon,
                                    contentDescription = null,
                                    tint = group.brandColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = group.brandColor
                                )
                            }

                            // 组内各模型卡片条目
                            groupModels.forEach { meta ->
                                val isSelected = meta.model == selectedModel
                                ModelMenuItemCard(
                                    meta = meta,
                                    isSelected = isSelected,
                                    onClick = {
                                        onModelSelected(meta.model)
                                        isModelMenuExpanded = false
                                    }
                                )
                            }

                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // 2. 单次生成数量分段单选胶囊 (1 / 2 / 4 张)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.tip("单次并发生成的图片数量（默认 1 张以节省资源）")
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.height(30.dp)
            ) {
                val counts = listOf(1, 2, 4)
                counts.forEachIndexed { index, count ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = counts.size),
                        onClick = { onCountSelected(count) },
                        selected = generationCount == count,
                        icon = {},
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "${count}张",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (generationCount == count) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * 独立的圆角轻质感模型卡片条目组件
 */
@Composable
private fun ModelMenuItemCard(
    meta: ModelStudioMeta,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        shape = AppShapes.small,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // 第一行：品牌微图标 + 模型名称 + 特性胶囊徽标 + 选中对勾
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = meta.group.brandColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = meta.group.icon,
                                contentDescription = null,
                                tint = meta.group.brandColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(spacing.small))

                    Text(
                        text = meta.model.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.width(spacing.small))

                    Surface(
                        shape = AppShapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = meta.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // 第二行：细腻弱化特性说明
            Text(
                text = meta.summaryZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

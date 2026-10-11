package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
 * 通用 AI 对话模型选择下拉菜单（对齐公用旗舰卡片化 DropdownMenu 架构与响应式自适应布局）
 *
 * @param expanded 菜单是否展开
 * @param selectedModel 当前选中的 Gemini 模型
 * @param onModelSelected 选择模型后的回调
 * @param onDismissRequest 关闭菜单回调
 * @param maxMenuWidth 最大宽度约束（默认 440.dp）
 * @param maxMenuHeight 最大高度约束（默认 520.dp）
 * @param offset 弹出位置偏移
 * @param modifier 修饰符
 */
@Composable
fun CommonModelSelectionMenu(
    expanded: Boolean,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    onDismissRequest: () -> Unit,
    minMenuWidth: Dp = 0.dp,
    maxMenuWidth: Dp = 440.dp,
    maxMenuHeight: Dp = 520.dp,
    offset: DpOffset = DpOffset(0.dp, (-8).dp),
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val studioModels = remember { StudioModelResolver.resolveStudioModels() }
    val effectiveMinWidth = maxOf(320.dp, minMenuWidth)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = PopupProperties(focusable = true),
        modifier = modifier
            .widthIn(min = effectiveMinWidth, max = maxMenuWidth)
            .heightIn(max = maxMenuHeight)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                AppShapes.medium
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. 顶部标题栏
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

            // 2. 按体系双分组动态渲染
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
                        CommonModelItemCard(
                            meta = meta,
                            isSelected = isSelected,
                            onClick = {
                                onModelSelected(meta.model)
                                onDismissRequest()
                            }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * 组内单模型条目卡片
 */
@Composable
private fun CommonModelItemCard(
    meta: ModelStudioMeta,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .clickable(onClick = onClick)
            .tip("切换至 ${meta.model.displayName}"),
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
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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

            Spacer(Modifier.height(3.dp))

            // 第二行：模型特长能力标签行（生图、思考推理等）
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (meta.model.supportsThinking) {
                    Surface(
                        shape = AppShapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "深度思考",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Surface(
                    shape = AppShapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "多模态视觉",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "• ${meta.summaryZh}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

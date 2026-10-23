package org.gemini.ui.forge.ui.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.service.studio.LayoutDiffResult
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * AI 布局变更 Diff 对比摘要与撤回卡片
 *
 * 遵循一文件一 Composable 铁律与设计系统 Tokens 规范。
 *
 * @param diffResult 布局比对差分结果
 * @param onUndo 一键撤回操作回调
 * @param modifier 修饰符
 */
@Composable
fun LayoutDiffSummaryCard(
    diffResult: LayoutDiffResult,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!diffResult.hasChanges) return
    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧变更胶囊标签与折叠切换
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                ) {
                    Text(
                        text = "📐 布局变更对比",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (diffResult.addedBlocks.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B5E20).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "+${diffResult.addedBlocks.size} 新增",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (diffResult.modifiedBlocks.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE65100).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "~${diffResult.modifiedBlocks.size} 调整",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (diffResult.removedBlocks.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = "-${diffResult.removedBlocks.size} 移除",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起详情" else "展开详情",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 右侧一键撤回按钮
                OutlinedButton(
                    onClick = onUndo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).tip("撤回本次 AI 排版调整并恢复上一版本")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤回",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "撤回排版",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 展开的变更明细清单
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    diffResult.addedBlocks.forEach { block ->
                        val blockTitle = "${block.type.name} (${block.id})"
                        Text(
                            text = "  + 新增: [$blockTitle] 尺寸: ${block.bounds.width.toInt()}×${block.bounds.height.toInt()} 坐标: (${block.bounds.left.toInt()}, ${block.bounds.top.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp
                        )
                    }

                    diffResult.modifiedBlocks.forEach { mod ->
                        val moveInfo = if (mod.isMoved) "位移 (${mod.oldBounds?.left?.toInt()}, ${mod.oldBounds?.top?.toInt()}) → (${mod.newBounds?.left?.toInt()}, ${mod.newBounds?.top?.toInt()})" else ""
                        val resizeInfo = if (mod.isResized) "尺寸调整 ${mod.oldBounds?.width?.toInt()}×${mod.oldBounds?.height?.toInt()} → ${mod.newBounds?.width?.toInt()}×${mod.newBounds?.height?.toInt()}" else ""
                        val detail = listOf(moveInfo, resizeInfo).filter { it.isNotBlank() }.joinToString("，")
                        Text(
                            text = "  ~ 调整: [${mod.name}] $detail",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800),
                            fontSize = 11.sp
                        )
                    }

                    diffResult.removedBlocks.forEach { block ->
                        val blockTitle = "${block.type.name} (${block.id})"
                        Text(
                            text = "  - 移除: [$blockTitle]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

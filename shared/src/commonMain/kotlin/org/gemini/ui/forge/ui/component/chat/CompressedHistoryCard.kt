package org.gemini.ui.forge.ui.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 长对话上下文压缩与记忆结晶状态展示卡片
 *
 * 遵循《一文件一 Composable》铁律与 ui-designer.md 人机工程学规范：
 * - 嵌套圆角曲率守恒：外层 16dp，内层底板 8dp；
 * - 8pt 空间网格系统：使用 [LocalAppSpacing.current]；
 * - 触控与光标热区保证：操作按钮热区 >= 32dp。
 *
 * @param compressedCount 已被压缩提炼的历史消息总数
 * @param summaryMarkdown 提炼出的核心设计记忆与图元拓扑 Markdown 正文
 * @param estimatedTokensSaved 预估节省的 Token 总数
 * @param onRecompress 可选的重新提炼回调
 * @param modifier 修饰符
 */
@Composable
fun CompressedHistoryCard(
    compressedCount: Int,
    summaryMarkdown: String,
    estimatedTokensSaved: Int,
    onRecompress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (compressedCount <= 0 && summaryMarkdown.isBlank()) return

    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.small)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 左侧摘要标题与指标胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (compressedCount > 0) "已结晶压缩 $compressedCount 条前序历史" else "长对话设计记忆与约束",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (estimatedTokensSaved > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "节省 ~${estimatedTokensSaved} Tokens",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 右侧操作区：重新提炼与展开/折叠
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    if (onRecompress != null) {
                        IconButton(
                            onClick = onRecompress,
                            modifier = Modifier
                                .size(32.dp)
                                .tip("重新提炼并更新设计记忆")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新提炼",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "收起记忆" else "查看设计记忆",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = spacing.small)
                ) {
                    // 内层摘要底板（嵌套圆角守恒 8dp，曲率视觉协调）
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = summaryMarkdown.ifBlank { "（暂无详细设计记忆内容）" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 19.sp,
                                    fontSize = 12.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(spacing.small)
                            )
                        }
                    }
                }
            }
        }
    }
}

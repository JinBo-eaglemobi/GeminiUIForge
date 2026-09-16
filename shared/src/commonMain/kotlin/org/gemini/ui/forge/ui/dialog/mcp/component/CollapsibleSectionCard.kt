package org.gemini.ui.forge.ui.dialog.mcp.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 现代通用可折叠卡片组件 (带旋转动效与折叠摘要)
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CollapsibleSectionCard(
    icon: ImageVector,
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    badge: @Composable (() -> Unit)? = null,
    headerExtra: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val arrowRotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            // 头部栏（整行点击切换展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .clickable { onToggle() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(spacing.small))
                        badge()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (headerExtra != null) {
                        headerExtra()
                        Spacer(Modifier.width(spacing.small))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(28.dp).tip(if (isExpanded) "收起" else "展开")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }
                }
            }

            // 折叠动效区
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

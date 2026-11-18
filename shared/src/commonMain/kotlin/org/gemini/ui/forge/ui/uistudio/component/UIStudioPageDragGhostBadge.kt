package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 视图页面拖拽过程中跟随鼠标指针实时移动的浮动胶囊徽标 (Page Drag Ghost Badge)。
 */
@Composable
fun UIStudioPageDragGhostBadge(
    pageId: String?,
    cursorPos: Offset?,
    modifier: Modifier = Modifier
) {
    if (pageId == null || cursorPos == null) return

    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val dynamicBadgeStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(spacing.inspectorFieldCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 8.dp,
        modifier = modifier
            .offset {
                with(density) {
                    IntOffset(
                        (cursorPos.x + spacing.medium.toPx()).toInt(),
                        (cursorPos.y + spacing.medium.toPx()).toInt()
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                modifier = Modifier.size(spacing.inspectorInlineIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = pageId,
                style = dynamicBadgeStyle.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

package org.gemini.ui.forge.ui.dialog.system.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.question_badge_recommended
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 问询弹窗选项卡片：label + description 双行结构 + 推荐徽章 + 单选/多选指示器
 * 交互七态：Default / Hover（底色提亮）/ Pressed（ripple）/ Focus-visible / Disabled / Selected
 */
@Composable
fun QuestionOptionCard(
    label: String,
    description: String,
    selected: Boolean,
    multiple: Boolean,
    recommended: Boolean = false,
    onClick: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = spacing.medium,
                vertical = spacing.small
            )
        ) {
            if (multiple) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() }
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = onClick
                )
            }
            Spacer(Modifier.width(spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (recommended) {
                        Spacer(Modifier.width(spacing.small))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(Res.string.question_badge_recommended),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(
                                    horizontal = spacing.extraSmall + 2.dp,
                                    vertical = 2.dp
                                )
                            )
                        }
                    }
                }
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

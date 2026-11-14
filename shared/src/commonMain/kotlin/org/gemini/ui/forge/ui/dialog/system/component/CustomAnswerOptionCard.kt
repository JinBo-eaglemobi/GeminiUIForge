package org.gemini.ui.forge.ui.dialog.system.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 问询弹窗自定义输入原位变形卡片：
 * 1. 未激活态：展现普通卡片形式（RadioButton + 标题“自定义答案” + 副标题）；
 * 2. 激活态：原位直接变形为无边框透明背景输入框，文案变成占位提示词，点击瞬间自动聚焦，零额外下挂控件。
 */
@Composable
fun CustomAnswerOptionCard(
    value: String,
    onValueChange: (String) -> Unit,
    active: Boolean,
    onActivate: () -> Unit,
    placeholder: String = "输入你自己的答案或补充意见…",
    title: String = "自定义答案",
    description: String = "不选预设方案，输入你自己的答案"
) {
    val spacing = LocalAppSpacing.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(active) {
        if (active) {
            focusRequester.requestFocus()
        }
    }

    val containerColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
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
            ) {
                if (!active) {
                    onActivate()
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    horizontal = spacing.medium,
                    vertical = if (active) spacing.extraSmall else spacing.small
                )
                .fillMaxWidth()
        ) {
            RadioButton(
                selected = active,
                onClick = {
                    if (!active) {
                        onActivate()
                    }
                }
            )

            Spacer(Modifier.width(spacing.small))

            if (active) {
                // 激活态：原位直接变形为无边框内联输入框（使用 TextField 保持 Compose 桌面端完美 IME / 键盘事件分发）
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = false,
                    maxLines = 3
                )
            } else {
                // 未激活态：普通标题与副标题
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

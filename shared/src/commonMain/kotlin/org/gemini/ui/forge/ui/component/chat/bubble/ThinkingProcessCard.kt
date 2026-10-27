package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.math.roundToInt

/**
 * 模型思考态折叠与微光脉冲动效卡片 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 思考中状态：呈现微光呼吸脉冲动画 (0.35f ~ 0.9f) 与毫秒级秒表计时，提供沉浸式推理反馈；
 * 2. 思考完成状态：转为淡雅高阶卡片，呈现已完成深度思考标签及推理耗时（秒），默认折叠节省视口空间；
 * 3. 展开查看：支持自由划选文本与一键复制完整思维链；
 * 4. 严格遵循 Design Tokens 与 UI/UX 人机工程学规范。
 */
@Composable
fun ThinkingProcessCard(
    thought: String,
    isThinking: Boolean = false,
    durationMs: Long? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    var isExpanded by remember { mutableStateOf(false) }

    // 思考中秒表计时器
    var elapsedSeconds by remember { mutableStateOf(0f) }
    LaunchedEffect(isThinking) {
        if (isThinking) {
            val startTime = getCurrentTimeMillis()
            while (isActive) {
                delay(100L)
                val current = getCurrentTimeMillis()
                elapsedSeconds = (current - startTime) / 1000f
            }
        }
    }

    // 微光呼吸脉冲动画 (仅在 isThinking = true 时启动)
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingPulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ThinkingPulseAlpha"
    )

    val effectiveAlpha = if (isThinking) pulseAlpha else 1f
    val borderColor = if (isThinking) {
        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val containerColor = if (isThinking) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)) {
            // 顶栏操作与状态行 (通过 DisableSelection 阻断外层划选光标侵入)
            DisableSelection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = thought.isNotBlank()) { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isThinking) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = "思考中",
                            modifier = Modifier.size(16.dp).alpha(effectiveAlpha),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = "已完成思考",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.width(spacing.small))

                    Text(
                        text = if (isThinking) {
                            "深度思考与逻辑推演中..."
                        } else {
                            if (isExpanded) "收起深度思考过程" else "查看深度思考过程"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    // 耗时指示器
                    val displayTime = when {
                        isThinking -> "${((elapsedSeconds * 10).roundToInt() / 10f)}s"
                        durationMs != null -> "${((durationMs / 100f).roundToInt() / 10f)}s"
                        else -> null
                    }
                    if (displayTime != null) {
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text(
                            text = "· $displayTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (thought.isNotBlank()) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 展开后的思维链详情
            AnimatedVisibility(visible = isExpanded && thought.isNotBlank()) {
                Column(modifier = Modifier.padding(top = spacing.small)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 1.dp
                    )
                    Spacer(Modifier.height(spacing.extraSmall))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                copy(thought, "思考过程已复制到剪贴板")
                            },
                            modifier = Modifier.size(24.dp).tip("复制思维链内容")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = thought,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                        )
                    }
                }
            }
        }
    }
}

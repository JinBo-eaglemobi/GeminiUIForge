package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.service.chat.ChatErrorAction
import org.gemini.ui.forge.service.chat.ClassifiedChatError
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * AI 异常智能诊断自愈卡片 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 结构化呈现异常类型与人类可读的友好排查建议；
 * 2. 针对特定异常动态提供针对性自愈行动（如 429 降级 Flash 模型、协议不合规切换协议）；
 * 3. 原始异常支持一键折叠/展开与复制，遵循 UI/UX 人机工程学设计规范。
 */
@Composable
fun ChatErrorActionCard(
    classifiedError: ClassifiedChatError,
    onRetry: () -> Unit,
    onFallbackFlashModel: (() -> Unit)? = null,
    onSwitchProtocol: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    var isRawDetailExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            // 1. 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "错误提示",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(spacing.small))
                Text(
                    text = classifiedError.friendlyTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(spacing.extraSmall))

            // 2. 友好建议
            Text(
                text = classifiedError.friendlyAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )

            Spacer(Modifier.height(spacing.small))

            // 3. 原始异常堆栈/报文折叠面板
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isRawDetailExpanded = !isRawDetailExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRawDetailExpanded) "收起原始错误信息" else "展开原始错误诊断信息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (isRawDetailExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }

            AnimatedVisibility(visible = isRawDetailExpanded) {
                Column(modifier = Modifier.padding(top = spacing.extraSmall)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = classifiedError.originalMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(spacing.small)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))

            // 4. 自愈行动操作条
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 行动 1：根据推荐策略呈现高亮主行动按钮
                when (classifiedError.recommendedAction) {
                    ChatErrorAction.FALLBACK_FLASH_MODEL -> {
                        if (onFallbackFlashModel != null) {
                            Button(
                                onClick = onFallbackFlashModel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.extraSmall),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("降级 3.8 Flash 并重试", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ChatErrorAction.SWITCH_GENERATE_CONTENT -> {
                        if (onSwitchProtocol != null) {
                            Button(
                                onClick = onSwitchProtocol,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.extraSmall),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("切换标准协议重试", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {}
                }

                // 常规重试按钮
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.extraSmall),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新生成", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.weight(1f))

                // 复制错误信息按钮
                IconButton(
                    onClick = {
                        copy(classifiedError.originalMessage, "错误信息已复制到剪贴板")
                    },
                    modifier = Modifier.size(28.dp).tip("复制完整错误日志")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制错误",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

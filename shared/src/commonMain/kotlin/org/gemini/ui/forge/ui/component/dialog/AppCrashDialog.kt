package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.model.crash.CrashPayload
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 应用内高保真全局异常弹窗 (In-App Crash & Visual Forensics Dialog)。
 *
 * 核心人机工程学与取证使命：
 * 1. 替代 OS 默认丑陋且阻塞 MCP 截图的外部灰色弹窗；
 * 2. 渲染于 Compose 顶层，确保 [screenshot_window] 工具可 100% 捕获完整报错画面与文字；
 * 3. 严格遵循系统原本的处理生命周期契约：点击"确定 / 退出程序"安全退出，亦提供快捷复制与忽略通道。
 *
 * @param payload 异常载荷实体
 * @param onExit 退出应用程序回调（执行系统原有退出逻辑）
 * @param onDismiss 忽略并尝试恢复工作区回调
 */
@Composable
fun AppCrashDialog(
    payload: CrashPayload,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    var isStackExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* 强模态拦截，禁止点击外部空白区域盲目关闭 */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .width(spacing.dialogConfigWidth)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium)
            ) {
                // 1. 标题栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(spacing.small))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payload.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "线程: ${payload.threadName} · 运行态异常已捕获留痕",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                // 2. 错误消息正文卡片
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = payload.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(spacing.small)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                // 3. 详细堆栈折叠切换栏
                TextButton(
                    onClick = { isStackExpanded = !isStackExpanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isStackExpanded) "收起详细错误堆栈回溯" else "展开详细错误堆栈回溯",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isStackExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isStackExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val stackScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(AppShapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), AppShapes.small)
                            .padding(spacing.small)
                    ) {
                        Text(
                            text = payload.stackTrace,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(stackScrollState)
                                .padding(end = 8.dp)
                        )
                        VerticalScrollbarAdapter(
                            scrollState = stackScrollState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                // 4. 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：复制报错详情
                    OutlinedButton(
                        onClick = {
                            val fullContent = buildString {
                                appendLine("【崩溃标题】: ${payload.title}")
                                appendLine("【崩溃线程】: ${payload.threadName}")
                                appendLine("【错误信息】: ${payload.message}")
                                appendLine("【堆栈回溯】:\n${payload.stackTrace}")
                            }
                            copy(fullContent, "已复制完整错误信息与堆栈到剪贴板")
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.tip("将完整报错内容与代码堆栈复制到系统剪贴板")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制详情", style = MaterialTheme.typography.labelSmall)
                    }

                    // 右侧：退出与尝试继续
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.tip("关闭此弹窗并尝试继续停留在工作区")
                        ) {
                            Text(
                                "尝试忽略",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = onExit,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.tip("遵循系统原有契约，安全退出当前程序")
                        ) {
                            Text("确定并退出", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

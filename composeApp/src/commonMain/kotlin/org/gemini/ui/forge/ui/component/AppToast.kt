package org.gemini.ui.forge.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.getCurrentTimeMillis

/** 提示类型 */
enum class ToastType {
    INFO, SUCCESS, ERROR
}

/**
 * 提示框数据模型。
 * 包含唯一 ID（使用时间戳）以确保连续发送相同消息时能重新触发动画。
 */
data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMillis: Long = 3000L,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = getCurrentTimeMillis()
)

/**
 * 全局顶部下滑提示框组件。
 * 包含自动关闭倒计时，支持手动点击关闭和自定义操作按钮。
 */
@Composable
fun AppToastContainer(
    toastData: ToastData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 倒计时的剩余进度 (1.0 -> 0.0)
    var progress by remember { mutableStateOf(1f) }

    // 监听数据变化，启动自动关闭定时器和进度条动画
    LaunchedEffect(toastData) {
        if (toastData != null) {
            progress = 1f
            val startTime = getCurrentTimeMillis()
            val duration = toastData.durationMillis

            launch {
                while (true) {
                    val elapsed = getCurrentTimeMillis() - startTime
                    if (elapsed >= duration) {
                        progress = 0f
                        break
                    }
                    progress = 1f - (elapsed.toFloat() / duration)
                    delay(16) // 约 60fps 刷新一次
                }
            }

            delay(duration)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = toastData != null,
        // 从顶部以外的位置向下滑入
        enter = slideInVertically(
            initialOffsetY = { -it - 50 },
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(animationSpec = tween(300)),
        // 向上滑出
        exit = slideOutVertically(
            targetOffsetY = { -it - 50 },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        toastData?.let { data ->
            val colorScheme = MaterialTheme.colorScheme
            // 根据主题底色的亮度来粗略判断当前是深色还是浅色模式，以便单独调配“成功”的绿色
            val isDark = colorScheme.surface.luminance() < 0.5f

            // 根据类型设定背景色
            val backgroundColor = when (data.type) {
                ToastType.SUCCESS -> if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                ToastType.ERROR -> colorScheme.errorContainer
                ToastType.INFO -> colorScheme.primaryContainer
            }

            // 根据类型设定文字和图标的颜色
            val contentColor = when (data.type) {
                ToastType.SUCCESS -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                ToastType.ERROR -> colorScheme.onErrorContainer
                ToastType.INFO -> colorScheme.onPrimaryContainer
            }

            // 根据类型设定图标
            val icon = when (data.type) {
                ToastType.SUCCESS -> Icons.Default.CheckCircle
                ToastType.ERROR -> Icons.Default.Error
                ToastType.INFO -> Icons.Default.Info
            }

            Surface(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    // 限制最小和最大宽度，为操作按钮留出空间
                    .widthIn(min = 250.dp, max = 500.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = backgroundColor,
                contentColor = contentColor
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = data.message,
                            style = MaterialTheme.typography.bodySmall, // 更紧凑的字体
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // 如果传入了操作按钮，则渲染
                        if (data.actionLabel != null && data.onAction != null) {
                            TextButton(
                                onClick = {
                                    data.onAction.invoke()
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                            ) {
                                Text(data.actionLabel, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // 手动关闭按钮
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 底部平滑倒计时进度条
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = contentColor.copy(alpha = 0.5f),
                        trackColor = Color.Transparent,
                        drawStopIndicator = {} // 禁用圆角端点（Compose M3 中默认有的端点）
                    )
                }
            }
        }
    }
}

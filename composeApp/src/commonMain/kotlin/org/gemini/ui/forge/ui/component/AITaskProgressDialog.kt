package org.gemini.ui.forge.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 通用的 AI 任务执行进度与日志对话框 (共享组件)
 */
@Composable
fun AITaskProgressDialog(
    title: String = "AI 正在处理...",
    logs: List<String>,
    isProcessing: Boolean, // 是否正在运行中
    isLogVisible: Boolean,
    onToggleLogVisibility: () -> Unit,
    onActionClick: () -> Unit, // 点击动作按钮 (运行中是中断，结束时是关闭)
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).height(400.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 状态标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, "Done", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        if (isProcessing) title else "处理任务已结束",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isProcessing) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                    )
                }
                
                Spacer(Modifier.height(24.dp))

                // 2. 日志控制条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("运行日志", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onToggleLogVisibility) {
                        Icon(
                            if (isLogVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. 日志显示区
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLogVisible,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.05f), AppShapes.medium).padding(8.dp)
                        ) {
                            LazyColumn(state = listState) {
                                items(logs) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        ),
                                        color = when {
                                            log.contains("错误", true) || log.contains("FAIL", true) -> MaterialTheme.colorScheme.error
                                            log.startsWith(">>>") -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.02f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 4. 操作按钮 (手动关闭)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onActionClick,
                        colors = if (isProcessing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
                                 else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = AppShapes.medium
                    ) {
                        Icon(if (isProcessing) Icons.Default.Cancel else Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isProcessing) "中断处理" else "关闭面板")
                    }
                }
            }
        }
    }
}

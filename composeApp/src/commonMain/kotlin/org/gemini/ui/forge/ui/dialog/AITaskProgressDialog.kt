package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 通用的 AI 任务执行进度与日志对话框 (共享组件)
 * 经过任务 3 重构：支持实时状态行、语义化日志和组合扩展。
 */
@Composable
fun AITaskProgressDialog(
    title: String = "AI 正在处理...",
    currentStatus: String = "", // 任务 3 新增：实时单行任务状态 (如进度数据大小)
    logs: List<String>,
    isProcessing: Boolean, // 是否正在运行中
    isLogVisible: Boolean,
    onToggleLogVisibility: () -> Unit,
    onActionClick: () -> Unit, // 点击动作按钮
    onDismiss: () -> Unit = {},
    // 任务 3 新增：组合扩展点，允许在日志上方插入自定义 UI (如任务 4 的并行列表)
    extraContent: (@Composable ColumnScope.() -> Unit)? = null 
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
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(min = 400.dp, max = 700.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 顶部栏：标题与状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Default.TaskAlt, "Done", tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (isProcessing) title else "处理任务已结束",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isProcessing) MaterialTheme.colorScheme.onSurface else Color(0xFF4CAF50)
                    )
                }

                // 2. 任务 3 核心增强：实时状态行
                if (currentStatus.isNotBlank() && isProcessing) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = AppShapes.small,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                // 3. 任务 3 新增：外部组合扩展点 (预留给任务 4 使用)
                extraContent?.invoke(this)

                // 4. 日志控制与列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "历史执行日志 (${logs.size})", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onToggleLogVisibility) {
                        Icon(
                            if (isLogVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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
                            SelectionContainer {
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
                                                log.contains("❌") || log.contains("错误") -> MaterialTheme.colorScheme.error
                                                log.contains("✅") -> Color(0xFF4CAF50)
                                                log.contains("⚠️") -> Color(0xFFFFA000)
                                                log.startsWith(">>>") || log.startsWith("---") -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.03f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 5. 底部操作栏
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!isProcessing) {
                        TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                            Text("忽略并返回")
                        }
                    }
                    Button(
                        onClick = onActionClick,
                        colors = if (isProcessing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
                                 else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = AppShapes.medium
                    ) {
                        Icon(if (isProcessing) Icons.Default.StopCircle else Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isProcessing) "中断所有任务" else "完成并关闭")
                    }
                }
            }
        }
    }
}

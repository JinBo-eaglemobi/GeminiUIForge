package org.gemini.ui.forge.ui.dialog.layer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gemini.ui.forge.model.history.HistoryEntry
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.layer.component.CurrentHistoryItem
import org.gemini.ui.forge.ui.dialog.layer.component.HistoryItem
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 操作历史记录面板弹窗 (HistoryPanelDialog)。
 *
 * 严格遵循弹窗宽度规范 (dialogConfigWidth = 520dp) 与一文件一 Composable 铁律。
 * 展示项目全量操作快照，支持时间戳展示与秒级历史跳转回溯。
 */
@Composable
fun HistoryPanelDialog(
    undoStack: List<HistoryEntry>,
    redoStack: List<HistoryEntry>,
    currentLabel: String = "当前状态",
    onJump: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogConfigWidth)
                .heightIn(min = 400.dp, max = 620.dp),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // 1. 头部标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "操作历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).tip("关闭历史面板")
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2. 历史快照列表
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (undoStack.isEmpty() && redoStack.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无操作历史记录", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 2.1 重做队列 (未来状态)
                            items(redoStack.asReversed()) { entry ->
                                HistoryItem(entry, isRedo = true, onClick = { onJump(entry.id) })
                            }

                            // 2.2 当前活跃快照
                            item {
                                CurrentHistoryItem(currentLabel)
                            }

                            // 2.3 撤销队列 (过去状态)
                            items(undoStack.asReversed()) { entry ->
                                HistoryItem(entry, isRedo = false, onClick = { onJump(entry.id) })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 3. 底部重置操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onReset,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.tip("将所有模块图元状态一键回滚重置到刚打开时的初始快照")
                    ) {
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重置到最初状态", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

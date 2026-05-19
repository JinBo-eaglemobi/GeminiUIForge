package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gemini.ui.forge.model.history.HistoryEntry
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 历史记录面板弹窗。
 * 展示当前项目的所有操作快照，支持跳转回溯。
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(0.7f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // 头部
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("操作历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                Spacer(Modifier.height(16.dp))

                // 列表
                Box(Modifier.weight(1f)) {
                    if (undoStack.isEmpty() && redoStack.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无操作记录", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 1. 未来状态 (Redo Stack) - 倒序排列，最近的在最前
                            items(redoStack.asReversed()) { entry ->
                                HistoryItem(entry, isRedo = true, onClick = { onJump(entry.id) })
                            }

                            // 2. 当前活跃状态
                            item {
                                CurrentHistoryItem(currentLabel)
                            }

                            // 3. 过去状态 (Undo Stack) - 倒序排列，最近的在最前
                            items(undoStack.asReversed()) { entry ->
                                HistoryItem(entry, isRedo = false, onClick = { onJump(entry.id) })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 底部操作
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onReset,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重置到最初状态")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, isRedo: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.Transparent,
        shape = AppShapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(8.dp).background(
                    if (isRedo) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    shape = CircleShape
                )
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRedo) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CurrentHistoryItem(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = AppShapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

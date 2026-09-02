package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_ws_console
import geminiuiforge.composeapp.generated.resources.gp_ws_console_clear
import geminiuiforge.composeapp.generated.resources.gp_ws_console_toggle
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 底部控制台面板：
 * 包含控制栏（图标 + 标题 + 日志等级 FilterChips + 一键清空 + 关闭）；
 * 日志输出区（LazyColumn + Monospace 字体 + 支持长按选择复制 + 支持日志级别筛选）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomConsolePanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    var selectedLogLevel by remember { mutableStateOf("ALL") }

    // 响应式筛选日志：依据等级前缀判定（[VERBOSE]、[INFO]、[WARN]、[ERROR]、[FATAL]）
    val filteredLogs = remember(viewModel.debugLogs, selectedLogLevel) {
        viewModel.debugLogs.filter { line ->
            when (selectedLogLevel) {
                "INFO" -> line.startsWith("[INFO]") || line.startsWith("[WARN]") || line.startsWith("[ERROR]") || line.startsWith("[FATAL]")
                "WARN" -> line.startsWith("[WARN]") || line.startsWith("[ERROR]") || line.startsWith("[FATAL]")
                "ERROR" -> line.startsWith("[ERROR]") || line.startsWith("[FATAL]")
                else -> true // ALL
            }
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 控制栏：标题 + 筛选器 + 动作 + 关闭
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.gp_ws_console),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))

                    // Chrome 式日志等级筛选器：ALL、INFO、WARN、ERROR（呼吸色强调，M3 FilterChip 风格）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        listOf("ALL", "INFO", "WARN", "ERROR").forEach { level ->
                            FilterChip(
                                selected = selectedLogLevel == level,
                                onClick = { selectedLogLevel = level },
                                label = { Text(level, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    // 一键清空
                    IconButton(
                        onClick = { viewModel.clearDebugLogs() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.gp_ws_console_clear),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 关闭控制台
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.gp_ws_console_toggle),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 日志展示区域
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        item {
                            Text(
                                text = "No logs match the selected filter ($selectedLogLevel). stream web logs here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(filteredLogs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

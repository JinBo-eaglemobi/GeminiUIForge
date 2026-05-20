package org.gemini.ui.forge.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.gemini.ui.forge.utils.getPlatformLogDirectory
import org.gemini.ui.forge.utils.streamLocalFileLines
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter

/**
 * 系统日志查看器对话框
 * 用于展示应用运行期间产生的本地日志文件，支持实时刷新、分级过滤和智能滚动
 * 
 * @param onDismiss 关闭对话框的回调
 */
@Composable
fun LogViewerDialog(
    onDismiss: () -> Unit
) {
    // 当前选中的日志级别过滤条件 (ALL, INFO, ERROR)
    var selectedLevel by remember { mutableStateOf("ALL") }
    // 缓存当前展示的日志行列表
    val logLines = remember { mutableStateListOf<String>() }
    // 标识是否正在读取日志文件
    var isLoading by remember { mutableStateOf(false) }
    // 加载日志过程中的错误信息，为 null 表示正常
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    // 管理日志列表的滚动状态
    val listState = rememberLazyListState()

    // 智能锁定逻辑：只有当滚动位置接近底部时（最后可见项的 index 接近总数），才判定为“在底部”
    // 用于决定是否有新日志到来时自动滚动到底部
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                // 如果最后可见项的 index 接近总数，则判定为“在底部”
                lastVisibleItem != null && lastVisibleItem.index >= totalItems - 2
            }
        }
    }
    
    // 判断当前是否完全处于列表顶部（第一项且偏移量为 0）
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // 核心自动滚动触发器：当 logLines 大小发生变化，且用户当前处于底部时，强制滚动到最后一行
    LaunchedEffect(logLines.size) {
        if (isAtBottom && logLines.isNotEmpty()) {
            listState.scrollToItem(logLines.size - 1)
        }
    }

    /**
     * 流式加载日志
     * 根据当前选定的级别读取对应的本地日志文件，并追加到 logLines 缓存中
     */
    fun loadLogs() {
        coroutineScope.launch {
            logLines.clear()
            errorMessage = null
            isLoading = true
            try {
                val logDir = getPlatformLogDirectory()
                val fileName = when (selectedLevel) {
                    "ERROR" -> "app_error.log"
                    "INFO" -> "app_info.log"
                    else -> "app_debug.log"
                }
                
                // 使用封装的流式读取工具，避免一次性加载超大日志文件导致内存溢出
                streamLocalFileLines("$logDir/$fileName") { chunk ->
                    val filteredChunk = if (selectedLevel == "ALL") chunk else chunk.filter { it.contains("[$selectedLevel]") || it.startsWith("---") }
                    if (filteredChunk.isNotEmpty()) {
                        logLines.addAll(filteredChunk)
                    }
                }
                
                if (logLines.isEmpty()) {
                    errorMessage = "暂无日志或无法读取日志文件"
                }
            } catch (e: Exception) {
                errorMessage = "加载日志失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // 当日志级别切换时，重新加载日志
    LaunchedEffect(selectedLevel) {
        loadLogs()
    }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // 顶部标题与控制操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("系统日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在流式加载...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { loadLogs() }) {
                            Icon(Icons.Default.Refresh, "刷新日志")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 过滤器：点击 Chip 切换不同级别的日志视图
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "INFO", "ERROR").forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 日志核心显示区域 (深色背景，代码风格)
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))) {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Color.Gray, modifier = Modifier.padding(16.dp).align(Alignment.Center))
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 主列表：包含 SelectionContainer 以允许用户选中文本复制
                            SelectionContainer(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(8.dp).padding(end = 12.dp)
                                ) {
                                    items(logLines.size) { index ->
                                        val line = logLines[index]
                                        // 根据日志标签着色增强可读性
                                        val color = when {
                                            line.contains("[ERROR]") -> Color(0xFFFF6B6B)
                                            line.contains("[INFO]") -> Color(0xFF4EC9B0)
                                            line.contains("[DEBUG]") -> Color(0xFFCCCCCC)
                                            else -> Color(0xFFCCCCCC)
                                        }
                                        Text(
                                            text = line,
                                            color = color,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace, // 等宽字体
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            
                            // 垂直滚动条适配器，关联当前 listState
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                                scrollState = listState
                            )
                        }
                    }

                    // 悬浮导航按钮组（位于右下角）
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 当不在顶部且有日志数据时显示“回到顶部”按钮
                        AnimatedVisibility(visible = !isAtTop && logLines.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            FloatingActionButton(
                                onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
                                modifier = Modifier.size(40.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "回到顶部")
                            }
                        }
                        
                        // 当不在底部且有日志数据时显示“回到底部”按钮
                        AnimatedVisibility(visible = !isAtBottom && logLines.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            FloatingActionButton(
                                onClick = { coroutineScope.launch { listState.scrollToItem(logLines.size - 1) } },
                                modifier = Modifier.size(40.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "回到底部")
                            }
                        }
                    }
                }
            }
        }
    }
}

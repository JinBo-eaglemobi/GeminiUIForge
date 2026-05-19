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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.gemini.ui.forge.utils.getPlatformLogDirectory
import org.gemini.ui.forge.utils.streamLocalFileLines
import org.gemini.ui.forge.utils.AppLogger

@Composable
fun LogViewerDialog(
    onDismiss: () -> Unit
) {
    var selectedLevel by remember { mutableStateOf("ALL") }
    val logLines = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 自动滚动逻辑：如果当前就在底部，则新数据进来时自动跟随
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) true
            else {
                // reverseLayout = true 时，index 0 就是最底部的最新数据
                listState.firstVisibleItemIndex == 0
            }
        }
    }

    LaunchedEffect(logLines.size) {
        if (isAtBottom && logLines.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    fun loadLogs() {
        coroutineScope.launch {
            logLines.clear()
            errorMessage = null
            isLoading = true
            try {
                AppLogger.d("LogViewer", "请求分块加载本地日志文件")
                kotlinx.coroutines.delay(100)
                
                val logDir = getPlatformLogDirectory()
                val fileName = when (selectedLevel) {
                    "ERROR" -> "app_error.log"
                    "INFO" -> "app_info.log"
                    else -> "app_debug.log"
                }
                
                streamLocalFileLines("$logDir/$fileName") { chunk ->
                    logLines.addAll(chunk)
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
                // 头部
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

                // 过滤器
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val levels = listOf("ALL", "INFO", "ERROR")
                    levels.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 日志列表区域
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))) {
                    SelectionContainer {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            // 自定义一直显示的滚动条
                            Box(modifier = Modifier.fillMaxSize().padding(end = 4.dp)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(8.dp).drawWithContent {
                                        drawContent()
                                        // 绘制常驻滚动条
                                        val layoutInfo = listState.layoutInfo
                                        if (layoutInfo.totalItemsCount > 0) {
                                            val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                            val totalHeight = layoutInfo.totalItemsCount.toFloat() // 简化计算，按项数算高度
                                            val visibleHeight = layoutInfo.visibleItemsInfo.size.toFloat()
                                            
                                            if (visibleHeight < totalHeight) {
                                                val scrollbarHeight = (visibleHeight / totalHeight) * size.height
                                                // reverseLayout = true 时，firstVisibleItemIndex = 0 在最下面
                                                val scrollOffset = (listState.firstVisibleItemIndex.toFloat() / totalHeight) * size.height
                                                
                                                // 滚动条槽
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    topLeft = Offset(size.width - 4.dp.toPx(), 0f),
                                                    size = Size(4.dp.toPx(), size.height),
                                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                                )
                                                
                                                // 滚动条滑块 (考虑到 reverseLayout，计算位置)
                                                val top = size.height - scrollOffset - scrollbarHeight
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.3f),
                                                    topLeft = Offset(size.width - 4.dp.toPx(), top.coerceIn(0f, size.height - scrollbarHeight)),
                                                    size = Size(4.dp.toPx(), scrollbarHeight),
                                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                                )
                                            }
                                        }
                                    },
                                    reverseLayout = true
                                ) {
                                    items(logLines.size) { index ->
                                        val line = logLines[logLines.size - 1 - index]
                                        val color = when {
                                            line.contains("[ERROR]") -> Color(0xFFFF6B6B)
                                            line.contains("[INFO]") -> Color(0xFF4EC9B0)
                                            line.contains("[DEBUG]") -> Color(0xFFCCCCCC)
                                            line.startsWith("---") -> Color(0xFF888888)
                                            else -> Color(0xFFCCCCCC)
                                        }
                                        Text(
                                            text = line,
                                            color = color,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 快速导航悬浮按钮
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(visible = logLines.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            FloatingActionButton(
                                onClick = { coroutineScope.launch { listState.animateScrollToItem(logLines.size - 1) } },
                                modifier = Modifier.size(40.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "回到顶部")
                            }
                        }
                        
                        AnimatedVisibility(visible = !isAtBottom, enter = fadeIn(), exit = fadeOut()) {
                            FloatingActionButton(
                                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                                modifier = Modifier.size(40.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
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

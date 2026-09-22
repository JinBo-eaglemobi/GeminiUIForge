package org.gemini.ui.forge.ui.dialog.ai.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 区域重塑全高度右侧可折叠日志与格式化代码抽屉组件 (RefineLogSidePanel)
 *
 * 遵循 340dp 规范宽度与一文件一 Composable 铁律。
 * 包含执行进度条、全高平滑终端日志、以及重塑产出的格式化 Pretty JSON 预览代码块。
 *
 * @param isExecuting 是否正在执行重塑
 * @param progress 进度比例 (0f ~ 1f)
 * @param statusText 当前即时状态文案
 * @param logs 终端流水日志列表
 * @param previewJson 重塑生成的新图元结构格式化 JSON 代码字符串
 * @param targetBlockId 目标图元 ID (用于代码行高亮)
 * @param onCollapse 点击收起侧边栏回调
 * @param onClearLogs 点击清空日志回调
 * @param onOpenDiffInspector 点击打开左右分栏比对审核弹窗回调
 */
@Composable
fun RefineLogSidePanel(
    isExecuting: Boolean,
    progress: Float,
    statusText: String,
    logs: List<String>,
    previewJson: String?,
    targetBlockId: String?,
    onCollapse: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenDiffInspector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(340.dp)
            .fillMaxHeight()
            .clip(AppShapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), AppShapes.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = AppShapes.medium
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            // 1. 顶部标题控制栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "执行流水与成果 (${logs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.size(24.dp).tip("清空当前日志")
                        ) {
                            Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(24.dp).tip("收起侧边日志面板")
                    ) {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 2. 状态与进度指示
            if (isExecuting || statusText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = statusText.ifBlank { if (isExecuting) "正在分析重构..." else "分析就绪" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                if (isExecuting) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(AppShapes.small)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. 垂直分块：上半部平滑终端日志，下半部格式化 JSON 预览代码块
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val logWeight = if (!previewJson.isNullOrBlank()) 0.45f else 1f
                Box(
                    modifier = Modifier
                        .weight(logWeight)
                        .fillMaxWidth()
                        .clip(AppShapes.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(6.dp)
                ) {
                    if (logs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无日志，等待启动...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(logs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                if (!previewJson.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    FormattedCodeViewer(
                        code = previewJson,
                        highlightKeyword = targetBlockId,
                        title = "生成新结构 JSON (格式化预览)",
                        titleContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(0.55f).fillMaxWidth()
                    )
                }
            }

            // 4. 底部审查主按钮 (有新代码产出时高亮呈现)
            if (!previewJson.isNullOrBlank() && !isExecuting) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenDiffInspector,
                    shape = AppShapes.medium,
                    modifier = Modifier.fillMaxWidth().tip("打开左右对称代码审查窗体，比对原代码与生成的新代码段")
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("审核并应用代码段", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

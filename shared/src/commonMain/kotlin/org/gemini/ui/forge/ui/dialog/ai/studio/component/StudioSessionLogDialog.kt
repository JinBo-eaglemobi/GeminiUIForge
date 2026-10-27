package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.manager.TrafficLogPreferences
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室真实的 API 网络通信日志与原始交互报文查看器（Raw Traffic Archive）
 *
 * 严格遵循通道分离与物理分文件归档原则，从磁盘旁路按需读取 100% 未加工的原始请求/响应报文。
 */
@Composable
fun StudioSessionLogDialog(
    scopeId: String,
    sessionId: String,
    trafficStore: SessionTrafficStore = remember { SessionTrafficStore() },
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val scope = rememberCoroutineScope()
    val copyAction = rememberClipboardAction()

    var records by remember { mutableStateOf<List<TrafficRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 全局一键折叠/展开控制：默认全部折叠，毫秒级首屏渲染
    var isAllExpanded by remember { mutableStateOf(false) }
    var expandToggleVersion by remember { mutableStateOf(0) }

    // 搜索、过滤与排序状态
    var searchQuery by remember { mutableStateOf("") }
    var directionFilter by remember {
        val initialDir = TrafficLogPreferences.getFilterDirection()
        mutableStateOf(if (initialDir == "REQ") TrafficDirection.REQ else if (initialDir == "RESP") TrafficDirection.RESP else null)
    }
    var sortOrder by remember { mutableStateOf(TrafficLogPreferences.getSortOrder()) }

    // 清空档案二次确认弹窗控制
    var showClearConfirm by remember { mutableStateOf(false) }

    // 存储当前选中的完整未折叠 Base64 字符串以供弹窗查看与解码
    var viewingBase64Payload by remember { mutableStateOf<String?>(null) }

    fun refreshRecords() {
        scope.launch {
            isLoading = true
            records = trafficStore.listRecords(scopeId, sessionId)
            isLoading = false
        }
    }

    // 按需从本地磁盘加载该会话的全部通信归档与用户偏好
    LaunchedEffect(scopeId, sessionId) {
        TrafficLogPreferences.loadInternal()
        sortOrder = TrafficLogPreferences.getSortOrder()
        val savedDir = TrafficLogPreferences.getFilterDirection()
        directionFilter = if (savedDir == "REQ") TrafficDirection.REQ else if (savedDir == "RESP") TrafficDirection.RESP else null
        refreshRecords()
    }

    // 统计信息派生
    val statsSummary = remember(records) {
        val total = records.size
        val reqCount = records.count { it.direction == TrafficDirection.REQ }
        val respCount = total - reqCount
        val totalBytes = records.sumOf { it.body.length.toLong() }
        val sizeStr = if (totalBytes >= 1024 * 1024) {
            "${totalBytes / 1024 / 1024} MB"
        } else {
            "${(totalBytes / 1024).coerceAtLeast(1)} KB"
        }
        StatsInfo(total, reqCount, respCount, sizeStr)
    }

    // 过滤与排序派生列表（derivedStateOf 避免不必要重算）
    val displayRecords by remember(records, searchQuery, directionFilter, sortOrder) {
        derivedStateOf {
            val query = searchQuery.trim()
            val filtered = if (query.isEmpty() && directionFilter == null) {
                records
            } else {
                records.filter { r ->
                    val dirOk = directionFilter == null || r.direction == directionFilter
                    val queryOk = query.isEmpty() ||
                            r.model.contains(query, ignoreCase = true) ||
                            r.url.contains(query, ignoreCase = true) ||
                            r.seq.toString() == query
                    dirOk && queryOk
                }
            }
            when (sortOrder) {
                TrafficSortOrder.TIME_ASC -> filtered.sortedBy { it.seq }
                TrafficSortOrder.TIME_DESC -> filtered.sortedByDescending { it.seq }
                TrafficSortOrder.REQ_FIRST -> filtered.sortedWith(
                    compareBy<TrafficRecord> { it.direction != TrafficDirection.REQ }.thenBy { it.seq }
                )
                TrafficSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.body.length }
            }
        }
    }

    AppDraggableDialog(
        onDismissRequest = onDismiss
    ) { draggableState ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ★ 依据 UI 规范响应式设计：占屏幕视口宽度的 88%，范围收拢在 960dp ~ 1400dp
            val dynamicWidth = (maxWidth * 0.88f).coerceIn(spacing.dialogLargeWidth, 1400.dp)

            Surface(
                modifier = Modifier
                    .width(dynamicWidth)
                    .fillMaxHeight(0.92f)
                    .padding(spacing.medium),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.large)
                ) {
                    // 1. 顶部 Header (挂载拖拽抓手)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggableDialogHandle(draggableState)
                            .padding(bottom = spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = AppShapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DataObject,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(spacing.medium))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "会话原始网络通信档案 (Raw Traffic)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(spacing.small))
                                    Surface(
                                        shape = AppShapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                Res.string.traffic_stats_summary,
                                                statsSummary.total,
                                                statsSummary.reqCount,
                                                statsSummary.respCount,
                                                statsSummary.sizeStr
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "100% 原始未脱敏的 HTTP 请求与响应，Base64 仅在展示层折叠，可直接点击解码预览图片",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 右侧操作区：一键折叠/展开 + 刷新 + 关闭
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isAllExpanded = !isAllExpanded
                                    expandToggleVersion++
                                },
                                modifier = Modifier.tip(if (isAllExpanded) "一键折叠全部报文" else "一键展开全部报文")
                            ) {
                                Icon(
                                    imageVector = if (isAllExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (isAllExpanded) "Collapse All" else "Expand All",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(spacing.extraSmall))
                            IconButton(
                                onClick = { refreshRecords() },
                                modifier = Modifier.tip("刷新最新通信报文")
                            ) {
                                if (isLoading && records.isNotEmpty()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(spacing.extraSmall))
                            IconButton(onClick = onDismiss, modifier = Modifier.tip("关闭")) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    // 2. 紧凑工具栏区
                    TrafficListToolbar(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        directionFilter = directionFilter,
                        onDirectionFilterChange = {
                            directionFilter = it
                            TrafficLogPreferences.updatePreferences(sortOrder, it?.name)
                        },
                        sortOrder = sortOrder,
                        onSortOrderChange = {
                            sortOrder = it
                            TrafficLogPreferences.updatePreferences(it, directionFilter?.name)
                        },
                        isEnabled = records.isNotEmpty() && !isLoading,
                        onClearRequested = { showClearConfirm = true },
                        modifier = Modifier.padding(bottom = spacing.small)
                    )

                    HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                    // 3. 主体内容列表区
                    if (isLoading) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    } else if (records.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(spacing.small))
                                Text(
                                    text = "当前会话暂无原始网络通信归档",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (displayRecords.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FilterListOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(spacing.small))
                                Text(
                                    text = "未找到符合过滤条件的通信报文",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            items(displayRecords, key = { "${it.seq}_${it.direction}" }) { record ->
                                TrafficRecordCard(
                                    record = record,
                                    defaultExpanded = isAllExpanded,
                                    expandToggleVersion = expandToggleVersion,
                                    onCopyRaw = { copyAction(record.body, "已复制第 ${record.seq} 条报文原文") },
                                    onInspectPayload = { payload -> viewingBase64Payload = payload }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 清空档案破坏性操作二次确认弹窗（严格遵循全局红线）
    if (showClearConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.traffic_clear_confirm_title),
            message = stringResource(Res.string.traffic_clear_confirm_message, records.size),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    trafficStore.deleteAll(scopeId, sessionId)
                    records = emptyList()
                    showClearConfirm = false
                    Toast.show(
                        message = "已清空当前会话的全部通信档案",
                        type = ToastType.SUCCESS
                    )
                }
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    // 5. 点击折叠 Base64 后弹出的图片解码/长文本查看器
    if (viewingBase64Payload != null) {
        TrafficPayloadViewer(
            payloadText = viewingBase64Payload!!,
            onDismiss = { viewingBase64Payload = null }
        )
    }
}

private data class StatsInfo(
    val total: Int,
    val reqCount: Int,
    val respCount: Int,
    val sizeStr: String
)

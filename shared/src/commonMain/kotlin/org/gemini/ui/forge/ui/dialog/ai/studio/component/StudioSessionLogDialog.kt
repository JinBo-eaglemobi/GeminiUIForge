package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

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

    // 存储当前选中的完整未折叠 Base64 字符串以供弹窗查看与解码
    var viewingBase64Payload by remember { mutableStateOf<String?>(null) }

    fun refreshRecords() {
        scope.launch {
            isLoading = true
            records = trafficStore.listRecords(scopeId, sessionId)
            isLoading = false
        }
    }

    // 按需从本地磁盘加载该会话的全部通信归档
    LaunchedEffect(scopeId, sessionId) {
        refreshRecords()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ★ 依据 UI 规范响应式设计：占屏幕视口宽度的 88%，范围收拢在 960dp ~ 1400dp，杜绝两侧空白浪费与频繁代码折行
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
                    // 1. 顶部 Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
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
                                            text = "共 ${records.size} 条报文",
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

                        // 右侧操作区：刷新 + 关闭
                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))

                // 2. 主体内容列表区
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
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        items(records, key = { "${it.seq}_${it.direction}" }) { record ->
                            TrafficRecordItem(
                                record = record,
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

    // 3. 点击折叠 Base64 后弹出的图片解码/长文本查看器
    if (viewingBase64Payload != null) {
        TrafficPayloadViewer(
            payloadText = viewingBase64Payload!!,
            onDismiss = { viewingBase64Payload = null }
        )
    }
}

private val prettyJsonParser = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
    isLenient = true
}

private fun formatJsonPretty(raw: String): String {
    val trimmed = raw.trim()
    if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
        return raw
    }
    return try {
        val element = prettyJsonParser.parseToJsonElement(trimmed)
        prettyJsonParser.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) {
        raw
    }
}

/**
 * 单条通信档案卡片组件
 */
@Composable
private fun TrafficRecordItem(
    record: TrafficRecord,
    onCopyRaw: () -> Unit,
    onInspectPayload: (String) -> Unit
) {
    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(true) }

    val isReq = record.direction == TrafficDirection.REQ
    val dirColor = if (isReq) Color(0xFF2196F3) else Color(0xFF4CAF50)
    val dirText = if (isReq) "REQUEST" else "RESPONSE"

    // 1. 先进行 JSON 语法树安全格式化（带 2 空格美化缩进）
    val prettyBody = remember(record.body) {
        formatJsonPretty(record.body)
    }

    // 2. 仅在 UI 展示层折叠超长 Base64，提取出其中的真实 Base64 供点击弹层查看
    val base64PayloadMap = remember(prettyBody) {
        val map = mutableMapOf<String, String>()
        val regex = Regex(""""(data|bytesBase64Encoded)"\s*:\s*"([A-Za-z0-9+/=]{128,})"""")
        regex.findAll(prettyBody).forEachIndexed { index, match ->
            val b64 = match.groupValues[2]
            val token = "__BASE64_TOKEN_${index}__"
            map[token] = b64
        }
        map
    }

    // 3. 生成仅用于 UI 呈现的折叠预览文本
    val displayBodyText = remember(prettyBody, base64PayloadMap) {
        if (base64PayloadMap.isEmpty()) {
            prettyBody
        } else {
            var text = prettyBody
            val regex = Regex(""""(data|bytesBase64Encoded)"\s*:\s*"([A-Za-z0-9+/=]{128,})"""")
            text = regex.replace(text) { match ->
                val field = match.groupValues[1]
                val b64 = match.groupValues[2]
                val sizeKb = (b64.length * 3 / 4) / 1024
                """"$field": "<Base64 数据已折叠 (~${sizeKb}KB) —— 点击下方按钮查看/预览图片>""""
            }
            text
        }
    }

    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), AppShapes.medium)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
            // 卡片头部元数据行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // 方向标识胶囊
                    Surface(
                        shape = AppShapes.small,
                        color = dirColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${record.seq.toString().padStart(4, '0')} $dirText",
                            style = MaterialTheme.typography.labelSmall,
                            color = dirColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (record.model.isNotBlank()) {
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = record.model,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (record.url.isNotBlank()) {
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = record.url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复制单条报文完整原文按钮
                    IconButton(
                        onClick = onCopyRaw,
                        modifier = Modifier.size(28.dp).tip("复制本条报文完整原文（含未截断 Base64）")
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp).tip(if (isExpanded) "收起报文" else "展开报文")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 报文展开内容
            if (isExpanded) {
                Spacer(Modifier.height(spacing.small))

                // 若包含折叠的 Base64 图像，提供便捷的直达点击按钮
                if (base64PayloadMap.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        base64PayloadMap.values.forEachIndexed { idx, payload ->
                            val sizeKb = (payload.length * 3 / 4) / 1024
                            ElevatedButton(
                                onClick = { onInspectPayload(payload) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).tip("点击弹出解码大图预览与完整原文复制"),
                                shape = AppShapes.small
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("预览 Base64 图片 #${idx + 1} (~${sizeKb}KB)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 报文等宽文本区：高度完全自适应文案实际高度，由外层统一平滑滚动，杜绝双重嵌套滚动
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(spacing.medium)
                    ) {
                        Text(
                            text = displayBodyText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true
                        )
                    }
                }
            }
        }
    }
}

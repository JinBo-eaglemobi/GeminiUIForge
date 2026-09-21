package org.gemini.ui.forge.ui.dialog.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.service.mcp.*
import org.gemini.ui.forge.ui.component.ToastType
import androidx.compose.ui.platform.testTag
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.mcp.component.CommandDictionaryDialog
import org.gemini.ui.forge.ui.dialog.mcp.component.TrafficRecordItem
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.looseJson
import kotlin.time.Instant

/**
 * 现代左右双分栏架构的 MCP 实时连接节点与通信日志全景调试控制台
 * 左侧栏 (310dp): 已连接/历史客户端会话与在线/已断开状态 (三行立体卡片)
 * 右侧栏 (自适应 970dp): 当前选中客户端的专属 1000 条双向 RPC 报文流水详情 (含指令释义与格式化 JSON)
 */
@Composable
fun McpTrafficInspectorDialog(
    onDismissRequest: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()

    val nodes by McpTrafficInspector.nodes.collectAsState()
    val logs by McpTrafficInspector.logs.collectAsState()
    val activeUrl by McpController.serverUrl.collectAsState()
    val isRunning by McpController.isRunning.collectAsState()

    // 支持多项/全量展开的响应式集合
    var expandedRecordIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var showHelpDictionaryDialog by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedNodeId) {
        if (selectedNodeId == null) {
            logs
        } else {
            logs.filter { it.sessionId == selectedNodeId }
        }
    }

    val isAllExpanded = remember(filteredLogs, expandedRecordIds) {
        filteredLogs.isNotEmpty() && filteredLogs.all { expandedRecordIds.contains(it.id) }
    }

    val selectedNode = remember(nodes, selectedNodeId) {
        nodes.find { it.id == selectedNodeId }
    }

    val connectedActiveCount = remember(nodes) {
        nodes.count { it.isActive }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogHugeWidth) // 1280.dp 全景宽屏标准
                .fillMaxHeight(0.92f)
                .padding(spacing.medium),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.large)
            ) {
                // 1. 顶部 Header (左侧标题行与右侧操作按钮严格同一水平基线对齐，杜绝因多行描述下沉)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.weight(1f).padding(end = spacing.medium)
                    ) {
                        Surface(
                            shape = AppShapes.medium,
                            color = Color(0xFF00ACC1).copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFF00ACC1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(spacing.medium))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(38.dp) // 与左侧图标 38.dp 同高，确保标题基线完全居中
                            ) {
                                Text(
                                    text = "MCP 通信流水与连接节点监控",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(spacing.small))
                                Surface(
                                    shape = AppShapes.small,
                                    color = if (isRunning) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    color = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isRunning) "服务监听中" else "服务未启动",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "实时捕获外部 AI 客户端 (OpenCode, Cursor 等) 的实质性 RPC 双向报文数据 (单连接保留上限 1000 条，已自动异步持久化刷盘)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 右侧工具操作区：高度限制在 38.dp 内，与左侧首行标题基线严格水平对其，关闭按钮位置常驻固定
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(38.dp)
                    ) {
                        // 指令帮助手册入口按钮
                        OutlinedButton(
                            onClick = { showHelpDictionaryDialog = true },
                            shape = AppShapes.small,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .testTag("btn_open_cmd_dict")
                                .height(30.dp).tip("查看 MCP 协议交互指令与工具字典速查")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Help, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("指令说明手册", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(Modifier.width(spacing.small))

                        // 一键全量展开/全量收起双向切换按钮 (遵循 One-Click Global Folding 规范)
                        if (filteredLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    expandedRecordIds = if (isAllExpanded || expandedRecordIds.isNotEmpty()) {
                                        emptySet() // 全量收起
                                    } else {
                                        filteredLogs.map { it.id }.toSet() // 全量展开
                                    }
                                },
                                shape = AppShapes.small,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp).tip(
                                    if (isAllExpanded || expandedRecordIds.isNotEmpty()) "一键收起所有已展开的报文详情" else "一键展开当前全部报文详情"
                                )
                            ) {
                                Icon(
                                    imageVector = if (isAllExpanded || expandedRecordIds.isNotEmpty()) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAllExpanded || expandedRecordIds.isNotEmpty()) "一键收起" else "一键展开",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(Modifier.width(spacing.small))
                        }

                        if (logs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { McpTrafficInspector.clearLogs() },
                                shape = AppShapes.small,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp).tip("清空已捕获的通信记录")
                            ) {
                                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("清空记录", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(spacing.small))
                        }

                        // 常驻关闭按钮，尺寸 32.dp 垂直居中对齐，绝对不受前方按钮动态伸缩影响
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp).tip("关闭窗口")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // 2. 主体左右双分栏架构
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    // ══════════════════════════════════════════════════════════
                    // 【左侧栏 (310dp)】已连接/历史客户端会话与状态
                    // ══════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "客户端连接 (${connectedActiveCount} 在线 / ${nodes.size} 总计)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // "全量客户端汇聚" 汇总项
                        val isAllSelected = selectedNodeId == null
                        Surface(
                            shape = AppShapes.small,
                            color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isAllSelected) 1.5.dp else 1.dp,
                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(AppShapes.small)
                                .clickable { selectedNodeId = null }
                                .tip("查看所有客户端的全部聚合报文")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AllInclusive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "全量客户端汇聚",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Text(
                                    text = "${logs.size} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // 客户端卡片列表
                        if (nodes.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无客户端连入\n请在外部 AI 配置端点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                nodes.forEach { node ->
                                    val isSelected = selectedNodeId == node.id
                                    val nodeLogCount = remember(logs, node.id) {
                                        logs.count { it.sessionId == node.id }
                                    }

                                    Surface(
                                        shape = AppShapes.small,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            !node.isActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                !node.isActive -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.small)
                                            .clickable {
                                                selectedNodeId = if (isSelected) null else node.id
                                            }
                                            .tip(if (isSelected) "正在查看此客户端 (点击取消筛选)" else "点击单独查看此客户端的交互报文")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            // 第 1 行：在线/离线指示灯 + 客户端名称 + 状态徽章/断开按钮
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                color = if (node.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = node.clientName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                if (node.isActive) {
                                                    IconButton(
                                                        onClick = {
                                                            val ok = McpTrafficInspector.disconnectNode(node.id)
                                                            if (ok) {
                                                                Toast.show("已手动断开与 ${node.clientName} 的连接", ToastType.INFO)
                                                            }
                                                        },
                                                        modifier = Modifier.size(20.dp).tip("手动断开此客户端连接")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.LinkOff,
                                                            contentDescription = "Disconnect",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                } else {
                                                    Surface(
                                                        shape = AppShapes.small,
                                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                                    ) {
                                                        Text(
                                                            text = "已断开",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // 第 2 行：真实客户端网络端点 (IP:Port，独占单行，等宽排版，绝不挤压)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Lan,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = node.ip,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // 第 3 行：左右独立的结构化微徽章 (心跳次数 + 流水条数，绝不竖排折行)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = AppShapes.small,
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = "${node.requestCount} 次心跳",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Surface(
                                                    shape = AppShapes.small,
                                                    color = if (nodeLogCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                ) {
                                                    Text(
                                                        text = "$nodeLogCount 条流水",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = if (nodeLogCount > 0) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                        color = if (nodeLogCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 垂直精细分割线
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // ══════════════════════════════════════════════════════════
                    // 【右侧栏 (自适应 970dp)】专属双向报文流水详情
                    // ══════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(spacing.medium)
                    ) {
                        // 顶部状态栏
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedNode != null) {
                                        "报文流水 · ${selectedNode.clientName}"
                                    } else {
                                        "报文流水 · 全量聚合视图"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = AppShapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${filteredLogs.size} 条记录",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "端点: ${activeUrl ?: "未监听"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 流水列表
                        if (filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), AppShapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(spacing.small))
                                    Text(
                                        text = if (selectedNode != null) {
                                            "客户端 [${selectedNode.clientName}] 暂无实质性工具调用记录"
                                        } else {
                                            "等待外部客户端发起实质性工具调用 (create_template, list_templates 等)..."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "高频心跳已在后台静默保活，此处仅呈现真实的业务 RPC 报文",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                items(filteredLogs, key = { it.id }) { record ->
                                    val isExpanded = expandedRecordIds.contains(record.id)
                                    TrafficRecordItem(
                                        record = record,
                                        isExpanded = isExpanded,
                                        onToggle = {
                                            expandedRecordIds = if (isExpanded) {
                                                expandedRecordIds - record.id
                                            } else {
                                                expandedRecordIds + record.id
                                            }
                                        },
                                        onCopy = { copyAction(record.payloadJson, "已复制报文 JSON") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // 指令说明手册独立对话框
    if (showHelpDictionaryDialog) {
        CommandDictionaryDialog(onDismiss = { showHelpDictionaryDialog = false })
    }
}

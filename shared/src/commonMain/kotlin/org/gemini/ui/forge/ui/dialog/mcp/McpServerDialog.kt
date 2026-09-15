package org.gemini.ui.forge.ui.dialog.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.service.mcp.ClientAppConfigStatus
import org.gemini.ui.forge.service.mcp.McpClientConfigManager
import org.gemini.ui.forge.service.mcp.McpClientType
import org.gemini.ui.forge.service.mcp.McpController
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.userHomePath
import org.gemini.ui.forge.utils.Toast
import org.jetbrains.compose.resources.stringResource

/**
 * 现代 Bento 仪表盘 + 全模块可折叠的 MCP 服务中心与客户端配置向导弹窗
 */
@Composable
fun McpServerDialog(
    onDismissRequest: () -> Unit,
    configManager: ConfigManager = remember { ConfigManager() }
) {
    val spacing = LocalAppSpacing.current
    val scope = rememberCoroutineScope()
    val copyAction = rememberClipboardAction()

    val isRunning by McpController.isRunning.collectAsState()
    val activeUrl by McpController.serverUrl.collectAsState()

    var hostInput by remember { mutableStateOf("127.0.0.1") }
    var portInput by remember { mutableStateOf(18330) }
    var followNav by remember { mutableStateOf(false) }

    // 三大核心功能块独立折叠状态
    var isServerConsoleExpanded by remember { mutableStateOf(true) }
    var isWizardExpanded by remember { mutableStateOf(true) }
    var isJsonSnippetExpanded by remember { mutableStateOf(false) }

    val isAllCollapsed = !isServerConsoleExpanded && !isWizardExpanded && !isJsonSnippetExpanded
    fun toggleAllCollapse() {
        val target = isAllCollapsed
        isServerConsoleExpanded = target
        isWizardExpanded = target
        isJsonSnippetExpanded = target
    }

    // 客户端探测状态列表
    var clientsStatus by remember { mutableStateOf<List<ClientAppConfigStatus>>(emptyList()) }

    val currentUrl = activeUrl ?: "http://$hostInput:$portInput/mcp"

    fun refreshClients() {
        clientsStatus = McpClientConfigManager.getSupportedClientsStatus(currentUrl)
    }

    fun toggleMcpServer() {
        if (isRunning) {
            McpController.stop()
            scope.launch { configManager.saveKey("MCP_ENABLED", "false") }
        } else {
            McpController.start(host = hostInput, port = portInput)
            scope.launch {
                configManager.saveKey("MCP_ENABLED", "true")
                configManager.saveKey("MCP_HOST", hostInput)
                configManager.saveKey("MCP_PORT", portInput.toString())
            }
        }
        refreshClients()
    }

    // 初始化加载
    LaunchedEffect(Unit) {
        val savedHost = configManager.loadKey("MCP_HOST")
        if (!savedHost.isNullOrBlank()) hostInput = savedHost
        val savedPort = configManager.loadKey("MCP_PORT")?.toIntOrNull()
        if (savedPort != null) portInput = savedPort
        val savedFollowNav = configManager.loadKey("MCP_FOLLOW_NAV") == "true"
        followNav = savedFollowNav
        refreshClients()
    }

    val copiedTip = stringResource(Res.string.mcp_config_copied)

    val clientConfigJson = remember(currentUrl) {
        """
        {
          "mcpServers": {
            "gemini-ui-forge": {
              "url": "$currentUrl",
              "disabled": true
            }
          }
        }
        """.trimIndent()
    }

    val connectedClientsCount = remember(clientsStatus) {
        clientsStatus.count { it.isConfigured }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogLargeWidth)
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
                // 1. 顶部 Header 品牌区
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = AppShapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(spacing.medium))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MCP 服务与集成中心",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(spacing.small))
                                Surface(
                                    shape = AppShapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "Streamable HTTP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "开放核心功能给外部 AI 客户端直接调用，实现工程资产自主操纵",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { toggleAllCollapse() },
                            modifier = Modifier.size(32.dp).tip(if (isAllCollapsed) "一键展开全部模块" else "一键折叠全部模块")
                        ) {
                            Icon(
                                imageVector = if (isAllCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = "Toggle All Fold",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(spacing.small))

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp).tip("关闭")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // 2. 主体可滚动区（包含 3 大独立可折叠功能块）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // ══════════════════════════════════════════════════════════
                    // 功能块 1：核心服务主控台 (可折叠)
                    // ══════════════════════════════════════════════════════════
                    CollapsibleSectionCard(
                        icon = Icons.Default.Dns,
                        title = "MCP 核心服务主控台",
                        isExpanded = isServerConsoleExpanded,
                        onToggle = { isServerConsoleExpanded = !isServerConsoleExpanded },
                        badge = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isRunning) "运行中 :$portInput" else "未启动",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        headerExtra = {
                            // 折叠状态下的快速启停小胶囊按钮
                            if (!isServerConsoleExpanded) {
                                Button(
                                    onClick = { toggleMcpServer() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = AppShapes.small,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (isRunning) "停止" else "启动",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            // 运行状态横幅
                            Card(
                                shape = AppShapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isRunning) {
                                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(spacing.medium).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(spacing.small))
                                        Column {
                                            Text(
                                                text = if (isRunning) "服务正常运行中" else "服务未启动",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = currentUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { copyAction(currentUrl, "已复制服务地址") },
                                            modifier = Modifier.size(32.dp).tip("复制服务 URL")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                                        }

                                        Spacer(Modifier.width(spacing.small))

                                        Button(
                                            onClick = { toggleMcpServer() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            ),
                                            shape = AppShapes.small,
                                            modifier = Modifier.tip(if (isRunning) "停止本地 MCP 服务" else "启动本地 MCP 服务")
                                        ) {
                                            Icon(
                                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isRunning) "停止服务" else "启动服务")
                                        }
                                    }
                                }
                            }

                            // 基础参数设置行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    NumberOutlinedTextField(
                                        value = portInput.toString(),
                                        onValueChange = { newStr ->
                                            val newPort = newStr.toIntOrNull() ?: portInput
                                            portInput = newPort
                                            scope.launch { configManager.saveKey("MCP_PORT", newPort.toString()) }
                                        },
                                        label = { Text("服务端口") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(modifier = Modifier.weight(1.5f)) {
                                    OutlinedTextField(
                                        value = hostInput,
                                        onValueChange = { newHost ->
                                            hostInput = newHost
                                            scope.launch { configManager.saveKey("MCP_HOST", newHost) }
                                        },
                                        label = { Text("绑定地址") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // 界面执行跟随开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AI 工具执行界面跟随",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "开启后工具执行完成将自动跳转至对应工作区页面（默认关闭，保持纯后台静默运行）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = followNav,
                                    onCheckedChange = { isChecked ->
                                        followNav = isChecked
                                        scope.launch { configManager.saveKey("MCP_FOLLOW_NAV", isChecked.toString()) }
                                    }
                                )
                            }
                        }
                    }

                    // ══════════════════════════════════════════════════════════
                    // 功能块 2：主流 AI 客户端一键配置向导 (可折叠)
                    // ══════════════════════════════════════════════════════════
                    CollapsibleSectionCard(
                        icon = Icons.Default.AutoFixHigh,
                        title = "主流 AI 客户端一键配置向导",
                        isExpanded = isWizardExpanded,
                        onToggle = { isWizardExpanded = !isWizardExpanded },
                        badge = {
                            Surface(
                                shape = AppShapes.small,
                                color = if (connectedClientsCount > 0) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "已接入 $connectedClientsCount / ${clientsStatus.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (connectedClientsCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        },
                        headerExtra = {
                            IconButton(
                                onClick = { refreshClients() },
                                modifier = Modifier.size(32.dp).tip("重新扫描外部客户端配置状态")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Text(
                                text = "自动感知本地配置文件，一键开关安全注入本工具服务（严格仅操作 gemini-ui-forge 单一服务项，其余配置与注释零破坏）：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = spacing.extraSmall)
                            )

                            // 2 列 Bento 网格排布
                            val rows = clientsStatus.chunked(2)
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                ) {
                                    rowItems.forEach { status ->
                                        ClientBentoCard(
                                            status = status,
                                            onToggle = { isChecked ->
                                                val ok = McpClientConfigManager.toggleClientConfig(
                                                    clientType = status.clientType,
                                                    enable = isChecked,
                                                    serverUrl = currentUrl
                                                )
                                                if (ok) {
                                                    Toast.show(
                                                        if (isChecked) "已将 MCP 服务注入 ${status.name}" else "已从 ${status.name} 中安全移除",
                                                        ToastType.SUCCESS
                                                    )
                                                    refreshClients()
                                                } else {
                                                    Toast.show("更新 ${status.name} 配置文件失败", ToastType.ERROR)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // 奇数项时补位占位
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // ══════════════════════════════════════════════════════════
                    // 功能块 3：手动高级配置代码 (可折叠，默认收起)
                    // ══════════════════════════════════════════════════════════
                    CollapsibleSectionCard(
                        icon = Icons.Default.Code,
                        title = "手动高级配置代码 (mcpServers JSON)",
                        isExpanded = isJsonSnippetExpanded,
                        onToggle = { isJsonSnippetExpanded = !isJsonSnippetExpanded },
                        badge = {
                            Text(
                                text = if (isJsonSnippetExpanded) "点击收起" else "备用查看",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.small)) {
                            Text(
                                text = "若您使用的是其他支持 MCP 协议的客户端，可手动复制以下 JSON 代码片段至其配置文件中：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = spacing.small)
                            )

                            Card(
                                shape = AppShapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.medium)
                            ) {
                                Column(modifier = Modifier.padding(spacing.medium)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "JSON Snippet",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedButton(
                                            onClick = { copyAction(clientConfigJson, copiedTip) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp).tip("点击复制配置代码")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("复制配置代码", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(Modifier.height(spacing.small))
                                    SelectionContainer {
                                        Text(
                                            text = clientConfigJson,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代通用可折叠卡片组件 (带旋转动效与折叠摘要)
 */
@Composable
private fun CollapsibleSectionCard(
    icon: ImageVector,
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    badge: @Composable (() -> Unit)? = null,
    headerExtra: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val arrowRotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            // 头部栏（整行点击切换展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .clickable { onToggle() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(spacing.small))
                        badge()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (headerExtra != null) {
                        headerExtra()
                        Spacer(Modifier.width(spacing.small))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(28.dp).tip(if (isExpanded) "收起" else "展开")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }
                }
            }

            // 折叠动效区
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

/**
 * 客户端生态 2-Column Bento 单卡
 */
@Composable
private fun ClientBentoCard(
    status: ClientAppConfigStatus,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    // 图标与品牌配色映射
    val clientIcon = when (status.clientType) {
        McpClientType.OPEN_CODE -> Icons.Default.Terminal
        McpClientType.CLAUDE_CODE -> Icons.Default.SmartToy
        McpClientType.CLAUDE_DESKTOP -> Icons.Default.Computer
        McpClientType.GEMINI_CLI -> Icons.Default.AutoAwesome
        McpClientType.CURSOR -> Icons.Default.NearMe
    }

    val brandTint = when (status.clientType) {
        McpClientType.OPEN_CODE -> Color(0xFF00C853)
        McpClientType.CLAUDE_CODE, McpClientType.CLAUDE_DESKTOP -> Color(0xFFFF9800)
        McpClientType.GEMINI_CLI -> Color(0xFF2979FF)
        McpClientType.CURSOR -> Color(0xFF9C27B0)
    }

    // 将长绝对路径智能缩写为 ~ 开头友好形态展示
    val simplifiedPath = remember(status.configPath) {
        if (userHomePath.isNotBlank() && status.configPath.startsWith(userHomePath)) {
            "~" + status.configPath.substring(userHomePath.length)
        } else {
            status.configPath
        }
    }

    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (status.isConfigured) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        modifier = modifier.border(
            width = 1.dp,
            color = if (status.isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            shape = AppShapes.medium
        )
    ) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = brandTint.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = clientIcon,
                                contentDescription = null,
                                tint = brandTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = status.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 独立开关
                Switch(
                    checked = status.isConfigured,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(Modifier.height(spacing.small))

            // 状态胶囊与路径
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = when {
                        status.isConfigured && status.isServiceDisabled -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        status.isConfigured -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        status.isFileExists -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    },
                    shape = AppShapes.small
                ) {
                    Text(
                        text = when {
                            status.isConfigured && status.isServiceDisabled -> "已配置 (默认禁用)"
                            status.isConfigured -> "已接入并启用"
                            status.isFileExists -> "未配置"
                            else -> "未检测到配置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            status.isConfigured && status.isServiceDisabled -> Color(0xFFE65100)
                            status.isConfigured -> Color(0xFF2E7D32)
                            status.isFileExists -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(spacing.small))

                Text(
                    text = simplifiedPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).tip(status.configPath)
                )
            }
        }
    }
}

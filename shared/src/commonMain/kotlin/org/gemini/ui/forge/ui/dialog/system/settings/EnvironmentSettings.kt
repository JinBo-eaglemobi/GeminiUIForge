package org.gemini.ui.forge.ui.dialog.system.settings


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.selection.SelectionContainer
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import org.gemini.ui.forge.ui.dialog.system.settings.component.EnvironmentItemLogPanel


/**
 * 环境依赖与 Python 包管理设置区块
 *
 * 分为三个选项卡：核心依赖、本地扩展管理和云端探索市场。
 * 支持检测系统环境、管理本地 Pip 包以及搜索和安装 PyPI 上的扩展包。
 *
 * @param envViewModel 环境依赖与包管理的视图模型，集中负责环境检测和 Pip 包生态操作
 */
@Composable
fun EnvironmentSettings(
    envViewModel: org.gemini.ui.forge.viewmodel.AppEnvViewModel
) {
    val status by envViewModel.status.collectAsState()
    val pipPackages by envViewModel.pipPackages.collectAsState()
    val isPipLoading by envViewModel.isPipLoading.collectAsState()
    val pipLogs by envViewModel.pipLogs.collectAsState()
    val isPipActionInProgress by envViewModel.isPipActionInProgress.collectAsState()
    val searchResult by envViewModel.searchResult.collectAsState()
    val isSearching by envViewModel.isSearching.collectAsState()
    val topMarketPackages by envViewModel.topMarketPackages.collectAsState()
    val isMarketLoading by envViewModel.isMarketLoading.collectAsState()
    val marketPage by envViewModel.marketPage.collectAsState()

    var envTab by remember { mutableStateOf(0) }
    // 记录各依赖项下方命令输出窗口的折叠状态（仅针对存在命令输出的项生效）
    val collapsedLogItems = remember { mutableStateMapOf<String, Boolean>() }
    val spacing = LocalAppSpacing.current

    // Segmented Button 风格的 Tab
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.medium)
            .padding(LocalAppSpacing.current.extraSmall),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("核心依赖", "本地扩展管理", "云端探索市场").forEachIndexed { index, title ->
            val isSelected = envTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable {
                        envTab = index
                        if (index == 2 && topMarketPackages.isEmpty()) envViewModel.loadMarketPage(0)
                    }
                    .padding(vertical = LocalAppSpacing.current.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(LocalAppSpacing.current.medium))

    if (envTab == 0) {
        val itemsWithOutput = status.items.filter {
            it.isInstalling || it.installLogs.isNotEmpty() || it.lastError != null
        }
        val allOutputCollapsed = itemsWithOutput.isNotEmpty() && itemsWithOutput.all {
            collapsedLogItems[it.name] == true
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle(stringResource(Res.string.env_python_check_title))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (itemsWithOutput.size >= 2) {
                    AppIconButton(
                        imageVector = if (allOutputCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                        tooltip = stringResource(
                            if (allOutputCollapsed) Res.string.env_log_expand_all_tip else Res.string.env_log_collapse_all_tip
                        ),
                        tier = AppIconButtonTier.COMPACT,
                        onClick = {
                            val targetCollapsed = !allOutputCollapsed
                            itemsWithOutput.forEach { item ->
                                collapsedLogItems[item.name] = targetCollapsed
                            }
                        }
                    )
                    Spacer(Modifier.width(spacing.extraSmall))
                }
                TextButton(onClick = { envViewModel.checkEnvironment() }, enabled = !status.isChecking) {
                    if (status.isChecking) CircularProgressIndicator(
                        Modifier.size(LocalAppSpacing.current.medium),
                        strokeWidth = 2.dp
                    )
                    else Icon(Icons.Default.Refresh, null, Modifier.size(LocalAppSpacing.current.medium))
                    Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                    Text(stringResource(Res.string.env_action_check))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
            status.items.forEach { item ->
                val hasOutputPanel = item.isInstalling || item.installLogs.isNotEmpty() || item.lastError != null
                val isLogCollapsed = collapsedLogItems[item.name] == true
                val isUninstallFlow = item.isUninstallAction || item.installLogs.firstOrNull()?.startsWith("开始卸载") == true

                // 当触发新一轮安装/卸载时自动展开；当命令输出被清空（如卸载完成）时自动移除折叠状态
                LaunchedEffect(hasOutputPanel, item.isInstalling) {
                    if (!hasOutputPanel) {
                        collapsedLogItems.remove(item.name)
                    } else if (item.isInstalling) {
                        collapsedLogItems[item.name] = false
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = AppShapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.3f
                        )
                    )
                ) {
                    val headerRowModifier = if (hasOutputPanel) {
                        Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.small)
                            .tip(
                                stringResource(
                                    if (isLogCollapsed) Res.string.env_log_expand_tip else Res.string.env_log_collapse_tip
                                )
                            )
                            .clickable { collapsedLogItems[item.name] = !isLogCollapsed }
                            .padding(12.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    }

                    Row(modifier = headerRowModifier, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (item.isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(LocalAppSpacing.current.large)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(item.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.isOutdated) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = AppShapes.small) {
                                        Text(
                                            "有更新",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = LocalAppSpacing.current.extraSmall,
                                                vertical = 2.dp
                                            ),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                // 仅当存在命令输出窗口时，在标题侧展示折叠箭头与折叠态状态标识；未执行任何命令输出的项无折叠标识
                                if (hasOutputPanel) {
                                    Spacer(Modifier.width(spacing.small))
                                    Icon(
                                        imageVector = if (isLogCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isLogCollapsed) {
                                        Spacer(Modifier.width(spacing.extraSmall))
                                        val statusText = when {
                                            item.isInstalling && isUninstallFlow -> stringResource(Res.string.env_status_uninstalling)
                                            item.isInstalling -> stringResource(Res.string.env_status_installing)
                                            item.isSuccess == true -> stringResource(Res.string.env_status_install_success)
                                            item.isSuccess == false && isUninstallFlow -> stringResource(Res.string.env_status_uninstall_failed)
                                            item.isSuccess == false -> stringResource(Res.string.env_status_install_failed)
                                            else -> stringResource(Res.string.env_log_title)
                                        }
                                        val statusColor = when {
                                            item.isInstalling -> MaterialTheme.colorScheme.primary
                                            item.isSuccess == true -> Color(0xFF4CAF50)
                                            item.isSuccess == false -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = "($statusText)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                            val verStr =
                                if (item.isInstalled) "${stringResource(Res.string.env_status_installed)}: ${item.version ?: "Unknown"}" else stringResource(
                                    Res.string.env_status_missing
                                )
                            Text(
                                text = verStr + if (item.isOutdated) " ➜ ${item.latestVersion}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isInstalled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }

                        if (item.isOutdated && !item.isInstalling) {
                            Button(
                                onClick = { envViewModel.installEnvironmentItem(item.name) },
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(
                                    horizontal = spacing.buttonActionPaddingHorizontal,
                                    vertical = spacing.buttonActionPaddingVertical
                                ),
                                modifier = Modifier
                                    .padding(end = spacing.small)
                                    .height(spacing.buttonActionHeight)
                                    .defaultMinSize(minWidth = spacing.buttonActionMinWidth)
                            ) {
                                Text(
                                    "更新",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (!item.isInstalled) {
                            Button(
                                onClick = { envViewModel.installEnvironmentItem(item.name) },
                                enabled = !item.isInstalling,
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(
                                    horizontal = spacing.buttonActionPaddingHorizontal,
                                    vertical = spacing.buttonActionPaddingVertical
                                ),
                                modifier = Modifier
                                    .height(spacing.buttonActionHeight)
                                    .defaultMinSize(minWidth = spacing.buttonActionMinWidth)
                            ) {
                                if (item.isInstalling) CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                else Text(
                                    stringResource(Res.string.env_action_install),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { envViewModel.uninstallEnvironmentItem(item.name) },
                                enabled = !item.isInstalling,
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(
                                    horizontal = spacing.buttonActionPaddingHorizontal,
                                    vertical = spacing.buttonActionPaddingVertical
                                ),
                                modifier = Modifier
                                    .height(spacing.buttonActionHeight)
                                    .defaultMinSize(minWidth = spacing.buttonActionMinWidth),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (item.isInstalling) CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    strokeWidth = 2.dp
                                )
                                else Text(
                                    stringResource(Res.string.env_action_uninstall),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 只要处于正在安装、或有历史日志、或有失败错误记录，均持久化展示日志面板供用户查阅与复制；支持点击上方依赖项行平滑折叠/展开
                    AnimatedVisibility(visible = hasOutputPanel && !isLogCollapsed) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                            EnvironmentItemLogPanel(
                                item = item,
                                onClearLogs = {
                                    collapsedLogItems.remove(item.name)
                                    envViewModel.clearEnvironmentLogs(item.name)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (status.items.any { it.name == "python" && !it.isInstalled }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = AppShapes.medium
            ) {
                Column(Modifier.padding(LocalAppSpacing.current.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(LocalAppSpacing.current.small))
                        Text(
                            stringResource(Res.string.env_error_missing_python),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { envViewModel.installEnvironmentItem("python") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(LocalAppSpacing.current.small))
                        Text(stringResource(Res.string.env_action_install))
                    }
                }
            }
        }
    } else if (envTab == 1) {
        // Pip Package Manager Tab - 纯本地管理
        var selectedPackages by remember { mutableStateOf(setOf<String>()) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle("Python 本地包管理")
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                if (selectedPackages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            envViewModel.batchUninstallPipPackages(selectedPackages.toList())
                            selectedPackages = emptySet()
                        },
                        enabled = !isPipActionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("卸载已选 (${selectedPackages.size})", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            envViewModel.batchInstallPipPackages(selectedPackages.toList())
                            selectedPackages = emptySet()
                        },
                        enabled = !isPipActionInProgress
                    ) {
                        Text("更新已选 (${selectedPackages.size})", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { envViewModel.checkEnvironment() }, enabled = !isPipLoading && !isPipActionInProgress) {
                    if (isPipLoading) CircularProgressIndicator(
                        Modifier.size(LocalAppSpacing.current.medium),
                        strokeWidth = 2.dp
                    )
                    else Icon(Icons.Default.Refresh, null, Modifier.size(LocalAppSpacing.current.medium))
                    Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                    Text("刷新")
                }
            }
        }

        if (isPipActionInProgress && pipLogs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraSmall)
                    .heightIn(min = spacing.dialogListMaxHeight * 0.6f, max = spacing.dialogListMaxHeight * 1.4f)
                    .background(Color.Black, AppShapes.small)
                    .padding(spacing.small)
            ) {
                val logScroll = rememberScrollState()
                Text(
                    text = pipLogs.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FF00),
                    modifier = Modifier.verticalScroll(logScroll)
                )
                LaunchedEffect(pipLogs.size) { logScroll.animateScrollTo(logScroll.maxValue) }
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
        }

        if (pipPackages.isEmpty() && !isPipLoading) {
            Text("没有检测到 Python 环境或没有安装包。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val installed = pipPackages.filter { it.isInstalled }

            if (installed.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "📦 已安装的包 (${installed.size})",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = LocalAppSpacing.current.extraSmall).weight(1f)
                    )
                    TextButton(onClick = {
                        selectedPackages = if (selectedPackages.containsAll(installed.map { it.name })) emptySet()
                        else selectedPackages + installed.map { it.name }
                    }) {
                        Text(
                            if (selectedPackages.containsAll(installed.map { it.name })) "全不选" else "全选",
                            fontSize = 12.sp
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.extraSmall)) {
                    installed.forEach { pkg ->
                        val isSelected = selectedPackages.contains(pkg.name)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                selectedPackages =
                                    if (isSelected) selectedPackages - pkg.name else selectedPackages + pkg.name
                            },
                            shape = AppShapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.3f
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = LocalAppSpacing.current.small,
                                    vertical = 6.dp
                                ), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    selectedPackages =
                                        if (checked) selectedPackages + pkg.name else selectedPackages - pkg.name
                                })
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SelectionContainer {
                                            Text(pkg.name, fontWeight = FontWeight.Medium)
                                        }
                                        if (pkg.isOutdated) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = AppShapes.small
                                            ) {
                                                Text(
                                                    "有更新",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(
                                                        horizontal = LocalAppSpacing.current.extraSmall,
                                                        vertical = 2.dp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "版本: ${pkg.version}${if (pkg.isOutdated) " ➜ ${pkg.latestVersion}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { envViewModel.openPackageHome(pkg.name) },
                                    modifier = Modifier.size(LocalAppSpacing.current.extraLarge)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "详情",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (pkg.isOutdated) {
                                    Spacer(Modifier.width(spacing.extraSmall))
                                    Button(
                                        onClick = { envViewModel.batchInstallPipPackages(listOf(pkg.name)) },
                                        enabled = !isPipActionInProgress,
                                        shape = AppShapes.medium,
                                        contentPadding = PaddingValues(
                                            horizontal = spacing.buttonActionPaddingHorizontal,
                                            vertical = spacing.buttonActionPaddingVertical
                                        ),
                                        modifier = Modifier
                                            .height(spacing.buttonActionHeight)
                                            .defaultMinSize(minWidth = spacing.buttonActionMinWidth)
                                    ) { Text("更新", fontSize = 12.sp, maxLines = 1, softWrap = false) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Tab 2: 云端探索市场 - 网格视图
        var searchQuery by remember { mutableStateOf("") }
        var selectedPackages by remember { mutableStateOf(setOf<String>()) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle("探索 PyPI 扩展市场")
            if (selectedPackages.isNotEmpty()) {
                Button(
                    onClick = {
                        envViewModel.batchInstallPipPackages(selectedPackages.toList())
                        selectedPackages = emptySet()
                    },
                    enabled = !isPipActionInProgress
                ) {
                    Text("安装已选 (${selectedPackages.size})", fontSize = 12.sp)
                }
            }
        }

        // 搜索框
        SelectAllOutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索 PyPI 全网扩展包...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(bottom = LocalAppSpacing.current.small),
            singleLine = true,
            shape = AppShapes.medium,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; envViewModel.clearSearchResult() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = { envViewModel.searchPipPackage(searchQuery) }) {
                        if (isSearching) CircularProgressIndicator(
                            Modifier.size(LocalAppSpacing.current.medium),
                            strokeWidth = 2.dp
                        )
                        else Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                    }
                }
            }
        )

        val result = searchResult
        if (result != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = LocalAppSpacing.current.extraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔍 搜索结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { envViewModel.clearSearchResult() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Search", modifier = Modifier.size(16.dp))
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .padding(bottom = LocalAppSpacing.current.small),
                shape = AppShapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SelectionContainer {
                            Text(result.name, fontWeight = FontWeight.Bold)
                        }
                        Text(result.description, style = MaterialTheme.typography.labelSmall)
                        if (!result.latestVersion.isNullOrEmpty()) {
                            Text(
                                "最新版本: ${result.latestVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { envViewModel.openPackageHome(result.name) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Info, "详情", tint = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { envViewModel.batchInstallPipPackages(listOf(result.name)) },
                        enabled = !isPipActionInProgress,
                        shape = AppShapes.medium,
                        contentPadding = PaddingValues(
                            horizontal = spacing.buttonActionPaddingHorizontal,
                            vertical = spacing.buttonActionPaddingVertical
                        ),
                        modifier = Modifier
                            .height(spacing.buttonActionHeight)
                            .defaultMinSize(minWidth = spacing.buttonActionMinWidth)
                    ) { Text("安装", maxLines = 1, softWrap = false) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = LocalAppSpacing.current.small))
        }

        if (isPipActionInProgress && pipLogs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraSmall)
                    .heightIn(min = spacing.dialogListMaxHeight * 0.6f, max = spacing.dialogListMaxHeight * 1.4f)
                    .background(Color.Black, AppShapes.small)
                    .padding(spacing.small)
            ) {
                val logScroll = rememberScrollState()
                Text(
                    text = pipLogs.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FF00),
                    modifier = Modifier.verticalScroll(logScroll)
                )
                LaunchedEffect(pipLogs.size) { logScroll.animateScrollTo(logScroll.maxValue) }
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(
                "🔥 热门排行榜",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marketPage > 0) {
                    TextButton(
                        onClick = { envViewModel.loadMarketPage(marketPage - 1) },
                        enabled = !isMarketLoading
                    ) { Text("上一页", fontSize = 12.sp) }
                }
                Text("第 ${marketPage + 1} 页", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { envViewModel.loadMarketPage(marketPage + 1) }, enabled = !isMarketLoading) {
                    Text(
                        "下一页",
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (isMarketLoading) {
            Box(
                Modifier.fillMaxWidth().padding(LocalAppSpacing.current.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small),
                horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .heightIn(min = 300.dp, max = 600.dp) // 使用定高避免 Scroll 嵌套 Crash
            ) {
                items(topMarketPackages) { pkg ->
                    val isSelected = selectedPackages.contains(pkg.name)
                    val isAlreadyInstalled =
                        pipPackages.any { it.name.equals(pkg.name, ignoreCase = true) && it.isInstalled }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            if (!isAlreadyInstalled) selectedPackages =
                                if (isSelected) selectedPackages - pkg.name else selectedPackages + pkg.name
                        },
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.3f
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAlreadyInstalled) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp).padding(4.dp)
                                    )
                                } else {
                                    Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                        selectedPackages =
                                            if (checked) selectedPackages + pkg.name else selectedPackages - pkg.name
                                    }, modifier = Modifier.padding(end = 4.dp))
                                }
                                Spacer(Modifier.width(LocalAppSpacing.current.small))
                                SelectionContainer(Modifier.weight(1f)) {
                                    Text(
                                        pkg.name,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { envViewModel.openPackageHome(pkg.name) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "详情",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(LocalAppSpacing.current.extraSmall))
                            Text(
                                pkg.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.heightIn(min = LocalAppSpacing.current.extraLarge)
                            )
                            Spacer(Modifier.height(LocalAppSpacing.current.small))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (isAlreadyInstalled) {
                                    Text(
                                        "已安装",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                } else {
                                    Button(
                                        onClick = { envViewModel.batchInstallPipPackages(listOf(pkg.name)) },
                                        enabled = !isPipActionInProgress,
                                        shape = AppShapes.medium,
                                        contentPadding = PaddingValues(
                                            horizontal = spacing.buttonActionPaddingHorizontal,
                                            vertical = spacing.buttonActionPaddingVertical
                                        ),
                                        modifier = Modifier
                                            .height(spacing.buttonActionHeight)
                                            .defaultMinSize(minWidth = spacing.buttonActionMinWidth)
                                    ) {
                                        Text("安装", fontSize = 12.sp, maxLines = 1, softWrap = false)
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
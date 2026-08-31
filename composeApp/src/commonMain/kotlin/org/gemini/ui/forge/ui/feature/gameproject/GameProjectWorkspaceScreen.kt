package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_debug_logs_section
import geminiuiforge.composeapp.generated.resources.gp_debug_no_selection
import geminiuiforge.composeapp.generated.resources.gp_debug_no_tree
import geminiuiforge.composeapp.generated.resources.gp_debug_props_section
import geminiuiforge.composeapp.generated.resources.gp_debug_toggle
import geminiuiforge.composeapp.generated.resources.gp_debug_toggle_hint
import geminiuiforge.composeapp.generated.resources.gp_debug_tree_section
import geminiuiforge.composeapp.generated.resources.gp_preview_no_html
import geminiuiforge.composeapp.generated.resources.gp_tree_empty
import geminiuiforge.composeapp.generated.resources.gp_ws_exit_fullscreen
import geminiuiforge.composeapp.generated.resources.gp_ws_fullscreen
import geminiuiforge.composeapp.generated.resources.gp_ws_language
import geminiuiforge.composeapp.generated.resources.gp_ws_left
import geminiuiforge.composeapp.generated.resources.gp_ws_no_project
import geminiuiforge.composeapp.generated.resources.gp_ws_open_dir
import geminiuiforge.composeapp.generated.resources.gp_ws_pm
import geminiuiforge.composeapp.generated.resources.gp_ws_refresh
import geminiuiforge.composeapp.generated.resources.gp_ws_right
import geminiuiforge.composeapp.generated.resources.gp_ws_selected_game
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.gameproject.DebugTreeNode
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.model.gameproject.GameProjectSession
import org.gemini.ui.forge.model.gameproject.ResourceTreeNode
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.feature.gameproject.preview.GameHtmlPreview
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.rememberLazyListState
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import androidx.compose.foundation.text.selection.SelectionContainer
import geminiuiforge.composeapp.generated.resources.gp_ws_console
import geminiuiforge.composeapp.generated.resources.gp_ws_console_toggle
import geminiuiforge.composeapp.generated.resources.gp_ws_console_clear
import org.jetbrains.compose.resources.stringResource

/**
 * 游戏项目专属工作台界面。
 * 布局：顶部当前游戏信息条；左侧项目资源树（可拖拽调宽/折叠）；
 * 中间渲染 build 目录 HTML（多页面切换 + 全屏预览）；
 * 右侧属性 / 调试信息面板（阶段 3 接入 debugtool 数据，当前为占位）。
 *
 * @param viewModel 游戏项目管理 ViewModel
 */
@Composable
fun GameProjectWorkspaceScreen(viewModel: GameProjectViewModel) {
    val project = GameProjectSession.currentProject
    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.gp_ws_no_project),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 进入工作台时确保资源树与 HTML 列表已加载（幂等）
    LaunchedEffect(project.id) {
        viewModel.loadWorkspace(project)
    }

    // 面板宽度与折叠/全屏状态（会话级）
    var leftWidth by remember { mutableStateOf(230.dp) }
    var rightWidth by remember { mutableStateOf(280.dp) }
    var leftCollapsed by remember { mutableStateOf(false) }
    var rightCollapsed by remember { mutableStateOf(false) }
    var fullscreenPreview by remember { mutableStateOf(false) }
    var consoleCollapsed by remember { mutableStateOf(true) }
    var consoleHeight by remember { mutableStateOf(160.dp) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：当前游戏信息条
        TopInfoBar(project)

        // 主体三栏：资源树 | HTML 预览 | 属性面板
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 左侧：资源树（全屏预览时整体隐藏）
            if (!fullscreenPreview) {
                if (leftCollapsed) {
                    CollapsedRail(onExpand = { leftCollapsed = false }, expandIcon = { Icons.Default.ChevronRight })
                } else {
                    ResourceTreePanel(
                        viewModel = viewModel,
                        modifier = Modifier.width(leftWidth).fillMaxHeight(),
                        onCollapse = { leftCollapsed = true }
                    )
                    VerticalSplitter(onDrag = { delta ->
                        leftWidth = (leftWidth + with(density) { delta.toDp() }).coerceIn(160.dp, 520.dp)
                    })
                }
            }

            // 中间：HTML 页签 + WebView 预览
            CenterPreviewPanel(
                viewModel = viewModel,
                fullscreen = fullscreenPreview,
                onToggleFullscreen = { fullscreenPreview = !fullscreenPreview },
                consoleCollapsed = consoleCollapsed,
                onToggleConsole = { consoleCollapsed = !consoleCollapsed },
                consoleHeight = consoleHeight,
                onConsoleHeightChange = { consoleHeight = it },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // 右侧：属性 / 调试面板（全屏预览时整体隐藏）
            if (!fullscreenPreview) {
                if (rightCollapsed) {
                    CollapsedRail(onExpand = { rightCollapsed = false }, expandIcon = { Icons.Default.ChevronLeft })
                } else {
                    VerticalSplitter(onDrag = { delta ->
                        rightWidth = (rightWidth - with(density) { delta.toDp() }).coerceIn(180.dp, 560.dp)
                    })
                    PropertiesPanel(
                        viewModel = viewModel,
                        modifier = Modifier.width(rightWidth).fillMaxHeight(),
                        onCollapse = { rightCollapsed = true }
                    )
                }
            }
        }
    }
}

/**
 * 顶部信息条（私有组件）：项目名 + 游戏/语言/管理模式胶囊 + 本地路径 + 打开目录。
 */
@Composable
private fun TopInfoBar(project: GameProjectInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            InfoChip(stringResource(Res.string.gp_ws_selected_game, project.selectedGame.ifBlank { "-" }))
            InfoChip(stringResource(Res.string.gp_ws_language, project.language.name))
            InfoChip(stringResource(Res.string.gp_ws_pm, project.packageManager.name))
            Text(
                text = project.localPath,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { getPlatform().openInFileExplorer(project.localPath) }) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(Res.string.gp_ws_open_dir),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 顶部信息小胶囊（私有组件）。
 */
@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = AppShapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 折叠后的窄轨道（私有组件）。
 * 整条轨道均可点击展开（兼容紧凑模式下 IconButton 最小尺寸被 36dp 轨道裁切的问题）。
 */
@Composable
private fun CollapsedRail(onExpand: () -> Unit, expandIcon: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight()
            .clickable { onExpand() }
            .pointerHoverIcon(PointerIcon.Hand),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            expandIcon()
        }
    }
}

/**
 * 左侧项目资源树面板（私有组件）。
 */
@Composable
private fun ResourceTreePanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    // 已展开的目录路径集合（会话级状态）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val visibleNodes = remember(viewModel.resourceTree, expandedPaths) {
        flattenTree(viewModel.resourceTree, expandedPaths)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 面板头：标题 + 刷新 + 折叠
            PanelHeader(
                title = stringResource(Res.string.gp_ws_left),
                onCollapse = onCollapse,
                collapseIcon = Icons.Default.ChevronLeft,
                actions = {
                    IconButton(onClick = { viewModel.refreshTree() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.gp_ws_refresh),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
            val tree = viewModel.resourceTree
            if (tree == null) {
                EmptyPanel(text = stringResource(Res.string.gp_tree_empty))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleNodes, key = { it.node.path }) { flat ->
                        TreeRow(
                            node = flat.node,
                            depth = flat.depth,
                            expanded = flat.node.path in expandedPaths,
                            onToggle = {
                                expandedPaths = if (flat.node.path in expandedPaths) {
                                    expandedPaths - flat.node.path
                                } else {
                                    expandedPaths + flat.node.path
                                }
                            },
                            onSelectHtml = { viewModel.selectHtml(flat.node.path) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 面板标题栏（私有组件）：标题 + 自定义动作 + 折叠按钮。
 */
@Composable
private fun PanelHeader(
    title: String,
    onCollapse: () -> Unit,
    collapseIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            actions()
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = collapseIcon,
                    contentDescription = title,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 树节点行（私有组件）：缩进 + 图标 + 名称；文件夹点击展开/收起，HTML 文件点击切换预览。
 */
@Composable
private fun TreeRow(
    node: ResourceTreeNode,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectHtml: () -> Unit
) {
    val isHtml = node.path.endsWith(".html", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clickable { if (node.isDirectory) onToggle() else if (isHtml) onSelectHtml() }
            .padding(start = (8 + depth * 14).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (node.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isHtml) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 中间预览面板（私有组件）：HTML 页签栏 + WebView 渲染区 + 全屏切换 + 控制台展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterPreviewPanel(
    viewModel: GameProjectViewModel,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    consoleCollapsed: Boolean,
    onToggleConsole: () -> Unit,
    consoleHeight: androidx.compose.ui.unit.Dp,
    onConsoleHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(4.dp),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 页签栏：HTML 文件切换 + 全屏按钮 + 控制台开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.htmlFiles, key = { it }) { path ->
                        FilterChip(
                            selected = path == viewModel.selectedHtml,
                            onClick = { viewModel.selectHtml(path) },
                            label = { Text(path.substringAfterLast('\\').substringAfterLast('/')) }
                        )
                    }
                }
                // 调试模式开关：开启后注入 laya.debugtool.js 并启用右侧调试面板
                IconButton(onClick = { viewModel.toggleDebugMode(!viewModel.debugMode) }) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = stringResource(Res.string.gp_debug_toggle),
                        modifier = Modifier.size(18.dp),
                        tint = if (viewModel.debugMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // 控制台展开开关按钮（全屏时自动隐藏）
                if (!fullscreen) {
                    IconButton(onClick = onToggleConsole) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = stringResource(Res.string.gp_ws_console_toggle),
                            modifier = Modifier.size(18.dp),
                            tint = if (!consoleCollapsed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(
                            if (fullscreen) Res.string.gp_ws_exit_fullscreen else Res.string.gp_ws_fullscreen
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // 渲染区
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.htmlFiles.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.gp_preview_no_html),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    GameHtmlPreview(
                        htmlPath = viewModel.selectedHtml,
                        debugMode = viewModel.debugMode,
                        inspectTarget = viewModel.inspectTarget,
                        onDebugMessage = viewModel::onDebugBridgeMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 底部控制台面板（全屏时隐藏）
            if (!consoleCollapsed && !fullscreen) {
                val density = LocalDensity.current
                HorizontalSplitter(onDrag = { delta ->
                    val newHeight = (consoleHeight - with(density) { delta.toDp() }).coerceIn(100.dp, 400.dp)
                    onConsoleHeightChange(newHeight)
                })
                BottomConsolePanel(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth().height(consoleHeight),
                    onClose = onToggleConsole
                )
            }
        }
    }
}

/**
 * 底部控制台面板（私有组件）：
 * 包含控制栏（图标 + 标题 + 一键清空 + 关闭）；
 * 日志输出区（LazyColumn + Monospace 字体 + 支持长按选择复制）。
 */
@Composable
private fun BottomConsolePanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.debugLogs.size) {
        if (viewModel.debugLogs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.debugLogs.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 控制栏：标题 + 动作 + 关闭
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
                    Spacer(Modifier.weight(1f))
                    // 一键清空
                    IconButton(
                        onClick = { viewModel.clearDebugLogs() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Delete,
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
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
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
                    if (viewModel.debugLogs.isEmpty()) {
                        item {
                            Text(
                                text = "No logs yet. Run pages in Debug Mode (bug icon) to stream web logs here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(viewModel.debugLogs) { line ->
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

/**
 * 右侧调试面板（私有组件）：
 * 上半区为节点树（层级展示，点击选中节点并查询属性）；
 * 下半区为属性 / 日志双页签（属性单行一条，1 秒周期动态刷新）。
 */
@Composable
private fun PropertiesPanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    // 已展开的调试树节点 ID 集合
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    // 下半区页签：true = 属性，false = 日志
    var showProps by remember { mutableStateOf(true) }
    val visibleDebugNodes = remember(viewModel.debugTree, expandedIds) {
        flattenDebugTree(viewModel.debugTree, expandedIds)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelHeader(
                title = stringResource(Res.string.gp_ws_right),
                onCollapse = onCollapse,
                collapseIcon = Icons.Default.ChevronRight
            )
            if (!viewModel.debugMode) {
                EmptyPanel(text = stringResource(Res.string.gp_debug_toggle_hint))
            } else {
                // 节点树区（层级展示）
                SectionLabel(stringResource(Res.string.gp_debug_tree_section))
                if (viewModel.debugTree == null) {
                    SectionHint(stringResource(Res.string.gp_debug_no_tree))
                } else {
                    LazyColumn(modifier = Modifier.weight(0.42f)) {
                        items(
                            visibleDebugNodes,
                            key = { it.depth.toString() + "|" + it.node.id + "|" + it.node.text }
                        ) { flat ->
                            DebugTreeRow(
                                node = flat.node,
                                depth = flat.depth,
                                selected = flat.node.id == viewModel.debugSelectedId,
                                onToggle = {
                                    expandedIds = if (flat.node.id in expandedIds) {
                                        expandedIds - flat.node.id
                                    } else {
                                        expandedIds + flat.node.id
                                    }
                                },
                                onSelect = { viewModel.selectDebugNode(flat.node.id.ifBlank { null }) }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                // 属性 / 日志页签
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = showProps,
                        onClick = { showProps = true },
                        label = { Text(stringResource(Res.string.gp_debug_props_section), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = !showProps,
                        onClick = { showProps = false },
                        label = { Text(stringResource(Res.string.gp_debug_logs_section), style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (showProps) {
                    if (viewModel.debugProps.isEmpty()) {
                        SectionHint(stringResource(Res.string.gp_debug_no_selection))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(viewModel.debugProps, key = { it.key }) { row ->
                                PropRow(row = row)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(viewModel.debugLogs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分区小标题（私有组件）。
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * 分区空态提示（私有组件，非占满型）。
 */
@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)
    )
}

/**
 * 调试树节点行（私有组件）：缩进 + 文本；点击选中并查询属性，含子节点时可展开/收起。
 */
@Composable
private fun DebugTreeRow(
    node: DebugTreeNode,
    depth: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable { onSelect(); if (node.item.isNotEmpty()) onToggle() }
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .padding(start = (6 + depth * 12).dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = node.text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 属性行（私有组件）：单行显示一条 key: value。
 */
@Composable
private fun PropRow(row: org.gemini.ui.forge.model.gameproject.DebugPropertyRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 面板空态提示（私有组件）。
 */
@Composable
private fun EmptyPanel(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * 资源树扁平化结果（文件内部私有数据类）。
 */
private data class FlatNode(
    val node: ResourceTreeNode,
    val depth: Int
)

/**
 * 将资源树按展开状态扁平化为可见行列表（文件内部私有函数）。
 */
private fun flattenTree(root: ResourceTreeNode?, expanded: Set<String>): List<FlatNode> {
    if (root == null) return emptyList()
    val out = mutableListOf<FlatNode>()
    fun walk(node: ResourceTreeNode, depth: Int) {
        node.children.forEach { child ->
            out.add(FlatNode(child, depth))
            if (child.isDirectory && child.path in expanded) {
                walk(child, depth + 1)
            }
        }
    }
    walk(root, 0)
    return out
}

/**
 * 调试树扁平化结果（文件内部私有数据类）。
 */
private data class FlatDebugNode(
    val node: DebugTreeNode,
    val depth: Int
)

/**
 * 将调试节点树按展开状态扁平化为可见行列表（文件内部私有函数）。
 */
private fun flattenDebugTree(root: DebugTreeNode?, expanded: Set<String>): List<FlatDebugNode> {
    if (root == null) return emptyList()
    val out = mutableListOf<FlatDebugNode>()
    fun walk(node: DebugTreeNode, depth: Int) {
        node.item.forEach { child ->
            out.add(FlatDebugNode(child, depth))
            if (child.item.isNotEmpty() && child.id in expanded) {
                walk(child, depth + 1)
            }
        }
    }
    walk(root, 0)
    return out
}

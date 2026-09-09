package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.extend.rememberClipboardAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_preview_no_html
import geminiuiforge.composeapp.generated.resources.gp_debug_toggle
import geminiuiforge.composeapp.generated.resources.gp_ws_console_toggle
import geminiuiforge.composeapp.generated.resources.gp_ws_debug_param
import geminiuiforge.composeapp.generated.resources.gp_ws_demo_param
import geminiuiforge.composeapp.generated.resources.gp_ws_dev_tools
import geminiuiforge.composeapp.generated.resources.gp_ws_exit_fullscreen
import geminiuiforge.composeapp.generated.resources.gp_ws_fullscreen
import geminiuiforge.composeapp.generated.resources.gp_ws_language
import geminiuiforge.composeapp.generated.resources.gp_ws_selected_game
import org.gemini.ui.forge.model.gameproject.GameProjectSession
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import org.gemini.ui.forge.ui.feature.gameproject.preview.GameHtmlPreview
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 中间预览面板：HTML 页签栏 + WebView 渲染区 + 全屏切换 + 控制台展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterPreviewPanel(
    viewModel: GameProjectViewModel,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    consoleCollapsed: Boolean,
    onToggleConsole: () -> Unit,
    consoleHeight: androidx.compose.ui.unit.Dp,
    onConsoleHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    reloadTrigger: Int,
    onReload: () -> Unit,
    devToolsTrigger: Int,
    onOpenDevTools: () -> Unit,
    isDemoSelected: Boolean,
    onToggleDemo: () -> Unit,
    isDebugSelected: Boolean,
    onToggleDebug: () -> Unit,
    currentUrl: String?,
    onUrlComputed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val project = GameProjectSession.currentProject
    val copyToClipboard = rememberClipboardAction()

    Surface(
        modifier = modifier.padding(4.dp),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 页签栏：HTML 文件切换 + 全屏按钮 + 控制台开关 + 刷新
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
                // 开启 Chromium 原生 DevTools (F12 调试窗口) 按钮
                IconButton(onClick = onOpenDevTools) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = stringResource(Res.string.gp_ws_dev_tools),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Demo / Debug URL 参数开关页签 (矢量 M3 FilterChip，默认均选中)
                FilterChip(
                    selected = isDemoSelected,
                    onClick = onToggleDemo,
                    label = { Text(stringResource(Res.string.gp_ws_demo_param), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(26.dp)
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = isDebugSelected,
                    onClick = onToggleDebug,
                    label = { Text(stringResource(Res.string.gp_ws_debug_param), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(26.dp)
                )
                Spacer(Modifier.width(4.dp))
                // 刷新页面按钮 (M3 内置 Refresh 图标)
                IconButton(onClick = onReload) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Page",
                        modifier = Modifier.size(18.dp)
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

            // 拟真浏览器地址栏：展示当前运行 of 本地 Web 服务 URL 链接，支持一键复制，让开发流程完全透明
            if (currentUrl != null && !fullscreen) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = AppShapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        SelectionContainer {
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(currentUrl, "已复制预览地址")
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
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
                        reloadTrigger = reloadTrigger,
                        devToolsTrigger = devToolsTrigger,
                        isDemoSelected = isDemoSelected,
                        isDebugSelected = isDebugSelected,
                        selectedGame = project?.selectedGame ?: "",
                        onUrlComputed = onUrlComputed,
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

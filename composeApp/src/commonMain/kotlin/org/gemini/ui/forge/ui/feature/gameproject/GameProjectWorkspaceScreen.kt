package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_ws_no_project
import org.gemini.ui.forge.model.gameproject.GameProjectSession
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.feature.gameproject.workspace.CollapsedRail
import org.gemini.ui.forge.ui.feature.gameproject.workspace.PropertiesPanel
import org.gemini.ui.forge.ui.feature.gameproject.workspace.ResourceTreePanel
import org.gemini.ui.forge.ui.feature.gameproject.workspace.CenterPreviewPanel
import org.gemini.ui.forge.ui.feature.gameproject.workspace.TopInfoBar
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 游戏项目专属工作台界面。
 * 布局：顶部当前游戏信息条；左侧项目资源树（可拖拽调宽/折叠）；
 * 中间渲染 build 目录 HTML（多页面切换 + 全屏预览）；
 * 右侧属性 / 调试信息面板（阶段 3 接入 debugtool 数据，当前为占位）。
 *
 * 经过千行大解耦重构，本类现已瘦身为纯声明式脚手架主容器，
 * 所有具体的子卡片面板均解耦拆分至 workspace/ 包下的物理独立文件中。
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

    // 面板宽度与折叠/全屏状态（会话级状态）
    var leftWidth by remember { mutableStateOf(230.dp) }
    var rightWidth by remember { mutableStateOf(280.dp) }
    var leftCollapsed by remember { mutableStateOf(false) }
    var rightCollapsed by remember { mutableStateOf(false) }
    var fullscreenPreview by remember { mutableStateOf(false) }
    var consoleCollapsed by remember { mutableStateOf(false) }
    var consoleHeight by remember { mutableStateOf(160.dp) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var devToolsTrigger by remember { mutableStateOf(0) }
    var isDemoSelected by remember { mutableStateOf(true) }
    var isDebugSelected by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
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
                reloadTrigger = reloadTrigger,
                onReload = { reloadTrigger++ },
                devToolsTrigger = devToolsTrigger,
                onOpenDevTools = { devToolsTrigger++ },
                isDemoSelected = isDemoSelected,
                onToggleDemo = { isDemoSelected = !isDemoSelected },
                isDebugSelected = isDebugSelected,
                onToggleDebug = { isDebugSelected = !isDebugSelected },
                currentUrl = currentUrl,
                onUrlComputed = { currentUrl = it },
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

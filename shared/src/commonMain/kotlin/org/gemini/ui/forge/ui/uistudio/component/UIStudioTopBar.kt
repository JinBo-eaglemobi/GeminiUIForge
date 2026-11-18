package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_save_layout
import geminiuiforge.composeapp.generated.resources.app_name
import geminiuiforge.composeapp.generated.resources.back_to_home
import geminiuiforge.composeapp.generated.resources.menu_help
import geminiuiforge.composeapp.generated.resources.menu_settings
import geminiuiforge.composeapp.generated.resources.ui_studio_btn_run_preview
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpController
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 一体化融合顶部导航栏组件。
 * 将大厅品牌面包屑导航（Gemini UI Forge / [UI Studio] 工程名 [分辨率]）、工作区专属工具组（侧栏/属性/撤销重做/运行/保存）
 * 与系统级通用入口（MCP通信/使用帮助/全局设置）100% 融合于同一单行工具栏中。
 */
@Composable
fun UIStudioTopBar(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    onBackToHome: () -> Unit,
    onMcpClicked: () -> Unit = {},
    onHelpClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicTitleStyle = if (spacing.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    val dynamicBadgeStyle = if (spacing.isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val dynamicPillStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val state = viewModel.state
    val projectName = state.project?.projectId ?: "UI Project"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.topBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：返回大厅按钮 + 软件主品牌 + 面包屑层级 + 标题徽章 + 项目名称 + 视口分辨率
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                AppIconButton(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBackToHome,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = stringResource(Res.string.back_to_home)
                )

                Text(
                    text = stringResource(Res.string.app_name),
                    style = dynamicTitleStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "/",
                    style = dynamicTitleStyle,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // UI Studio 标识胶囊
                Surface(
                    shape = AppShapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = spacing.extraSmall)
                ) {
                    Text(
                        text = "UI Studio",
                        style = dynamicBadgeStyle.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
                    )
                }

                // 项目名称
                Text(
                    text = projectName,
                    style = dynamicTitleStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 画布当前视图分辨率药丸
                val currentView = state.currentView
                val currentW = currentView?.width?.toInt() ?: state.canvasWidth.toInt()
                val currentH = currentView?.height?.toInt() ?: state.canvasHeight.toInt()

                Surface(
                    shape = AppShapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$currentW × $currentH",
                        style = dynamicPillStyle,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.inspectorFieldPaddingVertical)
                    )
                }
            }

            // 右侧：工作区工具组 + 细分割线 + 系统全局入口组
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 素材库侧栏显隐切换按钮
                AppIconButton(
                    imageVector = if (state.isAssetPanelVisible) Icons.AutoMirrored.Filled.ViewSidebar else Icons.Default.FolderOpen,
                    onClick = { viewModel.viewport.toggleAssetPanel() },
                    tier = AppIconButtonTier.LARGE,
                    tooltip = "切换左侧面板 (Toggle Sidebar)",
                    tint = if (state.isAssetPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 右侧面板显隐切换按钮
                AppIconButton(
                    imageVector = Icons.Default.Tune,
                    onClick = { viewModel.viewport.toggleRightSidebar() },
                    tier = AppIconButtonTier.LARGE,
                    tooltip = if (state.isRightSidebarVisible) "收起右侧面板" else "展开右侧面板",
                    tint = if (state.isRightSidebarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 撤销按钮 (Ctrl+Z)
                AppIconButton(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    onClick = { viewModel.canvasEditor.undo() },
                    enabled = state.canUndo,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = "撤销 (Ctrl+Z)",
                    tint = if (state.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )

                // 重做按钮 (Ctrl+Y)
                AppIconButton(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    onClick = { viewModel.canvasEditor.redo() },
                    enabled = state.canRedo,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = "重做 (Ctrl+Y)",
                    tint = if (state.canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )

                // 运行游戏预览按钮 (▶ 运行预览)
                val isGameActivated = remember(state.project?.gameProjectPath) {
                    val p = state.project?.gameProjectPath
                    !p.isNullOrBlank() && viewModel.scaffoldBridge.isGameProjectActivated(p)
                }

                AppIconButton(
                    imageVector = Icons.Default.PlayArrow,
                    onClick = { viewModel.onRunPreviewClicked() },
                    tier = AppIconButtonTier.LARGE,
                    tooltip = stringResource(Res.string.ui_studio_btn_run_preview),
                    tint = if (isGameActivated) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )

                // 一键保存工程按钮（带未保存状态小圆点指示）
                AppIconButton(
                    onClick = { viewModel.saveProject(templateRepo) },
                    enabled = !state.isSaving,
                    tier = AppIconButtonTier.LARGE,
                    isLoading = state.isSaving,
                    tooltip = if (state.isDirty) "${stringResource(Res.string.action_save_layout)} (有未保存修改)"
                    else stringResource(Res.string.action_save_layout)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(Res.string.action_save_layout),
                            tint = if (state.isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(LocalAppSpacing.current.iconLargeSize)
                        )
                        if (state.isDirty) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .background(Color(0xFFFF9100), CircleShape)
                            )
                        }
                    }
                }

                // 细分割线（区分工作区专属动作与全局系统服务）
                VerticalDivider(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // MCP 服务中心入口（常驻显示，带状态指示）
                val isMcpRunning by McpController.isRunning.collectAsState()
                AppIconButton(
                    onClick = onMcpClicked,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = if (isMcpRunning) "MCP 服务运行中 (点击管理)" else "MCP 服务与客户端配置"
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "MCP Service",
                            tint = if (isMcpRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(spacing.iconLargeSize)
                        )
                        if (isMcpRunning) {
                            Box(
                                modifier = Modifier
                                    .size(spacing.small)
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xFF4CAF50), shape = CircleShape)
                            )
                        }
                    }
                }

                // 帮助与设置按钮（始终显示）
                AppIconButton(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(Res.string.menu_help),
                    onClick = onHelpClicked,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = "查看使用说明"
                )
                AppIconButton(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.menu_settings),
                    onClick = onSettingsClicked,
                    tier = AppIconButtonTier.LARGE,
                    tooltip = "应用全局设置"
                )
            }
        }
    }
}

package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioLeftBottomTab
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 左侧复合边栏组件
 *
 * 采用上下分层结构：
 * - 上半部分：视图界面列表 (Views)
 * - 中间：水平拖拽分割手柄 (HorizontalSplitter)，支持上下自由拖拽改变高度比例
 * - 下半部分：带 Tab 切换的多功能区（素材库 Assets / 图层层级 Hierarchy）
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioLeftSidebar(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    var totalHeightPx by remember { mutableStateOf(1f) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { totalHeightPx = it.height.coerceAtLeast(1).toFloat() }
    ) {
        // 上半部分：视图界面管理
        UIStudioViewsPanel(
            viewModel = viewModel,
            templateRepo = templateRepo,
            modifier = Modifier
                .weight(state.viewsPanelHeightRatio.coerceIn(0.05f, 0.95f))
                .fillMaxWidth()
        )

        // 水平分割手柄（官方 draggable 封装）
        HorizontalSplitter(
            onDrag = { deltaPx ->
                val deltaRatio = deltaPx / totalHeightPx
                viewModel.viewport.updateViewsPanelHeightRatio(viewModel.state.viewsPanelHeightRatio + deltaRatio)
            },
            onDragEnd = {
                viewModel.viewport.saveWorkspaceLayout()
            }
        )

        // 下半部分：带 Tab 切换的多功能区 (素材库 / 图层层级) + 底部实时预览
        var bottomHeightPx by remember { mutableStateOf(1f) }
        var previewHeightRatio by remember { mutableStateOf(0.35f) }

        Column(
            modifier = Modifier
                .weight((1f - state.viewsPanelHeightRatio).coerceIn(0.05f, 0.95f))
                .fillMaxWidth()
                .onSizeChanged { bottomHeightPx = it.height.coerceAtLeast(1).toFloat() }
        ) {
            // Tab 切换与列表内容区 (素材库 / 图层树)
            Column(
                modifier = Modifier
                    .weight((1f - previewHeightRatio).coerceIn(0.2f, 0.85f))
                    .fillMaxWidth()
            ) {
                val dynamicTabStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                // 下半部分顶部分段切换栏
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spacing.sidebarTabBarHeight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        val currentTab = state.leftBottomTab

                        // Tab 1: 素材库
                        val isAssets = currentTab == UIStudioLeftBottomTab.ASSETS
                        Surface(
                            color = if (isAssets) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = AppShapes.small,
                            tonalElevation = if (isAssets) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(AppShapes.small)
                                .clickable { viewModel.pageManager.switchLeftBottomTab(UIStudioLeftBottomTab.ASSETS) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = if (isAssets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(spacing.inspectorInlineIconSize)
                                )
                                Spacer(modifier = Modifier.width(spacing.inspectorControlGap))
                                Text(
                                    text = "素材库",
                                    style = dynamicTabStyle.copy(
                                        fontWeight = if (isAssets) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isAssets) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Tab 2: 图层层级
                        val isHierarchy = currentTab == UIStudioLeftBottomTab.HIERARCHY
                        Surface(
                            color = if (isHierarchy) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = AppShapes.small,
                            tonalElevation = if (isHierarchy) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(AppShapes.small)
                                .clickable { viewModel.pageManager.switchLeftBottomTab(UIStudioLeftBottomTab.HIERARCHY) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = if (isHierarchy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(spacing.inspectorInlineIconSize)
                                )
                                Spacer(modifier = Modifier.width(spacing.inspectorControlGap))
                                Text(
                                    text = "图层层级",
                                    style = dynamicTabStyle.copy(
                                        fontWeight = if (isHierarchy) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isHierarchy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // 根据激活 Tab 渲染具体面板
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.leftBottomTab) {
                        UIStudioLeftBottomTab.ASSETS -> {
                            UIStudioAssetPanel(
                                viewModel = viewModel,
                                templateRepo = templateRepo,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        UIStudioLeftBottomTab.HIERARCHY -> {
                            UIStudioHierarchyPanel(
                                viewModel = viewModel,
                                templateRepo = templateRepo,
                                assetPathResolver = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.relativePath },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 水平分割手柄（自由拖拽调节左下实时预览面板高度）
            HorizontalSplitter(
                onDrag = { deltaPx ->
                    val deltaRatio = deltaPx / bottomHeightPx
                    previewHeightRatio = (previewHeightRatio - deltaRatio).coerceIn(0.15f, 0.75f)
                }
            )

            // 底部：高保真实时预览面板 (UIStudioPreviewPanel)
            UIStudioPreviewPanel(
                viewModel = viewModel,
                assetPathResolver = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.relativePath },
                assetUpdatedAtResolver = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.updatedAt },
                modifier = Modifier
                    .weight(previewHeightRatio.coerceIn(0.15f, 0.75f))
                    .fillMaxWidth()
            )
        }
    }
}


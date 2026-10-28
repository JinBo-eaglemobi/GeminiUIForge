package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab

/**
 * UI Studio 右侧复合边栏 (Right Sidebar)。
 *
 * 遵循一文件一 Composable 铁律与设计系统分栏规范：
 * - 顶部控制栏：[UIStudioRightSidebarHeader]，支持 [UIStudioRightTab.PROPERTY] 与 [UIStudioRightTab.AI_CHAT] 无缝切换；
 * - 属性检查模式 (PROPERTY)：
 *   - 上半部分：属性检查器面板 (UIStudioPropertyPanel)；
 *   - 中间：水平拖拽手柄 (HorizontalSplitter)，自由调节高度比例；
 *   - 下半部分：高保真实时预览面板 (UIStudioPreviewPanel)；
 * - AI 助手模式 (AI_CHAT)：
 *   - 嵌入式智能排版会话 (UIStudioEmbeddedChatPanel)，窄栏自适应响应式布局，支持独立逃逸至全景大弹窗。
 */
@Composable
fun UIStudioRightSidebar(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    var totalHeightPx by remember { mutableStateOf(1f) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { totalHeightPx = it.height.coerceAtLeast(1).toFloat() }
    ) {
        // 1. 顶部控制栏 (Tab 切换 + 独立弹窗入口 + 折叠边栏)
        UIStudioRightSidebarHeader(
            selectedTab = state.activeRightTab,
            onTabSelected = { viewModel.selectRightTab(it) },
            onOpenFullDialog = { viewModel.openAiChatDialog() },
            onCollapseSidebar = { viewModel.setRightSidebarVisible(false) },
            isAiChatDialogVisible = state.isAiChatDialogVisible,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 主体分页内容区：当 AI 全景大弹窗打开时，强制互斥显示属性面板 (PROPERTY)，单模态隔离
        val effectiveTab = if (state.isAiChatDialogVisible) UIStudioRightTab.PROPERTY else state.activeRightTab

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(
                targetState = effectiveTab,
                animationSpec = tween(200),
                label = "RightSidebarTabCrossfade"
            ) { tab ->
                when (tab) {
                    UIStudioRightTab.PROPERTY -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 上半部分：属性检查面板
                            UIStudioPropertyPanel(
                                viewModel = viewModel,
                                templateRepo = templateRepo,
                                modifier = Modifier
                                    .weight(state.propertyPanelHeightRatio.coerceIn(0.05f, 0.95f))
                                    .fillMaxWidth()
                            )

                            // 中间水平拖拽分割线手柄
                            HorizontalSplitter(
                                onDrag = { deltaPx ->
                                    val deltaRatio = deltaPx / totalHeightPx
                                    viewModel.updatePropertyPanelHeightRatio(viewModel.state.propertyPanelHeightRatio + deltaRatio)
                                },
                                onDragEnd = {
                                    viewModel.saveWorkspaceLayout()
                                }
                            )

                            // 下半部分：高保真独立实时预览面板
                            UIStudioPreviewPanel(
                                viewModel = viewModel,
                                assetPathResolver = { hash -> viewModel.findAssetByHash(hash)?.relativePath },
                                modifier = Modifier
                                    .weight((1f - state.propertyPanelHeightRatio).coerceIn(0.05f, 0.95f))
                                    .fillMaxWidth()
                            )
                        }
                    }

                    UIStudioRightTab.AI_CHAT -> {
                        UIStudioEmbeddedChatPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

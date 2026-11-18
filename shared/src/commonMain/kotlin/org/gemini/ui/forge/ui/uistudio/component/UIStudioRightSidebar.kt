package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab

/**
 * UI Studio 右侧复合边栏 (Right Sidebar)。
 *
 * 遵循一文件一 Composable 铁律与设计系统分栏规范：
 * - 顶部控制栏：[UIStudioRightSidebarHeader]，支持 [UIStudioRightTab.PROPERTY] 与 [UIStudioRightTab.AI_CHAT] 无缝切换；
 * - 属性检查模式 (PROPERTY)：
 *   - 属性检查器面板 (UIStudioPropertyPanel)，全高视口舒展，充裕展示全部几何与组件专属配置；
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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 1. 顶部控制栏 (Tab 切换 + 独立弹窗入口 + 折叠边栏)
        UIStudioRightSidebarHeader(
            selectedTab = state.activeRightTab,
            onTabSelected = { viewModel.viewport.selectRightTab(it) },
            onOpenFullDialog = { viewModel.pageManager.openAiChatDialog() },
            onCollapseSidebar = { viewModel.viewport.setRightSidebarVisible(false) },
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
                        // 属性检查面板彻底纵向全高舒展，拥有充裕的属性配置视口
                        UIStudioPropertyPanel(
                            viewModel = viewModel,
                            templateRepo = templateRepo,
                            modifier = Modifier.fillMaxSize()
                        )
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

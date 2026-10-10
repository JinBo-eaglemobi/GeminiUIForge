package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 左侧复合边栏组件
 *
 * 采用上下分层结构：
 * - 上半部分：视图界面列表 (Views)
 * - 中间：水平拖拽分割手柄 (HorizontalSplitter)，支持上下自由拖拽改变高度比例
 * - 下半部分：素材库面板 (Assets)
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
                .weight(state.viewsPanelHeightRatio.coerceIn(0.15f, 0.85f))
                .fillMaxWidth()
        )

        // 水平分割手柄
        HorizontalSplitter(
            onDrag = { deltaPx ->
                val deltaRatio = deltaPx / totalHeightPx
                viewModel.updateViewsPanelHeightRatio(state.viewsPanelHeightRatio + deltaRatio)
            }
        )

        // 下半部分：素材库管理
        UIStudioAssetPanel(
            viewModel = viewModel,
            templateRepo = templateRepo,
            modifier = Modifier
                .weight((1f - state.viewsPanelHeightRatio).coerceIn(0.15f, 0.85f))
                .fillMaxWidth()
        )
    }
}

package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.component.HorizontalSplitter
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 右侧复合边栏 (Right Sidebar)。
 *
 * 遵循一文件一 Composable 铁律与设计系统分栏规范：
 * - 上半部分：属性检查器面板 (UIStudioPropertyPanel)；
 * - 中间：水平拖拽手柄 (HorizontalSplitter)，支持自由调节上下高度比例；
 * - 下半部分：高保真实时预览面板 (UIStudioPreviewPanel)，支持素材图片尺寸与父组件渲染效果预览。
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
            .onSizeChanged { totalHeightPx = it.height.coerceAtLeast(1).toFloat() }
    ) {
        // 上半部分：属性检查面板
        UIStudioPropertyPanel(
            viewModel = viewModel,
            templateRepo = templateRepo,
            modifier = Modifier
                .weight(state.propertyPanelHeightRatio.coerceIn(0.05f, 0.95f))
                .fillMaxWidth()
        )

        // 中间水平拖拽分割线手柄（官方 draggable 封装）
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

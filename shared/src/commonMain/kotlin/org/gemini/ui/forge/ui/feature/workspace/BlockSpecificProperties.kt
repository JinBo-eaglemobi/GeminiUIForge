package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.runtime.Composable
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.ui.feature.workspace.property.*

/**
 * 专属属性编辑面板分发器组件
 *
 * 此组件是整个工作区右侧属性控制栏的专属配置核心入口。它负责监听和解析当前舞台（Canvas）
 * 中选中的组件块 [org.gemini.ui.forge.model.ui.UIBlock] 类型 [org.gemini.ui.forge.model.ui.UIBlockType]，
 * 动态劫持并分发渲染各个组件块类型的特有属性交互面板（如按钮文案、转轴行列及符号、文本样式、输入框Hint等）。
 *
 * @param viewModel 整个项目工作区的通用状态机 ViewModel，用于处理属性更迭的动作分发
 * @param state 当前统一工作区的全量运行时状态
 * @param apiKey 传递给下级弹窗（如转轴符号编辑器）使用的 Gemini API 鉴权密钥
 */
@Composable
fun BlockSpecificProperties(
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    apiKey: String
) {
    val selectedBlock = state.selectedBlock ?: return
    val blockType = selectedBlock.type
    val onPropertiesChanged = { props: BlockProperties ->
        viewModel.assetManager.updateBlockProperties(selectedBlock.id, props)
    }
    when (blockType) {
        UIBlockType.BUTTON -> ButtonPropertiesPanel(viewModel, state, selectedBlock, onPropertiesChanged)
        UIBlockType.VIEW -> ViewPropertiesPanel(selectedBlock, onPropertiesChanged)
        UIBlockType.TEXT -> TextPropertiesPanel(selectedBlock, onPropertiesChanged)
        UIBlockType.INPUT -> InputPropertiesPanel(selectedBlock, onPropertiesChanged)
        UIBlockType.REEL -> ReelPropertiesPanel(viewModel, state, apiKey, selectedBlock, onPropertiesChanged)
        UIBlockType.SPIN_BUTTON -> SpinButtonPropertiesPanel(viewModel, selectedBlock)
        else -> {
            // 其他类型保持原样
        }
    }
}

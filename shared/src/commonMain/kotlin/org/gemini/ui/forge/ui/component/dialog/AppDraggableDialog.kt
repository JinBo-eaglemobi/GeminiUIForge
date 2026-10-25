package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全平台通用视口自适应可拖拽弹窗外壳。
 * 托管全屏视口感知层，实时捕获视口物理包围盒供给 [DraggableDialogState] 进行四极安全夹具运算，
 * 并将弹窗初始居中且挂载 [draggableDialogContainer] 动态位移。
 *
 * @param onDismissRequest 关闭弹窗请求
 * @param state 外部传入的拖拽状态持有者，默认使用内置记忆实例
 * @param properties 弹窗属性，默认解禁平台默认宽度以支持视口全域平滑移动
 * @param modifier 弹窗本体修饰符
 * @param content 弹窗内部插槽，向子级暴露 [DraggableDialogState] 以便挂载标题栏拖拽抓手
 */
@Composable
fun AppDraggableDialog(
    onDismissRequest: () -> Unit,
    state: DraggableDialogState = rememberDraggableDialogState(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    modifier: Modifier = Modifier,
    content: @Composable (state: DraggableDialogState) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // 捕获全景视口物理尺寸，传递给 state 进行严密 Clamp 约束
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    state.viewportSize = coordinates.size
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = modifier.draggableDialogContainer(state)
            ) {
                content(state)
            }
        }
    }
}

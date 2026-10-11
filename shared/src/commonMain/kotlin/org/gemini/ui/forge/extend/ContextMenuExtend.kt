package org.gemini.ui.forge.extend

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 跨平台通用上下文菜单触发修饰符 (PC 端鼠标右键即时触发 + 移动端/触控屏幕长按触发)。
 *
 * 核心机制：
 * 1. PC 桌面端：拦截鼠标右键按下 (Secondary button press)，立即唤出上下文菜单，无需长按等待；
 * 2. 移动/触控端：利用 combinedClickable 的 onLongClick 监听长按手势，唤出同一份上下文菜单；
 * 3. 左键常规点击：执行常规 onClick 回调，双击执行 onDoubleClick 回调；
 * 4. 彻底消除全项目在各业务组件中散落手写重复 pointerInput 的冗余代码。
 *
 * @param enabled 是否启用手势交互
 * @param onClick 单击回调 (鼠标左键单击或触控轻触)
 * @param onDoubleClick 双击回调 (可选)
 * @param onContextMenu 上下文菜单触发回调 (PC 鼠标右键或移动端长按)
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.contextMenuClickable(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onContextMenu: () -> Unit
): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(onContextMenu) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        onContextMenu()
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { onClick?.invoke() },
            onDoubleClick = onDoubleClick,
            onLongClick = onContextMenu
        )
}

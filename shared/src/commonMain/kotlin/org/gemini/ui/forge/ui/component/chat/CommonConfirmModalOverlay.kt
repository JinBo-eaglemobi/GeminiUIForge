package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * 带有全屏深色半透明遮罩的通用操作确认模态覆盖层。
 *
 * 遵循一文件一 Composable 铁律与设计系统规范：
 * - 绝对置顶并自动获取键盘焦点；
 * - 本地优先拦截 Esc 键并就地消费，杜绝冒泡穿透关闭外层对话框；
 * - 点击暗色半透明遮罩外部区域自动触发 dismiss 关闭；
 * - 彻底根除 Compose Desktop 嵌套 Dialog (JDialog) 导致的 Z-order 物理层级遮蔽缺陷。
 *
 * @param title 标题
 * @param message 提示消息正文
 * @param confirmText 确认按钮文案
 * @param cancelText 取消按钮文案
 * @param isDestructive 是否为破坏性操作
 * @param onConfirm 用户点击确认按钮的回调
 * @param onDismiss 用户点击关闭、取消或遮罩外部的回调
 */
@Composable
fun CommonConfirmModalOverlay(
    title: String,
    message: String,
    confirmText: String = "确认",
    cancelText: String = "取消",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                    onDismiss()
                    true // 成功消费 Esc 事件，杜绝向外层冒泡
                } else {
                    false
                }
            }
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        CommonConfirmCard(
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            isDestructive = isDestructive,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // 拦截卡片内部点击，防遮罩冒泡
            )
        )
    }
}

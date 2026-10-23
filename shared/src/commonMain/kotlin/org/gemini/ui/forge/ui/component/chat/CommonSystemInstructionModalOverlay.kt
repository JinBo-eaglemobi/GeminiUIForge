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
 * 带有全屏深色半透明遮罩的模态覆盖层 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 绝对置顶并自动获取键盘焦点；
 * 2. 本地优先拦截 Esc 键并就地消费，杜绝冒泡穿透关闭外层对话框；
 * 3. 点击暗色半透明遮罩外部区域自动触发 dismiss 关闭；
 * 4. 内部嵌入居中的 [CommonSystemInstructionCard] 双模指令卡片。
 */
@Composable
fun CommonSystemInstructionModalOverlay(
    currentInstruction: String,
    defaultInstruction: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                    onDismiss()
                    true // 成功消费 Esc 事件，彻底阻断向父级 Dialog 冒泡！
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
        CommonSystemInstructionCard(
            currentInstruction = currentInstruction,
            defaultInstruction = defaultInstruction,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}


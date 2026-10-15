package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 带有全屏深色半透明遮罩的模态覆盖层 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 零双 Dialog 竞争冲突，绝对置顶；
 * 2. 点击暗色半透明遮罩外部区域自动触发 dismiss 关闭；
 * 3. 内部嵌入居中的 [CommonSystemInstructionCard] 双模指令卡片。
 */
@Composable
fun CommonSystemInstructionModalOverlay(
    currentInstruction: String,
    defaultInstruction: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
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

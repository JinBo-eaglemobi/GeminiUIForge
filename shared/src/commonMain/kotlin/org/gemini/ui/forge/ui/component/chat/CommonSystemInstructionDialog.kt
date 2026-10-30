package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 具有全屏视口自适应、支持标题栏拖拽且居中舒展的系统人设指令弹窗 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 基于 [AppDraggableDialog] 构建，彻底脱离宿主父容器（如嵌入式右侧栏）的物理宽度限制；
 * 2. 居中舒展 720dp ~ 960dp (dialogMediumWidth ~ dialogLargeWidth) 黄金响应式大弹窗；
 * 3. 内部嵌入双模指令编辑卡片 [CommonSystemInstructionCard]。
 */
@Composable
fun CommonSystemInstructionDialog(
    currentInstruction: String,
    defaultInstruction: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    AppDraggableDialog(
        onDismissRequest = onDismiss
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = (maxWidth * 0.72f).coerceIn(spacing.dialogMediumWidth, spacing.dialogLargeWidth)
            CommonSystemInstructionCard(
                currentInstruction = currentInstruction,
                defaultInstruction = defaultInstruction,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                modifier = Modifier
                    .width(dialogWidth)
                    .fillMaxHeight(0.88f)
            )
        }
    }
}



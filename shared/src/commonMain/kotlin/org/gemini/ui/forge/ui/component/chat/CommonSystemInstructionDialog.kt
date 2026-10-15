package org.gemini.ui.forge.ui.component.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 通用系统人设独立弹窗 (一文件一 Composable 规范)。
 *
 * 内部嵌入 [CommonSystemInstructionCard] 双模指令卡片。
 */
@Composable
fun CommonSystemInstructionDialog(
    currentInstruction: String,
    defaultInstruction: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CommonSystemInstructionCard(
            currentInstruction = currentInstruction,
            defaultInstruction = defaultInstruction,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

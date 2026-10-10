package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 通用系统人设指令内容卡片（纯组件，可直接嵌入弹窗或作为覆盖层）
 */
@Composable
fun CommonSystemInstructionCard(
    currentInstruction: String,
    defaultInstruction: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var editingText by remember(currentInstruction) { mutableStateOf(currentInstruction) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(spacing.dialogConfigWidth)
            .wrapContentHeight()
            .clickable(enabled = false) {}
    ) {
        Column(modifier = Modifier.padding(spacing.large)) {
            // 1. 顶部标题与重置按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = "系统人设指令 (System Instruction)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (defaultInstruction.isNotBlank()) {
                    IconButton(
                        onClick = { editingText = defaultInstruction },
                        modifier = Modifier.size(32.dp).tip("重置为系统出厂预设")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))

            Text(
                text = "在此设定当前 Agent 的全局角色定义、回答风格与设计约束。该指令将作为最高优先级系统提示词注入全部交互中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(spacing.medium))

            // 2. 指令编辑多行输入框
            OutlinedTextField(
                value = editingText,
                onValueChange = { editingText = it },
                placeholder = {
                    Text(
                        text = "输入自定义 System Prompt 指令...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(Modifier.height(spacing.large))

            // 3. 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.width(spacing.small))
                Button(
                    onClick = {
                        onConfirm(editingText.trim())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存并应用")
                }
            }
        }
    }
}

/**
 * 带有全屏深色半透明遮罩的模态覆盖层（零双 Dialog 竞争冲突，绝对置顶）
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
            .clickable { onDismiss() },
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

/**
 * 通用系统人设独立弹窗
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

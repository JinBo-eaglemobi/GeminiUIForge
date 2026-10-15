package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.markdown.AppMarkdown
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 系统人设指令内容卡片（支持 Write / Preview 双模分段切换，遵循一文件一 Composable 规范）。
 *
 * 核心特性：
 * 1. 顶部控制器：提供「编辑 (Write)」与「预览 (Preview)」分段切换控制器；
 * 2. 编辑模式：保证 System Prompt 纯文本字符与 Tokens 精确掌控；
 * 3. 预览模式：通过 [AppMarkdown] 原生高质感实时预览标题、列表、规则与代码；
 * 4. 底部操作栏：支持一键重置为系统出厂预设指令与保存并应用。
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 编辑, 1: 预览

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(spacing.dialogMediumWidth)
            .wrapContentHeight()
            .clickable(enabled = false) {}
    ) {
        Column(modifier = Modifier.padding(spacing.large)) {
            // 1. 顶部 Header 与模式切换
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

                // 「编辑 / 预览」分段切换控制条
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("编辑", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("预览", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))

            // 2. 主体内容区：编辑多行文本 vs Markdown 渲染视图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        placeholder = {
                            Text(
                                text = "输入 Markdown 格式的 System Prompt 指令（如 # Role, ## Rules）...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.medium)
                                .verticalScroll(scrollState)
                        ) {
                            if (editingText.isBlank()) {
                                Text(
                                    text = "暂无指令内容，请切换到「编辑」模式输入 Markdown 文本",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                AppMarkdown(content = editingText)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.large))

            // 3. 底部操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (defaultInstruction.isNotBlank()) {
                    TextButton(
                        onClick = { editingText = defaultInstruction },
                        modifier = Modifier.tip("重置为系统出厂预设指令")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复预设")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
}

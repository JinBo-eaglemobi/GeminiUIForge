package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.extend.rememberClipboardAction
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
    val copyToClipboard = rememberClipboardAction()

    val cardModifier = modifier
        .fillMaxWidth(0.96f)
        .fillMaxHeight(0.95f)
        .clickable(enabled = false) {}

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.large)
        ) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 预览模式下一键复制全文快捷按钮
                    if (selectedTab == 1 && editingText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1B2E),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { copyToClipboard(editingText, "已复制系统人设指令全文") }
                                .tip("一键复制系统人设指令全文到剪贴板")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "复制全文",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFCBD5E1)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "复制全文",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(Modifier.width(spacing.small))
                    }

                    // 「编辑 / 预览」锐利高对比度胶囊切换控制器（彻底根除 M3 SegmentedButton 默认勾选图标挤压与亚像素抗锯齿底影模糊缺陷）
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1B2E),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        // 编辑按钮
                        val isEditSelected = selectedTab == 0
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isEditSelected) Color(0xFF8B5CF6) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { selectedTab = 0 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isEditSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "编辑",
                                    fontSize = 12.sp,
                                    fontWeight = if (isEditSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isEditSelected) Color.White else Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        // 预览按钮
                        val isPreviewSelected = selectedTab == 1
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isPreviewSelected) Color(0xFF8B5CF6) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { selectedTab = 1 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isPreviewSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "预览",
                                    fontSize = 12.sp,
                                    fontWeight = if (isPreviewSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isPreviewSelected) Color.White else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }
        }

            Spacer(Modifier.height(spacing.medium))

            // 2. 主体内容区：编辑多行文本 vs Markdown 渲染视图
            val contentBoxModifier = Modifier
                .fillMaxWidth()
                .weight(1f)

            Box(modifier = contentBoxModifier) {
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        placeholder = {
                            Text(
                                text = "输入 Markdown 格式的 System Prompt 指令（如 # Role, ## Rules）...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
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
                                SelectionContainer {
                                    AppMarkdown(content = editingText)
                                }
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

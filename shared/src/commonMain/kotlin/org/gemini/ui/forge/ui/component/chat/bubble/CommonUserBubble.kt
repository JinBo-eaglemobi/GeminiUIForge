package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.getSelectedTextOrNull
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.formatTimestamp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.model.chat.ChatMentionTag
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.component.tip
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用用户对话气泡组件 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 鼠标自由划选与复制支持；
 * 2. 桌面端鼠标右键 / 悬浮唤起功能菜单（复制文本、重新编辑并填入、重新生成、删除本轮）；
 * 3. 关联附件图片缩略图网格；
 * 4. 附件/提及标签具备深色高对比度、悬停提亮与点击查看文档/图片大图能力。
 */
@Composable
fun CommonUserBubble(
    message: VisualChatMessage,
    maxBubbleWidth: Dp,
    onImageClick: ((String) -> Unit)? = null,
    onMentionClick: ((ChatMentionTag) -> Unit)? = null,
    onEditRequested: ((String) -> Unit)? = null,
    onRegenerateRequested: ((VisualChatMessage) -> Unit)? = null,
    onDeleteRequested: ((VisualChatMessage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val copy = rememberClipboardAction()
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var selectedTextSnapshot by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val userText = message.effectiveText

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = maxBubbleWidth)
        ) {
            Box {
                Surface(
                    color = Color(0xFF26213B), // 沉稳深色石板暗紫，告别浅粉紫色块的突兀与刺眼
                    contentColor = Color(0xFFF1F5F9), // 纯净柔和高亮白
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.pointerInput(message.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    val pos = event.changes.firstOrNull()?.position ?: Offset.Zero
                                    menuOffset = DpOffset(
                                        with(density) { pos.x.toDp() },
                                        with(density) { pos.y.toDp() }
                                    )
                                    event.changes.forEach { it.consume() }
                                    scope.launch {
                                        selectedTextSnapshot = getSelectedTextOrNull()
                                        showContextMenu = true
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        // 0. 本轮提问引用的 @ 外部文件/切片/视图上下文药丸（高对比度深色磨砂胶囊 + 点击查看预览 + 杜绝重复Emoji）
                        if (message.attachedMentions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                                modifier = Modifier.padding(bottom = spacing.small)
                            ) {
                                message.attachedMentions.forEach { mention ->
                                    val iconEmoji = when (mention.categoryName) {
                                        "VIEW" -> "🗖"
                                        "BLOCK" -> "❖"
                                        "ASSET" -> "📦"
                                        else -> "📄"
                                    }

                                    // 彻底剥离原始 title 中可能自带的各类 Emoji，根除 "📦 📦" 重复堆叠缺陷
                                    val cleanTitle = mention.title.trim()
                                        .removePrefix("📦").trim()
                                        .removePrefix("📄").trim()
                                        .removePrefix("❖").trim()
                                        .removePrefix("🗖").trim()

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0F172A).copy(alpha = 0.85f), // 深邃磨砂底色，与暗紫气泡高阶融洽
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f)), // 科技紫蓝细边框
                                        modifier = Modifier
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { onMentionClick?.invoke(mention) }
                                            .tip("点击预览文件或查看详情")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "$iconEmoji $cleanTitle",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFF8FAFC)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "预览文件",
                                                tint = Color(0xFFA5B4FC),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val userBubbleSelectionColors = remember {
                            TextSelectionColors(
                                handleColor = Color(0xFFA855F7),
                                backgroundColor = Color(0xFFA855F7).copy(alpha = 0.35f)
                            )
                        }
                        CompositionLocalProvider(LocalTextSelectionColors provides userBubbleSelectionColors) {
                            SelectionContainer {
                                Text(
                                    text = userText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFFF1F5F9),
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }

                        // 关联附件图片展示 (单图或多图网格)
                        val allImages = (listOfNotNull(message.generatedImageUri) + message.inputImageUris)
                            .filter { it.isNotBlank() }
                            .distinct()

                        if (allImages.isNotEmpty()) {
                            Spacer(Modifier.height(spacing.small))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                allImages.forEach { imgUri ->
                                    AsyncImage(
                                        model = imgUri,
                                        contentDescription = "附件图片",
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .clickable { onImageClick?.invoke(imgUri) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                // 右键上下文菜单 (跟随光标偏移)
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = menuOffset
                ) {
                    val selText = selectedTextSnapshot
                    if (!selText.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("复制选择文案") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showContextMenu = false
                                copy(selText, "已复制选中内容")
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(if (!selText.isNullOrBlank()) "复制全部文案" else "复制提问文本") },
                        leadingIcon = { Icon(Icons.Default.CopyAll, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            copy(userText, "已复制提问内容")
                        }
                    )

                    if (onEditRequested != null) {
                        DropdownMenuItem(
                            text = { Text("重新编辑并回填") },
                            leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showContextMenu = false
                                onEditRequested(userText)
                            }
                        )
                    }

                    if (onRegenerateRequested != null) {
                        DropdownMenuItem(
                            text = { Text("基于本轮重新生成回答") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showContextMenu = false
                                onRegenerateRequested(message)
                            }
                        )
                    }

                    if (onDeleteRequested != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除本轮对话", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showContextMenu = false
                                onDeleteRequested(message)
                            }
                        )
                    }
                }
            }

            // 底部元数据状态条：模型名称 + 发送时间 + 一键复制图标
            Row(
                modifier = Modifier.padding(top = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // 1. 发送使用的模型名称胶囊
                val modelName = message.modelName?.ifBlank { null }
                if (modelName != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                // 一键复制提问
                IconButton(
                    onClick = { copy(userText, "已复制提问内容") },
                    modifier = Modifier.size(20.dp).tip("一键复制提问")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制提问",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.width(spacing.small))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF4C1D95),
            modifier = Modifier.size(32.dp).tip("用户")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "用户",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
            }
        }
    }
}

package org.gemini.ui.forge.ui.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.extend.copyOnClick
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用 AI 对话消息气泡流组件
 *
 * 彻底剥离任何具体业务逻辑，通过 [messageActions] 插槽开放任意自定义动作注入能力。
 *
 * @param messages 历史对话消息列表
 * @param isGenerating 是否正在后台请求或流式推理中
 * @param streamingText 实时流式生成的打字文本
 * @param emptyTipText 消息为空时的占位文案
 * @param onImageClick 点击消息中关联图片的灯箱放大预览回调
 * @param messageActions 每条 AI 消息底部的自定义操作按钮插槽（如：应用到图元、保存至素材库等）
 * @param modifier 修饰符
 */
@Composable
fun CommonChatMessageList(
    messages: List<VisualChatMessage>,
    isGenerating: Boolean,
    streamingText: String = "",
    emptyTipText: String = "开始与 AI 助手对话，探索无限创意...",
    onImageClick: ((String) -> Unit)? = null,
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val listState = rememberLazyListState()

    // 自动吸底感知：判断当前列表是否贴近底部
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 2
        }
    }

    // 智能平滑吸底：当处于底部时，新增消息或流式推理自动保持吸底
    LaunchedEffect(messages.size, isGenerating, streamingText) {
        val total = messages.size + (if (isGenerating && streamingText.isNotBlank()) 1 else 0)
        if (total > 0 && isNearBottom) {
            listState.animateScrollToItem(total - 1)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = spacing.medium)) {
        val maxBubbleWidth = (maxWidth * 0.85f).coerceAtLeast(320.dp)

        if (messages.isEmpty() && !isGenerating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(spacing.small))
                    Text(
                        text = emptyTipText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(vertical = spacing.medium)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.role == "user") {
                        CommonUserBubble(
                            message = msg,
                            maxBubbleWidth = maxBubbleWidth,
                            onImageClick = onImageClick
                        )
                    } else {
                        CommonModelBubble(
                            message = msg,
                            maxBubbleWidth = maxBubbleWidth,
                            onImageClick = onImageClick,
                            messageActions = messageActions
                        )
                    }
                }

                // 流式打字中或正在生成气泡
                if (isGenerating) {
                    item(key = "generating_bubble") {
                        CommonGeneratingBubble(streamingText = streamingText)
                    }
                }
            }
        }
    }
}

/**
 * 通用用户消息气泡
 */
@Composable
private fun CommonUserBubble(
    message: VisualChatMessage,
    maxBubbleWidth: androidx.compose.ui.unit.Dp,
    onImageClick: ((String) -> Unit)?
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = maxBubbleWidth)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    val userText = message.prompt.ifBlank { message.textZh.ifBlank { message.textEn } }
                    Text(
                        text = userText,
                        style = MaterialTheme.typography.bodyMedium
                    )

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
        }

        Spacer(Modifier.width(spacing.small))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "用户",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 通用模型回复气泡（支持自定义操作插槽）
 */
@Composable
private fun CommonModelBubble(
    message: VisualChatMessage,
    maxBubbleWidth: androidx.compose.ui.unit.Dp,
    onImageClick: ((String) -> Unit)?,
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(spacing.small))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = maxBubbleWidth)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    val modelText = message.textZh.ifBlank { message.textEn.ifBlank { message.prompt } }
                    Text(
                        text = modelText,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 模型生成的图片展示
                    message.generatedImageUri?.takeIf { it.isNotBlank() }?.let { imgUri ->
                        Spacer(Modifier.height(spacing.small))
                        AsyncImage(
                            model = imgUri,
                            contentDescription = "生成结果图片",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { onImageClick?.invoke(imgUri) },
                            contentScale = ContentScale.Fit
                        )
                    }

                    // 底部操作区（默认复制按钮 + 外部插槽自定义扩展动作）
                    Spacer(Modifier.height(spacing.small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // 外部自定义注入的动作（例如应用到图元、保存到素材库等）
                        messageActions(message)

                        // 默认复制按钮
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(28.dp)
                                .copyOnClick(modelText, "已复制消息内容")
                                .tip("复制内容")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通用生成中打字/思考状态气泡
 */
@Composable
private fun CommonGeneratingBubble(
    streamingText: String
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(spacing.small))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                if (streamingText.isNotBlank()) {
                    Text(
                        text = streamingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 正在深度思考与组织回复中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

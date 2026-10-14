package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.component.chat.bubble.CommonGeneratingBubble
import org.gemini.ui.forge.ui.component.chat.bubble.CommonModelBubble
import org.gemini.ui.forge.ui.component.chat.bubble.CommonUserBubble
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用 AI 对话消息气泡流组件 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 【全局划选复制】：外层全包裹 [SelectionContainer]，用户可任意自由高亮文字并复制；
 * 2. 【多答版本翻页】：支持 < 1/2 > 分页切换历史历次回答；
 * 3. 【用户气泡功能菜单】：支持右键/长按唤起快捷操作（复制、重新编辑回填、重新生成、删除本轮）；
 * 4. 【高内聚解耦】：子气泡全部抽离为独立物理文件，遵循一文件一 Composable 铁律。
 *
 * @param messages 历史对话消息列表
 * @param isGenerating 是否正在后台请求或流式推理中
 * @param streamingText 实时流式生成的打字文本
 * @param emptyTipText 消息为空时的占位文案
 * @param onImageClick 点击消息中关联图片的灯箱放大预览回调
 * @param onEditRequested 重新编辑用户消息回填至输入框回调
 * @param onRegenerateRequested 重新生成本轮回答回调
 * @param onDeleteRequested 删除消息轮次回调
 * @param onVariantIndexChanged 单问多答翻页版本切换回调
 * @param messageActions 每条 AI 消息底部的自定义操作按钮插槽
 * @param modifier 修饰符
 */
@Composable
fun CommonChatMessageList(
    messages: List<VisualChatMessage>,
    isGenerating: Boolean,
    streamingText: String = "",
    emptyTipText: String = "开始与 AI 助手对话，探索无限创意...",
    onImageClick: ((String) -> Unit)? = null,
    onEditRequested: ((String) -> Unit)? = null,
    onRegenerateRequested: ((VisualChatMessage) -> Unit)? = null,
    onDeleteRequested: ((VisualChatMessage) -> Unit)? = null,
    onVariantIndexChanged: ((message: VisualChatMessage, newIndex: Int) -> Unit)? = null,
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

    // 智能平滑吸底：新增消息无条件滑动到最新一条；流式打字期间贴近底部时持续吸底
    LaunchedEffect(messages.size) {
        val total = messages.size + (if (isGenerating && streamingText.isNotBlank()) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    LaunchedEffect(streamingText) {
        val total = messages.size + (if (isGenerating && streamingText.isNotBlank()) 1 else 0)
        if (total > 0 && isNearBottom) {
            listState.animateScrollToItem(total - 1)
        }
    }

    SelectionContainer(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.medium)) {
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
                                onImageClick = onImageClick,
                                onEditRequested = onEditRequested,
                                onRegenerateRequested = onRegenerateRequested,
                                onDeleteRequested = onDeleteRequested
                            )
                        } else {
                            CommonModelBubble(
                                message = msg,
                                maxBubbleWidth = maxBubbleWidth,
                                onImageClick = onImageClick,
                                onVariantIndexChanged = { newIdx ->
                                    onVariantIndexChanged?.invoke(msg, newIdx)
                                },
                                onRegenerateRequested = onRegenerateRequested,
                                onDeleteRequested = onDeleteRequested,
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
}

package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.chat.bubble.CommonGeneratingBubble
import org.gemini.ui.forge.ui.component.chat.bubble.CommonModelBubble
import org.gemini.ui.forge.ui.component.chat.bubble.CommonUserBubble
import org.gemini.ui.forge.ui.component.chat.empty.CommonChatEmptyWelcomeView
import org.gemini.ui.forge.ui.component.chat.scroll.ChatScrollMemoryManager
import org.gemini.ui.forge.ui.component.chat.scroll.ChatScrollToBottomButton
import org.gemini.ui.forge.ui.component.chat.scroll.ChatTimeDivider
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用 AI 对话消息气泡流组件 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 【物理真实高度容器】：采用 [Column] + [verticalScroll]，100% 绝对物理像素排版，彻底根除虚拟估算导致的闪烁跳跃；
 * 2. 【官方原生垂直滚动条】：接入 Compose 官方原生 [VerticalScrollbarAdapter](ScrollState)，滑块长度恒定稳定，拖拽极致丝滑；
 * 3. 【会话滚动位置记忆】：依托 [ChatScrollMemoryManager] 记录离开位置，退出去再进入无缝恢复，初次进入默认吸底看最新消息；
 * 4. 【悬浮直达底部胶囊】：向上翻阅历史时优雅浮现，新消息/打字中带未读红点，点击平滑直达最新；
 * 5. 【阅读保护吸底机制】：历史阅读中不扯拽视口，自发消息无条件吸底；
 * 6. 【智能时间分组标签】：相隔超 5 分钟自动插入轻量居中时间标签；
 * 7. 【全局划选与多答版本】：全包裹 [SelectionContainer]，完整支持 < 1/2 > 翻页与右键菜单。
 */
@Composable
fun CommonChatMessageList(
    messages: List<VisualChatMessage>,
    isGenerating: Boolean,
    sessionId: String? = null,
    streamingText: String = "",
    emptyTipText: String = "开始与 AI 助手对话，探索无限创意...",
    viewName: String = "",
    viewSizeText: String = "",
    readyAssetCount: Int = 0,
    onSuggestionClick: (String) -> Unit = {},
    onImageClick: ((String) -> Unit)? = null,
    onEditRequested: ((String) -> Unit)? = null,
    onRegenerateRequested: ((VisualChatMessage) -> Unit)? = null,
    onDeleteRequested: ((VisualChatMessage) -> Unit)? = null,
    onVariantIndexChanged: ((message: VisualChatMessage, newIndex: Int) -> Unit)? = null,
    onOpenViewRequested: ((String) -> Unit)? = null,
    onMentionClick: ((org.gemini.ui.forge.model.chat.ChatMentionTag) -> Unit)? = null,
    onFallbackFlashModelRequested: ((VisualChatMessage) -> Unit)? = null,
    onSwitchProtocolRequested: ((VisualChatMessage) -> Unit)? = null,
    designMemoryContext: String? = null,
    onRecompressRequested: (() -> Unit)? = null,
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 标记当前 session 是否已有主动的用户滚动介入与是否已完成初次历史位置恢复
    var isUserInteracted by remember(sessionId) { mutableStateOf(false) }
    var hasRestoredScrollPosition by remember(sessionId) { mutableStateOf(false) }
    val cachedOffset = remember(sessionId) { ChatScrollMemoryManager.getScrollOffset(sessionId) }

    // 智能吸底锁定状态机：如果有历史缓存记录，初次挂载优先等待精准恢复位置，防止被吸底抢跑拽至末尾
    var shouldStickToBottom by remember(sessionId) { mutableStateOf(cachedOffset == null) }
    var previousScrollValue by remember(sessionId) { mutableStateOf(0) }

    // 自动吸底感知：判断当前列表是否贴近底部 (距底 <= 80px)
    val isNearBottom by remember {
        derivedStateOf {
            (scrollState.maxValue - scrollState.value) <= 80
        }
    }

    // 监听用户手势拖拽与滚动：用户主动向上翻阅历史时解除吸底锁定；滚回底部时重新恢复吸底锁定
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            isUserInteracted = true
            if (scrollState.value < previousScrollValue || !isNearBottom) {
                // 用户向上滑动或已离开底部：锁定解除，保护历史阅读体验
                shouldStickToBottom = false
            } else if (isNearBottom) {
                // 用户主动滑动贴底：恢复吸底模式
                shouldStickToBottom = true
            }
        }
        previousScrollValue = scrollState.value
    }

    // 会话进入或布局测量完成时的滚动位置记忆恢复：绝不无脑把页面拽到最极限底端
    LaunchedEffect(sessionId, scrollState.maxValue) {
        if (scrollState.maxValue > 0 && !hasRestoredScrollPosition) {
            if (cachedOffset != null) {
                // 存在历史离开位置：若用户尚未手动介入滚动，精准恢复至上次位置（若历史位置大于最大可滚动范围则取合理最大值）
                if (!isUserInteracted) {
                    val targetOffset = cachedOffset.coerceIn(0, scrollState.maxValue)
                    scrollState.scrollTo(targetOffset)
                    // 如果恢复的位置就在底部附近，则保持吸底模式；否则为用户阅读历史模式
                    shouldStickToBottom = (scrollState.maxValue - targetOffset) <= 80
                }
            } else {
                // 初次进入全新会话：默认自然停留在首屏顶部（offset = 0），让用户从上往下阅读！绝不强行拽到底部遮盖内容！
                if (!isUserInteracted) {
                    scrollState.scrollTo(0)
                    shouldStickToBottom = false
                }
            }
            hasRestoredScrollPosition = true
        }
    }

    // 核心物理高度监听：每当气泡高度撑大（如思考中卡片出现、流式排版高度增加等），只要处于吸底锁定态，毫秒级贴紧最新内容
    LaunchedEffect(scrollState.maxValue, shouldStickToBottom) {
        if (hasRestoredScrollPosition && shouldStickToBottom && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // 实时缓存用户滑动的位置：只有在历史位置恢复完成之后，才允许写回保存，防止初始未就绪时被脏数据冲刷
    LaunchedEffect(scrollState.value, scrollState.maxValue, hasRestoredScrollPosition) {
        if (hasRestoredScrollPosition && scrollState.maxValue > 0) {
            ChatScrollMemoryManager.saveScrollOffset(sessionId, scrollState.value)
        }
    }

    // 记录是否有在非底部产生的新内容
    var hasUnseenBottomContent by remember { mutableStateOf(false) }

    // 智能平滑吸底：
    // 1. 生成状态变化时（刚进入生成态时重置为吸底锁定）
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            shouldStickToBottom = true
            hasUnseenBottomContent = false
            if (scrollState.maxValue > 0) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    // 2. 新消息数量发生变化时 (自身发送提问时吸底；若是大模型完成消息入库，根据吸底锁定状态平滑贴底收尾)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg?.role == "user") {
                shouldStickToBottom = true
                scrollState.animateScrollTo(scrollState.maxValue)
                hasUnseenBottomContent = false
            } else {
                if (shouldStickToBottom) {
                    // 模型消息完整入库收尾：平滑滚动到最新底部，确保内容完整呈现
                    scrollState.animateScrollTo(scrollState.maxValue)
                    hasUnseenBottomContent = false
                } else {
                    // 用户正在上方翻看历史，不强行扯拽视口，点亮未读红点
                    hasUnseenBottomContent = !isNearBottom
                }
            }
        }
    }

    // 3. 流式打字期间：跟随吸底锁定
    LaunchedEffect(streamingText) {
        if (isGenerating && streamingText.isNotBlank()) {
            if (shouldStickToBottom) {
                scrollState.scrollTo(scrollState.maxValue)
                hasUnseenBottomContent = false
            } else {
                hasUnseenBottomContent = true
            }
        }
    }

    // 一旦滚动回底部，清除未读状态
    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            hasUnseenBottomContent = false
        }
    }

    val codeCoordinator = remember { org.gemini.ui.forge.ui.component.markdown.CodeBlockScrollCoordinator() }

    Box(modifier = modifier.fillMaxWidth()) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.medium)) {
                val maxBubbleWidth = (maxWidth * 0.85f).coerceAtLeast(320.dp)
                val currentViewportHeight = maxHeight

                androidx.compose.runtime.CompositionLocalProvider(
                    org.gemini.ui.forge.ui.component.markdown.LocalChatViewportHeight provides currentViewportHeight,
                    org.gemini.ui.forge.ui.component.markdown.LocalCodeScrollCoordinator provides codeCoordinator
                ) {
                    if (messages.isEmpty() && !isGenerating) {
                        CommonChatEmptyWelcomeView(
                            viewName = viewName,
                            viewSizeText = viewSizeText,
                            readyAssetCount = readyAssetCount,
                            onSuggestionClick = onSuggestionClick
                        )
                    } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Spacer(Modifier.height(spacing.medium))

                        val compressedCount = messages.count { it.isCompressed }
                        val effectiveMessages = if (compressedCount > 0) messages.filter { !it.isCompressed } else messages
                        val estimatedTokensSaved = messages.filter { it.isCompressed }.sumOf {
                            it.prompt.length + it.textZh.length + it.textEn.length
                        } / 2

                        // 若存在长对话结晶或设计记忆摘要，在列表最顶部以折叠卡片形式渲染
                        if (compressedCount > 0 || !designMemoryContext.isNullOrBlank()) {
                            CompressedHistoryCard(
                                compressedCount = compressedCount,
                                summaryMarkdown = designMemoryContext ?: "",
                                estimatedTokensSaved = estimatedTokensSaved,
                                onRecompress = onRecompressRequested
                            )
                        }

                        effectiveMessages.forEachIndexed { index, msg ->
                            // 智能时间分组标签 (相隔超 5 分钟自动插入居中标签)
                            val prevMsg = if (index > 0) effectiveMessages[index - 1] else null
                            val showTimeDivider = prevMsg == null || (msg.timestamp - prevMsg.timestamp > 300_000L)
                            if (showTimeDivider && msg.timestamp > 0L) {
                                ChatTimeDivider(timestamp = msg.timestamp)
                            }

                            if (msg.role == "user") {
                                CommonUserBubble(
                                    message = msg,
                                    maxBubbleWidth = maxBubbleWidth,
                                    onImageClick = onImageClick,
                                    onMentionClick = onMentionClick,
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
                                    onOpenViewRequested = onOpenViewRequested,
                                    onFallbackFlashModelRequested = onFallbackFlashModelRequested,
                                    onSwitchProtocolRequested = onSwitchProtocolRequested,
                                    messageActions = messageActions
                                )
                            }
                        }

                        // 流式打字中或正在生成气泡
                        if (isGenerating) {
                            CommonGeneratingBubble(streamingText = streamingText)
                        }

                        Spacer(Modifier.height(spacing.medium))
                    }

                    // 1. 桌面端官方原生垂直滚动条 (物理像素，零估算，绝对平滑)
                    VerticalScrollbarAdapter(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )

                    // 2. 悬浮「回到底部」直达胶囊按钮
                    ChatScrollToBottomButton(
                        visible = !isNearBottom,
                        hasNewMessage = hasUnseenBottomContent || (isGenerating && !isNearBottom),
                        onClick = {
                            shouldStickToBottom = true
                            hasUnseenBottomContent = false
                            scope.launch {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 20.dp)
                    )
                }
            }
        }
    }
}
}
}

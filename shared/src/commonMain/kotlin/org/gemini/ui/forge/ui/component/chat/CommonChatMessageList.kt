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
    onImageClick: ((String) -> Unit)? = null,
    onEditRequested: ((String) -> Unit)? = null,
    onRegenerateRequested: ((VisualChatMessage) -> Unit)? = null,
    onDeleteRequested: ((VisualChatMessage) -> Unit)? = null,
    onVariantIndexChanged: ((message: VisualChatMessage, newIndex: Int) -> Unit)? = null,
    onOpenViewRequested: ((String) -> Unit)? = null,
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 标记当前 session 是否已有主动的用户滚动介入
    var isUserInteracted by remember(sessionId) { mutableStateOf(false) }
    val cachedOffset = remember(sessionId) { ChatScrollMemoryManager.getScrollOffset(sessionId) }

    // 监听用户手势拖拽滚动：一旦用户开始主动拖拽或滚动，标记为用户已交互
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            isUserInteracted = true
        }
    }

    // 会话进入或布局测量完成时的滚动位置记忆恢复：绝不无脑把页面拽到最极限底端
    LaunchedEffect(sessionId, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            if (cachedOffset != null) {
                // 存在历史离开位置：若用户尚未手动介入滚动，精准恢复至上次位置（若历史位置大于最大可滚动范围则取合理最大值）
                if (!isUserInteracted) {
                    scrollState.scrollTo(cachedOffset.coerceIn(0, scrollState.maxValue))
                }
            } else {
                // 初次进入全新会话：默认自然停留在首屏顶部（offset = 0），让用户从上往下阅读！绝不强行拽到底部遮盖内容！
                if (!isUserInteracted) {
                    scrollState.scrollTo(0)
                }
            }
        }
    }

    // 实时缓存用户滑动的位置
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            ChatScrollMemoryManager.saveScrollOffset(sessionId, scrollState.value)
        }
    }

    // 自动吸底感知：判断当前列表是否贴近底部 (距底 <= 80px)
    val isNearBottom by remember {
        derivedStateOf {
            (scrollState.maxValue - scrollState.value) <= 80
        }
    }

    // 记录是否有在非底部产生的新内容
    var hasUnseenBottomContent by remember { mutableStateOf(false) }

    // 智能平滑吸底：
    // 1. 新消息数量发生变化时 (自身发送提问时吸底；若是大模型生成的大卡片/排版消息，不无脑直接滚到底部遮盖代码开头)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg?.role == "user") {
                scrollState.animateScrollTo(scrollState.maxValue)
                hasUnseenBottomContent = false
            } else {
                // 模型生成的超长消息：若之前贴近底部，不直接拉到极限底端，避免把下方的画布按钮直接拽出来而跳过了代码开头
                hasUnseenBottomContent = !isNearBottom
            }
        }
    }

    // 2. 流式打字期间：仅当贴近底部时持续吸底；若用户在上方翻看历史，不强行扯拽视口并点亮红点
    LaunchedEffect(streamingText) {
        if (isGenerating && streamingText.isNotBlank()) {
            if (isNearBottom) {
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

    // 外层手势协调器：统一拦截外层滚轮并在吸顶时移交代码块消费，支持惯性 Fling 接力
    val nestedScrollConnection = remember(codeCoordinator, scrollState) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                val deltaY = available.y
                // 动态记录进入吸顶时刻的外层偏移
                if (codeCoordinator.isReadyToScrollCode && codeCoordinator.targetOuterStickOffset < 0) {
                    codeCoordinator.targetOuterStickOffset = scrollState.value
                    org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "STICK_POINT_LOCKED -> outerStickOffset=${scrollState.value}")
                }
                if (codeCoordinator.shouldTrapScroll(deltaY, scrollState.value)) {
                    val consumed = codeCoordinator.consumeScroll(deltaY)
                    return androidx.compose.ui.geometry.Offset(0f, consumed)
                }
                org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "PASS_TO_OUTER deltaY=$deltaY | outerScroll=${scrollState.value}/${scrollState.maxValue}")
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val flingY = available.y
                val consumedY = codeCoordinator.consumeFlingNonBlocking(flingY, scrollState.value)
                if (consumedY != 0f) {
                    return androidx.compose.ui.unit.Velocity(0f, consumedY)
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInWindow()
                            codeCoordinator.viewportTopInWindow = bounds.top
                            codeCoordinator.viewportBottomInWindow = bounds.bottom
                        }
                        .nestedScroll(nestedScrollConnection)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Spacer(Modifier.height(spacing.medium))

                        messages.forEachIndexed { index, msg ->
                            // 智能时间分组标签 (相隔超 5 分钟自动插入居中标签)
                            val prevMsg = if (index > 0) messages[index - 1] else null
                            val showTimeDivider = prevMsg == null || (msg.timestamp - prevMsg.timestamp > 300_000L)
                            if (showTimeDivider && msg.timestamp > 0L) {
                                ChatTimeDivider(timestamp = msg.timestamp)
                            }

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
                                    onOpenViewRequested = onOpenViewRequested,
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

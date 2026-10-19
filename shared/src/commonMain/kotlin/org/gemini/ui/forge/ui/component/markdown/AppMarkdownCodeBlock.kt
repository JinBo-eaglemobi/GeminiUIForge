package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * Markdown 高质感深色代码块容器组件 (一文件一 Composable 规范)。
 *
 * 核心特性 (顶层物理吸附 Header 与黄金三阶段接力)：
 * 1. 【顶层物理吸附 Header】：Header 栏通过 [zIndex = 10f] 永久悬停置顶在代码框最上方，
 *    代码内容区域在下方独立滚动，顶部预留 40dp 呼吸边距，无论内容滚到哪一行，
 *    顶部的折叠头（JSON 标签、复制代码、折叠按钮）100% 绝对悬停在视口顶端可见！
 * 2. 【阶段一：整体上移】：代码框未撑满视口前，内部滚动完全禁用，滚轮 100% 推动外层页面整体上移；
 * 3. 【阶段二：吸顶接力】：代码框底部到位充满视口瞬间，折叠头吸附在视口顶端，激活代码内滚；
 * 4. 【阶段三：到底推上按钮】：代码内容滚到最后一行后，手势穿透解锁，页面恢复整体上移推上下方按钮；
 * 5. 内部挂载 [VerticalScrollbarAdapter] 跨端原生滚动条。
 */
@Composable
fun AppMarkdownCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var isCollapsed by remember { mutableStateOf(false) }

    val trimmedCode = remember(code) { code.trimEnd() }
    val lineCount = remember(trimmedCode) { trimmedCode.count { it == '\n' } + 1 }

    // 动态感知外层视口高度：长代码展开时撑满视口全部垂直净空间（留出 8dp 边距）
    val currentViewportHeight = LocalChatViewportHeight.current
    val dynamicCodeBlockHeight = remember(currentViewportHeight, lineCount) {
        // 如果行数较多（超过 15 行的排版长代码），直接填满当前视口，确保底部按钮完全在视口下方不可见！
        if (lineCount > 15) {
            (currentViewportHeight - 8.dp).coerceAtLeast(460.dp)
        } else {
            (currentViewportHeight - 16.dp).coerceAtLeast(320.dp)
        }
    }

    // 内部垂直与水平原生滚动状态
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // 接入外层统一协调器
    val coordinator = LocalCodeScrollCoordinator.current

    val copyAction: () -> Unit = {
        copy(trimmedCode, "代码已复制到剪贴板")
        isCopied = true
        scope.launch {
            delay(1500L)
            isCopied = false
        }
    }

    val toggleCollapseAction: () -> Unit = {
        isCollapsed = !isCollapsed
    }

    // 将本代码块的滚动状态注册到外层协调器中
    LaunchedEffect(verticalScrollState, isCollapsed) {
        if (!isCollapsed) {
            coordinator.activeCodeScrollState = verticalScrollState
        } else {
            coordinator.safeRelease(verticalScrollState)
        }
    }

    DisposableEffect(verticalScrollState) {
        onDispose {
            coordinator.safeRelease(verticalScrollState)
        }
    }

    // 代码块专用深色背景与细微边框
    val codeBackgroundColor = Color(0xFF1E1E24)
    val codeBorderColor = Color(0xFF2E323D)
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall)
            .onGloballyPositioned { coordinates ->
                if (!isCollapsed) {
                    val bounds = coordinates.boundsInWindow()
                    coordinator.activeBlockTopInWindow = bounds.top
                    coordinator.activeBlockBottomInWindow = bounds.bottom
                }
            },
        shape = shape,
        color = codeBackgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, codeBorderColor),
        shadowElevation = 2.dp
    ) {
        if (isCollapsed) {
            // 折叠态：仅展示单行紧凑 Header
            AppMarkdownCodeHeader(
                language = language,
                lineCount = lineCount,
                isCollapsed = true,
                isCopied = isCopied,
                onToggleCollapse = toggleCollapseAction,
                onCopyClick = copyAction
            )
        } else {
            // 展开态：采用顶层绝对吸附架构 (Header 在 Z 轴最顶层悬停，代码内容在下方滚动)
            // 针对长代码块显式撑满当前视口可用高度，彻底将下方按钮排挤在视口外部！
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (lineCount > 15) {
                            Modifier.height(dynamicCodeBlockHeight)
                        } else {
                            Modifier.heightIn(max = dynamicCodeBlockHeight)
                        }
                    )
            ) {
                // 1. 【底层：可滚动的代码文本展示区】(顶部 padding 38dp 为悬停 Header 留出完全通道)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 黄金门禁：未到位前 enabled = false，100% 整体上移页面！到位后才激活内滚！
                        .verticalScroll(verticalScrollState, enabled = coordinator.isReadyToScrollCode)
                        .horizontalScroll(horizontalScrollState)
                        .padding(top = 40.dp, start = spacing.medium, end = spacing.medium, bottom = spacing.medium)
                ) {
                    Text(
                        text = trimmedCode,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFABB2BF)
                        )
                    )
                }

                // 2. 【顶层：永久吸顶悬停折叠头 Header】(置顶悬停在代码框最顶部，无论代码怎么滚，100% 绝对可见！)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(10f),
                    color = Color(0xFF181A1F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, codeBorderColor),
                    shadowElevation = 4.dp
                ) {
                    AppMarkdownCodeHeader(
                        language = language,
                        lineCount = lineCount,
                        isCollapsed = false,
                        isCopied = isCopied,
                        onToggleCollapse = toggleCollapseAction,
                        onCopyClick = copyAction
                    )
                }

                // 3. 跨端垂直滚动条 (仅在代码内容超出可视高度出现滚动时呈现)
                if (verticalScrollState.maxValue > 0) {
                    VerticalScrollbarAdapter(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 44.dp, bottom = 4.dp, end = 2.dp),
                        scrollState = verticalScrollState
                    )
                }
            }
        }
    }
}

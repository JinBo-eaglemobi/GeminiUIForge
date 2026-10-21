package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.math.abs

/**
 * 跨平台优雅 Markdown 代码块呈现组件 (AppMarkdownCodeBlock)。
 *
 * 【深度对标 OpenCode 桌面端标准规范】：
 * 1. 独立视口高度上限：代码框高度拥有独立合理的约束 (视口 70%)，绝不过度侵占视口，绝不盲目做全局像素强锁；
 * 2. 就地嵌套手势边界接力 (Local Nested Scroll Dispatch)：
 *    - 当手势作用在代码框上时，代码框优先滚动内部多行文本；
 *    - 只有当代码内部滚到最顶端(0)或最底端(maxValue)无法再滚动时，多余的手势量自然传递给外层页面；
 *    - 代码框无论前后有多少段文案或按钮，完全不需要感知，天然实现“内滚完才推页面”；
 * 3. 吸顶 Header 置顶常驻：Header 在 Z 轴最顶层悬停，代码内容在下方滚动，无论怎么滑永远可见！
 */
@Composable
fun AppMarkdownCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var isCollapsed by remember { mutableStateOf(false) }

    val trimmedCode = remember(code) { code.trimEnd() }
    val lineCount = remember(trimmedCode) { trimmedCode.count { it == '\n' } + 1 }

    // 动态感知外层视口高度：长代码最大高度限制为当前视口的 70% (最低 260dp, 最高 560dp)
    val currentViewportHeight = LocalChatViewportHeight.current
    val maxCodeBlockHeight = remember(currentViewportHeight, lineCount) {
        if (lineCount > 10) {
            (currentViewportHeight * 0.70f).coerceIn(260.dp, 560.dp)
        } else {
            // 短代码行数很少时自适应其内容高度
            (currentViewportHeight * 0.45f).coerceIn(160.dp, 360.dp)
        }
    }

    // 内部垂直与水平原生滚动状态
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

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

    // 【OpenCode 原生嵌套接力连接器】：就地优先消费代码内滚，两端边界自然溢出传递给外层列表
    val localNestedScrollConnection = remember(verticalScrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                // 向上滑 (deltaY < 0，看后面代码)：只要内部还有内容没滚完，100% 优先就地消费！
                if (deltaY < 0f && verticalScrollState.value < verticalScrollState.maxValue) {
                    val remaining = (verticalScrollState.maxValue - verticalScrollState.value).toFloat()
                    val toConsume = minOf(abs(deltaY), remaining)
                    verticalScrollState.dispatchRawDelta(toConsume)
                    return Offset(0f, -toConsume)
                }
                // 向下滑 (deltaY > 0，回看上面代码)：只要内部还没回到第 0 行，100% 优先就地消费！
                if (deltaY > 0f && verticalScrollState.value > 0) {
                    val remaining = verticalScrollState.value.toFloat()
                    val toConsume = minOf(deltaY, remaining)
                    verticalScrollState.dispatchRawDelta(-toConsume)
                    return Offset(0f, toConsume)
                }
                // 内部已见顶或见底：零消费，自然传递给外层页面继续滚动！
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val flingY = available.y
                // 向上甩动且未见底：冲量全量注入代码内部滚动
                if (flingY < 0f && verticalScrollState.value < verticalScrollState.maxValue) {
                    val remainingPx = (verticalScrollState.maxValue - verticalScrollState.value).toFloat()
                    val toConsumePx = minOf(abs(flingY) * 0.25f, remainingPx)
                    verticalScrollState.dispatchRawDelta(toConsumePx)
                    // 若冲量已被完全吸收或还有剩余代码，吞掉外层冲量
                    return if (verticalScrollState.value < verticalScrollState.maxValue) {
                        available
                    } else {
                        Velocity(0f, -toConsumePx)
                    }
                }
                // 向下甩动且未见顶：冲量全量注入代码内部回滚
                if (flingY > 0f && verticalScrollState.value > 0) {
                    val remainingPx = verticalScrollState.value.toFloat()
                    val toConsumePx = minOf(flingY * 0.25f, remainingPx)
                    verticalScrollState.dispatchRawDelta(-toConsumePx)
                    return if (verticalScrollState.value > 0) {
                        available
                    } else {
                        Velocity(0f, toConsumePx)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val codeBackgroundColor = Color(0xFF1E1E24)
    val codeBorderColor = Color(0xFF2E323D)
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
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
            // 展开态：对标 OpenCode 采用局部嵌套接力与独立 maxHeight 容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCodeBlockHeight)
                    .nestedScroll(localNestedScrollConnection)
            ) {
                // 1. 【代码内容滚动区】(顶部预留 40dp 给置顶悬停 Header)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
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

                // 2. 【顶层置顶 Header】(在代码块自身最上方，吸附可见)
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

                // 3. 内部滚动条 (仅在超长溢出时呈现)
                if (verticalScrollState.maxValue > 0) {
                    VerticalScrollbarAdapter(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 42.dp, bottom = 4.dp, end = 2.dp),
                        scrollState = verticalScrollState
                    )
                }
            }
        }
    }
}

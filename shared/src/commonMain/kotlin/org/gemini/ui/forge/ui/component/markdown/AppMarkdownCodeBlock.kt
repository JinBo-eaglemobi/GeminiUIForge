package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.code_block_copied_toast
import geminiuiforge.composeapp.generated.resources.code_block_fold_line_tip
import geminiuiforge.composeapp.generated.resources.code_block_unfold_line_tip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.common.HorizontalScrollbarAdapter
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.code.CodeScopeFoldingEngine
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 跨平台零卡顿高性能 Markdown 代码块呈现组件 (AppMarkdownCodeBlock)。
 *
 * 【核心性能与交互规范】：
 * 1. 双单节点合并渲染（Constant O(1) Nodes）：
 *    - 行号列合并为 1 个右对齐 Text 节点（DisableSelection），代码正文与已折叠行尾内联胶囊合并为 1 个 AnnotatedString Text 节点；
 *    - 即使面对数千行 JSON / 代码，UI 树也恒定仅 2 个 Text 节点，彻底杜绝逐行 Row 重组掉帧；
 * 2. 基于 TextLayoutResult 的视口级虚拟裁剪（Viewport Culling）：
 *    - 仅针对当前滚动可视窗口 `[firstVisibleLine .. lastVisibleLine]` 内的可折叠行实例化 `CodeLineFoldHandle` 微型手柄；
 * 3. 就地嵌套手势边界接力 (Local Nested Scroll Dispatch) 与吸顶常驻 Header。
 */
@Composable
fun AppMarkdownCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val copy = rememberClipboardAction()
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var isCollapsed by remember { mutableStateOf(false) }

    val copiedToastMsg = stringResource(Res.string.code_block_copied_toast)

    val trimmedCode = remember(code, language) {
        CodeScopeFoldingEngine.formatIfCompactJson(code.trimEnd(), language)
    }
    val lines = remember(trimmedCode) { trimmedCode.lines() }
    val lineCount = lines.size

    // 1. 解析多语言可折叠作用域（原始类型数组栈，零装箱，200 万字符上限）
    val scopesByStartLine = remember(trimmedCode, language) {
        CodeScopeFoldingEngine.analyzeScopes(trimmedCode, lines, language)
    }

    // 2. 当前已折叠的起始行索引集合（0-based，超长代码自动折叠内部大型子作用域）
    val foldedStartLines = remember(trimmedCode, scopesByStartLine) {
        mutableStateSetOf<Int>().apply {
            addAll(CodeScopeFoldingEngine.computeInitialFoldedLines(lineCount, scopesByStartLine))
        }
    }
    val foldedSetSnapshot = foldedStartLines.toSet()

    // 3. 构建单节点合并渲染投影
    val projection = remember(lines, scopesByStartLine, foldedSetSnapshot) {
        CodeScopeFoldingEngine.buildProjection(lines, scopesByStartLine, foldedSetSnapshot)
    }

    val hasFoldableScopes = scopesByStartLine.isNotEmpty()
    val areAllScopesFolded = hasFoldableScopes && foldedStartLines.size >= scopesByStartLine.size

    // 动态感知外层视口高度
    val currentViewportHeight = LocalChatViewportHeight.current
    val maxCodeBlockHeight = remember(currentViewportHeight, lineCount, spacing) {
        if (lineCount > 10) {
            (currentViewportHeight * 0.70f).coerceIn(spacing.codeBlockTallMinHeight, spacing.codeBlockTallMaxHeight)
        } else {
            (currentViewportHeight * 0.45f).coerceIn(spacing.codeBlockShortMinHeight, spacing.codeBlockShortMaxHeight)
        }
    }

    // 内部垂直与水平原生滚动状态
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val coordinator = LocalCodeScrollCoordinator.current

    val copyAction: () -> Unit = {
        copy(trimmedCode, copiedToastMsg)
        isCopied = true
        scope.launch {
            delay(1500L)
            isCopied = false
        }
    }

    val toggleCollapseAction: () -> Unit = {
        isCollapsed = !isCollapsed
    }

    val toggleAllScopesAction: () -> Unit = {
        if (areAllScopesFolded) {
            foldedStartLines.clear()
        } else {
            foldedStartLines.addAll(scopesByStartLine.keys)
        }
    }

    val toggleSingleScope: (Int) -> Unit = { startLine ->
        if (startLine in foldedStartLines) {
            foldedStartLines.remove(startLine)
        } else {
            foldedStartLines.add(startLine)
        }
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
                if (deltaY < 0f && verticalScrollState.value < verticalScrollState.maxValue) {
                    val remaining = (verticalScrollState.maxValue - verticalScrollState.value).toFloat()
                    val toConsume = minOf(abs(deltaY), remaining)
                    verticalScrollState.dispatchRawDelta(toConsume)
                    return Offset(0f, -toConsume)
                }
                if (deltaY > 0f && verticalScrollState.value > 0) {
                    val remaining = verticalScrollState.value.toFloat()
                    val toConsume = minOf(deltaY, remaining)
                    verticalScrollState.dispatchRawDelta(-toConsume)
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val flingY = available.y
                if (flingY < 0f && verticalScrollState.value < verticalScrollState.maxValue) {
                    val remainingPx = (verticalScrollState.maxValue - verticalScrollState.value).toFloat()
                    val toConsumePx = minOf(abs(flingY) * 0.25f, remainingPx)
                    verticalScrollState.dispatchRawDelta(toConsumePx)
                    return if (verticalScrollState.value < verticalScrollState.maxValue) {
                        available
                    } else {
                        Velocity(0f, -toConsumePx)
                    }
                }
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

    var lineLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var codeLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val maxViewportPx = with(density) { maxCodeBlockHeight.toPx() }
    val handleSizePx = with(density) { spacing.iconButtonMicroSize.toPx() }

    // 4. 视口级虚拟裁剪：仅筛选当前滚动可视窗口内的可折叠行
    val viewportVisibleScopes by remember(projection, verticalScrollState, lineLayoutResult, maxViewportPx) {
        derivedStateOf {
            val layout = lineLayoutResult ?: return@derivedStateOf emptyList()
            if (projection.visibleScopes.isEmpty() || layout.lineCount == 0) return@derivedStateOf emptyList()
            val scrollY = (verticalScrollState.value.toFloat() - handleSizePx).coerceAtLeast(0f)
            val viewportBottom = verticalScrollState.value.toFloat() + maxViewportPx + handleSizePx
            val firstLine = layout.getLineForVerticalPosition(scrollY).coerceIn(0, layout.lineCount - 1)
            val lastLine = layout.getLineForVerticalPosition(viewportBottom).coerceIn(firstLine, layout.lineCount - 1)
            projection.visibleScopes.entries.filter { (visLine, _) ->
                visLine in firstLine..lastLine
            }
        }
    }

    val codeBackgroundColor = Color(0xFF1E1E24)
    val codeBorderColor = Color(0xFF2E323D)
    val shape = RoundedCornerShape(spacing.codeBlockCornerRadius)
    val sharedCodeTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        shape = shape,
        color = codeBackgroundColor,
        border = BorderStroke(1.dp, codeBorderColor),
        shadowElevation = 2.dp
    ) {
        if (isCollapsed) {
            AppMarkdownCodeHeader(
                language = language,
                lineCount = lineCount,
                isCollapsed = true,
                isCopied = isCopied,
                onToggleCollapse = toggleCollapseAction,
                onCopyClick = copyAction,
                hasFoldableScopes = hasFoldableScopes,
                areAllScopesFolded = areAllScopesFolded,
                onToggleAllScopes = toggleAllScopesAction
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCodeBlockHeight)
                    .nestedScroll(localNestedScrollConnection)
            ) {
                // 1. 【代码内容与左侧固定 Gutter 同步纵向滚动区】
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState)
                        .padding(
                            top = spacing.codeBlockHeaderOverlaySpace,
                            bottom = spacing.medium,
                            end = spacing.medium
                        )
                ) {
                    // 1.1 左侧 Gutter 区（折叠箭头槽 + 右对齐行号，不随横向滚动移动，禁止被选区复制）
                    DisableSelection {
                        Row(
                            modifier = Modifier.padding(start = spacing.extraSmall),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 视口裁剪后的折叠手柄槽位
                            if (hasFoldableScopes) {
                                Box(modifier = Modifier.width(spacing.iconButtonMicroSize)) {
                                    val layout = lineLayoutResult
                                    if (layout != null) {
                                        viewportVisibleScopes.forEach { (visLine, scopeItem) ->
                                            if (visLine < layout.lineCount) {
                                                val lineTop = layout.getLineTop(visLine)
                                                val lineBottom = layout.getLineBottom(visLine)
                                                val offsetY = ((lineTop + lineBottom - handleSizePx) * 0.5f).roundToInt()
                                                val isFolded = scopeItem.startLine in foldedStartLines
                                                val handleTip = if (isFolded) {
                                                    stringResource(Res.string.code_block_unfold_line_tip, scopeItem.hiddenLineCount)
                                                } else {
                                                    stringResource(Res.string.code_block_fold_line_tip, scopeItem.hiddenLineCount)
                                                }
                                                CodeLineFoldHandle(
                                                    isFolded = isFolded,
                                                    tooltip = handleTip,
                                                    onToggle = { toggleSingleScope(scopeItem.startLine) },
                                                    modifier = Modifier.offset { IntOffset(0, offsetY) }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.width(spacing.small))
                            }

                            // 单节点合并行号列
                            Text(
                                text = projection.lineNumbersText,
                                style = sharedCodeTextStyle.copy(
                                    color = Color(0xFF5C6370),
                                    textAlign = TextAlign.End
                                ),
                                softWrap = false,
                                onTextLayout = { lineLayoutResult = it },
                                modifier = Modifier.padding(end = spacing.small)
                            )
                        }
                    }

                    // 1.2 右侧单节点合并代码正文区（支持跨行纯净选择复制 + 独立横向滚动 + 点击内联折叠胶囊展开）
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Text(
                                text = projection.codeAnnotatedText,
                                style = sharedCodeTextStyle.copy(color = Color(0xFFABB2BF)),
                                softWrap = false,
                                onTextLayout = { codeLayoutResult = it },
                                modifier = Modifier.pointerInput(projection.codeAnnotatedText) {
                                    detectTapGestures { tapOffset ->
                                        val layout = codeLayoutResult ?: return@detectTapGestures
                                        val charOffset = layout.getOffsetForPosition(tapOffset)
                                        val hitBadge = projection.codeAnnotatedText
                                            .getStringAnnotations(
                                                tag = CodeScopeFoldingEngine.FOLD_BADGE_TAG,
                                                start = charOffset,
                                                end = charOffset
                                            )
                                            .firstOrNull()
                                        val startLine = hitBadge?.item?.toIntOrNull()
                                        if (startLine != null) {
                                            toggleSingleScope(startLine)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. 【顶层置顶 Header】(在代码块自身最上方，吸附可见)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(10f),
                    color = Color(0xFF181A1F),
                    border = BorderStroke(1.dp, codeBorderColor),
                    shadowElevation = 4.dp
                ) {
                    AppMarkdownCodeHeader(
                        language = language,
                        lineCount = lineCount,
                        isCollapsed = false,
                        isCopied = isCopied,
                        onToggleCollapse = toggleCollapseAction,
                        onCopyClick = copyAction,
                        hasFoldableScopes = hasFoldableScopes,
                        areAllScopesFolded = areAllScopesFolded,
                        onToggleAllScopes = toggleAllScopesAction
                    )
                }

                // 3. 内部滚动条 (仅在超长溢出时呈现)
                if (verticalScrollState.maxValue > 0) {
                    VerticalScrollbarAdapter(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = spacing.codeBlockHeaderOverlaySpace + 2.dp,
                                bottom = spacing.extraSmall,
                                end = 2.dp
                            ),
                        scrollState = verticalScrollState
                    )
                }

                if (horizontalScrollState.maxValue > 0) {
                    HorizontalScrollbarAdapter(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = spacing.extraSmall,
                                end = spacing.medium,
                                bottom = 2.dp
                            ),
                        scrollState = horizontalScrollState
                    )
                }
            }
        }
    }
}

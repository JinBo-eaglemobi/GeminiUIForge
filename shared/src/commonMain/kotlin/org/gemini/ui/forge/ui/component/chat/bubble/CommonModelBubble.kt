package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.getSelectedTextOrNull
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.service.chat.ChatErrorClassifier
import org.gemini.ui.forge.ui.component.markdown.AppMarkdown
import org.gemini.ui.forge.ui.component.tip
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全场景通用 AI 模型回复气泡组件 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 鼠标文字自由划选与复制，Initial 阶段拦截右键彻底阻断原生系统 copy 弹窗；
 * 2. 回复底部最开头展示：回复模型标签、耗时、Token 统计情况；
 * 3. 回复底部最后展示：单问多答子对话版本翻页条 (< 1/3 >) 以及一键复制所有文案图标；
 * 4. 思维链思考过程折叠/展开；
 * 5. 支持右键/长按弹出高级二级操作菜单（复制、重新生成、删除本轮）。
 */
@Composable
fun CommonModelBubble(
    message: VisualChatMessage,
    maxBubbleWidth: Dp,
    onImageClick: ((String) -> Unit)? = null,
    onVariantIndexChanged: ((newIndex: Int) -> Unit)? = null,
    onRegenerateRequested: ((VisualChatMessage) -> Unit)? = null,
    onDeleteRequested: ((VisualChatMessage) -> Unit)? = null,
    onOpenViewRequested: ((String) -> Unit)? = null,
    onFallbackFlashModelRequested: ((VisualChatMessage) -> Unit)? = null,
    onSwitchProtocolRequested: ((VisualChatMessage) -> Unit)? = null,
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val copy = rememberClipboardAction()
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var selectedTextSnapshot by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val modelText = message.effectiveText
    val variants = message.variants
    val hasVariants = variants.size > 1
    val currentVariantIdx = message.activeVariantIndex.coerceIn(0, (variants.size - 1).coerceAtLeast(0))

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp).tip("Gemini AI 助手")
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
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                        // 1. 思维链思考过程 (Thought Chain，高阶折叠与耗时呈现)
                        if (!message.thought.isNullOrBlank()) {
                            ThinkingProcessCard(
                                thought = message.thought,
                                isThinking = false,
                                durationMs = message.durationMs,
                                modifier = Modifier.padding(bottom = spacing.small)
                            )
                        }

                        // 1.5 工具调用状态与结果卡片
                        if (message.toolCalls.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(bottom = spacing.small),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                message.toolCalls.forEach { record ->
                                    ToolExecutionStatusCard(record = record)
                                }
                            }
                        }

                        // 2. 模型核心正文 或 专属错误卡片
                        // 必须以当前选中的具体回答版本 (modelText) 判定，严禁被历史整体消息的 isError 误伤成功版本
                        // 核心防误判铁律：若本轮消息成功执行了系统/MCP工具调用，正文为空属于合法正常现象，严禁误报为服务异常卡片
                        val hasToolCalls = message.toolCalls.isNotEmpty()
                        val isCurrentVariantFailed = if (hasToolCalls) {
                            message.isError && message.toolCalls.all { !it.isSuccess }
                        } else {
                            (modelText.isBlank() && message.thought.isNullOrBlank()) ||
                                modelText.startsWith("⚠️") ||
                                (message.isError && modelText.isBlank())
                        }
                        if (isCurrentVariantFailed) {
                            val errDetail = message.errorMessage?.ifBlank { null }
                                ?: modelText.ifBlank { null }
                                ?: "未捕获到服务正文（可点击顶部工具栏查看网络通信档案核验原始响应）"
                            val classified = remember(errDetail) { ChatErrorClassifier.classify(errDetail) }
                            ChatErrorActionCard(
                                classifiedError = classified,
                                onRetry = { onRegenerateRequested?.invoke(message) },
                                onFallbackFlashModel = onFallbackFlashModelRequested?.let { cb -> { cb(message) } },
                                onSwitchProtocol = onSwitchProtocolRequested?.let { cb -> { cb(message) } }
                            )
                        } else {
                            SelectionContainer {
                                AppMarkdown(
                                    content = modelText
                                )
                            }

                            // 若该消息包含界面排版方案，紧跟在 Markdown (含 ```json 代码框) 下方提供画布快捷入口
                            if (modelText.contains("已完成界面排版规划方案") || modelText.contains("apply_view_layout")) {
                                val viewName = Regex("""(?:\\?"|")viewName(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""").find(message.prompt)?.groupValues?.get(1)
                                    ?: Regex("""\[([^\]]+)\]""").find(modelText)?.groupValues?.get(1)
                                    ?: "FootballSlotMain"

                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { onOpenViewRequested?.invoke(viewName) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("在画布中打开视图 [$viewName]", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. 模型生成的图片展示
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
                    }
                }

                // 右键上下文菜单 (阻断原生系统 copy 弹窗，跟随光标偏移)
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
                                copy(selText, "已复制选中内容")
                                showContextMenu = false
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(if (!selText.isNullOrBlank()) "复制全部文案" else "复制全部回复文案") },
                        leadingIcon = { Icon(Icons.Default.CopyAll, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            copy(modelText, "已复制全部回复文案")
                            showContextMenu = false
                        }
                    )

                    if (onRegenerateRequested != null) {
                        DropdownMenuItem(
                            text = { Text("重新生成回答") },
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

            // 底部元数据状态条 (紧凑跟随于气泡左下方，零横向跨屏割裂)
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. 模型名称胶囊
                val displayModel = message.modelName?.ifBlank { "Gemini" } ?: "Gemini"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = displayModel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }

                // 2. 推理耗时
                message.durationMs?.let { ms ->
                    val timeStr = if (ms >= 1000L) {
                        "${ms / 1000}.${(ms % 1000) / 100}s"
                    } else {
                        "${ms}ms"
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 3. Token 消耗情况
                val tokenCount = message.totalTokens ?: message.candidatesTokens
                if (tokenCount != null && tokenCount > 0) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "$tokenCount tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 4. 子对话版本翻页条 (< 1/3 >)
                if (hasVariants) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                        modifier = Modifier.height(24.dp).padding(start = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentVariantIdx > 0) {
                                        onVariantIndexChanged?.invoke(currentVariantIdx - 1)
                                    }
                                },
                                enabled = currentVariantIdx > 0,
                                modifier = Modifier.size(18.dp).tip("查看上一个回答版本")
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "上一版",
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Text(
                                text = "${currentVariantIdx + 1}/${variants.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (currentVariantIdx < variants.size - 1) {
                                        onVariantIndexChanged?.invoke(currentVariantIdx + 1)
                                    }
                                },
                                enabled = currentVariantIdx < variants.size - 1,
                                modifier = Modifier.size(18.dp).tip("查看下一个回答版本")
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "下一版",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                messageActions(message)

                // 5. 一键复制所有文案图标 (紧贴在翻页与信息右侧)
                IconButton(
                    onClick = { copy(modelText, "已复制全部回复文案") },
                    modifier = Modifier.size(20.dp).tip("一键复制全部文案")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制全部文案",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

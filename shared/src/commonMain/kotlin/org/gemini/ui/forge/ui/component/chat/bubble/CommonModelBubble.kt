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
import org.gemini.ui.forge.ui.component.tip
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
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val copy = rememberClipboardAction()
    var isThoughtExpanded by remember { mutableStateOf(false) }
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
                        // 1. 思维链思考过程 (Thought Chain，可折叠)
                        if (!message.thought.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { isThoughtExpanded = !isThoughtExpanded },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Psychology,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isThoughtExpanded) "收起思考过程" else "查看深度思考过程",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Icon(
                                            if (isThoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    AnimatedVisibility(visible = isThoughtExpanded) {
                                        Text(
                                            text = message.thought,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 模型核心正文 或 专属错误卡片
                        // 必须以当前选中的具体回答版本 (modelText) 判定，严禁被历史整体消息的 isError 误伤成功版本
                        val isCurrentVariantFailed = (modelText.isBlank() && message.thought.isNullOrBlank()) ||
                            modelText.startsWith("⚠️") ||
                            (message.isError && modelText.isBlank())
                        if (isCurrentVariantFailed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = "错误",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "服务响应异常 (API Error)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    val errDetail = message.errorMessage?.ifBlank { null }
                                        ?: modelText.ifBlank { null }
                                        ?: "未捕获到服务正文（可点击顶部工具栏查看网络通信档案核验原始响应）"
                                    Spacer(Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = errDetail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onRegenerateRequested?.invoke(message) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("重新生成", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            SelectionContainer {
                                Text(
                                    text = modelText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
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

            // 底部元数据状态条 (外置于气泡卡片外部，实现与用户气泡的对称美学)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 最开头 (左侧)：当前回复模型名称、耗时与 Token 消耗情况
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // 模型名称胶囊
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

                    // 推理耗时
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

                    // Token 消耗情况
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
                }

                Spacer(Modifier.width(spacing.small))

                // 在最后 (右侧)：单问多答子对话翻页条 + 外部插槽 + 一键复制所有文案图标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // 子对话版本翻页条 (< 1/3 >)
                    if (hasVariants) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                            modifier = Modifier.height(24.dp).padding(end = 4.dp)
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

                    // 一键复制所有文案图标
                    IconButton(
                        onClick = { copy(modelText, "已复制全部回复文案") },
                        modifier = Modifier
                            .size(20.dp)
                            .tip("一键复制全部文案")
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
}

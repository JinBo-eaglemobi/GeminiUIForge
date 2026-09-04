package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
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
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.isFileExists
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室中间对话气泡流组件（完整元数据信息 + 文件夹打开 + 破裂图占位）
 */
@Composable
fun StudioChatMessageList(
    messages: List<VisualChatMessage>,
    isGenerating: Boolean,
    statusLog: String,
    streamingText: String,
    pendingCount: Int = 1,
    currentVariantImageUri: String? = null,
    onImageClick: (String) -> Unit,
    onApplyImage: (String) -> Unit,
    onVariantImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isGenerating, streamingText) {
        if (messages.isNotEmpty() || isGenerating) {
            val targetIdx = (messages.size + if (isGenerating) 1 else 0) - 1
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = spacing.medium)) {
        if (messages.isEmpty() && !isGenerating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(spacing.small))
                    Text(
                        text = stringResource(Res.string.ai_studio_empty_chat_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = spacing.medium)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.role == "user") {
                        UserMessageBubble(msg, onImageClick = onImageClick)
                    } else {
                        ModelMessageBubble(
                            message = msg,
                            currentVariantImageUri = currentVariantImageUri,
                            onImageClick = onImageClick,
                            onApply = onApplyImage,
                            onVariant = onVariantImage
                        )
                    }
                }

                // 正在生成状态与占位图片骨架屏卡片
                if (isGenerating) {
                    item {
                        GeneratingSkeletonBubble(
                            statusLog = statusLog,
                            streamingText = streamingText,
                            pendingCount = pendingCount
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用户指令消息气泡（右侧头像 + 自适应宽度图文一体）
 */
@Composable
private fun UserMessageBubble(
    msg: VisualChatMessage,
    onImageClick: (String) -> Unit
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        // 气泡主体
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = AppShapes.medium,
            modifier = Modifier.widthIn(max = 640.dp).wrapContentWidth(Alignment.End)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 参考图展示
                if (msg.inputImageUris.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        msg.inputImageUris.forEach { uri ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(AppShapes.small)
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .clickable { onImageClick(uri) }
                                    .tip("点击放大查看参考图"),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (msg.textZh.isNotBlank()) {
                    Text(
                        text = msg.textZh,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (msg.textEn.isNotBlank() && msg.textEn != msg.textZh) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.textEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.width(spacing.small))

        // 用户头像
        Surface(
            modifier = Modifier.size(34.dp).clip(AppShapes.small),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * AI 模型回复与生成图片气泡（包含物理路径检测、在文件夹中显示与破裂占位）
 */
@Composable
private fun ModelMessageBubble(
    message: VisualChatMessage,
    currentVariantImageUri: String?,
    onImageClick: (String) -> Unit,
    onApply: (String) -> Unit,
    onVariant: (String) -> Unit
) {
    val spacing = LocalAppSpacing.current
    val applyTip = stringResource(Res.string.ai_studio_btn_apply)
    val variantTip = stringResource(Res.string.ai_studio_btn_variant)
    val isCurrentVariant = message.generatedImageUri != null && message.generatedImageUri == currentVariantImageUri

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI 头像
        Surface(
            modifier = Modifier.size(34.dp).clip(AppShapes.small),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(spacing.small))

        // 气泡主体
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = AppShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.widthIn(max = 680.dp).wrapContentWidth(Alignment.Start)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 完整思考过程/文本解析
                Text(
                    text = message.textZh.ifBlank { message.textEn },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!message.generatedImageUri.isNullOrBlank()) {
                    val imgUri = message.generatedImageUri
                    val isFileExistsState = produceState(initialValue = true, imgUri) {
                        value = isFileExists(imgUri) || imgUri.startsWith("data:image")
                    }
                    val isFileExists = isFileExistsState.value
                    Spacer(Modifier.height(spacing.small))

                    // 大图展示区
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                            .clip(AppShapes.medium)
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), AppShapes.medium)
                            .clickable(enabled = isFileExists) { onImageClick(imgUri) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFileExists) {
                            AsyncImage(
                                model = imgUri,
                                contentDescription = "Generated Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // 悬浮微调与应用胶囊栏
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(spacing.small),
                                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                            ) {
                                // 在文件夹中显示按钮
                                IconButton(
                                    onClick = { getPlatform().openInFileExplorer(imgUri) },
                                    modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), AppShapes.small).tip("在本地系统资源管理器中定位该图片")
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }

                                // 微调/取消微调切换按钮
                                if (isCurrentVariant) {
                                    Button(
                                        onClick = { onVariant("") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.height(28.dp).tip("取消微调，恢复初始参考底图"),
                                        shape = AppShapes.small,
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("取消微调", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onVariant(imgUri) },
                                        modifier = Modifier.height(28.dp).tip("基于此图进行下一轮微调生成"),
                                        shape = AppShapes.small,
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("微调", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Button(
                                    onClick = { onApply(imgUri) },
                                    modifier = Modifier.height(28.dp).tip(applyTip),
                                    shape = AppShapes.small,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(applyTip, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            // 文件丢失破裂图占位
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BrokenImage, contentDescription = "Missing", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("本地文件已移除或丢失", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // 底部详细信息指示栏（展示文件名、格式与本地路径）
                    val fileName = remember(imgUri) {
                        if (imgUri.startsWith("data:image")) "Base64 临时数据"
                        else imgUri.substringAfterLast("/").substringAfterLast("\\")
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().tip(imgUri),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (imgUri.endsWith(".png", ignoreCase = true)) "PNG (Alpha 透明)" else if (imgUri.startsWith("data:image")) "Base64 图像" else "JPG 高清原图",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = fileName.take(36),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 正在生成时的思考文字与占位骨架屏
 */
@Composable
private fun GeneratingSkeletonBubble(
    statusLog: String,
    streamingText: String,
    pendingCount: Int
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(34.dp).clip(AppShapes.small),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(spacing.small))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = AppShapes.medium,
            modifier = Modifier.widthIn(max = 680.dp).wrapContentWidth(Alignment.Start)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = statusLog.ifBlank { "AI 正在分析设计意图并渲染..." },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (streamingText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = streamingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 预留的图片占位框骨架屏
                Spacer(Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(pendingCount.coerceIn(1, 4)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clip(AppShapes.medium)
                                .background(Color.Black.copy(alpha = 0.05f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), AppShapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("正在生成方案 ${it + 1}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

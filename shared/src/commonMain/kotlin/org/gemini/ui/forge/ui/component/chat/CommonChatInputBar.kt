package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.extend.readClipboardImageBytes
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.rememberFilePicker

/**
 * 全场景通用多模态 AI 对话输入控制栏
 *
 * 支持多行自适应输入、多模态附件、动态模型与协议选择，并通过 [leadingActions] 与 [trailingActions] 开放任意自定义操作插槽。
 *
 * @param isGenerating 是否正在后台生成中
 * @param selectedModel 当前选中的 Gemini 模型
 * @param onModelSelected 模型变更回调
 * @param selectedApiFlavor 当前选中的通信协议
 * @param onApiFlavorSelected 协议变更回调
 * @param onSendMessage 触发发送消息回调 (text, attachments)
 * @param config 通用配置参数
 * @param leadingActions 控制栏前置自定义动作插槽（如系统人设按钮、生图预设等）
 * @param trailingActions 发送按钮旁后置自定义动作插槽
 * @param modifier 修饰符
 */
@Composable
fun CommonChatInputBar(
    isGenerating: Boolean,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    selectedApiFlavor: ApiFlavor,
    onApiFlavorSelected: (ApiFlavor) -> Unit,
    onSendMessage: (text: String, attachments: List<CommonChatAttachment>) -> Unit,
    modifier: Modifier = Modifier,
    config: CommonChatStudioConfig = CommonChatStudioConfig(),
    onImagePreviewRequested: ((String) -> Unit)? = null,
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = LocalAppSpacing.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<CommonChatAttachment>>(emptyList()) }

    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var isFlavorMenuExpanded by remember { mutableStateOf(false) }

    // 剪贴板图片挂载方法
    fun pasteImageFromClipboard() {
        scope.launch {
            val bytes = readClipboardImageBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                val time = getCurrentTimeMillis()
                val name = "clipboard_$time.png"
                // 自动保存至应用缓存目录，获得持久化 URI 供 Coil 缩略图异步加载与灯箱全景预览
                val cachedUri = try {
                    val cacheRel = "cache/clipboard/$name"
                    val file = org.gemini.ui.forge.data.TemplateFile(cacheRel)
                    file.writeBytes(bytes)
                    file.getAbsolutePath()
                } catch (e: Exception) {
                    null
                }
                attachments = attachments + CommonChatAttachment(
                    name = name,
                    localUri = cachedUri,
                    bytes = bytes,
                    mimeType = "image/png"
                )
                Toast.show("已挂载剪贴板截图", ToastType.SUCCESS)
            }
        }
    }

    // 本地附件选择器
    val filePicker = rememberFilePicker(
        extensions = listOf("png", "jpg", "jpeg", "webp", "json", "txt")
    ) { path ->
        path?.takeIf { it.isNotBlank() }?.let { filePath ->
            scope.launch {
                val fileName = filePath.substringAfterLast('\\').substringAfterLast('/')
                val bytes = readLocalFileBytes(filePath)
                val mime = when {
                    fileName.endsWith(".png", true) -> "image/png"
                    fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                    fileName.endsWith(".webp", true) -> "image/webp"
                    fileName.endsWith(".json", true) -> "application/json"
                    else -> "text/plain"
                }
                attachments = attachments + CommonChatAttachment(name = fileName, localUri = filePath, bytes = bytes, mimeType = mime)
            }
        }
    }

    val canSend = (inputText.isNotBlank() || attachments.isNotEmpty()) && !isGenerating

    fun performSend() {
        if (!canSend) return
        val text = inputText.trim()
        val currentAttach = attachments
        inputText = ""
        attachments = emptyList()
        onSendMessage(text, currentAttach)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        val effectiveDialogWidth = config.dialogWidth ?: containerWidth
        // ★ 核心动态响应式视口：浮层菜单统一严格按视口宽度的 85% 计算，最大高度占视口高度的 70%
        val adaptiveMenuWidth = effectiveDialogWidth * 0.85f
        val dynamicMenuMaxHeight = config.dialogHeight?.let { it * 0.70f } ?: 520.dp

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            // 1. 已选中的多模态附件预览栏
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachments.forEachIndexed { index, attach ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                attach.localUri?.let { uri -> onImagePreviewRequested?.invoke(uri) }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                if (attach.mimeType.startsWith("image/")) {
                                    val imageModel: Any? = attach.localUri ?: attach.bytes
                                    if (imageModel != null) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Image, null, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = attach.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除附件",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            attachments = attachments.toMutableList().apply { removeAt(index) }
                                        },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 2. 自适应多行文本输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = if (config.inputPlaceholder.contains("Ctrl+V")) config.inputPlaceholder else "${config.inputPlaceholder} (支持 Ctrl+V 粘贴截图)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 160.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            if (event.key == Key.Enter && !event.isShiftPressed) {
                                performSend()
                                true
                            } else if (event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)) {
                                pasteImageFromClipboard()
                                false
                            } else false
                        } else false
                    },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(Modifier.height(spacing.small))

            // 3. 底部操作控制栏（动态模型选择器、协议切换器、自定义动作插槽、发送按钮）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧工具群（前置插槽 + 附件按钮 + 模型选择 + 协议选择）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    // 外部自定义前置动作插槽（如系统人设指令入口、生图预设等）
                    leadingActions()

                    // 多模态附件上传按钮
                    if (config.showAttachmentPicker) {
                        IconButton(
                            onClick = { filePicker() },
                            modifier = Modifier.size(32.dp).tip("添加附件文件或参考图片")
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "添加附件",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 动态模型选择器（对齐公用旗舰 DropdownMenu 下拉卡片菜单）
                    if (config.showModelSelector) {
                        var modelBtnWidthPx by remember { mutableStateOf(0) }
                        Box(modifier = Modifier.onGloballyPositioned { modelBtnWidthPx = it.size.width }) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { isModelMenuExpanded = true }
                                    .tip("选择 AI 模型 (当前: ${selectedModel.displayName})")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = selectedModel.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            val modelBtnWidthDp = with(LocalDensity.current) { modelBtnWidthPx.toDp() }
                            CommonModelSelectionMenu(
                                expanded = isModelMenuExpanded,
                                selectedModel = selectedModel,
                                onModelSelected = onModelSelected,
                                onDismissRequest = { isModelMenuExpanded = false },
                                minMenuWidth = modelBtnWidthDp,
                                maxMenuWidth = adaptiveMenuWidth,
                                maxMenuHeight = dynamicMenuMaxHeight
                            )
                        }
                    }

                    // 原生协议切换器
                    if (config.showApiFlavorSelector) {
                        var flavorBtnWidthPx by remember { mutableStateOf(0) }
                        Box(modifier = Modifier.onGloballyPositioned { flavorBtnWidthPx = it.size.width }) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { isFlavorMenuExpanded = true }
                                    .tip("切换通信协议 (当前: ${selectedApiFlavor.name})")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (selectedApiFlavor) {
                                            ApiFlavor.INTERACTIONS -> "⚡ Interactions"
                                            ApiFlavor.GENERATE_CONTENT -> "⚡ generateContent"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            val flavorBtnWidthDp = with(LocalDensity.current) { flavorBtnWidthPx.toDp() }
                            DropdownMenu(
                                expanded = isFlavorMenuExpanded,
                                onDismissRequest = { isFlavorMenuExpanded = false },
                                offset = DpOffset(0.dp, (-8).dp),
                                modifier = Modifier
                                    .widthIn(min = flavorBtnWidthDp, max = adaptiveMenuWidth)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        org.gemini.ui.forge.ui.theme.AppShapes.medium
                                    )
                            ) {
                                config.availableApiFlavors.forEach { flavor ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = when (flavor) {
                                                        ApiFlavor.INTERACTIONS -> "⚡ Interactions API"
                                                        ApiFlavor.GENERATE_CONTENT -> "⚡ generateContent API"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (flavor == selectedApiFlavor) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (flavor == selectedApiFlavor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        onClick = {
                                            onApiFlavorSelected(flavor)
                                            isFlavorMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 右侧工具群（外部后置插槽 + 发送按钮）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    trailingActions()

                    Button(
                        onClick = { performSend() },
                        enabled = canSend,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).tip("发送消息 (Enter)")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(text = "发送", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

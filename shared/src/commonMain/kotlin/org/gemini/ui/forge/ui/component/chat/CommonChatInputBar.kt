package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.extend.readClipboardImageBytes
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.ThinkingLevel
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.data.isFileExistsInternal
import org.gemini.ui.forge.state.MentionState
import org.gemini.ui.forge.ui.component.chat.mention.MentionDropdownPanel
import org.gemini.ui.forge.ui.component.chat.mention.MentionPillBar
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.component.chat.drop.DropOverlayBox
import org.gemini.ui.forge.ui.component.chat.drop.onFileDrop
import org.gemini.ui.forge.ui.component.chat.skill.SkillSelectorPill
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.canvas.CanvasSnapshotMode
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.model.ModelStudioGroup
import org.gemini.ui.forge.service.ai.ImageGenIntentDetector
import org.gemini.ui.forge.manager.ConfigManager
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
// 大文件云端上传物理阈值：5MB (5 * 1024 * 1024 字节)
private const val CLOUD_UPLOAD_THRESHOLD_BYTES = 5 * 1024 * 1024L

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

private fun isImageFile(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

@Composable
fun CommonChatInputBar(
    isGenerating: Boolean,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    selectedApiFlavor: ApiFlavor,
    onApiFlavorSelected: (ApiFlavor) -> Unit,
    currentThinkingLevel: ThinkingLevel = ThinkingLevel.DEFAULT,
    onThinkingLevelSelected: (ThinkingLevel) -> Unit = {},
    onSendMessage: (text: String, attachments: List<CommonChatAttachment>) -> Unit,
    modifier: Modifier = Modifier,
    inputTextOverride: String? = null,
    onInputTextConsumed: () -> Unit = {},
    config: CommonChatStudioConfig = CommonChatStudioConfig(),
    onImagePreviewRequested: ((String) -> Unit)? = null,
    mentionState: MentionState? = null,
    mentionCandidates: List<MentionItem> = emptyList(),
    mentionMaxHeight: Dp = 480.dp,
    onFilesDropped: ((List<String>) -> Unit)? = null,
    externalDroppedFiles: List<String>? = null,
    onExternalDroppedFilesConsumed: () -> Unit = {},
    onDeleteMentionItem: ((MentionItem) -> Unit)? = null,
    activeSkills: List<UiStudioSkill> = emptyList(),
    allSkills: List<UiStudioSkill> = emptyList(),
    enabledSkillIds: Set<String> = emptySet(),
    onToggleSkill: (String, Boolean) -> Unit = { _, _ -> },
    onRefreshSkills: (() -> Unit)? = null,
    onOpenSkillsDirectory: (() -> Unit)? = null,
    skillMode: SkillActivationMode = SkillActivationMode.AUTO,
    onSkillModeChanged: (SkillActivationMode) -> Unit = {},
    onStopGeneration: (() -> Unit)? = null,
    onCaptureCanvasSnapshotRequested: ((CanvasSnapshotMode, (CommonChatAttachment) -> Unit) -> Unit)? = null,
    isCapturingCanvasSnapshot: Boolean = false,
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = LocalAppSpacing.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var preferCloudUpload by remember { mutableStateOf(true) }
    var attachments by remember { mutableStateOf<List<CommonChatAttachment>>(emptyList()) }
    var isDropHovering by remember { mutableStateOf(false) }
    var previewSnapshotAttachment by remember { mutableStateOf<CommonChatAttachment?>(null) }
    var isSnapshotMenuExpanded by remember { mutableStateOf(false) }
    var previousNonImageModel by remember { mutableStateOf<GeminiModel?>(null) }

    // 回填外部重新编辑的文本
    LaunchedEffect(inputTextOverride) {
        if (!inputTextOverride.isNullOrBlank()) {
            inputText = inputTextOverride
            onInputTextConsumed()
        }
    }

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

    // 统一将文件物理路径转换为会话附件并追加到列表
    fun addFilesAsAttachments(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            val newAttachments = paths.mapNotNull { filePath ->
                try {
                    val fileName = filePath.substringAfterLast('\\').substringAfterLast('/')
                    val bytes = readLocalFileBytes(filePath)
                    val mime = when {
                        fileName.endsWith(".png", true) -> "image/png"
                        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                        fileName.endsWith(".webp", true) -> "image/webp"
                        fileName.endsWith(".gif", true) -> "image/gif"
                        fileName.endsWith(".bmp", true) -> "image/bmp"
                        fileName.endsWith(".json", true) -> "application/json"
                        fileName.endsWith(".xml", true) -> "application/xml"
                        fileName.endsWith(".yaml", true) || fileName.endsWith(".yml", true) -> "text/yaml"
                        fileName.endsWith(".md", true) -> "text/markdown"
                        fileName.endsWith(".kt", true) || fileName.endsWith(".kts", true) -> "text/x-kotlin"
                        fileName.endsWith(".txt", true) || fileName.endsWith(".log", true) -> "text/plain"
                        else -> "application/octet-stream"
                    }
                    CommonChatAttachment(name = fileName, localUri = filePath, bytes = bytes, mimeType = mime)
                } catch (_: Throwable) {
                    null
                }
            }
            if (newAttachments.isNotEmpty()) {
                attachments = attachments + newAttachments
            }
        }
    }

    // 核心统一分流接入管道：将拖拽或选择的文件按图片/文档精准路由
    fun handleIncomingFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        val (imagePaths, docPaths) = paths.partition { isImageFile(it) }

        // A. 图片资产：无论是否有 mentionState，统统作为多模态图片附件处理
        if (imagePaths.isNotEmpty()) {
            addFilesAsAttachments(imagePaths)
        }

        // B. 文档与非图片格式：
        if (docPaths.isNotEmpty()) {
            if (onFilesDropped != null) {
                // 宿主提供业务级文件处理（例如 UIStudioViewModel.handleFileDropped 会挂载为 @Mention 参考文档）
                onFilesDropped.invoke(docPaths)
            } else if (mentionState != null) {
                // 宿主启用了工程上下文实体系统（@Mention）：直接挂载为 DOC 胶囊，严禁再进入 attachments！
                docPaths.forEach { docPath ->
                    val fileName = docPath.substringAfterLast('\\').substringAfterLast('/')
                    mentionState.attachMention(
                        MentionItem(
                            id = "doc_$docPath",
                            title = fileName,
                            subtitle = docPath,
                            category = MentionCategory.DOC,
                            icon = Icons.Default.Description,
                            payload = MentionPayload.DocPayload(
                                fileName = fileName,
                                absolutePath = docPath
                            )
                        )
                    )
                }
                Toast.show("已挂载 ${docPaths.size} 份参考文档", ToastType.INFO)
            } else {
                // 纯通用对话场景兜底：作为普通二进制文件附件
                addFilesAsAttachments(docPaths)
            }
        }
    }

    // 监听外部注入的文件（如从聊天主视口拖入）
    LaunchedEffect(externalDroppedFiles) {
        val files = externalDroppedFiles
        if (!files.isNullOrEmpty()) {
            handleIncomingFiles(files)
            onExternalDroppedFilesConsumed()
        }
    }

    // 本地附件选择器
    val filePicker = rememberFilePicker(
        extensions = listOf("png", "jpg", "jpeg", "webp", "json", "txt", "md", "yaml", "xml", "kt", "kts", "sql", "log")
    ) { path ->
        path?.takeIf { it.isNotBlank() }?.let { filePath ->
            handleIncomingFiles(listOf(filePath))
        }
    }

    val loadedPresetModel by produceState<String?>(initialValue = config.defaultImageGenModel, config.defaultImageGenModel) {
        value = if (config.defaultImageGenModel != null) {
            config.defaultImageGenModel
        } else {
            ConfigManager().loadKey("DEFAULT_IMAGE_GEN_MODEL")?.takeIf { it.isNotBlank() }
        }
    }

    val loadedAutoSwitchEnabled by produceState<Boolean>(initialValue = config.isAutoSwitchImageGenModelEnabled ?: true, config.isAutoSwitchImageGenModelEnabled) {
        value = if (config.isAutoSwitchImageGenModelEnabled != null) {
            config.isAutoSwitchImageGenModelEnabled
        } else {
            ConfigManager().loadKey("AUTO_SWITCH_IMAGE_GEN_MODEL")?.toBooleanStrictOrNull() ?: true
        }
    }

    val canSend = (inputText.isNotBlank() || attachments.isNotEmpty()) && !isGenerating

    fun performSend() {
        if (!canSend) return
        val text = inputText.trim()
        val effectivePresetModel = loadedPresetModel
        if (loadedAutoSwitchEnabled && ImageGenIntentDetector.isImageGenIntent(text) && !ImageGenIntentDetector.isImageSpecialized(selectedModel)) {
            val targetModel = ImageGenIntentDetector.resolveTargetGeminiModel(effectivePresetModel)
            previousNonImageModel = selectedModel
            onModelSelected(targetModel)
            Toast.show("检测到生图意图，已自动切换至 ${targetModel.displayName}", ToastType.INFO)
        }
        val currentAttach = attachments.map { attach ->
            val isLarge = (attach.bytes?.size?.toLong() ?: 0L) >= CLOUD_UPLOAD_THRESHOLD_BYTES
            attach.copy(preferCloudUpload = isLarge && preferCloudUpload)
        }
        inputText = ""
        attachments = emptyList()
        onSendMessage(text, currentAttach)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onFileDrop(
                onDragStateChanged = { isDropHovering = it },
                onFilesDropped = { paths ->
                    handleIncomingFiles(paths)
                }
            )
    ) {
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
            // 0. 已挂载的「@」工程上下文实体药丸胶囊栏
            if (mentionState != null && mentionState.attachedMentions.isNotEmpty()) {
                MentionPillBar(
                    mentions = mentionState.attachedMentions,
                    onRemove = { item -> mentionState.removeMention(item) },
                    onItemClick = { item ->
                        when (val payload = item.payload) {
                            is MentionPayload.DocPayload -> {
                                onImagePreviewRequested?.invoke(payload.absolutePath)
                            }
                            is MentionPayload.AssetPayload -> {
                                onImagePreviewRequested?.invoke(payload.filePath)
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier.padding(bottom = spacing.small)
                )
            }

            // 1. 已选中的多模态附件预览栏与云端上传选项
            if (attachments.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        attachments.forEachIndexed { index, attach ->
                            if (attach.name.contains("画布快照")) {
                                CanvasSnapshotAttachmentPill(
                                    attachment = attach,
                                    onRemove = {
                                        attachments = attachments.toMutableList().apply { removeAt(index) }
                                    },
                                    onPreview = {
                                        previewSnapshotAttachment = attach
                                    }
                                )
                            } else {
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

                    val maxAttachmentBytes = remember(attachments) {
                        attachments.maxOfOrNull { it.bytes?.size?.toLong() ?: 0L } ?: 0L
                    }
                    val hasLargeAttachment = maxAttachmentBytes >= CLOUD_UPLOAD_THRESHOLD_BYTES

                    // 仅当存在 >= 5MB 的大文件附件时才弹出 Google File API 选项；小文件直接 Base64 嵌入，保持输入栏清爽
                    if (hasLargeAttachment) {
                        Spacer(Modifier.height(4.dp))
                        val formattedMb = ((maxAttachmentBytes / (1024.0 * 1024.0) * 10).toInt() / 10.0)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { preferCloudUpload = !preferCloudUpload }
                        ) {
                            Checkbox(
                                checked = preferCloudUpload,
                                onCheckedChange = { preferCloudUpload = it },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "优先上传至云端服务 (Google File API)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "⚠️ 包含大文件 (${formattedMb}MB)，建议云端上传",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. 智能「@」联想浮层面板：动态置于输入框正上方，确保输入文字100%肉眼可见
            if (mentionState != null && mentionState.isMentionMenuVisible) {
                MentionDropdownPanel(
                    state = mentionState,
                    candidates = mentionCandidates,
                    maxHeight = mentionMaxHeight,
                    onDeleteItem = onDeleteMentionItem,
                    onItemSelected = { selected ->
                        val docPayload = selected.payload as? MentionPayload.DocPayload
                        if (docPayload != null) {
                            scope.launch {
                                if (!isFileExistsInternal(docPayload.absolutePath)) {
                                    mentionState.closeMenu()
                                    val atIndex = inputText.lastIndexOf('@')
                                    if (atIndex >= 0) {
                                        inputText = inputText.substring(0, atIndex)
                                    }
                                    Toast.show(
                                        "引用的文件已在磁盘被删除，已自动清理缓存",
                                        ToastType.INFO
                                    )
                                } else {
                                    mentionState.attachMention(selected)
                                    val atIndex = inputText.lastIndexOf('@')
                                    if (atIndex >= 0) {
                                        inputText = inputText.substring(0, atIndex)
                                    }
                                }
                            }
                        } else {
                            mentionState.attachMention(selected)
                            val atIndex = inputText.lastIndexOf('@')
                            if (atIndex >= 0) {
                                inputText = inputText.substring(0, atIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }

            // 2.5 视觉审查意图感知胶囊气泡 (当输入涉及排版/布局且未挂载快照时提示)
            val visualIntentKeywords = remember { setOf("排版", "布局", "界面", "视觉", "重叠", "好看", "效果", "对齐") }
            val hasVisualIntent = remember(inputText, attachments) {
                visualIntentKeywords.any { inputText.contains(it) } && attachments.none { it.name.contains("画布快照") }
            }

            if (config.showCanvasSnapshotAction && onCaptureCanvasSnapshotRequested != null && hasVisualIntent) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable {
                            onCaptureCanvasSnapshotRequested(CanvasSnapshotMode.OFFSCREEN_COMPOSITE) { newAttach ->
                                attachments = attachments + newAttach
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "检测到排版视觉咨询，点击一键挂载当前画布截图 📷",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 2.6 生图意图感知与自动切回胶囊气泡
            val effectivePresetModel = loadedPresetModel
            val isImageIntent = remember(inputText) {
                ImageGenIntentDetector.isImageGenIntent(inputText)
            }
            val isCurrentNonImageModel = remember(selectedModel) {
                !ImageGenIntentDetector.isImageSpecialized(selectedModel)
            }
            val targetImageModel = remember(effectivePresetModel) {
                ImageGenIntentDetector.resolveTargetGeminiModel(effectivePresetModel)
            }

            if (loadedAutoSwitchEnabled && isImageIntent && isCurrentNonImageModel) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable {
                            previousNonImageModel = selectedModel
                            onModelSelected(targetImageModel)
                            Toast.show("已切换至生图模型：${targetImageModel.displayName}", ToastType.INFO)
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "检测到生图意图，发送时将自动切换至「${targetImageModel.displayName}」(点击可立即切换)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else if (previousNonImageModel != null && !isCurrentNonImageModel) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable {
                            val prev = previousNonImageModel
                            if (prev != null) {
                                onModelSelected(prev)
                                previousNonImageModel = null
                                Toast.show("已切回原模型：${prev.displayName}", ToastType.INFO)
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "当前已自动切换为生图模型，点击切回原模型「${previousNonImageModel?.displayName}」",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 自适应多行文本输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { newText ->
                    inputText = newText
                    if (mentionState != null) {
                        val lastAtIndex = newText.lastIndexOf('@')
                        if (lastAtIndex >= 0) {
                            val textAfterAt = newText.substring(lastAtIndex + 1)
                            if (!textAfterAt.contains(' ') && !textAfterAt.contains('\n')) {
                                mentionState.openMenu(textAfterAt)
                            } else {
                                mentionState.closeMenu()
                            }
                        } else {
                            if (mentionState.isMentionMenuVisible) {
                                mentionState.closeMenu()
                            }
                        }
                    }
                },
                minLines = 2,
                maxLines = 6,
                placeholder = {
                    Text(
                        text = if (config.inputPlaceholder.contains("Ctrl+V")) config.inputPlaceholder else "${config.inputPlaceholder} (输入 @ 引用上下文，支持 Ctrl+V 粘贴截图)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            if (mentionState?.isMentionMenuVisible == true) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (mentionCandidates.isNotEmpty()) {
                                            mentionState.selectedIndex = (mentionState.selectedIndex + 1) % mentionCandidates.size
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.DirectionUp -> {
                                        if (mentionCandidates.isNotEmpty()) {
                                            mentionState.selectedIndex = if (mentionState.selectedIndex <= 0) {
                                                mentionCandidates.size - 1
                                            } else {
                                                mentionState.selectedIndex - 1
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.Enter, Key.Tab -> {
                                        if (mentionCandidates.isNotEmpty() && mentionState.selectedIndex in mentionCandidates.indices) {
                                            val selected = mentionCandidates[mentionState.selectedIndex]
                                            mentionState.attachMention(selected)
                                            val atIndex = inputText.lastIndexOf('@')
                                            if (atIndex >= 0) {
                                                inputText = inputText.substring(0, atIndex)
                                            }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    Key.Escape -> {
                                        mentionState.closeMenu()
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                            if (isGenerating && onStopGeneration != null && event.key == Key.Escape) {
                                onStopGeneration()
                                return@onPreviewKeyEvent true
                            }
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

                    // 「@」工程上下文引用快捷按钮
                    if (mentionState != null) {
                        IconButton(
                            onClick = {
                                if (mentionState.isMentionMenuVisible) {
                                    mentionState.closeMenu()
                                } else {
                                    if (!inputText.endsWith("@")) {
                                        inputText = if (inputText.isBlank() || inputText.endsWith(" ")) "${inputText}@" else "$inputText @"
                                    }
                                    mentionState.openMenu("")
                                }
                            },
                            modifier = Modifier.size(32.dp).tip("引用工程上下文实体 (@)")
                        ) {
                            Icon(
                                Icons.Default.AlternateEmail,
                                contentDescription = "引用上下文",
                                modifier = Modifier.size(18.dp),
                                tint = if (mentionState.isMentionMenuVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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

                    // 画布快照多模态挂载按钮 (📷)
                    if (config.showCanvasSnapshotAction && onCaptureCanvasSnapshotRequested != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(32.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    onCaptureCanvasSnapshotRequested(CanvasSnapshotMode.OFFSCREEN_COMPOSITE) { newAttach ->
                                        attachments = attachments + newAttach
                                    }
                                },
                                enabled = !isCapturingCanvasSnapshot,
                                modifier = Modifier.size(32.dp).tip("挂载当前画布多模态截图 (纯净整页合成)")
                            ) {
                                if (isCapturingCanvasSnapshot) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = "挂载画布快照",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Box {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "切换快照模式",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable { isSnapshotMenuExpanded = true }
                                        .tip("切换快照模式"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                DropdownMenu(
                                    expanded = isSnapshotMenuExpanded,
                                    onDismissRequest = { isSnapshotMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("纯净整页合成 (100% 原始分辨率)") },
                                        onClick = {
                                            isSnapshotMenuExpanded = false
                                            onCaptureCanvasSnapshotRequested(CanvasSnapshotMode.OFFSCREEN_COMPOSITE) { newAttach ->
                                                attachments = attachments + newAttach
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("当前视口截图 (所见即所得)") },
                                        onClick = {
                                            isSnapshotMenuExpanded = false
                                            onCaptureCanvasSnapshotRequested(CanvasSnapshotMode.VIEWPORT_CLIP) { newAttach ->
                                                attachments = attachments + newAttach
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 技能状态药丸与手动切换 (Spec-to-View 意图自动探测，对齐旗舰自适应尺寸规范)
                    SkillSelectorPill(
                        skills = allSkills,
                        enabledSkillIds = enabledSkillIds,
                        onToggleSkill = onToggleSkill,
                        onRefreshSkills = onRefreshSkills,
                        onOpenSkillsDirectory = onOpenSkillsDirectory,
                        activeSkills = activeSkills,
                        currentMode = skillMode,
                        onModeChanged = onSkillModeChanged,
                        minMenuWidth = 340.dp,
                        maxMenuWidth = adaptiveMenuWidth,
                        maxMenuHeight = dynamicMenuMaxHeight
                    )

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

                    // 思考模式切换器（紧邻模型选择器，仅当模型支持思考时展现）
                    CommonThinkingLevelSelector(
                        selectedModel = selectedModel,
                        currentLevel = currentThinkingLevel,
                        onLevelSelected = onThinkingLevelSelected,
                        useShortLabel = true,
                        maxMenuHeight = dynamicMenuMaxHeight,
                        offset = DpOffset(0.dp, (-8).dp)
                    )

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
                                    .tip("切换通信协议 (当前: ${selectedApiFlavor.displayNameZh})")
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
                                    .widthIn(min = maxOf(flavorBtnWidthDp, 240.dp), max = adaptiveMenuWidth)
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
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = when (flavor) {
                                                            ApiFlavor.INTERACTIONS -> "⚡ Interactions API"
                                                            ApiFlavor.GENERATE_CONTENT -> "⚡ generateContent API"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (flavor == selectedApiFlavor) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (flavor == selectedApiFlavor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    if (flavor == selectedApiFlavor) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = when (flavor) {
                                                        ApiFlavor.INTERACTIONS -> "新一代交互协议 · 链式上下文与高级管道"
                                                        ApiFlavor.GENERATE_CONTENT -> "官方标准生成协议 · 经典流式响应"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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

                    if (isGenerating && onStopGeneration != null) {
                        Button(
                            onClick = { onStopGeneration() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).tip("中止本次 AI 生成 (Esc)")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "中止",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "中止",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
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

        // 画布快照高清大图预览弹窗
        previewSnapshotAttachment?.let { snapshot ->
            CanvasSnapshotPreviewDialog(
                attachment = snapshot,
                onDismiss = { previewSnapshotAttachment = null }
            )
        }

        // 外部文件拖拽悬停全域遮罩
        DropOverlayBox(
            visible = isDropHovering,
            modifier = Modifier.matchParentSize()
        )
    }
}
}

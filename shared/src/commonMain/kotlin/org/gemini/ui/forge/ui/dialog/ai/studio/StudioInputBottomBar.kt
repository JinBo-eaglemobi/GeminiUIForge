package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.readClipboardImageBytes
import org.gemini.ui.forge.manager.MattingPreset
import org.gemini.ui.forge.manager.PromptPresetManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioPresetDropdownMenu
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionConfigRow
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.rememberFilePicker
import org.jetbrains.compose.resources.stringResource

/**
 * 待发送图片来源元信息
 */
private enum class PendingImageSource(val label: String) {
    ORIGINAL_BLOCK("图元参考图"),
    LOCAL_PICKER("本地图片"),
    CLIPBOARD_PASTE("剪贴板截图")
}

/**
 * 全新自然多模态双层聊天输入中枢
 */
@Composable
fun StudioInputBottomBar(
    promptZh: String,
    promptEn: String,
    originalBlockPromptZh: String? = null,
    originalBlockPromptEn: String? = null,
    referenceImageUri: String?,
    previewMemoryBytes: ByteArray?,
    onReferenceImageClick: ((Any) -> Unit)? = null,
    canCropFromPage: Boolean = false,
    onStartRegionCrop: () -> Unit = {},
    onOpenRefConfig: () -> Unit = {},
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    generationCount: Int,
    onCountSelected: (Int) -> Unit,
    isGenerating: Boolean,
    isOptimizingPrompt: Boolean,
    storage: LocalFileStorage,
    onOptimizeRequested: (sourceText: String, isZh: Boolean, onOptimized: (String) -> Unit) -> Unit,
    onSend: (userPrompt: String, activeLang: PromptLanguage, isImageToImage: Boolean, isPng: Boolean, useCloudBgRemoval: Boolean, isUploadToCloud: Boolean, model: GeminiModel, count: Int, customImageBytes: ByteArray?) -> Unit,
    onCancel: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenAssetGallery: () -> Unit = {},
    historicalAssetCount: Int = 0,
    dialogWidth: androidx.compose.ui.unit.Dp? = null,
    dialogHeight: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val scope = rememberCoroutineScope()

    // 中英提示词模板选择器（ZH | EN 紧凑胶囊）
    var activeLang by remember {
        mutableStateOf(if (promptZh.isNotBlank() || promptEn.isBlank()) PromptLanguage.ZH else PromptLanguage.EN)
    }
    var textZh by remember(promptZh) { mutableStateOf(promptZh) }
    var textEn by remember(promptEn) { mutableStateOf(promptEn) }

    val currentDisplayPrompt = if (activeLang == PromptLanguage.ZH) textZh else textEn

    // 待发送附加图片（支持三种来源：图元自带参考图、本地选择图片、剪贴板粘贴图片）
    var pendingImageBytes by remember(previewMemoryBytes) { mutableStateOf<ByteArray?>(previewMemoryBytes) }
    var pendingImageUri by remember(referenceImageUri) { mutableStateOf<String?>(referenceImageUri) }
    var pendingSource by remember(referenceImageUri, previewMemoryBytes) {
        mutableStateOf(
            if (previewMemoryBytes != null || !referenceImageUri.isNullOrBlank()) PendingImageSource.ORIGINAL_BLOCK
            else null
        )
    }

    var isPng by remember { mutableStateOf(true) }
    var isUploadToCloud by remember { mutableStateOf(false) }

    val presetManager = remember { PromptPresetManager(storage) }
    var presets by remember { mutableStateOf<List<MattingPreset>>(emptyList()) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        presets = presetManager.loadPresets()
    }

    // 本地文件选择器
    val filePicker = rememberFilePicker(
        title = "选择参考图片",
        isFolder = false,
        extensions = listOf("png", "jpg", "jpeg", "webp")
    ) { pickedPath ->
        if (!pickedPath.isNullOrBlank()) {
            pendingImageUri = pickedPath
            pendingImageBytes = null
            pendingSource = PendingImageSource.LOCAL_PICKER
            Toast.show("已添加本地图片", ToastType.SUCCESS)
        }
    }

    // 剪贴板图片粘贴逻辑
    fun pasteImageFromClipboard(): Boolean {
        var handled = false
        scope.launch {
            val bytes = readClipboardImageBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                pendingImageBytes = bytes
                pendingImageUri = null
                pendingSource = PendingImageSource.CLIPBOARD_PASTE
                Toast.show("已自动挂载剪贴板截图", ToastType.SUCCESS)
                handled = true
            }
        }
        return handled
    }

    // 是否有待发送图片
    val hasPendingImage = pendingImageBytes != null || !pendingImageUri.isNullOrBlank()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        val effectiveDialogWidth = dialogWidth ?: containerWidth
        // ★ 核心动态响应式视口：预设菜单严格占视口宽度的 85%，菜单最大高度严格占视口高度的 70%
        val adaptivePresetMenuWidth = effectiveDialogWidth * 0.85f
        val adaptiveModelMenuWidth = (effectiveDialogWidth * 0.55f).coerceIn(380.dp, 600.dp)
        val dynamicMenuMaxHeight = dialogHeight?.let { it * 0.70f }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // ──────────────────────────────────────────────────────────
                // 1. 上层：待发送图片/参考图预览附件栏 (有图展开，无图折叠)
                // ──────────────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = hasPendingImage,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        shape = AppShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.extraSmall)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // 72dp 缩略预览图
                                Surface(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(AppShapes.small)
                                        .clickable {
                                            val model = pendingImageBytes ?: pendingImageUri
                                            if (model != null) onReferenceImageClick?.invoke(model)
                                        },
                                    shape = AppShapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    val model = pendingImageBytes ?: pendingImageUri
                                    if (model != null) {
                                        AsyncImage(
                                            model = model,
                                            contentDescription = "Pending Image",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(Modifier.width(spacing.medium))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = AppShapes.small,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = pendingSource?.label ?: "图片附件",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(spacing.small))
                                        Text(
                                            text = "多模态改图模式",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    val desc = when (pendingSource) {
                                        PendingImageSource.ORIGINAL_BLOCK -> "当前图元关联的参考裁切图"
                                        PendingImageSource.LOCAL_PICKER -> pendingImageUri?.substringAfterLast('\\')?.substringAfterLast('/') ?: "本地图片"
                                        PendingImageSource.CLIPBOARD_PASTE -> "来自系统剪贴板截图 (PNG 格式)"
                                        null -> ""
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 放大查看按钮
                                IconButton(
                                    onClick = {
                                        val model = pendingImageBytes ?: pendingImageUri
                                        if (model != null) onReferenceImageClick?.invoke(model)
                                    },
                                    modifier = Modifier.size(30.dp).tip("全屏查看大图")
                                ) {
                                    Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(18.dp))
                                }

                                Spacer(Modifier.width(4.dp))

                                // 移除按钮
                                IconButton(
                                    onClick = {
                                        pendingImageBytes = null
                                        pendingImageUri = null
                                        pendingSource = null
                                    },
                                    modifier = Modifier.size(30.dp).tip("移除当前图片（转为纯文本生成）")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // ──────────────────────────────────────────────────────────
                // 优化中提示横幅
                // ──────────────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = isOptimizingPrompt,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = AppShapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.extraSmall)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(
                                text = "AI 正在润色并扩展提示词细节，请稍候...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ──────────────────────────────────────────────────────────
                // 2. 下层：聊天对话输入框（★ 弹性 2~6 行，超过自动内部滚动）
                // ──────────────────────────────────────────────────────────
                OutlinedTextField(
                    value = currentDisplayPrompt,
                    onValueChange = { newText ->
                        // 手动改动内容时，重置预设选中状态
                        selectedPresetId = null
                        if (activeLang == PromptLanguage.ZH) {
                            textZh = newText
                        } else {
                            textEn = newText
                        }
                    },
                    placeholder = {
                        Text(
                            text = if (hasPendingImage) "描述修改要求（如：给边缘添加发光光晕、修改主色调为金色，支持 Ctrl+V 粘贴新截图）..."
                            else "发消息给 AI（描述生图要求，支持 Ctrl+V 粘贴截图）...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { keyEvent ->
                            // 监听 Ctrl+V / Cmd+V 快捷键粘贴图片
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.V && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                                if (pasteImageFromClipboard()) {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        },
                    minLines = 2,
                    maxLines = 6,
                    shape = AppShapes.medium,
                    enabled = !isOptimizingPrompt && !isGenerating
                )

                // ──────────────────────────────────────────────────────────
                // 3. 底部功能工具栏
                // ──────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧工具图标组（★ 常驻参考图画框与来源管理入口）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 📎 添加本地图片
                        IconButton(
                            onClick = { filePicker() },
                            enabled = !isOptimizingPrompt && !isGenerating,
                            modifier = Modifier.size(34.dp).tip("添加本地参考图片 (PNG/JPG)")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Add Image",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // ✂️ 画框选区截取（★ 常驻显示：只要原图存在，随时可重新画框截取）
                        if (canCropFromPage) {
                            IconButton(
                                onClick = onStartRegionCrop,
                                enabled = !isOptimizingPrompt && !isGenerating,
                                modifier = Modifier.size(34.dp).tip("从原图中自定义画框截取局部区域作为参考底图")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "Crop from Page",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ⚙️ 参考图配置管理（★ 常驻显示：支持选区/换图/一键还原初始底图/清除）
                        IconButton(
                            onClick = onOpenRefConfig,
                            enabled = !isOptimizingPrompt && !isGenerating,
                            modifier = Modifier.size(34.dp).tip("参考底图来源配置管理（选区/换图/还原初始）")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Reference Config",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // ❝ 一键引用当前图元原本的双语提示词
                        val hasOriginalPrompt = !originalBlockPromptZh.isNullOrBlank() || !originalBlockPromptEn.isNullOrBlank()
                        IconButton(
                            onClick = {
                                selectedPresetId = null
                                textZh = originalBlockPromptZh ?: ""
                                textEn = originalBlockPromptEn ?: ""
                                Toast.show("已带入当前图元原始提示词", ToastType.SUCCESS)
                            },
                            enabled = !isOptimizingPrompt && !isGenerating && hasOriginalPrompt,
                            modifier = Modifier.size(34.dp).tip(if (hasOriginalPrompt) "一键引用带入当前图元已有的原始提示词" else "当前图元无提示词")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Quote Original",
                                tint = if (hasOriginalPrompt) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // ✦ 分类专业场景预设（★ 最大 85% 视口自适应包裹 + 70% 最大高度 + 双语成对注入）
                        if (presets.isNotEmpty()) {
                            StudioPresetDropdownMenu(
                                presets = presets,
                                selectedPresetId = selectedPresetId,
                                maxMenuWidth = adaptivePresetMenuWidth,
                                maxMenuHeight = dynamicMenuMaxHeight,
                                onPresetSelected = { preset ->
                                    selectedPresetId = preset.id
                                    textZh = preset.promptZh
                                    textEn = preset.promptEn
                                }
                            )
                        }

                        Spacer(Modifier.width(spacing.extraSmall))

                        // 透明 PNG 勾选
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.tip("生成透明 PNG 图像（去除背景色）")
                        ) {
                            Checkbox(
                                checked = isPng,
                                onCheckedChange = { isPng = it },
                                enabled = !isOptimizingPrompt && !isGenerating,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("透明PNG", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(Modifier.width(spacing.extraSmall))

                        // 📜 通信报文日志按钮
                        IconButton(
                            onClick = onOpenLogs,
                            modifier = Modifier.size(34.dp).tip("查看本次会话与 AI 通信的原始真实网络报文 (Request/Response)")
                        ) {
                            Icon(Icons.Default.DataObject, contentDescription = "Logs", modifier = Modifier.size(18.dp))
                        }

                        // 🖼️ 历史生成资产弹窗按钮
                        IconButton(
                            onClick = onOpenAssetGallery,
                            modifier = Modifier.size(34.dp).tip("查看历史生成资产 ($historicalAssetCount)")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Asset Gallery",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 右侧：中英微型切换胶囊 + 模型参数 + 优化按钮 + 发送按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        // ★ 极简紧凑型中英切换微胶囊 (28dp，双向即时切换当前文案)
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(AppShapes.small)
                                        .background(if (activeLang == PromptLanguage.ZH) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { activeLang = PromptLanguage.ZH }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "ZH",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeLang == PromptLanguage.ZH) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(AppShapes.small)
                                        .background(if (activeLang == PromptLanguage.EN) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { activeLang = PromptLanguage.EN }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "EN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeLang == PromptLanguage.EN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 模型配置微型条 (自适应内容包裹，最大宽度 55% 视口，最大高度 70%)
                        StudioSessionConfigRow(
                            selectedModel = selectedModel,
                            onModelSelected = onModelSelected,
                            generationCount = generationCount,
                            onCountSelected = onCountSelected,
                            maxMenuWidth = adaptiveModelMenuWidth,
                            maxMenuHeight = dynamicMenuMaxHeight
                        )

                        // ⚡ 提示词优化按钮
                        FilledTonalIconButton(
                            onClick = {
                                val source = currentDisplayPrompt.trim()
                                if (source.isNotBlank()) {
                                    onOptimizeRequested(source, activeLang == PromptLanguage.ZH) { optimized ->
                                        selectedPresetId = null
                                        if (activeLang == PromptLanguage.ZH) {
                                            textZh = optimized
                                        } else {
                                            textEn = optimized
                                        }
                                    }
                                }
                            },
                            enabled = !isOptimizingPrompt && !isGenerating && currentDisplayPrompt.isNotBlank(),
                            modifier = Modifier.size(38.dp).tip("AI 自动优化并润色当前输入的提示词"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            if (isOptimizingPrompt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = "Optimize",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ➤ 发送 / 中止生成按钮（★ 所见即所发：严格只发送当前输入框内的单语文案）
                        if (isGenerating) {
                            Button(
                                onClick = onCancel,
                                modifier = Modifier.height(38.dp).tip("中止当前 AI 任务"),
                                shape = AppShapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("中止", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        } else if (isOptimizingPrompt) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.height(38.dp),
                                shape = AppShapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("优化中...", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val promptToSend = currentDisplayPrompt.trim()
                                    onSend(
                                        promptToSend,
                                        activeLang,
                                        hasPendingImage,
                                        isPng,
                                        false,
                                        isUploadToCloud,
                                        selectedModel,
                                        generationCount,
                                        pendingImageBytes
                                    )
                                },
                                enabled = currentDisplayPrompt.isNotBlank() || hasPendingImage,
                                modifier = Modifier.height(38.dp).tip("发送当前文案给 AI 开始生成"),
                                shape = AppShapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(
                                    text = "发送",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

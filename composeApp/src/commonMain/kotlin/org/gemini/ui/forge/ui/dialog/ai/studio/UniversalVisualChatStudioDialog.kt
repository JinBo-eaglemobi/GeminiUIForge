package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.SessionCacheManager
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.BlockRefinementDialog
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioReferenceSourceDialog
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionLogDialog
import org.gemini.ui.forge.ui.dialog.asset.AssetSelectionDialog
import org.gemini.ui.forge.ui.dialog.asset.ImageEditorDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.rememberFilePicker
import org.gemini.ui.forge.viewmodel.VisualChatStudioViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 全局统一多轮对话式 AI 视觉交互工作室弹窗（高阶交互旗舰版）
 */
@Composable
fun UniversalVisualChatStudioDialog(
    scopeId: String,
    projectName: String = "",
    block: UIBlock? = null,
    initialReferenceImageUri: String? = null,
    pageSourceImageUri: String? = null,
    pageWidth: Float = 1080f,
    pageHeight: Float = 1920f,
    currentLang: PromptLanguage = PromptLanguage.ZH,
    apiKey: String,
    storage: LocalFileStorage,
    aiService: AIGenerationService,
    templateRepo: TemplateRepository,
    onApplyAsset: (imagePath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sessionCacheManager = remember { SessionCacheManager(storage) }
    val viewModel = remember(scopeId) {
        VisualChatStudioViewModel(
            scopeId = scopeId,
            projectName = projectName,
            block = block,
            initialReferenceImageUri = initialReferenceImageUri,
            aiService = aiService,
            templateRepo = templateRepo,
            sessionManager = sessionCacheManager
        )
    }

    val state by viewModel.uiState.collectAsState()
    val spacing = LocalAppSpacing.current

    var selectedVariantRefImage by remember { mutableStateOf<String?>(null) }
    var lightboxImageUri by remember { mutableStateOf<String?>(null) }
    var showAssetGalleryDialog by remember { mutableStateOf(false) }
    var moduleHistoricalImages by remember { mutableStateOf<List<TemplateFile>>(emptyList()) }
    var isProcessingHistoricalBg by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var pendingEditorImageUri by remember { mutableStateOf<String?>(null) }
    var showRefSourceDialog by remember { mutableStateOf(false) }
    var showRegionSelectorDialog by remember { mutableStateOf(false) }
    var showNoInitialRefGuideDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val localRefImagePicker = rememberFilePicker(
        title = "选择参考底图",
        isFolder = false,
        extensions = listOf("png", "jpg", "jpeg", "webp")
    ) { uri ->
        if (!uri.isNullOrBlank()) {
            viewModel.updateActiveReferenceImage(uri)
            selectedVariantRefImage = null
            Toast.show("已更新参考底图", ToastType.SUCCESS)
        }
    }

    // 当打开历史资产窗口时，自动加载当前模块磁盘目录下的全量历史图片（与属性面板完全一致）
    LaunchedEffect(showAssetGalleryDialog) {
        if (showAssetGalleryDialog) {
            moduleHistoricalImages = viewModel.loadModuleHistoricalImages()
        }
    }

    // 左侧会话抽屉宽度比例（默认 0.2f，支持鼠标自由拖拽）
    var leftSidebarWeight by remember { mutableStateOf(0.2f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶栏 Header（标准 16dp / 12dp 呼吸感边距）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = stringResource(Res.string.ai_studio_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (block != null) {
                            Spacer(Modifier.width(spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = AppShapes.small
                            ) {
                                Text(
                                    text = block.id.ifBlank { block.type.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        // 【会话 JSON 交互日志】查看按钮
                        IconButton(
                            onClick = { showLogDialog = true },
                            modifier = Modifier.size(32.dp).tip("查看当前会话完整交互 JSON 数据与 Prompt 结构")
                        ) {
                            Icon(
                                Icons.Default.DataObject,
                                contentDescription = "JSON Log",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 【历史生成资产】按需呼出操作按钮
                        FilledTonalButton(
                            onClick = { showAssetGalleryDialog = true },
                            shape = AppShapes.small,
                            modifier = Modifier.height(32.dp).tip("查看并选择当前模块/会话的历史生成资产"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "历史生成资产 (${state.sessionGeneratedImages.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).tip(stringResource(Res.string.btn_close_dialog))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. 主体工作区（支持拖拽调节左侧栏宽度比例）
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左侧会话抽屉 (响应式动态权重，全量支持多会话列表与删除)
                        Box(modifier = Modifier.weight(leftSidebarWeight).fillMaxHeight()) {
                            StudioSessionSidebar(
                                sessions = state.historySessions,
                                currentSession = state.currentSession,
                                hasCompressedContext = state.hasCompressedContext,
                                onNewChat = { viewModel.createNewChat() },
                                onSelectSession = { viewModel.switchSession(it) },
                                onDeleteSession = { viewModel.deleteSession(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 水平可拖动分割手柄 (Draggable Divider)
                        Box(
                            modifier = Modifier
                                .width(LocalAppSpacing.current.extraSmall)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                .pointerHoverIcon(ResizeHorizontalIcon)
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        val deltaWeight = delta / totalWidthPx
                                        leftSidebarWeight = (leftSidebarWeight + deltaWeight).coerceIn(0.15f, 0.38f)
                                    }
                                )
                        )

                        // 中部与底栏核心交互区
                        Column(
                            modifier = Modifier
                                .weight(1f - leftSidebarWeight)
                                .fillMaxHeight()
                                .padding(horizontal = spacing.medium, vertical = spacing.small)
                        ) {
                            // 中部对话气泡流（双方头像 + 自适应宽度 + 占位骨架屏 + 二次点击微调取消）
                            StudioChatMessageList(
                                messages = state.currentSession?.messages ?: emptyList(),
                                isGenerating = state.isGenerating,
                                statusLog = state.statusLog,
                                streamingText = state.streamingText,
                                pendingCount = state.pendingCount,
                                currentVariantImageUri = selectedVariantRefImage,
                                onImageClick = { imgUri -> lightboxImageUri = imgUri },
                                onApplyImage = { imageUri ->
                                    coroutineScope.launch {
                                        val targetW = block?.bounds?.width?.toInt() ?: 0
                                        val targetH = block?.bounds?.height?.toInt() ?: 0
                                        val size = try {
                                            org.gemini.ui.forge.utils.getImageSize(imageUri)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        val actualW = size?.first ?: 0
                                        val actualH = size?.second ?: 0
                                        if (targetW > 0 && targetH > 0 && (actualW != targetW || actualH != targetH)) {
                                            // 尺寸不符，弹出切图加工与烘焙界面
                                            pendingEditorImageUri = imageUri
                                            Toast.show("图片尺寸 ($actualW×$actualH) 与当前模块 ($targetW×$targetH) 不一致，正在打开切图加工...", ToastType.INFO)
                                        } else {
                                            onApplyAsset(imageUri)
                                        }
                                    }
                                },
                                onVariantImage = { imageUri ->
                                    if (selectedVariantRefImage == imageUri || imageUri.isBlank()) {
                                        selectedVariantRefImage = null
                                        Toast.show("已取消微调，恢复初始参考底图", ToastType.INFO)
                                    } else {
                                        selectedVariantRefImage = imageUri
                                        Toast.show("已将该图片作为微调参考图", ToastType.INFO)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(Modifier.height(spacing.small))

                            val effectiveRefImage = selectedVariantRefImage ?: state.activeReferenceImageUri ?: initialReferenceImageUri

                            // 底部智能输入中枢（包含场景下拉器 + 从零新图/以图生图双模分段切换 + 纯净参考图卡片与弹窗配置 + 独立模型/张数 + 中止按钮）
                            StudioInputBottomBar(
                                promptZh = block?.userPromptZh ?: "",
                                promptEn = block?.userPromptEn ?: "",
                                referenceImageUri = effectiveRefImage,
                                previewMemoryBytes = state.previewMemoryBytes,
                                isImageToImage = state.isImageToImageMode,
                                onModeChanged = { viewModel.setCreationMode(it) },
                                onOpenRefConfig = { showRefSourceDialog = true },
                                isVariantMode = selectedVariantRefImage != null,
                                onRevertToOriginal = {
                                    selectedVariantRefImage = null
                                    Toast.show("已还原为模块初始参考底图", ToastType.INFO)
                                },
                                onReferenceImageClick = { lightboxImageUri = it.toString() },
                                initialLanguage = currentLang,
                                selectedModel = state.selectedModel,
                                onModelSelected = { viewModel.updateModel(it) },
                                generationCount = state.generationCount,
                                onCountSelected = { viewModel.updateGenerationCount(it) },
                                isGenerating = state.isGenerating,
                                isOptimizingPrompt = state.isOptimizingPrompt,
                                storage = storage,
                                onOptimizeRequested = { text, _ ->
                                    viewModel.optimizePrompt(text, apiKey) {}
                                },
                                onCancel = { viewModel.cancelCurrentGeneration() },
                                onSend = { zh, en, activeLang, isI2I, isPng, cloudBg, uploadCloud, model, count ->
                                    viewModel.sendGenerationRequest(
                                        promptZh = zh,
                                        promptEn = en,
                                        activeLang = activeLang,
                                        apiKey = apiKey,
                                        model = model,
                                        generationCount = count,
                                        referenceImageUri = effectiveRefImage,
                                        isImageToImage = isI2I,
                                        isPng = isPng,
                                        useCloudBgRemoval = cloudBg,
                                        isUploadToCloud = uploadCloud
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. 全屏高清图片灯箱覆盖层
        if (!lightboxImageUri.isNullOrBlank()) {
            StudioImageLightbox(
                imageUri = lightboxImageUri!!,
                onDismiss = { lightboxImageUri = null }
            )
        }

        // 4. 参考底图配置与专业来源选择弹窗
        if (showRefSourceDialog) {
            StudioReferenceSourceDialog(
                canCropFromPage = !pageSourceImageUri.isNullOrBlank() && block != null,
                hasCurrentReference = state.previewMemoryBytes != null || !state.activeReferenceImageUri.isNullOrBlank(),
                hasInitialReference = !initialReferenceImageUri.isNullOrBlank(),
                onStartRegionSelect = { showRegionSelectorDialog = true },
                onPickLocalImage = { localRefImagePicker() },
                onRestoreInitial = {
                    coroutineScope.launch {
                        val initUri = initialReferenceImageUri?.ifBlank { null }
                        val exists = if (initUri != null) isFileExists(initUri) else false
                        if (exists) {
                            viewModel.updateActiveReferenceImage(initUri)
                            selectedVariantRefImage = null
                            Toast.show("已还原为模块初始参考底图", ToastType.INFO)
                        } else {
                            // 发现没有设置过或文件已丢失，弹窗引导去设置！
                            showNoInitialRefGuideDialog = true
                        }
                    }
                },
                onClearReference = {
                    viewModel.clearReferenceImage()
                    selectedVariantRefImage = null
                    Toast.show("已清除参考底图", ToastType.INFO)
                },
                onDismiss = { showRefSourceDialog = false }
            )
        }

        // 5. 自由交互式选区画框对话框（延时按需切图：仅内存暂存预览，发送时自动物理切图）
        if (showRegionSelectorDialog && !pageSourceImageUri.isNullOrBlank() && block != null) {
            BlockRefinementDialog(
                block = block,
                isReferenceAreaOnly = true,
                imageUri = TemplateFile(pageSourceImageUri),
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                onDismiss = { showRegionSelectorDialog = false },
                onConfirm = { updatedBlock ->
                    showRegionSelectorDialog = false
                    coroutineScope.launch {
                        viewModel.setPendingReferenceArea(
                            pageSourceUri = pageSourceImageUri,
                            bounds = updatedBlock.bounds,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight
                        )
                        selectedVariantRefImage = null
                        Toast.show("已在内存中暂存选区（发送时自动物理切图）", ToastType.SUCCESS)
                    }
                }
            )
        }

        // 6. 初始参考图未设置引导对话框
        if (showNoInitialRefGuideDialog) {
            AppConfirmDialog(
                title = "尚未设置初始参考图",
                message = "该模块此前尚未设置过专属初始参考区域。是否现在前往页面原图进行画框框选？",
                confirmText = "前往设置参考图",
                onConfirm = {
                    showNoInitialRefGuideDialog = false
                    showRegionSelectorDialog = true
                },
                onDismiss = { showNoInitialRefGuideDialog = false }
            )
        }

        // 4. 标准历史生成资产选择弹窗 (全量对齐属性面板历史数据源与功能)
        if (showAssetGalleryDialog) {
            AssetSelectionDialog(
                title = "当前模块历史生成资产",
                candidates = moduleHistoricalImages,
                targetWidth = block?.bounds?.width ?: 0f,
                targetHeight = block?.bounds?.height ?: 0f,
                isProcessing = isProcessingHistoricalBg,
                onImageSelected = { selectedFile ->
                    onApplyAsset(selectedFile.getAbsolutePath())
                    showAssetGalleryDialog = false
                    Toast.show("已成功应用选中的历史资产", ToastType.SUCCESS)
                    onDismiss()
                },
                onBatchRemoveBg = { uris ->
                    coroutineScope.launch {
                        val file = uris.firstOrNull() ?: return@launch
                        try {
                            isProcessingHistoricalBg = true
                            Toast.show("正在启动本地 AI 抠图引擎...", ToastType.INFO)
                            val bytes = file.readBytes() ?: throw Exception("无法读取文件数据")
                            val noBgBytes = aiService.removeBackgroundLocal(bytes) ?: throw Exception("本地抠图失败")
                            templateRepo.saveBlockResource(
                                templateName = projectName,
                                blockId = block?.id ?: "chat_gen",
                                fileNamePrefix = "nobg",
                                bytes = noBgBytes,
                                isPng = true
                            )
                            moduleHistoricalImages = viewModel.loadModuleHistoricalImages()
                            isProcessingHistoricalBg = false
                            Toast.show("本地去背景成功！已生成透明 PNG", ToastType.SUCCESS)
                        } catch (e: Exception) {
                            isProcessingHistoricalBg = false
                            Toast.show("本地去背景失败: ${e.message}", ToastType.ERROR)
                        }
                    }
                },
                onDeleteImages = { filesToDelete ->
                    coroutineScope.launch {
                        filesToDelete.forEach { it.delete() }
                        moduleHistoricalImages = viewModel.loadModuleHistoricalImages()
                        Toast.show("已删除选中的资产文件", ToastType.SUCCESS)
                    }
                },
                onClearAll = {
                    coroutineScope.launch {
                        moduleHistoricalImages.forEach { it.delete() }
                        moduleHistoricalImages = emptyList()
                        Toast.show("已清空该模块的所有历史资产", ToastType.SUCCESS)
                    }
                },
                onDismiss = { showAssetGalleryDialog = false }
            )
        }

        // 5. 真实 API 网络通信日志弹窗 (StudioSessionLogDialog)
        if (showLogDialog) {
            StudioSessionLogDialog(
                session = state.currentSession,
                rawNetworkLog = state.rawNetworkLog,
                onDismiss = { showLogDialog = false }
            )
        }

        // 6. 尺寸不一致时弹出的加工与烘焙切图界面 (ImageEditorDialog)
        if (pendingEditorImageUri != null && block != null) {
            ImageEditorDialog(
                block = block,
                initialImageUri = pendingEditorImageUri!!,
                onDismiss = { pendingEditorImageUri = null },
                onConfirm = { bytes, mode, config, cropBytes ->
                    coroutineScope.launch {
                        try {
                            val savedFile = templateRepo.saveBlockResource(
                                templateName = projectName,
                                blockId = block.id,
                                fileNamePrefix = "baked",
                                bytes = bytes,
                                isPng = true
                            )
                            onApplyAsset(savedFile.getAbsolutePath())
                            pendingEditorImageUri = null
                        } catch (e: Exception) {
                            Toast.show("保存烘焙切图失败: ${e.message}", ToastType.ERROR)
                        }
                    }
                }
            )
        }
    }
}

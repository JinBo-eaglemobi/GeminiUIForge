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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
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
import org.gemini.ui.forge.ui.dialog.asset.ResourceSlotSelectionDialog
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
    onApplyAssetToSlots: ((imagePath: String, selectedSlots: List<Int>) -> Unit)? = null,
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
    var lightboxImageModel by remember { mutableStateOf<Any?>(null) }
    var showAssetGalleryDialog by remember { mutableStateOf(false) }
    var moduleHistoricalImages by remember { mutableStateOf<List<TemplateFile>>(emptyList()) }
    var isProcessingHistoricalBg by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var pendingEditorImageUri by remember { mutableStateOf<String?>(null) }
    var pendingSlotSelectionImagePath by remember { mutableStateOf<String?>(null) }
    var showRefSourceDialog by remember { mutableStateOf(false) }
    var showRegionSelectorDialog by remember { mutableStateOf(false) }
    var showNoInitialRefGuideDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 退出防误触拦截：若 AI 正在生图或优化中，强制弹窗二次确认并安全中止
    val handleSafeDismiss: () -> Unit = {
        if (state.isGenerating || state.isOptimizingPrompt) {
            showExitConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    // 智能拦截分发：检查当前图元是否具有多个资源状态槽位
    val handleFinalApplyAsset: (String) -> Unit = { finalPath ->
        val slots = block?.assetStates ?: emptyList()
        if (slots.size > 1 && onApplyAssetToSlots != null) {
            pendingSlotSelectionImagePath = finalPath
        } else {
            onApplyAsset(finalPath)
        }
    }

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
        onDismissRequest = handleSafeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val studioDialogWidth = maxWidth
                val studioDialogHeight = maxHeight

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

                    IconButton(
                        onClick = handleSafeDismiss,
                        modifier = Modifier.size(32.dp).tip(stringResource(Res.string.btn_close_dialog))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                onImageClick = { imgUri -> lightboxImageModel = imgUri },
                                 onApplyImage = { imageUri ->
                                     coroutineScope.launch {
                                         val validUri = viewModel.ensureImageCached(imageUri)
                                         // 防空拦截：若无法恢复且文件不存在，弹出明确错误提示并拦截，严禁打开空白编辑器
                                         val isFileExisted = org.gemini.ui.forge.utils.isFileExists(validUri)
                                         if (!isFileExisted && !validUri.startsWith("data:image")) {
                                             Toast.show("图片文件不存在且无法恢复", ToastType.ERROR)
                                             return@launch
                                         }

                                         val targetW = block?.bounds?.width?.toInt() ?: 0
                                         val targetH = block?.bounds?.height?.toInt() ?: 0
                                         val size = try {
                                             org.gemini.ui.forge.utils.getImageSize(validUri)
                                         } catch (e: Exception) {
                                             null
                                         }

                                         if (size == null) {
                                             // 无法解析尺寸，若文件存在则直接应用，避免误入空白切图页面
                                             if (isFileExisted) {
                                                 handleFinalApplyAsset(validUri)
                                             } else {
                                                 Toast.show("无法读取图片尺寸或文件已损坏", ToastType.ERROR)
                                             }
                                             return@launch
                                         }

                                         val actualW = size.first
                                         val actualH = size.second

                                         // 背景模块特殊保护：若是背景模块且比例近似或尺寸匹配，直接应用；或者尺寸完全一致时直接应用
                                         val isBackground = block?.type == org.gemini.ui.forge.model.ui.UIBlockType.BACKGROUND
                                         val isSizeMatch = actualW == targetW && actualH == targetH
                                         val isRatioMatch = isBackground && targetW > 0 && targetH > 0 && 
                                                 kotlin.math.abs((actualW.toFloat() / actualH) - (targetW.toFloat() / targetH)) < 0.05f

                                         if (targetW > 0 && targetH > 0 && !isSizeMatch && !isRatioMatch) {
                                             // 尺寸不符且非等比背景，弹出切图加工与烘焙界面
                                             pendingEditorImageUri = validUri
                                             Toast.show("图片尺寸 ($actualW×$actualH) 与当前模块 ($targetW×$targetH) 不一致，正在打开切图加工...", ToastType.INFO)
                                         } else {
                                             handleFinalApplyAsset(validUri)
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

                            // 底部智能输入中枢（自然多模态双层聊天输入）
                            StudioInputBottomBar(
                                promptZh = block?.userPromptZh ?: "",
                                promptEn = block?.userPromptEn ?: "",
                                originalBlockPromptZh = block?.userPromptZh,
                                originalBlockPromptEn = block?.userPromptEn,
                                referenceImageUri = effectiveRefImage,
                                previewMemoryBytes = state.previewMemoryBytes,
                                onReferenceImageClick = { lightboxImageModel = it },
                                canCropFromPage = !pageSourceImageUri.isNullOrBlank() && block != null,
                                onStartRegionCrop = { showRegionSelectorDialog = true },
                                onOpenRefConfig = { showRefSourceDialog = true },
                                selectedModel = state.selectedModel,
                                onModelSelected = { viewModel.updateModel(it) },
                                generationCount = state.generationCount,
                                onCountSelected = { viewModel.updateGenerationCount(it) },
                                currentThinkingLevel = state.thinkingLevel,
                                onThinkingLevelSelected = { viewModel.updateThinkingLevel(it) },
                                isGenerating = state.isGenerating,
                                isOptimizingPrompt = state.isOptimizingPrompt,
                                storage = storage,
                                onOptimizeRequested = { text, isZh, onOptimized ->
                                    if (apiKey.isBlank()) {
                                        Toast.show("请先在设置中配置 Gemini API Key", ToastType.ERROR)
                                        return@StudioInputBottomBar
                                    }
                                    Toast.show("AI 正在流式优化提示词...", ToastType.INFO)
                                    viewModel.optimizePrompt(
                                        sourceText = text,
                                        apiKey = apiKey,
                                        onChunk = { partial ->
                                            onOptimized(partial)
                                        },
                                        onResult = { finalOptimized ->
                                            onOptimized(finalOptimized)
                                            Toast.show("提示词优化完成", ToastType.SUCCESS)
                                        }
                                    )
                                },
                                onCancel = { viewModel.cancelCurrentGeneration() },
                                onOpenLogs = { showLogDialog = true },
                                onOpenAssetGallery = { showAssetGalleryDialog = true },
                                historicalAssetCount = state.sessionGeneratedImages.size,
                                dialogWidth = studioDialogWidth,
                                dialogHeight = studioDialogHeight,
                                onSend = { userPrompt, activeLang, pendingUri, pendingBytes, isPng, uploadCloud, model, count ->
                                    val finalRefUri = pendingUri ?: effectiveRefImage
                                    viewModel.sendGenerationRequest(
                                        userPrompt = userPrompt,
                                        activeLang = activeLang,
                                        apiKey = apiKey,
                                        model = model,
                                        generationCount = count,
                                        referenceImageUri = finalRefUri,
                                        isImageToImage = pendingUri != null || pendingBytes != null || !finalRefUri.isNullOrBlank(),
                                        isPng = isPng,
                                        isUploadToCloud = uploadCloud,
                                        customImageBytes = pendingBytes
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

        // 3. 全屏高清图片灯箱覆盖层（支持文件路径与内存切片字节流，纯滚轮缩放+手绘标注+保存联动）
        if (lightboxImageModel != null) {
            StudioImageLightbox(
                imageModel = lightboxImageModel!!,
                projectName = projectName,
                storage = storage,
                onSaveAsReference = { annotatedPath ->
                    // 自动转为当前专属参考底图，并转入本地图片模式，退出其他微调模式
                    selectedVariantRefImage = null
                    viewModel.setCustomReferenceImage(annotatedPath)
                    Toast.show("已将手绘标注图设为当前会话参考图", ToastType.SUCCESS)
                },
                onDismiss = { lightboxImageModel = null }
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
                scopeId = scopeId,
                sessionId = state.currentSession?.id ?: "default_sess",
                trafficStore = viewModel.trafficStore,
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
                            handleFinalApplyAsset(savedFile.getAbsolutePath())
                            pendingEditorImageUri = null
                        } catch (e: Exception) {
                            Toast.show("保存烘焙切图失败: ${e.message}", ToastType.ERROR)
                        }
                    }
                }
            )
        }

        // 7. 多资源状态槽位多选应用对话框 (ResourceSlotSelectionDialog)
        if (pendingSlotSelectionImagePath != null && block != null) {
            ResourceSlotSelectionDialog(
                block = block,
                imagePath = pendingSlotSelectionImagePath!!,
                onDismiss = { pendingSlotSelectionImagePath = null },
                onConfirm = { selectedSlots ->
                    val path = pendingSlotSelectionImagePath!!
                    pendingSlotSelectionImagePath = null
                    onApplyAssetToSlots?.invoke(path, selectedSlots)
                }
            )
        }

        // 8. 退出防误触与任务安全中止拦截弹窗
        if (showExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(spacing.small))
                        Text("AI 任务正在运行中", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text("当前 AI 正在生成图像或处理任务中，强制关闭窗口将自动中止当前任务。确定要退出吗？")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cancelCurrentGeneration()
                            showExitConfirmDialog = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("强制关闭并终止", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text("继续等待")
                    }
                }
            )
        }
    }
}

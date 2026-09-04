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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionLogDialog
import org.gemini.ui.forge.ui.dialog.asset.AssetSelectionDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
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
    var showLogDialog by remember { mutableStateOf(false) }

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
                        // 左侧会话抽屉 (响应式动态权重)
                        Box(modifier = Modifier.weight(leftSidebarWeight).fillMaxHeight()) {
                            StudioSessionSidebar(
                                currentSession = state.currentSession,
                                hasCompressedContext = state.hasCompressedContext,
                                onNewChat = { viewModel.createNewChat() },
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
                                    onApplyAsset(imageUri)
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

                            // 底部智能输入中枢（包含左侧固定参考图卡片 + 自适应双列宽屏场景下拉器 + 独立模型/张数配置 + 中止按钮 + 云端上传开关）
                            StudioInputBottomBar(
                                promptZh = block?.userPromptZh ?: "",
                                promptEn = block?.userPromptEn ?: "",
                                referenceImageUri = selectedVariantRefImage ?: initialReferenceImageUri,
                                isVariantMode = selectedVariantRefImage != null,
                                onRevertToOriginal = {
                                    selectedVariantRefImage = null
                                    Toast.show("已还原为模块初始参考底图", ToastType.INFO)
                                },
                                onReferenceImageClick = { lightboxImageUri = it },
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
                                onSend = { zh, en, activeLang, isPng, cloudBg, uploadCloud, model, count ->
                                    viewModel.sendGenerationRequest(
                                        promptZh = zh,
                                        promptEn = en,
                                        activeLang = activeLang,
                                        apiKey = apiKey,
                                        model = model,
                                        generationCount = count,
                                        referenceImageUri = selectedVariantRefImage ?: initialReferenceImageUri,
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

        // 4. 标准历史生成资产选择弹窗 (AssetSelectionDialog)
        if (showAssetGalleryDialog) {
            val candidateFiles = state.sessionGeneratedImages.map { TemplateFile(it) }
            AssetSelectionDialog(
                title = "当前模块历史生成资产",
                candidates = candidateFiles,
                targetWidth = block?.bounds?.width ?: 0f,
                targetHeight = block?.bounds?.height ?: 0f,
                onImageSelected = { selectedFile ->
                    onApplyAsset(selectedFile.getAbsolutePath())
                    showAssetGalleryDialog = false
                    Toast.show("已成功应用选中的历史资产", ToastType.SUCCESS)
                    onDismiss()
                },
                onDeleteImages = {},
                onClearAll = {},
                onDismiss = { showAssetGalleryDialog = false }
            )
        }

        // 5. 会话完整 JSON 交互日志弹窗 (StudioSessionLogDialog)
        if (showLogDialog) {
            StudioSessionLogDialog(
                session = state.currentSession,
                onDismiss = { showLogDialog = false }
            )
        }
    }
}

package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.manager.MattingPreset
import org.gemini.ui.forge.manager.PromptPresetManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.ui.dialog.ai.component.BilingualPromptEditor
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioPresetDropdownMenu
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionConfigRow
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.rememberFilePicker
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室底部智能输入中枢（自适应宽屏 Bento 场景预设 + 独立模型与张数控制 + 固定参考图 + 中止按钮）
 */
@Composable
fun StudioInputBottomBar(
    promptZh: String,
    promptEn: String,
    referenceImageUri: String? = null,
    previewMemoryBytes: ByteArray? = null,
    isImageToImage: Boolean = true,
    onModeChanged: (Boolean) -> Unit = {},
    onOpenRefConfig: () -> Unit = {},
    isVariantMode: Boolean = false,
    onRevertToOriginal: (() -> Unit)? = null,
    onReferenceImageClick: ((Any) -> Unit)? = null,
    initialLanguage: PromptLanguage = PromptLanguage.ZH,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    generationCount: Int,
    onCountSelected: (Int) -> Unit,
    isGenerating: Boolean,
    isOptimizingPrompt: Boolean,
    storage: LocalFileStorage,
    onOptimizeRequested: (sourceText: String, isZh: Boolean) -> Unit,
    onSend: (zh: String, en: String, activeLang: PromptLanguage, isImageToImage: Boolean, isPng: Boolean, useCloudBgRemoval: Boolean, isUploadToCloud: Boolean, model: GeminiModel, count: Int) -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var currentZh by remember(promptZh) { mutableStateOf(promptZh) }
    var currentEn by remember(promptEn) { mutableStateOf(promptEn) }
    var currentActiveLang by remember(initialLanguage) { mutableStateOf(initialLanguage) }

    var isPng by remember { mutableStateOf(true) }
    var isUploadToCloud by remember { mutableStateOf(false) }
    val useCloudBgRemoval = false // 当前项目环境临时禁止选择

    val presetManager = remember { PromptPresetManager(storage) }
    var presets by remember { mutableStateOf<List<MattingPreset>>(emptyList()) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    // 物理文件真实存在性探测（若已被磁盘物理删除，呈现丢失警示）
    var isRefImageMissing by remember(referenceImageUri, previewMemoryBytes) { mutableStateOf(false) }

    LaunchedEffect(referenceImageUri, previewMemoryBytes) {
        if (previewMemoryBytes == null && !referenceImageUri.isNullOrBlank()) {
            isRefImageMissing = !isFileExists(referenceImageUri)
        } else {
            isRefImageMissing = false
        }
    }

    LaunchedEffect(Unit) {
        presets = presetManager.loadPresets()
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        val adaptiveMenuWidth = (containerWidth * 0.88f).coerceIn(560.dp, 880.dp)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = AppShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 1. 顶部工具栏：【创建新图 vs 以图生图双模分段切换】 + 【场景预设库】 + 【独立模型与张数控制】
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧与中间：双模切换大胶囊 + 专业场景预设下拉器
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        // 核心模式切换大胶囊（高度 36dp，彻底屏蔽 M3 自带重叠勾选图标）
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(36.dp)) {
                            SegmentedButton(
                                selected = !isImageToImage,
                                onClick = { onModeChanged(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                icon = {}, // ★ 彻底屏蔽自带的勾选打勾图标，杜绝重叠！
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "从零创建新图",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (!isImageToImage) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                },
                                modifier = Modifier.widthIn(min = 120.dp).tip("纯文字从零直接创建新图（不携带参考底图，全宽展开输入区）")
                            )
                            SegmentedButton(
                                selected = isImageToImage,
                                onClick = { onModeChanged(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                icon = {}, // ★ 彻底屏蔽自带的勾选打勾图标，杜绝重叠！
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Image, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "以图生图/微调",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isImageToImage) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                },
                                modifier = Modifier.widthIn(min = 120.dp).tip("基于参考底图进行风格重塑、局部修改或抠图")
                            )
                        }

                        // 专业场景预设下拉器
                        if (presets.isNotEmpty()) {
                            StudioPresetDropdownMenu(
                                presets = presets,
                                selectedPresetId = selectedPresetId,
                                adaptiveWidth = adaptiveMenuWidth,
                                onPresetSelected = { preset ->
                                    selectedPresetId = preset.id
                                    currentZh = preset.promptZh
                                    currentEn = preset.promptEn
                                }
                            )
                        }
                    }

                    // 右侧：当前会话专属模型切换器与单次生图数量胶囊
                    StudioSessionConfigRow(
                        selectedModel = selectedModel,
                        onModelSelected = onModelSelected,
                        generationCount = generationCount,
                        onCountSelected = onCountSelected
                    )
                }

                // 2. 中部核心区：模式自适应展开（从零生图全宽铺开；以图生图展示 135dp 纯净视口卡片）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isImageToImage) {
                        val hasRef = previewMemoryBytes != null || (!referenceImageUri.isNullOrBlank() && !isRefImageMissing)
                        val previewModel: Any? = previewMemoryBytes ?: referenceImageUri

                        // 以图生图模式：纯净大方视口卡片（宽 135dp，高 135dp，点击弹窗统一配置）
                        Surface(
                            modifier = Modifier
                                .width(135.dp)
                                .height(135.dp)
                                .clip(AppShapes.medium)
                                .border(
                                    1.dp,
                                    if (isRefImageMissing) MaterialTheme.colorScheme.error
                                    else if (hasRef) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    AppShapes.medium
                                )
                                .clickable { onOpenRefConfig() }
                                .tip(if (isRefImageMissing) "参考图文件已丢失，点击重新配置或框选" else "点击配置、重新框选或更换参考底图"),
                            color = if (isRefImageMissing) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRefImageMissing) {
                                    // 物理文件已丢失状态
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "参考图资源已丢失",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "点击重新框选/更换",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else if (hasRef && previewModel != null) {
                                    AsyncImage(
                                        model = previewModel,
                                        contentDescription = "Reference Image Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    // 左上角状态徽章
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp),
                                        shape = AppShapes.extraSmall,
                                        color = if (previewMemoryBytes != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                                else if (isVariantMode) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                                    ) {
                                        Text(
                                            text = if (previewMemoryBytes != null) "框选预览"
                                                   else if (isVariantMode) "微调底图"
                                                   else "参考底图",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (previewMemoryBytes != null) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else if (isVariantMode) MaterialTheme.colorScheme.onTertiaryContainer
                                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    // 右上角还原（微调模式可用）
                                    if (isVariantMode && onRevertToOriginal != null) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(22.dp)
                                                .clickable { onRevertToOriginal() }
                                                .tip("撤销微调，还原为初始底图"),
                                            shape = AppShapes.small,
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Icon(
                                                Icons.Default.Restore,
                                                contentDescription = "Restore Original",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }

                                    // 右下角放大查看
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 24.dp, end = 4.dp)
                                            .size(20.dp)
                                            .clickable { onReferenceImageClick?.invoke(previewModel) }
                                            .tip("点击全屏放大查看"),
                                        shape = AppShapes.small,
                                        color = Color.Black.copy(alpha = 0.65f)
                                    ) {
                                        Icon(
                                            Icons.Default.ZoomIn,
                                            contentDescription = "Zoom In",
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }

                                    // 底部悬浮磨砂操作条（提示点击弹窗配置）
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        color = Color.Black.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = "点击更换/框选",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    // 尚未设置参考图
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "未设置参考底图",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "点击配置/框选",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 右侧双语提示词编辑器（唯一的提示词编辑器，从零生图时填满 100% 宽度，以图生图时填满剩余空间）
                    Box(modifier = Modifier.weight(1f)) {
                        BilingualPromptEditor(
                            promptZh = currentZh,
                            promptEn = currentEn,
                            initialLanguage = initialLanguage,
                            onLanguageChanged = { currentActiveLang = it },
                            onPromptConfirmed = { newZh, newEn ->
                                currentZh = newZh
                                currentEn = newEn
                            },
                            onOptimizeRequested = onOptimizeRequested,
                            isOptimizing = isOptimizingPrompt,
                            showExplicitConfirmButton = false
                        )
                    }
                }

                // 3. 底部选项开关与发送/中止按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.tip("生成透明 PNG 图像（去除背景色）")
                        ) {
                            Checkbox(
                                checked = isPng,
                                onCheckedChange = { isPng = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("透明 PNG", style = MaterialTheme.typography.labelMedium)
                        }

                        // 是否上传至云端（默认不选中）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.tip("将参考图上传至云端存储，方便跨会话多次复用与历史引用")
                        ) {
                            Checkbox(
                                checked = isUploadToCloud,
                                onCheckedChange = { isUploadToCloud = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("上传参考图至云端", style = MaterialTheme.typography.labelMedium)
                        }

                        // 临时禁用的云端抠图选项
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .alpha(0.5f)
                                .tip("当前项目环境暂不可用（临时禁用）")
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = {},
                                enabled = false,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("大模型抠图 (暂不可用)", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (isGenerating) {
                        // 正在生成时展示红色【中止生成】按钮
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.height(38.dp).tip("中止当前 AI 生成任务"),
                            shape = AppShapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(
                                text = "中止生成",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isImageToImage) {
                                    val hasValidRef = previewMemoryBytes != null || (!referenceImageUri.isNullOrBlank() && !isRefImageMissing)
                                    if (!hasValidRef) {
                                        Toast.show("当前以图生图参考底图不存在或已丢失，请重新配置参考图，或切换为「从零创建新图」", ToastType.ERROR)
                                        return@Button
                                    }
                                }
                                onSend(currentZh, currentEn, currentActiveLang, isImageToImage, isPng, useCloudBgRemoval, isUploadToCloud, selectedModel, generationCount)
                            },
                            enabled = currentZh.isNotBlank() || currentEn.isNotBlank(),
                            modifier = Modifier.height(38.dp).tip(stringResource(Res.string.ai_studio_send_btn)),
                            shape = AppShapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(
                                text = stringResource(Res.string.ai_studio_send_btn),
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

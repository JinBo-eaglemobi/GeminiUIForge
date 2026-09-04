package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.manager.MattingPreset
import org.gemini.ui.forge.manager.PromptPresetManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.component.BilingualPromptEditor
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioPresetDropdownMenu
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionConfigRow
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室底部智能输入中枢（自适应宽屏 Bento 场景预设 + 独立模型与张数控制 + 固定参考图 + 中止按钮）
 */
@Composable
fun StudioInputBottomBar(
    promptZh: String,
    promptEn: String,
    referenceImageUri: String? = null,
    isVariantMode: Boolean = false,
    onRevertToOriginal: (() -> Unit)? = null,
    onReferenceImageClick: ((String) -> Unit)? = null,
    initialLanguage: PromptLanguage = PromptLanguage.ZH,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    generationCount: Int,
    onCountSelected: (Int) -> Unit,
    isGenerating: Boolean,
    isOptimizingPrompt: Boolean,
    storage: LocalFileStorage,
    onOptimizeRequested: (sourceText: String, isZh: Boolean) -> Unit,
    onSend: (zh: String, en: String, activeLang: PromptLanguage, isPng: Boolean, useCloudBgRemoval: Boolean, isUploadToCloud: Boolean, model: GeminiModel, count: Int) -> Unit,
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
                // 1. 顶部工具栏：【自适应宽屏 Bento 场景预设库】 + 【会话独立模型与 1/2/4 张分段单选】
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：专业场景预设下拉器（自适应单行卡片弹窗）
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

                    // 右侧：当前会话专属模型切换器与单次生图数量胶囊
                    StudioSessionConfigRow(
                        selectedModel = selectedModel,
                        onModelSelected = onModelSelected,
                        generationCount = generationCount,
                        onCountSelected = onCountSelected
                    )
                }

                // 2. 中部核心区：【左侧固定参考图小卡片】 + 【右侧标准 BilingualPromptEditor】
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧固定参考底图卡片（存在参考图时常驻展示）
                    if (!referenceImageUri.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .width(110.dp)
                                .height(130.dp)
                                .clip(AppShapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
                            .clickable { onReferenceImageClick?.invoke(referenceImageUri) }
                            .tip("当前基准参考图（点击放大查看）"),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(AppShapes.small)
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = referenceImageUri,
                                    contentDescription = "Pinned Reference Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(14.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isVariantMode) "微调参考图" else "基准参考图",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVariantMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                if (isVariantMode && onRevertToOriginal != null) {
                                    IconButton(
                                        onClick = onRevertToOriginal,
                                        modifier = Modifier.size(18.dp).tip("撤销微调，还原为模块初始参考底图")
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = "Restore Original",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 右侧双语提示词编辑器
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
                            onSend(currentZh, currentEn, currentActiveLang, isPng, useCloudBgRemoval, isUploadToCloud, selectedModel, generationCount)
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

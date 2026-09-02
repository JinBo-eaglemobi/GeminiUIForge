package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage

import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AdvancedSettingsDialog
import org.gemini.ui.forge.ui.dialog.ai.ButtonStateGenDialog
import org.gemini.ui.forge.ui.dialog.asset.ImageEditorDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.dialog.ai.ImageToImageGenDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*

/**
 * 渲染资产生成相关的属性内容。
 * 
 * 针对 SPIN_BUTTON 特殊模块进行多分生图重构：
 * 1. 在预览图上方添加 Tab 栏：默认状态 (Spin) 和 停止状态 (Stop)。
 * 2. 切换 Tab 时，当前的对比图片、解绑、历史选择以及下方的 AI 提示词输入，完全根据切换的选项进行更新和读写。
 * 3. 提示词采用 SpinButtonProperties 中对应的 spinPromptZh / spinPromptEn 和 stopPromptZh / stopPromptEn 独立读写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetGenPropertyContent(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String
) {
    val selectedBlock = state.selectedBlock ?: return
    var showImageEditor by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showImg2ImgDialog by remember { mutableStateOf(false) }
    var showNoRefDialog by remember { mutableStateOf(false) }
    var useChatContext by remember { mutableStateOf(false) }
    val spacing = LocalAppSpacing.current

    ButtonStateGenDialog(state, viewModel, apiKey)

    if (showImageEditor) {
        ImageEditorDialog(
            block = selectedBlock,
            onDismiss = { showImageEditor = false },
            onConfirm = { bytes, mode, config, cropBytes ->
                viewModel.assetManager.bakeBlockImage(
                    selectedBlock.id,
                    mode,
                    config,
                    selectedBlock.bounds.width.toInt(),
                    selectedBlock.bounds.height.toInt(),
                    selectedBlock.bounds.width.toInt(),
                    selectedBlock.bounds.height.toInt(),
                    bytes,
                    cropBytes
                )
                showImageEditor = false
            }
        )
    }

    if (showAdvancedSettings) {
        AdvancedSettingsDialog(state, viewModel, onDismiss = { showAdvancedSettings = false })
    }

    val states = selectedBlock.assetStates
    val isMultiState = states.isNotEmpty()

    // Tab 栏状态
    var currentTab by remember(selectedBlock.id) { mutableStateOf(0) }

    // 动态重定向获取当前状态绑定的图片和历史 ID 后缀
    val currentImageToDisplay = remember(selectedBlock.id, currentTab, selectedBlock) {
        selectedBlock.getCurrentImageUri(currentTab)
    }
    val historicalIdSuffix = remember(selectedBlock.id, currentTab) {
        selectedBlock.getHistoricalIdSuffix(currentTab)
    }


    val baseImageGenerated = selectedBlock.getCurrentImageUri(0) != null
    val canGenerate = currentTab == 0 || baseImageGenerated

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Fingerprint,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "正在编辑: ${selectedBlock.id}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            SuggestionChip(
                onClick = { },
                label = {
                    Text(
                        stringResource(selectedBlock.type.getDisplayNameRes()),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                shape = AppShapes.small,
                border = null,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showAdvancedSettings = true },
                modifier = Modifier.weight(1f).height(40.dp).tip("设置全项目通用的 AI 风格关键词和参考图"),
                shape = AppShapes.small
            ) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("全局风格", style = MaterialTheme.typography.labelSmall)
            }
            ModelSelector(state, viewModel, Modifier.weight(1.2f).tip("选择当前生图任务使用的 AI模型"))
        }

        // 当组件有多态资产状态时自适应渲染 Tab 栏
        if (isMultiState) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                TabRow(
                    selectedTabIndex = currentTab,
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { } // 移除传统的底部线条指示器，改用全身高亮背景
                ) {
                    states.forEachIndexed { index, stateInfo ->
                        val isSelected = currentTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { currentTab = index },
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(AppShapes.small)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                ),
                            text = {
                                Text(
                                    text = stateInfo.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .height(180.dp)
                .clip(AppShapes.medium)
                .background(Color.Black.copy(alpha = 0.05f))
                .clickable {
                    if (currentImageToDisplay != null && canGenerate) showImageEditor = true
                }
        ) {
            if (!canGenerate) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请先生成默认/正常状态的图片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else if (currentImageToDisplay != null) {
                AsyncImage(
                    model = currentImageToDisplay.getAbsolutePath(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HideImage,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "尚未绑定资源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.showHistoricalDialog(selectedBlock.id + historicalIdSuffix) },
                modifier = Modifier.weight(1.2f),
                shape = AppShapes.medium,
                enabled = canGenerate
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("历史/切换", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    val updatedBlock = selectedBlock.clearImageUri(currentTab)
                    viewModel.assetManager.updateBlock(updatedBlock)
                },
                modifier = Modifier.weight(0.8f),
                shape = AppShapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                enabled = canGenerate
            ) {
                Text("解绑", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (currentImageToDisplay != null && canGenerate) {
            Button(
                onClick = { showImageEditor = true },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("物理加工与固化", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.alpha(0.2f))

        val systemLang = androidx.compose.ui.text.intl.Locale.current.language
        val effectiveLang = state.currentLang.resolve(systemLang)

        // 采用 remember(selectedBlock.id, currentTab, effectiveLang) 精准缓存和计算，保证重构与选择反馈
        val prompt = remember(selectedBlock.id, currentTab, effectiveLang, selectedBlock) {
            selectedBlock.getPrompt(currentTab, effectiveLang)
        }
        val otherPrompt = remember(selectedBlock.id, currentTab, effectiveLang, selectedBlock) {
            selectedBlock.getOtherPrompt(currentTab, effectiveLang)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "AI 提示词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang ->
                        SegmentedButton(
                            selected = effectiveLang == lang,
                            onClick = { viewModel.switchLang(lang) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            SelectAllOutlinedTextField(
                value = prompt,
                onValueChange = { newValue ->
                    val updatedBlock = selectedBlock.updatePrompt(currentTab, effectiveLang, newValue)
                    viewModel.assetManager.updateBlock(updatedBlock)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    if (otherPrompt.isNotBlank()) {
                        Text(
                            "当前语言为空，系统将使用: ${otherPrompt.take(20)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    } else {
                        Text(stringResource(Res.string.prop_prompt_hint), style = MaterialTheme.typography.bodySmall)
                    }
                },
                maxLines = 8,
                enabled = !state.isGenerating && canGenerate
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useChatContext, onCheckedChange = { useChatContext = it }, enabled = canGenerate)
                Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        // 将优化结果反馈写入对应的 blockPrompt 字段
                        viewModel.layoutEditor.optimizePrompt(selectedBlock.id, apiKey, effectiveLang, useChatContext)
                    },
                    enabled = !state.isGenerating && canGenerate && (prompt.isNotBlank() || otherPrompt.isNotBlank())
                ) {
                    Icon(Icons.Default.AutoFixHigh, "优化提示词", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = AppShapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isGenerateTransparent,
                        onCheckedChange = { checked -> viewModel.updateState { it.copy(isGenerateTransparent = checked) } },
                        enabled = canGenerate
                    )
                    Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodySmall)
                }
                if (state.isGenerateTransparent) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                        Checkbox(
                            checked = state.isPrioritizeCloudRemoval,
                            onCheckedChange = { checked -> viewModel.updateState { it.copy(isPrioritizeCloudRemoval = checked) } },
                            enabled = canGenerate
                        )
                        Text("优先云端抠图", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.assetGen.onRequestGeneration(apiKey, if (prompt.isNotBlank()) prompt else otherPrompt, currentTab)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).tip("根据提示词直接生成全新 AI 资源图"),
            shape = AppShapes.medium,
            enabled = !state.isGenerating && canGenerate
        ) {
            if (state.isGenerating) CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            else {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(spacing.small))
                Text(if (canGenerate) "立即生成资源" else "请先生成默认/正常状态的图片")
            }
        }

        // 以图生图（参考图局部修改/抠图）操作入口按钮
        OutlinedButton(
            onClick = {
                if (selectedBlock.referenceImage != null) {
                    showImg2ImgDialog = true
                } else {
                    showNoRefDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp).tip(stringResource(Res.string.btn_img2img_tip)),
            shape = AppShapes.medium,
            enabled = !state.isGenerating && canGenerate
        ) {
            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(spacing.small))
            Text(stringResource(Res.string.btn_img2img_label), style = MaterialTheme.typography.labelLarge)
        }

        // 1. 未设置参考区域时的拦截提示对话框
        if (showNoRefDialog) {
            AppConfirmDialog(
                title = stringResource(Res.string.img2img_no_ref_title),
                message = stringResource(Res.string.img2img_no_ref_desc),
                confirmText = stringResource(Res.string.img2img_btn_set_now),
                onConfirm = {
                    showNoRefDialog = false
                    viewModel.showReferenceArea(selectedBlock.id)
                },
                onDismiss = { showNoRefDialog = false }
            )
        }

        // 2. 已设置参考区域时的以图生图生成资源对话框（以临时输入的 Prompt 为准）
        if (showImg2ImgDialog && selectedBlock.referenceImage != null) {
            ImageToImageGenDialog(
                block = selectedBlock,
                referenceImage = selectedBlock.referenceImage,
                initialTransparent = state.isGenerateTransparent,
                initialCloudRemoval = state.isPrioritizeCloudRemoval,
                isGenerating = state.isGenerating,
                onDismiss = { showImg2ImgDialog = false },
                onStartGen = { pZh, pEn, isTrans, isCloud ->
                    val effectivePrompt = if (effectiveLang == PromptLanguage.ZH) {
                        pZh.ifBlank { pEn }
                    } else {
                        pEn.ifBlank { pZh }
                    }
                    // 更新透明背景与云端抠图配置
                    viewModel.updateState { it.copy(isGenerateTransparent = isTrans, isPrioritizeCloudRemoval = isCloud) }
                    // 传入输入框中的最新临时 Prompt 执行以图生图
                    viewModel.assetGen.onRequestGeneration(apiKey, effectivePrompt, currentTab)
                    showImg2ImgDialog = false
                }
            )
        }
    }
}

@Composable
private fun ModelSelector(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = AppShapes.small,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(220.dp)) {
            GeminiModel.entries.filter { it.modelName.contains("image") || it.modelName.contains("imagen") }
                .forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { viewModel.updateState { it.copy(selectedModel = model) }; expanded = false },
                        leadingIcon = {
                            if (state.selectedModel == model) Icon(
                                Icons.Default.Check,
                                null,
                                Modifier.size(18.dp)
                            )
                        }
                    )
                }
        }
    }
}

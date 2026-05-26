package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.AdvancedSettingsDialog
import org.gemini.ui.forge.ui.dialog.ButtonStateGenDialog
import org.gemini.ui.forge.ui.dialog.ImageEditorDialog
import org.gemini.ui.forge.ui.theme.AppShapes
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
    var useChatContext by remember { mutableStateOf(false) }

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

    // 针对 SPIN_BUTTON 进行的分身状态检测
    val isSpinButton = selectedBlock.type == UIBlockType.SPIN_BUTTON
    val spinProps = selectedBlock.properties as? BlockProperties.SpinButtonProperties
    
    // Tab 栏状态：0 为 Spin (默认)，1 为 Stop (停止)
    var currentTab by remember(selectedBlock.id) { mutableStateOf(0) }

    // 动态重定向获取当前状态绑定的图片和历史 ID 后缀
    val currentImageToDisplay = if (isSpinButton) {
        if (currentTab == 0) spinProps?.spinUri else spinProps?.stopUri
    } else {
        selectedBlock.currentImageUri
    }

    val historicalIdSuffix = if (isSpinButton) {
        if (currentTab == 0) "_spin" else "_stop"
    } else {
        ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(text = "正在编辑: ${selectedBlock.id}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            SuggestionChip(
                onClick = { },
                label = { Text(stringResource(selectedBlock.type.getDisplayNameRes()), style = MaterialTheme.typography.labelSmall) },
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

        // 3. 当确定当前模块有特殊生图需求的时候，在预览图上面添加tab栏，默认就是当前正常的 (Spin)
        if (isSpinButton) {
            TabRow(
                selectedTabIndex = currentTab,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("默认状态 (Spin)", style = MaterialTheme.typography.labelSmall) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("停止状态 (Stop)", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(AppShapes.medium).background(Color.Black.copy(alpha = 0.05f))
                .clickable { if (currentImageToDisplay != null) showImageEditor = true }
        ) {
            if (currentImageToDisplay != null) {
                AsyncImage(
                    model = currentImageToDisplay.getAbsolutePath(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HideImage, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("尚未绑定资源", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.showHistoricalDialog(selectedBlock.id + historicalIdSuffix) },
                modifier = Modifier.weight(1.2f),
                shape = AppShapes.medium
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("历史/切换", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { 
                    if (isSpinButton) {
                        val currentProps = spinProps ?: BlockProperties.SpinButtonProperties()
                        val newProps = if (currentTab == 0) currentProps.copy(spinUri = null) else currentProps.copy(stopUri = null)
                        viewModel.assetManager.updateBlockProperties(selectedBlock.id, newProps)
                    } else {
                        viewModel.assetManager.clearSelectedImage(selectedBlock.id) 
                    }
                },
                modifier = Modifier.weight(0.8f),
                shape = AppShapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("解绑", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (currentImageToDisplay != null) {
            Button(
                onClick = { showImageEditor = true },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("物理加工与固化", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.alpha(0.2f))

        val systemLang = androidx.compose.ui.text.intl.Locale.current.language
        val effectiveLang = state.currentLang.resolve(systemLang)

        // 4. 对与 SpinButton 提示词也是中英文配置的，根据当前切换的 tab 状态进行更新读写内容
        val prompt = if (isSpinButton) {
            val p = spinProps ?: BlockProperties.SpinButtonProperties()
            if (currentTab == 0) {
                if (effectiveLang == PromptLanguage.ZH) p.spinPromptZh else p.spinPromptEn
            } else {
                if (effectiveLang == PromptLanguage.ZH) p.stopPromptZh else p.stopPromptEn
            }
        } else {
            if (effectiveLang == PromptLanguage.ZH) selectedBlock.userPromptZh else selectedBlock.userPromptEn
        }

        val otherPrompt = if (isSpinButton) {
            val p = spinProps ?: BlockProperties.SpinButtonProperties()
            if (currentTab == 0) {
                if (effectiveLang == PromptLanguage.ZH) p.spinPromptEn else p.spinPromptZh
            } else {
                if (effectiveLang == PromptLanguage.ZH) p.stopPromptEn else p.stopPromptZh
            }
        } else {
            if (effectiveLang == PromptLanguage.ZH) selectedBlock.userPromptEn else selectedBlock.userPromptZh
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("AI 提示词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                    if (isSpinButton) {
                        val currentProps = spinProps ?: BlockProperties.SpinButtonProperties()
                        val newProps = if (currentTab == 0) {
                            if (effectiveLang == PromptLanguage.ZH) currentProps.copy(spinPromptZh = newValue) else currentProps.copy(spinPromptEn = newValue)
                        } else {
                            if (effectiveLang == PromptLanguage.ZH) currentProps.copy(stopPromptZh = newValue) else currentProps.copy(stopPromptEn = newValue)
                        }
                        viewModel.assetManager.updateBlockProperties(selectedBlock.id, newProps)
                    } else {
                        viewModel.assetManager.updateBlockPrompt(selectedBlock.id, effectiveLang, newValue) 
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    if (otherPrompt.isNotBlank()) {
                        Text("当前语言为空，系统将使用: ${otherPrompt.take(20)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    } else {
                        Text(stringResource(Res.string.prop_prompt_hint), style = MaterialTheme.typography.bodySmall)
                    }
                },
                maxLines = 8,
                enabled = !state.isGenerating
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useChatContext, onCheckedChange = { useChatContext = it })
                Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { 
                        // 将优化结果反馈写入对应的 blockPrompt 字段
                        viewModel.layoutEditor.optimizePrompt(selectedBlock.id, apiKey, effectiveLang, useChatContext) 
                    },
                    enabled = !state.isGenerating && (prompt.isNotBlank() || otherPrompt.isNotBlank())
                ) {
                    Icon(Icons.Default.AutoFixHigh, "优化提示词", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = AppShapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.isGenerateTransparent, onCheckedChange = { checked -> viewModel.updateState { it.copy(isGenerateTransparent = checked) } })
                    Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodySmall)
                }
                if (state.isGenerateTransparent) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                        Checkbox(checked = state.isPrioritizeCloudRemoval, onCheckedChange = { checked -> viewModel.updateState { it.copy(isPrioritizeCloudRemoval = checked) } })
                        Text("优先云端抠图", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = { 
                viewModel.assetGen.onRequestGeneration(apiKey, if (prompt.isNotBlank()) prompt else otherPrompt) 
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !state.isGenerating
        ) {
            if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("立即生成资源")
            }
        }
    }
}

@Composable
private fun ModelSelector(state: ProjectWorkspaceState, viewModel: ProjectWorkspaceViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, shape = AppShapes.small, modifier = Modifier.fillMaxWidth().height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(220.dp)) {
            GeminiModel.entries.filter { it.modelName.contains("image") || it.modelName.contains("imagen") }
                .forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { viewModel.updateState { it.copy(selectedModel = model) }; expanded = false },
                        leadingIcon = { if (state.selectedModel == model) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    )
                }
        }
    }
}

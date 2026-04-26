package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.state.TemplateAssetGenState
import org.gemini.ui.forge.state.TemplateAssetGenViewModel
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.utils.rememberImagePicker

/**
 * 资产生成配置面板：负责模型选择、参考图设置、风格关键词输入以及提示词编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetGenPropertyPanel(
    state: TemplateAssetGenState,
    viewModel: TemplateAssetGenViewModel,
    apiKey: String,
    currentEditingLang: PromptLanguage,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBlock = state.selectedBlock
    val projectName = state.projectName.replace(" ", "_")
    
    // 使用 TemplateFile 定义起点目录
    val projectAssetsBase = TemplateFile("templates/$projectName/assets")
    
    // 获取带初始路径的选择器触发器
    val imagePicker = projectAssetsBase.rememberImagePicker { uris ->
        uris.firstOrNull()?.let { 
            viewModel.setReferenceImageExternal(it)
        }
    }
    
    var showModelMenu by remember { mutableStateOf(false) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            stringResource(Res.string.editor_gen_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showAdvancedSettingsDialog = true },
                modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = AppShapes.small,
                enabled = !state.isGenerating
            ) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("风格与参考", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { viewModel.openBatchGenDialog() },
                modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = AppShapes.small,
                enabled = !state.isGenerating,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.AutoAwesomeMotion, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.action_batch_gen), style = MaterialTheme.typography.labelSmall)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            OutlinedButton(
                onClick = { showModelMenu = true },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = AppShapes.small,
                enabled = !state.isGenerating
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }

            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = { showModelMenu = false },
                modifier = Modifier.width(220.dp)
            ) {
                GeminiModel.entries
                    .filter { 
                        it.supportedMethods.contains("predict") || 
                        it.modelName.contains("imagen") || 
                        it.modelName.contains("image") 
                    }
                    .forEach { model ->
                        val isImagen = model.supportedMethods.contains("predict") || model.modelName.contains("imagen")
                        val modelNameText = model.displayName
                        val techText = if (isImagen) "Imagen API" else "Native Gemini"
                        
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(modelNameText, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        techText, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setImageGenModel(model)
                                showModelMenu = false
                            },
                            leadingIcon = {
                                if (state.selectedModel == model) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
            }
        }
        
        if (selectedBlock != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = AppShapes.small,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "正在编辑: ${selectedBlock.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (showAdvancedSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showAdvancedSettingsDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false
                ),
                title = { Text("风格与参考设置", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "风格参考图 (图生图引导)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(100.dp).padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.referenceImageUri != null) {
                                    AsyncImage(
                                        model = state.referenceImageUri.getAbsolutePath(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = AppShapes.small,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.padding(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Button(
                                    onClick = { imagePicker() },
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    shape = AppShapes.small,
                                    enabled = !state.isGenerating
                                ) {
                                    Text("更改参考图", style = MaterialTheme.typography.labelSmall)
                                }
                                if (state.referenceImageUri != null) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { viewModel.setReferenceImage(null) },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("移除参考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "全局风格关键词",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.globalStyle,
                            onValueChange = { viewModel.setGlobalStyle(it) },
                            placeholder = { Text("例如：Cyberpunk, neon lights...", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            singleLine = false,
                            textStyle = MaterialTheme.typography.bodySmall,
                            enabled = !state.isGenerating
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "提示：设置的风格和参考图将在每次生成时自动生效。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            viewModel.saveStyleSettings {
                                showAdvancedSettingsDialog = false
                            }
                        },
                        enabled = !state.isGenerating
                    ) {
                        Text("保存并完成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdvancedSettingsDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (selectedBlock == null) {
            Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "模块物理参数 (只读)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoItem("X", selectedBlock.bounds.left.toInt().toString(), Modifier.weight(1f))
                        InfoItem("Y", selectedBlock.bounds.top.toInt().toString(), Modifier.weight(1f))
                        InfoItem("W", selectedBlock.bounds.width.toInt().toString(), Modifier.weight(1f))
                        InfoItem("H", selectedBlock.bounds.height.toInt().toString(), Modifier.weight(1f))
                    }
                }
            }

            Text(
                text = "当前绑定资源",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = AppShapes.medium,
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedBlock.currentImageUri != null) {
                        AsyncImage(
                            model = selectedBlock.currentImageUri.getAbsolutePath(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.HideImage,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "尚未绑定任何资源",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onShowHistory, modifier = Modifier.weight(1f), shape = AppShapes.medium) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("历史记录")
                }
                OutlinedButton(
                    onClick = { viewModel.clearSelectedImage(selectedBlock.id) },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.medium,
                    enabled = selectedBlock.currentImageUri != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("解绑")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = currentEditingLang == lang,
                        onClick = { onSwitchEditingLang(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(lang.displayName) }
                    )
                }
            }

            val displayPrompt = if (currentEditingLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
            var tempPrompt by remember(selectedBlock.id, currentEditingLang, displayPrompt) { mutableStateOf(displayPrompt) }

            OutlinedTextField(
                value = tempPrompt,
                onValueChange = { tempPrompt = it },
                readOnly = false,
                label = { Text("${stringResource(Res.string.label_description_content)} (${currentEditingLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 8,
                enabled = !state.isGenerating
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isGenerateTransparent,
                    onCheckedChange = { viewModel.setGenerateTransparent(it) },
                    enabled = !state.isGenerating
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "开启后自动处理背景",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isGenerateTransparent) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.isPrioritizeCloudRemoval,
                        onCheckedChange = { viewModel.setPrioritizeCloudRemoval(it) },
                        enabled = !state.isGenerating
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("优先云端抠图", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.onRequestGeneration(apiKey, tempPrompt) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isGenerating && tempPrompt.isNotBlank(),
                shape = AppShapes.medium
            ) {
                if (state.isGenerating)
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                else
                    Text(stringResource(Res.string.editor_start_gen))
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

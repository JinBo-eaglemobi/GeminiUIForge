package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.state.TemplateAssetGenState
import org.gemini.ui.forge.state.TemplateAssetGenViewModel
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.AITaskProgressDialog
import org.gemini.ui.forge.ui.dialog.AssetCropDialog
import org.gemini.ui.forge.ui.dialog.AssetSelectionDialog
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar
import org.gemini.ui.forge.utils.rememberImagePicker

import org.gemini.ui.forge.utils.AppLogger

/**
 * 资产生成页面主容器组件。
 */
@Composable
fun TemplateAssetGenScreen(
    initialProject: ProjectState,
    initialProjectName: String,
    templateRepo: TemplateRepository,
    cloudAssetManager: CloudAssetManager,
    configManager: ConfigManager,
    effectiveApiKey: String,
    initialPromptLang: PromptLanguage,
    saveEvent: kotlinx.coroutines.flow.SharedFlow<Unit>,
    onSaveRequest: (String, ProjectState) -> Unit,
    onDirtyChanged: (Boolean) -> Unit
) {
    val aiService = AIGenerationService(cloudAssetManager, configManager)
    val viewModel: TemplateAssetGenViewModel = viewModel(key = initialProjectName) {
        TemplateAssetGenViewModel(
            initialProject,
            initialProjectName,
            initialPromptLang,
            templateRepo,
            cloudAssetManager,
            aiService,
            onDirtyChanged = onDirtyChanged
        )
    }
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 监听全局保存事件
    LaunchedEffect(saveEvent) {
        saveEvent.collect {
            onSaveRequest(initialProjectName, state.project)
        }
    }

    LaunchedEffect(initialProject) {
        viewModel.reload(initialProject)
    }

    var showHistoricalDialog by remember { mutableStateOf(false) }
    var historicalImages by remember { mutableStateOf<List<TemplateFile>>(emptyList()) }
    var pendingCropUri by remember { mutableStateOf<String?>(null) }
    var showAILogs by remember { mutableStateOf(false) }

    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            showAILogs = true
        }
    }

    if (state.showAITaskDialog) {
        val dialogTitle = when {
            state.generationLogs.any { it.contains("优化") } -> "智能优化提示词中..."
            state.generationLogs.any { it.contains("抠图") } -> "本地批量处理中..."
            else -> "AI 资源生成中..."
        }
        
        AITaskProgressDialog(
            title = dialogTitle,
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = showAILogs,
            onToggleLogVisibility = { showAILogs = !showAILogs },
            onActionClick = { if (state.isGenerating) viewModel.cancelGeneration() else viewModel.closeAITaskDialog() },
            onDismiss = { viewModel.closeAITaskDialog() }
        )
    }

    var showCurrentGenerationResults by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.generatedCandidates) {
        if (!state.isGenerating && state.generatedCandidates.isNotEmpty()) {
            showCurrentGenerationResults = true
        }
    }

    if (pendingCropUri != null) {
        var isCropping by remember { mutableStateOf(false) }
        AssetCropDialog(
            imageUri = pendingCropUri!!,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            onConfirm = { rect ->
                isCropping = true
                coroutineScope.launch {
                    val success = viewModel.onImageCroppedAndSelected(pendingCropUri!!, rect)
                    pendingCropUri = null
                    isCropping = false
                    if (success) {
                        showCurrentGenerationResults = false
                        showHistoricalDialog = false
                    }
                }
            },
            onDismiss = { if (!isCropping) pendingCropUri = null }
        )
    }

    if (showCurrentGenerationResults) {
        AssetSelectionDialog(
            title = "AI 生成资源预览",
            candidates = state.generatedCandidates,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            isProcessing = state.isLocalProcessing,
            onImageSelected = { viewModel.onImageSelected(it); showCurrentGenerationResults = false },
            onCropRequested = { pendingCropUri = it.getAbsolutePath() },
            onDeleteImages = { viewModel.deleteImages(it) },
            onClearAll = { viewModel.clearCandidates(); showCurrentGenerationResults = false },
            onBatchRemoveBg = { uris ->
                viewModel.batchRemoveBackgroundLocal(uris) { newPaths ->
                    viewModel.appendCandidates(newPaths)
                }
            },
            onDismiss = { showCurrentGenerationResults = false }
        )
    }

    if (showHistoricalDialog) {
        AssetSelectionDialog(
            title = "历史资源列表",
            candidates = historicalImages,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            isProcessing = state.isLocalProcessing,
            onImageSelected = { viewModel.onImageSelected(it); showHistoricalDialog = false },
            onCropRequested = { pendingCropUri = it.getAbsolutePath() },
            onDeleteImages = { uris ->
                viewModel.deleteImages(uris); historicalImages = historicalImages.filter { it !in uris }
            },
            onClearAll = { },
            onBatchRemoveBg = { uris ->
                viewModel.batchRemoveBackgroundLocal(uris) { newPaths ->
                    historicalImages = historicalImages + newPaths
                }
            },
            onDismiss = { showHistoricalDialog = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var leftWeight by remember { mutableStateOf(0.15f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.3f) }

        Row(modifier = Modifier.fillMaxSize()) {
            HierarchySidebar(
                blocks = state.currentPage?.blocks ?: emptyList(),
                selectedBlockId = state.selectedBlockId,
                onBlockClicked = { viewModel.onBlockClicked(it) },
                onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                onMoveBlock = { d, t, p -> viewModel.moveBlock(d, t, p) },
                onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) },
                onRenameBlock = { old, new -> viewModel.renameBlock(old, new) },
                onToggleVisibility = { id, visible -> viewModel.toggleBlockVisibility(id, visible) },
                onToggleAllVisibility = { visible -> viewModel.toggleAllBlocksVisibility(visible) },
                modifier = Modifier.weight(leftWeight).fillMaxHeight(),
                isReadOnly = true
            )

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.1f..0.3f) {
                    leftWeight += dw; centerWeight -= dw
                }
            })

            Column(modifier = Modifier.weight(centerWeight).fillMaxHeight()) {
                val pages = state.project.pages
                val selectedIndex = pages.indexOfFirst { it.id == state.selectedPageId }.coerceAtLeast(0)
                if (pages.isNotEmpty()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        pages.forEachIndexed { index, page ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { if (!state.isGenerating) viewModel.onPageSelected(page.id) },
                                text = { Text(page.nameStr) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CanvasArea(
                        pageWidth = state.currentPage?.width ?: 1080f,
                        pageHeight = state.currentPage?.height ?: 1920f,
                        blocks = state.currentPage?.blocks ?: emptyList(),
                        stageBackgroundColor = state.stageBackgroundColor,
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = { viewModel.onBlockClicked(it) },
                        onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                        onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                        editingGroupId = state.editingGroupId,
                        onExitGroupEdit = { viewModel.exitGroupEditMode() },
                        referenceUri = state.currentPage?.sourceImageUri?.getAbsolutePath(),
                        isVisualMode = state.isVisualMode,
                        onToggleVisualMode = { viewModel.toggleVisualMode() },
                        isReadOnly = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) {
                    rightWeight -= dw; centerWeight += dw
                }
            })

            Surface(
                modifier = Modifier.weight(rightWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PropertyPanel(
                        state = state,
                        viewModel = viewModel,
                        apiKey = effectiveApiKey,
                        currentEditingLang = state.currentLang,
                        onSwitchEditingLang = { viewModel.switchLang(it) },
                        onShowHistory = {
                            state.selectedBlock?.let { block ->
                                coroutineScope.launch {
                                    historicalImages = viewModel.loadHistoricalImages(block.id)
                                    showHistoricalDialog = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(
    state: TemplateAssetGenState,
    viewModel: TemplateAssetGenViewModel,
    apiKey: String,
    currentEditingLang: PromptLanguage,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    onShowHistory: () -> Unit
) {
    val selectedBlock = state.selectedBlock
    val projectName = state.projectName.replace(" ", "_")
    
    // 使用 TemplateFile 定义起点目录
    val projectAssetsBase = TemplateFile("templates/$projectName/assets")
    
    // 获取带初始路径的选择器触发器 (注意这里是在 Composable 顶层调用)
    val imagePicker = projectAssetsBase.rememberImagePicker { uris ->
        uris.firstOrNull()?.let { 
            viewModel.setReferenceImageExternal(it)
        }
    }
    
    var showModelMenu by remember { mutableStateOf(false) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.editor_gen_settings),
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showAdvancedSettingsDialog = true },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = AppShapes.small,
                    enabled = !state.isGenerating
                ) {
                    Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("风格与参考", style = MaterialTheme.typography.labelSmall)
                }

                Box {
                    OutlinedButton(
                        onClick = { showModelMenu = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = AppShapes.small,
                        enabled = !state.isGenerating
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall)
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

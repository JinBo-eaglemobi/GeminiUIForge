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
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.state.TemplateAssetGenState
import org.gemini.ui.forge.state.TemplateAssetGenViewModel
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.dialog.AITaskProgressDialog
import org.gemini.ui.forge.ui.dialog.AssetCropDialog
import org.gemini.ui.forge.ui.dialog.AssetSelectionDialog
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar

/**
 * 资产生成页面主容器组件。
 * 负责 [TemplateAssetGenViewModel] 的生命周期管理（State Hoisting 状态提升），
 * 管理三栏布局（图层树、画布预览、生成设置），以及处理生成进度、历史资源等对话框的弹出交互。
 */
@Composable
fun TemplateAssetGenScreen(
    initialProject: ProjectState,
    initialProjectName: String,
    templateRepo: TemplateRepository,
    cloudAssetManager: CloudAssetManager,
    effectiveApiKey: String,
    initialPromptLang: PromptLanguage,
    onProjectUpdated: (ProjectState) -> Unit
) {

    val aiService = AIGenerationService(cloudAssetManager)
    // 1. 初始化 ViewModel 并将其生命周期与 Screen 绑定
    val viewModel: TemplateAssetGenViewModel = viewModel(key = initialProjectName) {
        TemplateAssetGenViewModel(initialProject, initialProjectName, initialPromptLang, templateRepo, cloudAssetManager, aiService)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(initialProject) {
        viewModel.reload(initialProject)
    }

    // 监听内部状态变化（手动同步模式，已移除自动同步 LaunchedEffect）

    val coroutineScope = rememberCoroutineScope()
    // 历史资源列表的弹窗状态
    var showHistoricalDialog by remember { mutableStateOf(false) }
    var historicalImages by remember { mutableStateOf<List<String>>(emptyList()) }
    // 等待被裁剪处理的图片 URI
    var pendingCropUri by remember { mutableStateOf<String?>(null) }
    // 控制 AI 日志显隐的本地状态
    var showAILogs by remember { mutableStateOf(false) }

    // 当 AI 开始生成时，自动展开日志面板以提升反馈感
    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            showAILogs = true
        }
    }

    // --- 弹窗组件区 ---

    // 1. AI 任务执行进度与日志弹窗
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

    // 2. 本次生成资源选择弹窗（自动弹出机制）
    var showCurrentGenerationResults by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.generatedCandidates) {
        // 如果生成任务结束，并且成功返回了候选图片集合，则自动弹出供用户挑选
        if (!state.isGenerating && state.generatedCandidates.isNotEmpty()) {
            showCurrentGenerationResults = true
        }
    }

    // 3. 图像裁剪确认弹窗
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

    // 显示最新生成的候选图像列表
    if (showCurrentGenerationResults) {
        AssetSelectionDialog(
            title = "AI 生成资源预览",
            candidates = state.generatedCandidates,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            isProcessing = state.isLocalProcessing,
            onImageSelected = { viewModel.onImageSelected(it); showCurrentGenerationResults = false },
            onCropRequested = { pendingCropUri = it }, // 修改：触发裁剪时不立刻关闭资源弹窗
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

    // 显示历史资源列表
    if (showHistoricalDialog) {
        AssetSelectionDialog(
            title = "历史资源列表",
            candidates = historicalImages,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            isProcessing = state.isLocalProcessing,
            onImageSelected = { viewModel.onImageSelected(it); showHistoricalDialog = false },
            onCropRequested = { pendingCropUri = it }, // 修改：触发裁剪时不立刻关闭历史弹窗
            onDeleteImages = { uris ->
                viewModel.deleteImages(uris); historicalImages = historicalImages.filter { it !in uris }
            },
            onClearAll = { }, // 历史记录暂时不支持一键清空
            onBatchRemoveBg = { uris ->
                viewModel.batchRemoveBackgroundLocal(uris) { newPaths ->
                    // 仅追加到历史列表，不干扰 AI 生成预览的 candidates 集合
                    historicalImages = historicalImages + newPaths
                }
            }, // 修复：补充了历史弹窗的触发回调
            onDismiss = { showHistoricalDialog = false }
        )
    }

    // --- 主界面布局：三栏结构 ---
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // 三栏默认权重
        var leftWeight by remember { mutableStateOf(0.15f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.3f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // [左栏] 图层结构树
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
                isReadOnly = true // 资产生成页面不允许改变图层结构的主体
            )

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.1f..0.3f) {
                    leftWeight += dw; centerWeight -= dw
                }
            })

            // [中栏] 页面 Tab 与画布预览
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
                        referenceUri = state.currentPage?.sourceImageUri,
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

            // [右栏] 生成参数配置与提示词编辑面板
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

/**
 * 资产生成参数面板组件。
 * 提供选中组件的物理参数预览、绑定的资源图像查看，以及触发 AI 图像生成与透明度处理等控制。
 */
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
            
            // 显示当前选中的模块 ID/名称
            if (selectedBlock != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = AppShapes.small
                ) {
                    Text(
                        text = selectedBlock.id,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (selectedBlock == null) {
            // 未选中任何块的空状态提示
            Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 1. 只读物理坐标预览面板
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

            // 2. 当前绑定资源缩略图展示
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
                            model = selectedBlock.currentImageUri,
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

            // 3. 历史记录与资源解绑按钮
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

            // 4. 语言切换与描述提示词展示
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

            val displayPrompt =
                if (currentEditingLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
            
            // 使用本地状态，允许用户在生图前临时修改 Prompt，但不持久化到模板
            var tempPrompt by remember(selectedBlock.id, currentEditingLang, displayPrompt) { 
                mutableStateOf(displayPrompt) 
            }

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

            // 5. 图像生成特性配置（透明度/扣图）
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

            // 6. 执行生成按钮
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

/**
 * 封装的只读信息展示小组件
 */
@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}


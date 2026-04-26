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
import org.gemini.ui.forge.ui.dialog.BatchAssetGenDialog
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.CanvasArea
import org.gemini.ui.forge.ui.component.HierarchySidebar
import org.gemini.ui.forge.utils.rememberImagePicker

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
    shortcutEvent: kotlinx.coroutines.flow.SharedFlow<org.gemini.ui.forge.model.app.ShortcutAction>,
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

    // 监听全局快捷键事件
    LaunchedEffect(shortcutEvent) {
        shortcutEvent.collect { action ->
            if (action == org.gemini.ui.forge.model.app.ShortcutAction.SAVE) {
                onSaveRequest(initialProjectName, state.project)
            }
        }
    }

    LaunchedEffect(initialProject) {
        viewModel.reload(initialProject)
    }

    if (state.showBatchGenDialog) {
        val currentPageBlocks = state.currentPage?.blocks ?: emptyList()
        // 递归查找所有缺失图片的 Block
        fun findAllMissing(blocks: List<UIBlock>): List<UIBlock> {
            val result = mutableListOf<UIBlock>()
            for (b in blocks) {
                if (b.currentImageUri == null) result.add(b)
                result.addAll(findAllMissing(b.children))
            }
            return result
        }
        val missingBlocks = findAllMissing(currentPageBlocks)

        BatchAssetGenDialog(
            blocks = missingBlocks,
            onCancel = { viewModel.closeBatchGenDialog() },
            onStartGen = { selected ->
                viewModel.startBatchGeneration(effectiveApiKey, selected)
            }
        )
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
        val progressText = state.batchProgress?.let { (curr, total) ->
            "正在批量生成: $curr / $total"
        }
        val dialogTitle = when {
            progressText != null -> progressText
            state.generationLogs.any { it.contains("优化") } -> "智能优化提示词中..."
            state.generationLogs.any { it.contains("抠图") } -> "本地批量处理中..."
            else -> "AI 资源生成中..."
        }
        
        AITaskProgressDialog(
            title = dialogTitle,
            currentStatus = state.currentTaskStatus,
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = state.isGenerationLogVisible,
            onToggleLogVisibility = { viewModel.toggleGenerationLogVisibility() },
            onActionClick = { if (state.isGenerating) viewModel.cancelGeneration() else viewModel.closeAITaskDialog() },
            onDismiss = { viewModel.closeAITaskDialog() },
            extraContent = {
                // 任务 4：并行工作看板
                if (state.activeWorkers.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = AppShapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "并行任务看板 (并发上限: ${state.activeWorkers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            state.activeWorkers.forEach { worker ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            worker.isCompleted -> Icons.Default.CheckCircle
                                            worker.isBusy -> Icons.Default.Sync
                                            else -> Icons.Default.Circle
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = when {
                                            worker.isCompleted -> Color(0xFF4CAF50)
                                            worker.isBusy -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    )
                                    
                                    Spacer(Modifier.width(8.dp))
                                    
                                    Text(
                                        text = if (worker.blockId.isBlank()) "空闲槽位" else worker.blockId,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(100.dp),
                                        color = if (worker.blockId.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    
                                    Text(
                                        text = worker.action,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    if (worker.info.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = worker.info,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                if (worker.id < state.activeWorkers.size - 1) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    var showCurrentGenerationResults by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.generatedCandidates, state.batchPendingConfirmBlock) {
        if (!state.isGenerating && state.generatedCandidates.isNotEmpty()) {
            showCurrentGenerationResults = true
        }
    }

    if (pendingCropUri != null) {
        var isCropping by remember { mutableStateOf(false) }
        AssetCropDialog(
            imageUri = pendingCropUri!!,
            targetWidth = (state.batchPendingConfirmBlock ?: state.selectedBlock)?.bounds?.width ?: 0f,
            targetHeight = (state.batchPendingConfirmBlock ?: state.selectedBlock)?.bounds?.height ?: 0f,
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
        val pendingBlock = state.batchPendingConfirmBlock
        val targetBlock = pendingBlock ?: state.selectedBlock
        AssetSelectionDialog(
            title = if (pendingBlock != null) 
                "批量确认 [${pendingBlock.id}] (${state.batchProgress?.first}/${state.batchProgress?.second})" 
                else "AI 生成资源预览",
            candidates = state.generatedCandidates,
            initialSelectedUri = targetBlock?.currentImageUri,
            targetWidth = targetBlock?.bounds?.width ?: 0f,
            targetHeight = targetBlock?.bounds?.height ?: 0f,
            isProcessing = state.isLocalProcessing,
            onImageSelected = { 
                viewModel.onImageSelected(it)
                showCurrentGenerationResults = false 
            },
            onCropRequested = { pendingCropUri = it.getAbsolutePath() },
            onDeleteImages = { viewModel.deleteImages(it) },
            onClearAll = { 
                if (pendingBlock != null) {
                    viewModel.skipCurrentBatchConfirmation()
                } else {
                    viewModel.clearCandidates()
                }
                showCurrentGenerationResults = false 
            },
            onBatchRemoveBg = { uris ->
                viewModel.batchRemoveBackgroundLocal(uris) { newPaths ->
                    viewModel.appendCandidates(newPaths)
                }
            },
            onDismiss = { 
                if (pendingBlock != null) {
                    viewModel.skipCurrentBatchConfirmation()
                }
                showCurrentGenerationResults = false 
            }
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
                    AssetGenPropertyPanel(
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

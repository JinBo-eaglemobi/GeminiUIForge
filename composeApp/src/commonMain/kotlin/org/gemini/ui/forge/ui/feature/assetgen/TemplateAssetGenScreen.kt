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
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.EditorState
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.component.AITaskProgressDialog
import kotlinx.coroutines.launch
import org.gemini.ui.forge.ui.feature.common.CanvasArea
import org.gemini.ui.forge.ui.feature.common.HierarchySidebar

@Composable
fun TemplateAssetGenScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String?) -> Unit,
    onBlockDoubleClicked: (String) -> Unit,
    onExitGroupEdit: () -> Unit,
    onPromptChanged: (String) -> Unit,
    onSwitchEditingLanguage: (PromptLanguage) -> Unit,
    onGenerateRequested: () -> Unit,
    onImageSelected: (String, org.gemini.ui.forge.model.ui.SerialRect?) -> Unit,
    onDeleteImages: (List<String>) -> Unit,
    onClearHistoricalCandidates: () -> Unit,
    onClearSelectedImage: (String) -> Unit,
    onLoadHistoricalImages: suspend (String) -> List<String>,
    onMoveBlock: (String, String?, DropPosition) -> Unit,
    onBlockDragged: (String, Float, Float) -> Unit,
    onRenameBlock: (String, String) -> Unit,
    onAddCustomBlock: (String, UIBlockType, Float, Float) -> Unit,
    onToggleTransparent: (Boolean) -> Unit = {},
    onTogglePrioritizeCloud: (Boolean) -> Unit = {},
    onCancelGeneration: () -> Unit = {},
    onToggleGenerationLog: () -> Unit = {},
    onCloseAITaskDialog: () -> Unit = {},
    isVisualMode: Boolean = false,
    onToggleVisualMode: () -> Unit = {},
    onToggleVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onToggleAllVisibility: (Boolean) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var showHistoricalDialog by remember { mutableStateOf(false) }
    var historicalImages by remember { mutableStateOf<List<String>>(emptyList()) }

    var pendingCropUri by remember { mutableStateOf<String?>(null) }

    if (state.showAITaskDialog) {
        AITaskProgressDialog(
            title = if (state.generationLogs.any { it.contains("优化") }) "智能优化提示词中..." else "AI 资源生成中...",
            logs = state.generationLogs,
            isProcessing = state.isGenerating,
            isLogVisible = state.isGenerationLogVisible,
            onToggleLogVisibility = onToggleGenerationLog,
            onActionClick = { if (state.isGenerating) onCancelGeneration() else onCloseAITaskDialog() },
            onDismiss = onCloseAITaskDialog
        )
    }

    var showCurrentGenerationResults by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.generatedCandidates) {
        if (!state.isGenerating && state.generatedCandidates.isNotEmpty()) {
            showCurrentGenerationResults = true
        }
    }

    if (pendingCropUri != null) {
        AssetCropDialog(
            imageUri = pendingCropUri!!,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            onConfirm = { rect ->
                state.selectedBlockId?.let { id -> onImageSelected(pendingCropUri!!, rect) }
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    if (showCurrentGenerationResults) {
        AssetSelectionDialog(
            title = "AI 生成资源预览",
            candidates = state.generatedCandidates,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            targetWidth = state.selectedBlock?.bounds?.width ?: 0f,
            targetHeight = state.selectedBlock?.bounds?.height ?: 0f,
            onImageSelected = { onImageSelected(it, null); showCurrentGenerationResults = false },
            onCropRequested = { pendingCropUri = it; showCurrentGenerationResults = false },
            onDeleteImages = { onDeleteImages(it) },
            onClearAll = { onClearHistoricalCandidates(); showCurrentGenerationResults = false },
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
            onImageSelected = { onImageSelected(it, null); showHistoricalDialog = false },
            onCropRequested = { pendingCropUri = it; showHistoricalDialog = false },
            onDeleteImages = { uris -> onDeleteImages(uris); historicalImages = historicalImages.filter { it !in uris } },
            onClearAll = { },
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
                onBlockClicked = onBlockClicked,
                onMoveBlock = onMoveBlock,
                onAddCustomBlock = onAddCustomBlock,
                onRenameBlock = onRenameBlock,
                onToggleVisibility = onToggleVisibility,
                onToggleAllVisibility = onToggleAllVisibility,
                modifier = Modifier.weight(leftWeight).fillMaxHeight(),
                isReadOnly = true
            )

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.1f..0.3f) { leftWeight += dw; centerWeight -= dw }
            })

            Column(modifier = Modifier.weight(centerWeight).fillMaxHeight()) {
                val pages = state.project.pages
                val selectedIndex = pages.indexOfFirst { it.id == state.selectedPageId }.coerceAtLeast(0)
                if (pages.isNotEmpty()) {
                    PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        pages.forEachIndexed { index, page -> Tab(selected = selectedIndex == index, onClick = { onPageSelected(page.id) }, text = { Text(page.nameStr) }) }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CanvasArea(
                        pageWidth = state.currentPage?.width ?: 1080f,
                        pageHeight = state.currentPage?.height ?: 1920f,
                        blocks = state.currentPage?.blocks ?: emptyList(),
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = onBlockClicked,
                        onBlockDoubleClicked = onBlockDoubleClicked,
                        onBlockDragged = onBlockDragged,
                        editingGroupId = state.editingGroupId,
                        onExitGroupEdit = onExitGroupEdit,
                        referenceUri = state.currentPage?.sourceImageUri,
                        isVisualMode = isVisualMode,
                        onToggleVisualMode = onToggleVisualMode,
                        isReadOnly = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) { rightWeight -= dw; centerWeight += dw }
            })

            Surface(modifier = Modifier.weight(rightWeight).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PropertyPanel(
                        selectedBlock = state.selectedBlock,
                        currentEditingLang = state.currentEditingPromptLang,
                        isGenerateTransparent = state.isGenerateTransparent,
                        isPrioritizeCloud = state.isPrioritizeCloudRemoval,
                        onToggleTransparent = onToggleTransparent,
                        onTogglePrioritizeCloud = onTogglePrioritizeCloud,
                        onSwitchEditingLang = onSwitchEditingLanguage,
                        isGenerating = state.isGenerating,
                        onPromptChanged = onPromptChanged,
                        onGenerateRequested = onGenerateRequested,
                        onShowHistory = { state.selectedBlock?.let { block -> coroutineScope.launch { historicalImages = onLoadHistoricalImages(block.id); showHistoricalDialog = true } } },
                        onUnbindImage = { state.selectedBlockId?.let { onClearSelectedImage(it) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(
    selectedBlock: UIBlock?,
    currentEditingLang: PromptLanguage,
    isGenerateTransparent: Boolean,
    isPrioritizeCloud: Boolean,
    onToggleTransparent: (Boolean) -> Unit,
    onTogglePrioritizeCloud: (Boolean) -> Unit,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    isGenerating: Boolean,
    onPromptChanged: (String) -> Unit,
    onGenerateRequested: () -> Unit,
    onShowHistory: () -> Unit,
    onUnbindImage: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(Res.string.editor_gen_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
        if (selectedBlock == null) {
            Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = AppShapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("模块物理参数 (只读)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoItem("X", selectedBlock.bounds.left.toInt().toString(), Modifier.weight(1f))
                        InfoItem("Y", selectedBlock.bounds.top.toInt().toString(), Modifier.weight(1f))
                        InfoItem("W", selectedBlock.bounds.width.toInt().toString(), Modifier.weight(1f))
                        InfoItem("H", selectedBlock.bounds.height.toInt().toString(), Modifier.weight(1f))
                    }
                }
            }
            Text(text = "当前绑定资源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Card(modifier = Modifier.fillMaxWidth().height(160.dp), shape = AppShapes.medium, colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedBlock.currentImageUri != null) {
                        AsyncImage(model = selectedBlock.currentImageUri, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HideImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Text("尚未绑定任何资源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowHistory, modifier = Modifier.weight(1f), shape = AppShapes.medium) { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("历史记录") }
                OutlinedButton(onClick = onUnbindImage, modifier = Modifier.weight(1f), shape = AppShapes.medium, enabled = selectedBlock.currentImageUri != null, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("解绑") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang -> SegmentedButton(selected = currentEditingLang == lang, onClick = { onSwitchEditingLang(lang) }, shape = SegmentedButtonDefaults.itemShape(index = index, count = 2), label = { Text(lang.displayName) }) }
            }
            val displayPrompt = if (currentEditingLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
            OutlinedTextField(value = displayPrompt, onValueChange = { onPromptChanged(it) }, label = { Text("${stringResource(Res.string.label_description_content)} (${currentEditingLang.displayName})") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), maxLines = 8, enabled = !isGenerating)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isGenerateTransparent, onCheckedChange = { onToggleTransparent(it) }, enabled = !isGenerating); Column(modifier = Modifier.padding(start = 8.dp)) { Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodyMedium); Text("开启后自动处理背景", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (isGenerateTransparent) { Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isPrioritizeCloud, onCheckedChange = { onTogglePrioritizeCloud(it) }, enabled = !isGenerating); Column(modifier = Modifier.padding(start = 8.dp)) { Text("优先云端抠图", style = MaterialTheme.typography.bodyMedium) } } }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGenerateRequested, modifier = Modifier.fillMaxWidth(), enabled = !isGenerating && displayPrompt.isNotBlank(), shape = AppShapes.medium) { if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Text(stringResource(Res.string.editor_start_gen)) }
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

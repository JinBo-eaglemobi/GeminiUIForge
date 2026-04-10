package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onImageSelected: (String) -> Unit,
    onDeleteImages: (List<String>) -> Unit,
    onClearHistoricalCandidates: () -> Unit, // 清除本次生成的候选
    onClearSelectedImage: (String) -> Unit, // 解除绑定
    onLoadHistoricalImages: suspend (String) -> List<String>, // 加载历史资源
    onMoveBlock: (String, String?, DropPosition) -> Unit,
    onBlockDragged: (String, Float, Float) -> Unit,
    onRenameBlock: (String, String) -> Unit,
    onAddCustomBlock: (String, UIBlockType, Float, Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showHistoricalDialog by remember { mutableStateOf(false) }
    var historicalImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }

    // 处理 AI 刚生成完毕的弹窗展示
    var showCurrentGenerationResults by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.generatedCandidates) {
        if (!state.isGenerating && state.generatedCandidates.isNotEmpty()) {
            showCurrentGenerationResults = true
        }
    }

    if (showCurrentGenerationResults) {
        AssetSelectionDialog(
            title = "AI 生成资源预览",
            candidates = state.generatedCandidates,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            onImageSelected = { 
                onImageSelected(it)
                showCurrentGenerationResults = false
            },
            onDeleteImages = { uris ->
                // 删除后，UI 会自动通过 state.generatedCandidates 的变化而刷新
                onDeleteImages(uris)
            },
            onClearAll = {
                onClearHistoricalCandidates()
                showCurrentGenerationResults = false
            },
            onDismiss = { showCurrentGenerationResults = false }
        )
    }

    if (showHistoricalDialog) {
        AssetSelectionDialog(
            title = "历史资源列表",
            candidates = historicalImages,
            initialSelectedUri = state.selectedBlock?.currentImageUri,
            onImageSelected = {
                onImageSelected(it)
                showHistoricalDialog = false
            },
            onDeleteImages = { uris ->
                // 关键修复：同步更新本地历史记录列表，触发弹窗刷新
                onDeleteImages(uris)
                historicalImages = historicalImages.filter { it !in uris }
            },
            onClearAll = { /* 历史记录预览不提供清理所有候选的功能 */ },
            onDismiss = { showHistoricalDialog = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        
        var leftWeight by remember { mutableStateOf(0.15f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.3f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // 最左侧：层级列表
            HierarchySidebar(
                blocks = state.currentPage?.blocks ?: emptyList(),
                selectedBlockId = state.selectedBlockId,
                onBlockClicked = onBlockClicked,
                onMoveBlock = onMoveBlock,
                onAddCustomBlock = onAddCustomBlock,
                onRenameBlock = onRenameBlock,
                modifier = Modifier.weight(leftWeight).fillMaxHeight(),
                isReadOnly = true
            )

            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (leftWeight + dw in 0.1f..0.3f) {
                    leftWeight += dw
                    centerWeight -= dw
                }
            })

            // 中间：标签页 + 画布面板
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
                                onClick = { onPageSelected(page.id) },
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
                        selectedBlockId = state.selectedBlockId,
                        onBlockClicked = onBlockClicked,
                        onBlockDoubleClicked = onBlockDoubleClicked,
                        onBlockDragged = onBlockDragged,
                        editingGroupId = state.editingGroupId,
                        onExitGroupEdit = onExitGroupEdit,
                        referenceUri = state.currentPage?.sourceImageUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            VerticalSplitter(onDrag = { delta ->
                val dw = delta / totalWidthPx
                if (rightWeight - dw in 0.2f..0.4f) {
                    rightWeight -= dw
                    centerWeight += dw
                }
            })

            // 右侧属性面板
            Surface(
                modifier = Modifier.weight(rightWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    PropertyPanel(
                        selectedBlock = state.selectedBlock,
                        currentEditingLang = state.currentEditingPromptLang,
                        onSwitchEditingLang = onSwitchEditingLanguage,
                        isGenerating = state.isGenerating,
                        onPromptChanged = onPromptChanged,
                        onGenerateRequested = onGenerateRequested,
                        onShowHistory = {
                            state.selectedBlock?.let { block ->
                                coroutineScope.launch {
                                    historicalImages = onLoadHistoricalImages(block.id)
                                    showHistoricalDialog = true
                                }
                            }
                        },
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
    selectedBlock: org.gemini.ui.forge.model.ui.UIBlock?,
    currentEditingLang: PromptLanguage,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    isGenerating: Boolean,
    onPromptChanged: (String) -> Unit,
    onGenerateRequested: () -> Unit,
    onShowHistory: () -> Unit,
    onUnbindImage: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(Res.string.editor_gen_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedBlock == null) {
            Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 资源预览与操作区
            Text(
                text = "当前绑定资源",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = AppShapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedBlock.currentImageUri != null) {
                        AsyncImage(
                            model = selectedBlock.currentImageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HideImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Text("尚未绑定任何资源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShowHistory,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("历史记录")
                }
                OutlinedButton(
                    onClick = onUnbindImage,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.medium,
                    enabled = selectedBlock.currentImageUri != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("解除绑定")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 提示词语言切换器
            Text(
                text = stringResource(Res.string.editor_prompt_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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

            OutlinedTextField(
                value = displayPrompt,
                onValueChange = { onPromptChanged(it) },
                label = { Text("${stringResource(Res.string.label_description_content)} (${currentEditingLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 8,
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGenerateRequested,
                modifier = Modifier.fillMaxWidth().widthIn(min = 120.dp),
                enabled = !isGenerating && displayPrompt.isNotBlank(),
                shape = AppShapes.medium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(Res.string.editor_start_gen))
                    }
                }
            }
        }
    }
}

package org.gemini.ui.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.viewmodel.EditorState
import org.gemini.ui.forge.viewmodel.PromptLanguage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.input.pointer.pointerHoverIcon
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*

@Composable
fun EditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String) -> Unit,
    onBlockDoubleClicked: (String) -> Unit,
    onExitGroupEdit: () -> Unit,
    onPromptChanged: (String) -> Unit,
    onSwitchEditingLanguage: (PromptLanguage) -> Unit,
    onGenerateRequested: () -> Unit,
    onImageSelected: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidth = maxWidth
        
        // 全平台统一横向布局（左侧层级 + 中间画布区 + 右侧属性栏）
        var hierarchyWeight by remember { mutableStateOf(0.15f) }
        var canvasWeight by remember { mutableStateOf(0.55f) }
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        Row(modifier = Modifier.fillMaxSize()) {
            // 最左侧：层级列表
            HierarchySidebar(
                blocks = state.currentPage?.blocks ?: emptyList(),
                selectedBlockId = state.selectedBlockId,
                onBlockClicked = onBlockClicked,
                modifier = Modifier.weight(hierarchyWeight).fillMaxHeight()
            )

            // 拖拽分割线 1
            VerticalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())

            // 中间：标签页 + 画布面板
            Column(modifier = Modifier.weight(canvasWeight).fillMaxHeight()) {
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
                        editingGroupId = state.editingGroupId,
                        onExitGroupEdit = onExitGroupEdit,
                        referenceUri = state.currentPage?.sourceImageUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 中间的拖拽分割线 2
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerHoverIcon(org.gemini.ui.forge.ResizeHorizontalIcon)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val deltaWeight = delta / totalWidthPx
                            // 简单调整 canvasWeight，保持整体比例
                            canvasWeight = (canvasWeight - deltaWeight).coerceIn(0.3f, 0.7f)
                        }
                    )
            )

            // 右侧属性面板
            Surface(
                modifier = Modifier.weight(1f - hierarchyWeight - canvasWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    CandidateGallery(
                        candidates = state.generatedCandidates,
                        onImageSelected = onImageSelected
                    )
                    PropertyPanel(
                        selectedBlock = state.selectedBlock,
                        currentEditingLang = state.currentEditingPromptLang,
                        onSwitchEditingLang = onSwitchEditingLanguage,
                        isGenerating = state.isGenerating,
                        onPromptChanged = onPromptChanged,
                        onGenerateRequested = onGenerateRequested
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(
    selectedBlock: org.gemini.ui.forge.domain.UIBlock?,
    currentEditingLang: PromptLanguage,
    onSwitchEditingLang: (PromptLanguage) -> Unit,
    isGenerating: Boolean,
    onPromptChanged: (String) -> Unit,
    onGenerateRequested: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "生成设置",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedBlock == null) {
            Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 提示词语言切换器
            Text(
                text = "生成提示词语言",
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

            // 根据选定语言动态提取展示文本
            val displayPrompt = if (currentEditingLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh

            OutlinedTextField(
                value = displayPrompt,
                onValueChange = { onPromptChanged(it) },
                label = { Text("详细描述 (${currentEditingLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 8,
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGenerateRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGenerating && displayPrompt.isNotBlank()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(if (isGenerating) "正在生成..." else "开始生成图片资源")
            }
        }
    }
}

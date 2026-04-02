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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.input.pointer.pointerHoverIcon

@Composable
fun EditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onGenerateRequested: () -> Unit,
    onImageSelected: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidth = maxWidth
        
        // 全平台统一横向布局（左侧画布区 + 右侧属性栏�?
        var leftWeight by remember { mutableStateOf(0.6f) }
        val totalWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }

        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：标签页 + 画布面板
            Column(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 中间的拖拽分割线
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
                            leftWeight = (leftWeight + deltaWeight).coerceIn(0.3f, 0.7f)
                        }
                    )
            )

            // 右侧属性面�?
            Surface(
                modifier = Modifier.weight(1f - leftWeight).fillMaxHeight(),
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
                        isGenerating = state.isGenerating,
                        onPromptChanged = onPromptChanged,
                        onGenerateRequested = onGenerateRequested
                    )
                }
            }
        }
    }
}

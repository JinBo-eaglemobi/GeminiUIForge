package org.gemini.ui.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.gemini.ui.forge.viewmodel.EditorState
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerHoverIcon

@Composable
fun TemplateEditorScreen(
    state: EditorState,
    onPageSelected: (String) -> Unit,
    onBlockClicked: (String) -> Unit,
    onBlockBoundsChanged: (String, Float, Float, Float, Float) -> Unit,
    onBlockTypeChanged: (String, UIBlockType) -> Unit,
    onAddBlock: (UIBlockType) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onSaveTemplate: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidth = maxWidth
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        
        var leftWeight by remember { mutableStateOf(0.2f) }
        var centerWeight by remember { mutableStateOf(0.55f) }
        var rightWeight by remember { mutableStateOf(0.25f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // ==================== 左侧：工具栏 ====================
            Surface(
                modifier = Modifier.weight(leftWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        stringResource(Res.string.editor_tools), 
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text("Pages:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val pages = state.project.pages
                    pages.forEach { page ->
                        val isSelected = state.selectedPageId == page.id
                        TextButton(
                            onClick = { onPageSelected(page.id) },
                            colors = if(isSelected) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary) 
                                     else ButtonDefaults.textButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(start = 8.dp)
                        ) {
                            Text(page.nameStr, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Add UI Block:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(UIBlockType.entries.toTypedArray()) { type ->
                            OutlinedButton(
                                onClick = { onAddBlock(type) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(type.getDisplayNameRes()), maxLines = 1)
                            }
                        }
                    }
                    
                    Button(
                        onClick = onSaveTemplate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(stringResource(Res.string.action_save_layout))
                    }
                }
            }

            // 分割线 1
            VerticalSplitter(
                onDrag = { deltaPx ->
                    val deltaW = deltaPx / totalWidthPx
                    if (leftWeight + deltaW in 0.15f..0.3f) {
                        leftWeight += deltaW
                        centerWeight -= deltaW
                    }
                }
            )

            // ==================== 中间：画布 ====================
            Box(modifier = Modifier.weight(centerWeight).fillMaxHeight()) {
                CanvasArea(
                    pageWidth = state.currentPage?.width ?: 1080f,
                    pageHeight = state.currentPage?.height ?: 1920f,
                    blocks = state.currentPage?.blocks ?: emptyList(),
                    selectedBlockId = state.selectedBlockId,
                    onBlockClicked = onBlockClicked,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 分割线 2
            VerticalSplitter(
                onDrag = { deltaPx ->
                    val deltaW = deltaPx / totalWidthPx
                    if (rightWeight - deltaW in 0.2f..0.4f) {
                        rightWeight -= deltaW
                        centerWeight += deltaW
                    }
                }
            )

            // ==================== 右侧：属性面板 ====================
            Surface(
                modifier = Modifier.weight(rightWeight).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                val block = state.selectedBlock
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(Res.string.editor_properties), 
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (block == null) {
                        Text("Select a block to edit its bounds.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BlockPropertiesEditor(
                            block = block,
                            onBoundsChanged = { l, t, r, b -> onBlockBoundsChanged(block.id, l, t, r, b) },
                            onTypeChanged = { newType -> onBlockTypeChanged(block.id, newType) },
                            onDelete = { onDeleteBlock(block.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalSplitter(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerHoverIcon(org.gemini.ui.forge.ResizeHorizontalIcon)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDrag(delta) }
            )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockPropertiesEditor(
    block: UIBlock,
    onBoundsChanged: (Float, Float, Float, Float) -> Unit,
    onTypeChanged: (UIBlockType) -> Unit,
    onDelete: () -> Unit
) {
    var expandedType by remember { mutableStateOf(false) }

    // Type Selector
    ExposedDropdownMenuBox(
        expanded = expandedType,
        onExpandedChange = { expandedType = !expandedType }
    ) {
        OutlinedTextField(
            value = stringResource(block.type.getDisplayNameRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.prop_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expandedType,
            onDismissRequest = { expandedType = false }
        ) {
            UIBlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.getDisplayNameRes())) },
                    onClick = {
                        onTypeChanged(type)
                        expandedType = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Bounds editing
    var xStr by remember(block.id, block.bounds.left) { mutableStateOf(block.bounds.left.toInt().toString()) }
    var yStr by remember(block.id, block.bounds.top) { mutableStateOf(block.bounds.top.toInt().toString()) }
    var wStr by remember(block.id, block.bounds.width) { mutableStateOf(block.bounds.width.toInt().toString()) }
    var hStr by remember(block.id, block.bounds.height) { mutableStateOf(block.bounds.height.toInt().toString()) }

    fun submitBounds() {
        val x = xStr.toFloatOrNull() ?: block.bounds.left
        val y = yStr.toFloatOrNull() ?: block.bounds.top
        val w = wStr.toFloatOrNull()?.coerceAtLeast(10f) ?: block.bounds.width
        val h = hStr.toFloatOrNull()?.coerceAtLeast(10f) ?: block.bounds.height
        onBoundsChanged(x, y, x + w, y + h)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = xStr,
            onValueChange = { xStr = it; submitBounds() },
            label = { Text(stringResource(Res.string.prop_x)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = yStr,
            onValueChange = { yStr = it; submitBounds() },
            label = { Text(stringResource(Res.string.prop_y)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = wStr,
            onValueChange = { wStr = it; submitBounds() },
            label = { Text(stringResource(Res.string.prop_width)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = hStr,
            onValueChange = { hStr = it; submitBounds() },
            label = { Text(stringResource(Res.string.prop_height)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onDelete,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.action_delete_block))
    }
}

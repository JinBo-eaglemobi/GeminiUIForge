package org.gemini.ui.forge.ui.feature.common
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.ResizeVerticalIcon
import androidx.compose.ui.unit.Density
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.getDisplayNameRes

@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String?) -> Unit,
    onBlockDoubleClicked: (String) -> Unit = {},
    onBlockDragStart: (String) -> Unit = {},
    onBlockDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    editingGroupId: String? = null,
    onExitGroupEdit: () -> Unit = {},
    referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    referenceUri: String? = null,
    referenceOpacity: Float = 0.4f,
    isVisualMode: Boolean = false,
    onToggleVisualMode: () -> Unit = {},
    isReadOnly: Boolean = false,
    stageBackgroundColor: String = "#2D2D2D",
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val stageColor = remember(stageBackgroundColor) {
        try {
            val colorStr = stageBackgroundColor.removePrefix("#")
            val colorLong = colorStr.toLong(16)
            if (colorStr.length <= 6) Color(colorLong or 0xFF000000L) else Color(colorLong)
        } catch (e: Exception) {
            Color(0xFF2D2D2D)
        }
    }

    fun updateZoom(newZoom: Float, centroid: Offset) {
        val oldZoom = zoom
        val nextZoom = newZoom.coerceIn(0.1f, 10f)
        if (oldZoom == nextZoom) return
        val zoomFactor = nextZoom / oldZoom
        pan = Offset(
            x = centroid.x - (centroid.x - pan.x) * zoomFactor,
            y = centroid.y - (centroid.y - pan.y) * zoomFactor
        )
        zoom = nextZoom
    }

    var internalReferenceMode by remember { mutableStateOf(referenceMode) }
    LaunchedEffect(referenceMode) { internalReferenceMode = referenceMode }
    var internalReferenceOpacity by remember { mutableStateOf(referenceOpacity) }

    val currentBlocks by rememberUpdatedState(blocks)
    val currentEditingGroupId by rememberUpdatedState(editingGroupId)
    val currentOnBlockClicked by rememberUpdatedState(onBlockClicked)
    val currentOnBlockDoubleClicked by rememberUpdatedState(onBlockDoubleClicked)
    val currentOnBlockDragged by rememberUpdatedState(onBlockDragged)
    val currentOnBlockDragStart by rememberUpdatedState(onBlockDragStart)
    val currentOnExitGroupEdit by rememberUpdatedState(onExitGroupEdit)

    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value
    var splitWeight by remember { mutableStateOf(0.5f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) {
                Surface(modifier = Modifier.weight(splitWeight).fillMaxWidth().padding(8.dp), color = Color.Black, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Image(bitmap = refBitmap, contentDescription = "Ref", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant).pointerHoverIcon(ResizeVerticalIcon).draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta -> val deltaWeight = delta / totalHeightPx; splitWeight = (splitWeight + deltaWeight).coerceIn(0.1f, 0.9f) }))
            }

            val canvasWeight = if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
            Box(modifier = Modifier.weight(canvasWeight).fillMaxWidth().clipToBounds()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val baseScale = min(maxWidth.value / pageWidth, maxHeight.value / pageHeight) * 0.9f
                    val offsetX = (maxWidth.value - (pageWidth * baseScale)) / 2
                    val offsetY = (maxHeight.value - (pageHeight * baseScale)) / 2

                    // 核心交互层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(zoom, pan, offsetX, offsetY, baseScale) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    val viewCenterX = containerWidthPx / 2f
                                    val viewCenterY = containerHeightPx / 2f
                                    updateZoom(zoom * zoomChange, Offset(viewCenterX, viewCenterY))
                                    pan += panChange
                                }
                            }
                            .pointerInput(zoom, pan, offsetX, offsetY, baseScale) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val change = event.changes.firstOrNull() ?: continue
                                            val delta = change.scrollDelta.y
                                            if (delta != 0f) {
                                                val multiplier = if (delta > 0) 0.9f else 1.1f
                                                val viewCenterX = containerWidthPx / 2f
                                                val viewCenterY = containerHeightPx / 2f
                                                updateZoom(zoom * multiplier, Offset(viewCenterX, viewCenterY))
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        // 舞台背景绘制
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX.dp, y = offsetY.dp)
                                .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp)
                                .background(stageColor)
                                .border(BorderStroke((1.dp / zoom) / baseScale, MaterialTheme.colorScheme.outlineVariant))
                        )

                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                            Image(bitmap = refBitmap, contentDescription = null, alpha = internalReferenceOpacity, modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp).size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp), contentScale = ContentScale.FillBounds)
                        }

                        // 内容展示 Box
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(zoom, pan, offsetX, offsetY, baseScale) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                            val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            
                                            if (hitBlock != null) {
                                                currentOnBlockDoubleClicked(hitBlock.id)
                                            } else {
                                                // 核心修复：双击空白处退出隔离模式
                                                if (currentEditingGroupId != null) currentOnExitGroupEdit()
                                            }
                                        },
                                        onTap = { offset ->
                                            val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                            val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            currentOnBlockClicked(hitBlock?.id)
                                        }
                                    )
                                }
                                .pointerInput(zoom, pan, offsetX, offsetY, baseScale, isReadOnly) {
                                    if (isReadOnly) return@pointerInput
                                    var dragTargetId: String? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                            val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            
                                            // 核心修复：命中即可拖拽
                                            if (hitBlock != null) {
                                                dragTargetId = hitBlock.id
                                                currentOnBlockDragStart(hitBlock.id)
                                            } else {
                                                dragTargetId = null
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            val tid = dragTargetId ?: return@detectDragGestures
                                            change.consume()
                                            val logicalDx = dragAmount.x / density.density / (baseScale * zoom)
                                            val logicalDy = dragAmount.y / density.density / (baseScale * zoom)
                                            currentOnBlockDragged(tid, logicalDx, logicalDy)
                                        },
                                        onDragEnd = { dragTargetId = null },
                                        onDragCancel = { dragTargetId = null }
                                    )
                                }
                        ) {
                            currentBlocks.forEach { block ->
                                RenderBlock(
                                    block = block,
                                    parentX = offsetX,
                                    parentY = offsetY,
                                    baseScale = baseScale,
                                    zoom = zoom,
                                    isSelected = block.id == selectedBlockId,
                                    isDimmed = shouldDim(block, editingGroupId),
                                    isVisualMode = isVisualMode,
                                    density = density,
                                    selectedBlockId = selectedBlockId,
                                    editingGroupId = editingGroupId
                                )
                            }
                        }
                    }
                }
            }
        }

        // 隔离模式指示器：恢复为轻量化样式
        if (editingGroupId != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    
                    val prefix = stringResource(Res.string.group_editing_indicator_prefix)
                    Text(
                        text = "$prefix $editingGroupId",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                    Spacer(Modifier.width(8.dp))
                    
                    Text(
                        text = stringResource(Res.string.action_exit),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clickable { onExitGroupEdit() }
                            .padding(4.dp)
                    )
                }
            }
        }

        // 全局浮动控制栏
        Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), shadowElevation = 6.dp) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val viewCenterX = containerWidthPx / 2f
                val viewCenterY = containerHeightPx / 2f
                val centerOffset = Offset(viewCenterX, viewCenterY)
                IconButton(onClick = { updateZoom(zoom - 0.2f, centerOffset) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, "-", modifier = Modifier.size(16.dp)) }
                Box(modifier = Modifier.height(28.dp).width(42.dp), contentAlignment = Alignment.Center) { Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) }
                IconButton(onClick = { updateZoom(zoom + 0.2f, centerOffset) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, "+", modifier = Modifier.size(16.dp)) }
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(18.dp)) }
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconToggleButton(checked = isVisualMode, onCheckedChange = { onToggleVisualMode() }, modifier = Modifier.size(28.dp)) {
                    Icon(if (isVisualMode) Icons.Default.AutoFixNormal else Icons.Default.AutoFixOff, "Visual", modifier = Modifier.size(18.dp), tint = if (isVisualMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                if (referenceUri != null) {
                    VerticalDivider(modifier = Modifier.height(16.dp))
                    val isRefEnabled = internalReferenceMode != ReferenceDisplayMode.HIDDEN
                    IconToggleButton(checked = isRefEnabled, onCheckedChange = { internalReferenceMode = if (it) ReferenceDisplayMode.SPLIT else ReferenceDisplayMode.HIDDEN }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isRefEnabled) Icons.Default.Image else Icons.Default.VisibilityOff, "Toggle Ref", modifier = Modifier.size(18.dp), tint = if (isRefEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isRefEnabled) {
                        VerticalDivider(modifier = Modifier.height(16.dp))
                        IconToggleButton(checked = internalReferenceMode == ReferenceDisplayMode.SPLIT, onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.SPLIT }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.VerticalSplit, "Split", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                        IconToggleButton(checked = internalReferenceMode == ReferenceDisplayMode.OVERLAY, onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.OVERLAY }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Layers, "Overlay", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) { Spacer(modifier = Modifier.width(4.dp)); Slider(value = internalReferenceOpacity, onValueChange = { internalReferenceOpacity = it }, modifier = Modifier.width(100.dp).height(24.dp), valueRange = 0.1f..1f) }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderBlock(
    block: UIBlock,
    parentX: Float,
    parentY: Float,
    baseScale: Float,
    zoom: Float,
    isSelected: Boolean,
    isDimmed: Boolean,
    isVisualMode: Boolean,
    density: Density,
    selectedBlockId: String?,
    editingGroupId: String?
) {
    if (!block.isVisible) return
    val imageBitmapState = produceState<ImageBitmap?>(null, block.currentImageUri) { value = block.currentImageUri?.decodeBase64ToBitmap() }
    val imageBitmap = imageBitmapState.value
    val currentX = parentX + block.bounds.left * baseScale
    val currentY = parentY + block.bounds.top * baseScale
    val hidePlaceholder = isVisualMode && imageBitmap != null

    val selectionColor = Color(0xFF18A0FB) // Figma 风格的专业选中蓝

    Box(
        modifier = Modifier
            .offset(x = currentX.dp, y = currentY.dp)
            .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (hidePlaceholder) Color.Transparent 
                else if (isSelected) selectionColor.copy(alpha = 0.15f) 
                else if (isDimmed) Color.Black.copy(alpha = 0.4f) 
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .border(
                width = if (hidePlaceholder && !isSelected) 0.dp 
                        else (1.dp / zoom), 
                color = if (isSelected) selectionColor 
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else if (block.currentImageUri != null) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
        } 
        if (!hidePlaceholder && imageBitmap == null && block.currentImageUri == null) {
            Text(text = stringResource(block.type.getDisplayNameRes()), style = MaterialTheme.typography.labelSmall, color = if (isDimmed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
    }

    block.children.forEach { child ->
        RenderBlock(block = child, parentX = currentX, parentY = currentY, baseScale = baseScale, zoom = zoom, isSelected = child.id == selectedBlockId, isDimmed = isDimmed, isVisualMode = isVisualMode, density = density, selectedBlockId = selectedBlockId, editingGroupId = editingGroupId)
    }
}

private fun findHitBlock(blocks: List<UIBlock>, lx: Float, ly: Float, parentLx: Float, parentLy: Float, editingGroupId: String?): UIBlock? {
    if (editingGroupId == null) {
        for (i in blocks.indices.reversed()) {
            val block = blocks[i]
            if (!block.isVisible) continue
            val absL = parentLx + block.bounds.left
            val absT = parentLy + block.bounds.top
            val absR = parentLx + block.bounds.right
            val absB = parentLy + block.bounds.bottom
            if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) return block
        }
        return null
    }
    val targetGroup = findBlockInList(blocks, editingGroupId) ?: return null
    val groupAbsPos = calculateBlockAbsolutePosition(blocks, editingGroupId) ?: Offset.Zero
    for (i in targetGroup.children.indices.reversed()) {
        val child = targetGroup.children[i]
        if (!child.isVisible) continue
        val absL = groupAbsPos.x + child.bounds.left
        val absT = groupAbsPos.y + child.bounds.top
        val absR = groupAbsPos.x + child.bounds.right
        val absB = groupAbsPos.y + child.bounds.bottom
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) return child
    }
    val gL = groupAbsPos.x
    val gT = groupAbsPos.y
    val gR = groupAbsPos.x + targetGroup.bounds.width
    val gB = groupAbsPos.y + targetGroup.bounds.height
    if (lx >= gL && lx <= gR && ly >= gT && ly <= gB) return targetGroup
    return null
}

private fun findBlockInList(blocks: List<UIBlock>, id: String): UIBlock? {
    for (block in blocks) {
        if (block.id == id) return block
        val found = findBlockInList(block.children, id)
        if (found != null) return found
    }
    return null
}

private fun calculateBlockAbsolutePosition(blocks: List<UIBlock>, id: String, currentX: Float = 0f, currentY: Float = 0f): Offset? {
    for (block in blocks) {
        val absX = currentX + block.bounds.left
        val absY = currentY + block.bounds.top
        if (block.id == id) return Offset(absX, absY)
        val found = calculateBlockAbsolutePosition(block.children, id, absX, absY)
        if (found != null) return found
    }
    return null
}

private fun shouldDim(block: UIBlock, editingGroupId: String?): Boolean {
    if (editingGroupId == null) return false
    if (block.id == editingGroupId) return false
    return !containsBlock(block, editingGroupId)
}

private fun containsBlock(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { containsBlock(it, targetId) }
}

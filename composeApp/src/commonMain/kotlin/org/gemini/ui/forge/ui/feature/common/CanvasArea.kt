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
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    var internalReferenceMode by remember { mutableStateOf(referenceMode) }
    LaunchedEffect(referenceMode) { internalReferenceMode = referenceMode }
    var internalReferenceOpacity by remember { mutableStateOf(referenceOpacity) }

    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value
    var splitWeight by remember { mutableStateOf(0.5f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalHeightPx = with(density) { maxHeight.toPx() }

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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(blocks, zoom, pan, offsetX, offsetY, baseScale) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        // 核心修正：考虑 transformOrigin=(0,0) 的点击坐标逆推
                                        val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                        val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                        val hitBlock = findHitBlock(blocks, lx, ly, 0f, 0f, editingGroupId)
                                        if (hitBlock != null) onBlockDoubleClicked(hitBlock.id)
                                    },
                                    onTap = { offset ->
                                        val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                        val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                        val hitBlock = findHitBlock(blocks, lx, ly, 0f, 0f, editingGroupId)
                                        onBlockClicked(hitBlock?.id)
                                    }
                                )
                            }
                            .pointerInput(blocks, zoom, pan, offsetX, offsetY, baseScale) {
                                var dragTargetId: String? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val lx = (offset.x / density.density - pan.x / density.density) / (baseScale * zoom) - offsetX / baseScale
                                        val ly = (offset.y / density.density - pan.y / density.density) / (baseScale * zoom) - offsetY / baseScale
                                        val hitBlock = findHitBlock(blocks, lx, ly, 0f, 0f, editingGroupId)
                                        if (hitBlock != null && !shouldDim(hitBlock, editingGroupId)) {
                                            dragTargetId = hitBlock.id
                                            if (hitBlock.id != selectedBlockId) onBlockClicked(hitBlock.id)
                                            onBlockDragStart(hitBlock.id)
                                        } else dragTargetId = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        val tid = dragTargetId ?: return@detectDragGestures
                                        change.consume()
                                        val logicalDx = dragAmount.x / density.density / (baseScale * zoom)
                                        val logicalDy = dragAmount.y / density.density / (baseScale * zoom)
                                        onBlockDragged(tid, logicalDx, logicalDy)
                                    },
                                    onDragEnd = { dragTargetId = null },
                                    onDragCancel = { dragTargetId = null }
                                )
                            }
                            .pointerInput(Unit) { detectTransformGestures { _, panChange, zoomChange, _ -> zoom = (zoom * zoomChange).coerceIn(0.1f, 5f); pan += panChange } }
                            .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.type == PointerEventType.Scroll) { val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f; if (delta != 0f) zoom = (zoom * (if (delta > 0) 0.9f else 1.1f)).coerceIn(0.1f, 5f); event.changes.forEach { it.consume() } } } } }
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0f, 0f) // 强制 (0,0) 为原点，简化数学推导
                            }
                    ) {
                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                            Image(bitmap = refBitmap, contentDescription = null, alpha = internalReferenceOpacity, modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp).size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp), contentScale = ContentScale.FillBounds)
                        }

                        blocks.forEach { block ->
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

        if (editingGroupId != null) {
            Surface(modifier = Modifier.align(Alignment.TopStart).padding(12.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(Res.string.group_editing_indicator, editingGroupId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.action_exit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.clickable { onExitGroupEdit() }.padding(4.dp))
                }
            }
        }

        Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), shadowElevation = 6.dp) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", modifier = Modifier.size(16.dp)) }
                Box(modifier = Modifier.height(28.dp).width(42.dp), contentAlignment = Alignment.Center) { Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) }
                IconButton(onClick = { zoom = (zoom + 0.2f).coerceAtMost(5f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", modifier = Modifier.size(16.dp)) }
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp)) }
                
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconToggleButton(checked = isVisualMode, onCheckedChange = { onToggleVisualMode() }, modifier = Modifier.size(28.dp)) {
                    Icon(if (isVisualMode) Icons.Default.AutoFixNormal else Icons.Default.AutoFixOff, contentDescription = "Visual Mode", modifier = Modifier.size(18.dp), tint = if (isVisualMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }

                if (referenceUri != null) {
                    VerticalDivider(modifier = Modifier.height(16.dp))
                    val isRefEnabled = internalReferenceMode != ReferenceDisplayMode.HIDDEN
                    IconToggleButton(checked = isRefEnabled, onCheckedChange = { internalReferenceMode = if (it) ReferenceDisplayMode.SPLIT else ReferenceDisplayMode.HIDDEN }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isRefEnabled) Icons.Default.Image else Icons.Default.VisibilityOff, contentDescription = "Toggle Reference", modifier = Modifier.size(18.dp), tint = if (isRefEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    if (isRefEnabled) {
                        VerticalDivider(modifier = Modifier.height(16.dp))
                        IconToggleButton(checked = internalReferenceMode == ReferenceDisplayMode.SPLIT, onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.SPLIT }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.VerticalSplit, contentDescription = "Split View", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                        IconToggleButton(checked = internalReferenceMode == ReferenceDisplayMode.OVERLAY, onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.OVERLAY }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Layers, contentDescription = "Overlay View", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
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

    Box(
        modifier = Modifier
            .offset(x = currentX.dp, y = currentY.dp)
            .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (hidePlaceholder) Color.Transparent else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else if (isDimmed) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(width = if (hidePlaceholder && !isSelected) 0.dp else if (isSelected) (2.dp / zoom) else (1.dp / zoom), color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
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

private fun findHitBlock(blocks: List<UIBlock>, lx: Float, ly: Float, parentLx: Float, parentLy: Float, editingGroupId: String?, isInsideEditingGroup: Boolean = false): UIBlock? {
    for (i in blocks.indices.reversed()) {
        val block = blocks[i]
        if (!block.isVisible) continue
        val absL = parentLx + block.bounds.left
        val absT = parentLy + block.bounds.top
        val absR = parentLx + block.bounds.right
        val absB = parentLy + block.bounds.bottom
        val currentlyInside = isInsideEditingGroup || block.id == editingGroupId
        val hitChild = findHitBlock(block.children, lx, ly, absL, absT, editingGroupId, currentlyInside)
        if (hitChild != null) return hitChild
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) {
            if (editingGroupId == null || currentlyInside) return block
        }
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

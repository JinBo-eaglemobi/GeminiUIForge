package org.gemini.ui.forge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.viewmodel.ReferenceDisplayMode
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import kotlin.math.min
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.ResizeVerticalIcon
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.Density

@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    onBlockDoubleClicked: (String) -> Unit = {},
    onBlockDragStart: (String) -> Unit = {},
    onBlockDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    editingGroupId: String? = null,
    onExitGroupEdit: () -> Unit = {},
    
    // 参考图显示增强
    referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    referenceUri: String? = null,
    referenceOpacity: Float = 0.4f,
    
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 内部管理参考图显示模式（如果是通过传入修改，我们在此包装一层以支持内部切换）
    var internalReferenceMode by remember { mutableStateOf(referenceMode) }
    LaunchedEffect(referenceMode) { internalReferenceMode = referenceMode }

    var internalReferenceOpacity by remember { mutableStateOf(referenceOpacity) }

    // 异步加载参考图位图
    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value

    var splitWeight by remember { mutableStateOf(0.5f) } // 默认 50%

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalHeightPx = with(density) { maxHeight.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            
            // 模式 A：分屏对照 (参考图在上)
            if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) {
                Surface(
                    modifier = Modifier.weight(splitWeight).fillMaxWidth().padding(8.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Image(bitmap = refBitmap, contentDescription = "Ref", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }

                // 拖拽分割线 (上下分屏)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(org.gemini.ui.forge.ResizeVerticalIcon)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                val deltaWeight = delta / totalHeightPx
                                splitWeight = (splitWeight + deltaWeight).coerceIn(0.1f, 0.9f)
                            }
                        )
                )
            }

            // 主舞台区域
            val canvasWeight = if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
            Box(modifier = Modifier.weight(canvasWeight).fillMaxWidth().clipToBounds()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val baseScale = min(maxWidth.value / pageWidth, maxHeight.value / pageHeight) * 0.9f
                    val offsetX = (maxWidth.value - (pageWidth * baseScale)) / 2
                    val offsetY = (maxHeight.value - (pageHeight * baseScale)) / 2

                    // 核心容器：承载 UI 块、点击手势、参考蒙层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(blocks, editingGroupId) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        val lx = ((offset.x / density.density - offsetX - pan.x / density.density) / (baseScale * zoom))
                                        val ly = ((offset.y / density.density - offsetY - pan.y / density.density) / (baseScale * zoom))
                                        
                                        val hitBlock = findHitBlock(blocks, lx, ly, 0f, 0f, editingGroupId)
                                        if (hitBlock != null) onBlockDoubleClicked(hitBlock.id)
                                    },
                                    onTap = { offset ->
                                        val lx = ((offset.x / density.density - offsetX - pan.x / density.density) / (baseScale * zoom))
                                        val ly = ((offset.y / density.density - offsetY - pan.y / density.density) / (baseScale * zoom))
                                        
                                        val hitBlock = findHitBlock(blocks, lx, ly, 0f, 0f, editingGroupId)
                                        onBlockClicked(hitBlock?.id ?: "")
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(0.1f, 5f)
                                    pan += panChange
                                }
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                            if (delta != 0f) zoom = (zoom * (if (delta > 0) 0.9f else 1.1f)).coerceIn(0.1f, 5f)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
                    ) {
                        // 1. 渲染模式 B：重叠参考图 (作为底层)
                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                            Image(
                                bitmap = refBitmap,
                                contentDescription = null,
                                alpha = internalReferenceOpacity,
                                modifier = Modifier
                                    .offset(x = offsetX.dp, y = offsetY.dp)
                                    .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }

                        // 2. 渲染 UI 块集合 (递归)
                        blocks.forEach { block ->
                            RenderBlock(
                                block = block,
                                parentX = offsetX,
                                parentY = offsetY,
                                baseScale = baseScale,
                                zoom = zoom,
                                isSelected = block.id == selectedBlockId,
                                selectedBlockId = selectedBlockId,
                                editingGroupId = editingGroupId,
                                isDimmed = shouldDim(block, editingGroupId),
                                density = density,
                                onBlockClicked = onBlockClicked,
                                onBlockDragged = onBlockDragged
                            )
                        }
                    }
                }
            }
        } // 结束 Column

        // 孤立编辑模式状态提示 (悬浮在最外层)
        if (editingGroupId != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.group_editing_indicator, editingGroupId), 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.action_exit), 
                        style = MaterialTheme.typography.labelLarge, 
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clickable { onExitGroupEdit() }.padding(4.dp)
                    )
                }
            }
        }

        // 悬浮工具条 (悬浮在最外层)
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant, // 改为有明显区分度的背景色
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), // 添加边框
            shadowElevation = 6.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", modifier = Modifier.size(16.dp)) }
                Box(modifier = Modifier.height(28.dp).width(42.dp), contentAlignment = Alignment.Center) {
                    Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
                IconButton(onClick = { zoom = (zoom + 0.2f).coerceAtMost(5f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", modifier = Modifier.size(16.dp)) }
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp)) }
                
                if (referenceUri != null) {
                    VerticalDivider(modifier = Modifier.height(16.dp))
                    
                    val isRefEnabled = internalReferenceMode != ReferenceDisplayMode.HIDDEN
                    
                    // 开启/关闭参考图总开关
                    IconToggleButton(
                        checked = isRefEnabled,
                        onCheckedChange = { 
                            internalReferenceMode = if (it) ReferenceDisplayMode.SPLIT else ReferenceDisplayMode.HIDDEN 
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(if (isRefEnabled) Icons.Default.Image else Icons.Default.VisibilityOff, contentDescription = "Toggle Reference", modifier = Modifier.size(18.dp), tint = if (isRefEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }

                    if (isRefEnabled) {
                        VerticalDivider(modifier = Modifier.height(16.dp))
                        
                        // 上下分屏按钮
                        IconToggleButton(
                            checked = internalReferenceMode == ReferenceDisplayMode.SPLIT,
                            onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.SPLIT },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.VerticalSplit, contentDescription = "Split View", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        
                        // 叠加模式按钮
                        IconToggleButton(
                            checked = internalReferenceMode == ReferenceDisplayMode.OVERLAY,
                            onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.OVERLAY },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = "Overlay View", modifier = Modifier.size(18.dp), tint = if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }

                        // 叠加模式下的透明度滑动条
                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Slider(
                                value = internalReferenceOpacity,
                                onValueChange = { internalReferenceOpacity = it },
                                modifier = Modifier.width(100.dp).height(24.dp),
                                valueRange = 0.1f..1f
                            )
                        }
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
    density: Density,
    onBlockClicked: (String) -> Unit,
    onBlockDragged: (String, Float, Float) -> Unit,
    selectedBlockId: String?,
    editingGroupId: String?
) {
    val imageBitmapState = produceState<ImageBitmap?>(null, block.currentImageUri) {
        value = block.currentImageUri?.decodeBase64ToBitmap()
    }
    val imageBitmap = imageBitmapState.value

    val currentX = parentX + block.bounds.left * baseScale
    val currentY = parentY + block.bounds.top * baseScale

    val currentBaseScale by rememberUpdatedState(baseScale)
    val currentDensity by rememberUpdatedState(density.density)

    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnBlockClicked by rememberUpdatedState(onBlockClicked)
    val currentOnBlockDragged by rememberUpdatedState(onBlockDragged)

    Box(
        modifier = Modifier
            .offset(x = currentX.dp, y = currentY.dp)
            .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    isDimmed -> Color.Black.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) // 提高背景亮度
                }
            )
            .border(
                width = if (isSelected) (2.dp / zoom) else (1.dp / zoom),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) // 提高边框亮度
            )
            .pointerInput(block.id, isDimmed) {
                if (isDimmed) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        if (!currentIsSelected) {
                            currentOnBlockClicked(block.id)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val logicalDx = dragAmount.x / currentDensity / currentBaseScale
                        val logicalDy = dragAmount.y / currentDensity / currentBaseScale
                        currentOnBlockDragged(block.id, logicalDx, logicalDy)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (block.currentImageUri != null) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
        } else {
            Text(
                text = stringResource(block.type.getDisplayNameRes()), 
                style = MaterialTheme.typography.labelSmall, 
                color = if (isDimmed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary, // 文字亮度调到最高
                textAlign = TextAlign.Center
            )
        }
    }

    // 递归渲染子组件
    block.children.forEach { child ->
        RenderBlock(
            block = child,
            parentX = currentX,
            parentY = currentY,
            baseScale = baseScale,
            zoom = zoom,
            isSelected = child.id == selectedBlockId,
            isDimmed = isDimmed, // 如果父组件被变暗，子组件也变暗
            density = density,
            onBlockClicked = onBlockClicked,
            onBlockDragged = onBlockDragged,
            selectedBlockId = selectedBlockId,
            editingGroupId = editingGroupId
        )
    }
}

private fun findHitBlock(blocks: List<UIBlock>, lx: Float, ly: Float, parentLx: Float, parentLy: Float, editingGroupId: String?, isInsideEditingGroup: Boolean = false): UIBlock? {
    for (i in blocks.indices.reversed()) {
        val block = blocks[i]
        val absL = parentLx + block.bounds.left
        val absT = parentLy + block.bounds.top
        val absR = parentLx + block.bounds.right
        val absB = parentLy + block.bounds.bottom

        val currentlyInside = isInsideEditingGroup || block.id == editingGroupId
        
        // 递归找子组件
        val hitChild = findHitBlock(block.children, lx, ly, absL, absT, editingGroupId, currentlyInside)
        if (hitChild != null) return hitChild

        // 如果处于组编辑模式，仅允许点击组内成员或组本身
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) {
            if (editingGroupId == null || currentlyInside) {
                return block
            }
        }
    }
    return null
}

private fun shouldDim(block: UIBlock, editingGroupId: String?): Boolean {
    if (editingGroupId == null) return false
    // 如果 block 本身就是正在编辑的组，不变暗
    if (block.id == editingGroupId) return false
    // 如果 block 包含正在编辑的组（它是祖先），也不变暗（或者根据需求决定，通常祖先作为背景可以不变暗或变淡）
    // 这里简单处理：如果正在编辑的组是该 block 的后代，则该 block 不变暗
    return !containsBlock(block, editingGroupId)
}

/** 检查 targetId 是否在 currentBlock 的层级树中（包含其本身） */
private fun containsBlock(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { containsBlock(it, targetId) }
}

package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_exit
import geminiuiforge.composeapp.generated.resources.group_editing_indicator_prefix
import org.gemini.ui.forge.ResizeVerticalIcon
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min

/**
 * 画布区域组件：负责渲染基础 Slots 模板及已绑定的图片。
 * 支持缩放、平移、模块选中、拖拽以及参考图对比等核心交互功能。
 *
 * @param pageWidth 页面原始宽度（逻辑单位）
 * @param pageHeight 页面原始高度（逻辑单位）
 * @param blocks 渲染的 UI 模块列表
 * @param selectedBlockId 当前选中的模块 ID
 * @param onBlockClicked 模块点击回调
 * @param onBlockDoubleClicked 模块双击回调
 * @param onBlockDragStart 开始拖拽回调
 * @param onBlockDragged 拖拽进行中回调
 * @param editingGroupId 当前处于隔离编辑模式的组 ID
 * @param onExitGroupEdit 退出组编辑模式的回调
 * @param referenceMode 参考图显示模式
 * @param referenceUri 参考图的资源路径或 Base64 字符串
 * @param referenceOpacity 参考图透明度
 * @param isVisualMode 是否为视觉模式（显示生成图）
 * @param onToggleVisualMode 切换视觉模式的回调
 * @param isHideOutlines 是否隐藏模块描边
 * @param onToggleHideOutlines 切换隐藏描边的回调
 * @param isReadOnly 是否为只读模式
 * @param stageBackgroundColor 舞台背景颜色
 */
@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    selectedBlockIds: Set<String> = emptySet(),
    onBlockClicked: (String?, Boolean) -> Unit,
    onBlockDoubleClicked: (String) -> Unit = {},
    onBlockDragStart: (String) -> Unit = {},
    onBlockDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onBlockDragEnd: (String) -> Unit = {},
    editingGroupId: String? = null,
    onExitGroupEdit: () -> Unit = {},
    referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    onReferenceModeChange: (ReferenceDisplayMode) -> Unit = {},
    referenceUri: String? = null,
    referenceOpacity: Float = 0.4f,
    onReferenceOpacityChange: (Float) -> Unit = {},
    isVisualMode: Boolean = false,
    onToggleVisualMode: () -> Unit = {},
    isHideOutlines: Boolean = false,
    onToggleHideOutlines: () -> Unit = {},
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
        } catch (_: Exception) {
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

    // 当分屏/参考模式切换时，自动重置缩放和偏移，保证立刻刷新居中
    LaunchedEffect(referenceMode) {
        zoom = 1f
        pan = Offset.Zero
    }

    val currentBlocks by rememberUpdatedState(blocks)
    val currentEditingGroupId by rememberUpdatedState(editingGroupId)
    val currentOnBlockClicked by rememberUpdatedState(onBlockClicked)
    val currentOnBlockDoubleClicked by rememberUpdatedState(onBlockDoubleClicked)
    val currentOnBlockDragged by rememberUpdatedState(onBlockDragged)
    val currentOnBlockDragStart by rememberUpdatedState(onBlockDragStart)
    val currentOnBlockDragEnd by rememberUpdatedState(onBlockDragEnd)
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
            if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) {
                Surface(
                    modifier = Modifier.weight(splitWeight).fillMaxWidth().padding(8.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Image(bitmap = refBitmap, contentDescription = "Ref", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant).pointerHoverIcon(ResizeVerticalIcon)
                        .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta ->
                            val deltaWeight = delta / totalHeightPx
                            splitWeight = (splitWeight + deltaWeight).coerceIn(0.1f, 0.9f)
                        })
                )
            }

            val canvasWeight = if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
            Box(modifier = Modifier.weight(canvasWeight).fillMaxWidth().clipToBounds()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxBlockRight = currentBlocks.maxOfOrNull { it.bounds.right } ?: 0f
                    val maxBlockBottom = currentBlocks.maxOfOrNull { it.bounds.bottom } ?: 0f
                    val effectiveWidth = maxOf(pageWidth, maxBlockRight)
                    val effectiveHeight = maxOf(pageHeight, maxBlockBottom)

                    val baseScale = min(maxWidth.value / effectiveWidth, maxHeight.value / effectiveHeight) * 0.9f
                    val offsetX = (maxWidth.value - (effectiveWidth * baseScale)) / 2 + (effectiveWidth - pageWidth) / 2 * baseScale
                    val offsetY = (maxHeight.value - (effectiveHeight * baseScale)) / 2 + (effectiveHeight - pageHeight) / 2 * baseScale

                    val currentBaseScale by rememberUpdatedState(baseScale)
                    val currentOffsetX by rememberUpdatedState(offsetX)
                    val currentOffsetY by rememberUpdatedState(offsetY)

                    var isInteractingWithBlock by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                    if (isInteractingWithBlock) return@detectTransformGestures
                                    val finalZoomChange = if (abs(zoomChange - 1.0f) < 0.005f) 1.0f else zoomChange
                                    updateZoom(zoom * finalZoomChange, centroid)
                                    pan += panChange
                                }
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val change = event.changes.firstOrNull() ?: continue
                                            val delta = change.scrollDelta.y
                                            if (delta != 0f) {
                                                val multiplier = if (delta > 0) 0.9f else 1.1f
                                                updateZoom(zoom * multiplier, change.position)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y; transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        Box(
                            modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp).size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp)
                                .background(stageColor).border(BorderStroke((1.dp / zoom) / baseScale, MaterialTheme.colorScheme.outlineVariant))
                        )

                        if (referenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                            Image(bitmap = refBitmap, contentDescription = null, alpha = referenceOpacity, modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp).size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp), contentScale = ContentScale.FillBounds)
                        }

                        var isMultiSelectActive by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            isMultiSelectActive = event.keyboardModifiers.isShiftPressed ||
                                                    event.keyboardModifiers.isCtrlPressed ||
                                                    event.keyboardModifiers.isMetaPressed
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            if (hitBlock != null) currentOnBlockDoubleClicked(hitBlock.id) else if (currentEditingGroupId != null) currentOnExitGroupEdit() else currentOnBlockClicked(null, false)
                                        },
                                        onTap = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            currentOnBlockClicked(hitBlock?.id, isMultiSelectActive)
                                        }
                                    )
                                }
                                .pointerInput(isReadOnly) {
                                    if (isReadOnly) return@pointerInput
                                    var dragTargetId: String? = null; var isPanningStage = false
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale
                                            val hitBlock = findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            if (hitBlock != null) { dragTargetId = hitBlock.id; isPanningStage = false; isInteractingWithBlock = true; currentOnBlockDragStart(hitBlock.id) }
                                            else { dragTargetId = null; isPanningStage = true; isInteractingWithBlock = false }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (isPanningStage) pan += dragAmount else if (dragTargetId != null) {
                                                val logicalDx = dragAmount.x / density.density / currentBaseScale
                                                val logicalDy = dragAmount.y / density.density / currentBaseScale
                                                currentOnBlockDragged(dragTargetId!!, logicalDx, logicalDy)
                                            }
                                        },
                                        onDragEnd = { dragTargetId?.let { currentOnBlockDragEnd(it) }; dragTargetId = null; isPanningStage = false; isInteractingWithBlock = false },
                                        onDragCancel = { dragTargetId?.let { currentOnBlockDragEnd(it) }; dragTargetId = null; isPanningStage = false; isInteractingWithBlock = false }
                                    )
                                }
                        ) {
                            currentBlocks.forEach { block ->
                                RenderBlock(
                                    block = block, parentX = offsetX, parentY = offsetY, baseScale = baseScale, zoom = zoom,
                                    isSelected = block.id == selectedBlockId || selectedBlockIds.contains(block.id),
                                    isDimmed = shouldDim(block, editingGroupId),
                                    isVisualMode = isVisualMode, isHideOutlines = isHideOutlines,
                                    density = density, selectedBlockId = selectedBlockId,
                                    selectedBlockIds = selectedBlockIds, editingGroupId = editingGroupId
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editingGroupId != null) {
            Surface(modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 68.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "${stringResource(Res.string.group_editing_indicator_prefix)} $editingGroupId", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.action_exit), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.clickable { onExitGroupEdit() }.padding(4.dp))
                }
            }
        }

        val currentCanvasWeight = if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
        CanvasFloatingControlBar(
            zoom = zoom, updateZoom = ::updateZoom, onResetZoom = { zoom = 1f; pan = Offset.Zero },
            isVisualMode = isVisualMode, onToggleVisualMode = onToggleVisualMode,
            isHideOutlines = isHideOutlines, onToggleHideOutlines = onToggleHideOutlines,
            referenceUri = referenceUri, internalReferenceMode = referenceMode,
            onReferenceModeChange = onReferenceModeChange,
            internalReferenceOpacity = referenceOpacity, onReferenceOpacityChange = onReferenceOpacityChange,
            centerOffset = Offset(containerWidthPx / 2f, (containerHeightPx * currentCanvasWeight) / 2f),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * 精确查找手势点击位置（逻辑坐标）所命中的最上层 UIBlock（支持多层嵌套检测）。
 * 当处于隔离编辑组（editingGroupId 激活）时，会优先且仅限制于在该隔离组的子模块内部进行碰撞命中检测。
 * 
 * @param blocks 当前页面中所有顶层模块列表
 * @param lx 点击位置的逻辑 X 坐标（已排除画布本身的缩放与绝对物理偏移）
 * @param ly 点击位置的逻辑 Y 坐标
 * @param parentLx 递归调用时，父容器累计的逻辑 X 偏移量
 * @param parentLy 递归调用时，父容器累计的逻辑 Y 偏移量
 * @param editingGroupId 当前激活隔离编辑的组 ID。若为 null，则在全局根节点中进行命中遍历
 * @return 命中的 UIBlock 实例。若没有任何图层命中则返回 null
 */
private fun findHitBlock(blocks: List<UIBlock>, lx: Float, ly: Float, parentLx: Float, parentLy: Float, editingGroupId: String?): UIBlock? {
    if (editingGroupId == null) {
        // 全局模式下，从上至下（倒序遍历，后绘制的在最上层）检索所有命中的顶层模块
        for (i in blocks.indices.reversed()) {
            val block = blocks[i]
            if (!block.isVisible) continue
            val absL = parentLx + block.bounds.left; val absT = parentLy + block.bounds.top
            val absR = parentLx + block.bounds.right; val absB = parentLy + block.bounds.bottom
            if (lx in absL..absR && ly >= absT && ly <= absB) return block
        }
        return null
    }
    // 隔离编辑模式下：仅在被编辑的隔离组的子元素列表中检索命中
    val targetGroup = findBlockInList(blocks, editingGroupId) ?: return null
    val groupAbsPos = calculateBlockAbsolutePosition(blocks, editingGroupId) ?: Offset.Zero
    for (i in targetGroup.children.indices.reversed()) {
        val child = targetGroup.children[i]
        if (!child.isVisible) continue
        val absL = groupAbsPos.x + child.bounds.left; val absT = groupAbsPos.y + child.bounds.top
        val absR = groupAbsPos.x + child.bounds.right; val absB = groupAbsPos.y + child.bounds.bottom
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) return child
    }
    // 如果子元素都没命中，但点击点落在了组容器本身的 bounds 内，则返回组容器本身
    val gL = groupAbsPos.x; val gT = groupAbsPos.y
    val gR = groupAbsPos.x + targetGroup.bounds.width; val gB = groupAbsPos.y + targetGroup.bounds.height
    if (lx >= gL && lx <= gR && ly >= gT && ly <= gB) return targetGroup
    return null
}

/**
 * 递归遍历模块树，通过 ID 检索对应的 UIBlock。
 * 
 * @param blocks 用于递归搜索的模块列表
 * @param id 目标模块的唯一标识符
 * @return 匹配的 UIBlock 实例。若未找到则返回 null
 */
private fun findBlockInList(blocks: List<UIBlock>, id: String): UIBlock? {
    for (block in blocks) {
        if (block.id == id) return block
        val found = findBlockInList(block.children, id)
        if (found != null) return found
    }
    return null
}

/**
 * 递归计算指定模块在当前画布上的全局绝对逻辑位置。
 * 由于 UIBlock 树中 bounds 存储的通常是相对于直接父容器的相对偏移量，因此需要向下累计父级的坐标以得出绝对坐标。
 * 
 * @param blocks 搜索起点的模块列表
 * @param id 目标模块唯一标识符
 * @param currentX 递归调用中累计传递的绝对 X 逻辑坐标
 * @param currentY 递归调用中累计传递的绝对 Y 逻辑坐标
 * @return 目标模块的绝对坐标 Offset。如果未找到该模块则返回 null
 */
private fun calculateBlockAbsolutePosition(blocks: List<UIBlock>, id: String, currentX: Float = 0f, currentY: Float = 0f): Offset? {
    for (block in blocks) {
        val absX = currentX + block.bounds.left; val absY = currentY + block.bounds.top
        if (block.id == id) return Offset(absX, absY)
        val found = calculateBlockAbsolutePosition(block.children, id, absX, absY)
        if (found != null) return found
    }
    return null
}

/**
 * 判断特定模块在隔离编辑模式下是否应该呈现半透明虚化（Dim）状态。
 * 当隔离编辑某一组件组时，只有该组件本身以及它的所有后代子图层显示高亮，画布上的其他非相关组件都要进行灰暗置度处理。
 * 
 * @param block 待检测判断的图层模块
 * @param editingGroupId 当前处于隔离编辑的组 ID。若为 null，则处于全局普通编辑，所有图层正常显示
 * @return true 代表需要将图层变暗虚化，false 代表正常高亮渲染
 */
private fun shouldDim(block: UIBlock, editingGroupId: String?): Boolean {
    if (editingGroupId == null) return false
    if (block.id == editingGroupId) return false
    return !containsBlock(block, editingGroupId)
}

/**
 * 递归判断指定的父图层是否包含（或其自身就是）目标子图层 ID。
 * 
 * @param currentBlock 用于开始检索的父组件实例
 * @param targetId 需匹配的后代子组件的 ID 标识符
 * @return true 表示存在该包含关系，false 表示不包含
 */
private fun containsBlock(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { containsBlock(it, targetId) }
}

package org.gemini.ui.forge.ui.component

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
import androidx.compose.ui.text.font.FontWeight
import org.gemini.ui.forge.ResizeVerticalIcon
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.AppLogger
import kotlin.math.abs

/**
 * 画布区域组件：负责渲染基础 Slots 模板及已绑定的图片。
 * 支持缩放、平移、模块选中、拖拽以及参考图对比等核心交互功能。
 *
 * @param pageWidth 页面原始宽度（逻辑单位）
 * @param pageHeight 页面原始高度（逻辑单位）
 * @param blocks 渲染的 UI 模块列表
 * @param selectedBlockId 当前选中的模块 ID
 * @param onBlockClicked 模块点击回调
 * @param onBlockDoubleClicked 模块双击回调（通常用于进入组编辑）
 * @param onBlockDragStart 开始拖拽回调
 * @param onBlockDragged 拖拽进行中回调
 * @param editingGroupId 当前处于隔离编辑模式的组 ID
 * @param onExitGroupEdit 退出组编辑模式的回调
 * @param referenceMode 参考图显示模式（隐藏/分屏/叠加）
 * @param referenceUri 参考图的资源路径或 Base64 字符串
 * @param referenceOpacity 参考图叠加时的透明度
 * @param isVisualMode 是否为视觉模式（隐藏占位线框，仅显示图片内容）
 * @param onToggleVisualMode 切换视觉模式的回调
 * @param isReadOnly 是否为只读模式（禁用拖拽等修改操作）
 * @param stageBackgroundColor 舞台背景颜色（十六进制字符串）
 */
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
    // 舞台交互状态
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 解析舞台背景色
    val stageColor = remember(stageBackgroundColor) {
        try {
            val colorStr = stageBackgroundColor.removePrefix("#")
            val colorLong = colorStr.toLong(16)
            if (colorStr.length <= 6) Color(colorLong or 0xFF000000L) else Color(colorLong)
        } catch (e: Exception) {
            Color(0xFF2D2D2D) // 默认深灰
        }
    }

    /**
     * 更新缩放比例，并以指定中心点进行坐标补偿，实现类似 Figma 的中心缩放效果
     */
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

    // 参考图显示内部状态
    var internalReferenceMode by remember { mutableStateOf(referenceMode) }
    LaunchedEffect(referenceMode) { internalReferenceMode = referenceMode }
    var internalReferenceOpacity by remember { mutableStateOf(referenceOpacity) }

    // 当分屏/参考模式切换时，自动重置缩放和偏移，保证立刻刷新居中
    LaunchedEffect(internalReferenceMode) {
        zoom = 1f
        pan = Offset.Zero
    }

    // 状态快照，用于在 PointerInput 闭包中引用最新状态
    val currentBlocks by rememberUpdatedState(blocks)
    val currentEditingGroupId by rememberUpdatedState(editingGroupId)
    val currentOnBlockClicked by rememberUpdatedState(onBlockClicked)
    val currentOnBlockDoubleClicked by rememberUpdatedState(onBlockDoubleClicked)
    val currentOnBlockDragged by rememberUpdatedState(onBlockDragged)
    val currentOnBlockDragStart by rememberUpdatedState(onBlockDragStart)
    val currentOnExitGroupEdit by rememberUpdatedState(onExitGroupEdit)
    val currentPageWidth by rememberUpdatedState(pageWidth)
    val currentPageHeight by rememberUpdatedState(pageHeight)

    // 加载参考图位图
    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value
    var splitWeight by remember { mutableStateOf(0.5f) } // 分屏时的比例权重

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            // 参考图分屏模式渲染
            if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) {
                Surface(
                    modifier = Modifier.weight(splitWeight).fillMaxWidth().padding(8.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Image(
                        bitmap = refBitmap,
                        contentDescription = "Ref",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                // 分屏拖拽条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(ResizeVerticalIcon)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                val deltaWeight = delta / totalHeightPx
                                splitWeight = (splitWeight + deltaWeight).coerceIn(0.1f, 0.9f)
                            }
                        )
                )
            }

            // 画布主体部分
            val canvasWeight =
                if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
            Box(modifier = Modifier.weight(canvasWeight).fillMaxWidth().clipToBounds()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // 动态计算有效画布区域（取舞台尺寸与所有模块最远边界的最大值，确保溢出模块也能看到）
                    val maxBlockRight = currentBlocks.maxOfOrNull { it.bounds.right } ?: 0f
                    val maxBlockBottom = currentBlocks.maxOfOrNull { it.bounds.bottom } ?: 0f
                    val effectiveWidth = maxOf(pageWidth, maxBlockRight)
                    val effectiveHeight = maxOf(pageHeight, maxBlockBottom)

                    // 初始缩放比例：使有效区域自适应容器大小
                    val baseScale = min(maxWidth.value / effectiveWidth, maxHeight.value / effectiveHeight) * 0.9f
                    // 居中偏移量
                    val offsetX =
                        (maxWidth.value - (effectiveWidth * baseScale)) / 2 + (effectiveWidth - pageWidth) / 2 * baseScale
                    val offsetY =
                        (maxHeight.value - (effectiveHeight * baseScale)) / 2 + (effectiveHeight - pageHeight) / 2 * baseScale

                    // 状态快照与协调
                    val currentBaseScale by rememberUpdatedState(baseScale)
                    val currentOffsetX by rememberUpdatedState(offsetX)
                    val currentOffsetY by rememberUpdatedState(offsetY)

                    // 交互协调状态：标记当前是否正在进行模块操作
                    var isInteractingWithBlock by remember { mutableStateOf(false) }

                    // 核心交互层：处理手势变换和平移缩放
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                    // 关键：如果正在拖拽模块，则直接忽略画布变换
                                    if (isInteractingWithBlock) return@detectTransformGestures

                                    // 增加缩放死区阈值，防止单指微小抖动触发缩放
                                    val finalZoomChange = if (abs(zoomChange - 1.0f) < 0.005f) 1.0f else zoomChange
                                    
                                    updateZoom(zoom * finalZoomChange, centroid)
                                    pan += panChange
                                }
                            }
                            .pointerInput(Unit) {
                                // 桌面端滚动滚轮缩放支持
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
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        // ...（绘制舞台与参考图逻辑保持不变）...
                        // 绘制舞台物理边界背景
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX.dp, y = offsetY.dp)
                                .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp)
                                .background(stageColor)
                                .border(
                                    BorderStroke(
                                        (1.dp / zoom) / baseScale,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                        )

                        // 叠加模式下的参考图绘制
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

                        // 模块渲染容器
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // 点击与双击检测
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale

                                            val hitBlock =
                                                findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)

                                            if (hitBlock != null) {
                                                currentOnBlockDoubleClicked(hitBlock.id)
                                            } else {
                                                if (currentEditingGroupId != null) {
                                                    currentOnExitGroupEdit()
                                                } else {
                                                    // 非组编辑状态下双击舞台空白处，取消选中
                                                    currentOnBlockClicked(null)
                                                }
                                            }
                                        },
                                        onTap = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale

                                            val hitBlock =
                                                findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)
                                            currentOnBlockClicked(hitBlock?.id)
                                        }
                                    )
                                }
                                .pointerInput(isReadOnly) {
                                    if (isReadOnly) return@pointerInput
                                    var dragTargetId: String? = null
                                    var isPanningStage = false

                                    // 模块拖拽与画布平移逻辑
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val lx = (offset.x / density.density - currentOffsetX) / currentBaseScale
                                            val ly = (offset.y / density.density - currentOffsetY) / currentBaseScale

                                            val hitBlock =
                                                findHitBlock(currentBlocks, lx, ly, 0f, 0f, currentEditingGroupId)

                                            if (hitBlock != null) {
                                                dragTargetId = hitBlock.id
                                                isPanningStage = false
                                                isInteractingWithBlock = true // 锁定变换
                                                currentOnBlockDragStart(hitBlock.id)
                                            } else {
                                                dragTargetId = null
                                                isPanningStage = true
                                                isInteractingWithBlock = false
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume() // 消费位移

                                            val tid = dragTargetId
                                            if (isPanningStage) {
                                                pan += dragAmount
                                            } else if (tid != null) {
                                                val logicalDx = dragAmount.x / density.density / currentBaseScale
                                                val logicalDy = dragAmount.y / density.density / currentBaseScale
                                                currentOnBlockDragged(tid, logicalDx, logicalDy)
                                            }
                                        },
                                        onDragEnd = { 
                                            dragTargetId = null
                                            isPanningStage = false
                                            isInteractingWithBlock = false // 解锁变换
                                        },
                                        onDragCancel = { 
                                            dragTargetId = null
                                            isPanningStage = false
                                            isInteractingWithBlock = false // 解锁变换
                                        }
                                    )
                                }
                        ) {
                            // 递归渲染所有 UI 模块
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

        // 隔离模式指示器：当进入组编辑时，顶部显示当前组 ID 及退出按钮
        if (editingGroupId != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Layers,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))

                    val prefix = stringResource(Res.string.group_editing_indicator_prefix)
                    Text(
                        text = "$prefix $editingGroupId",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(Modifier.width(12.dp))
                    VerticalDivider(
                        modifier = Modifier.height(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = stringResource(Res.string.action_exit),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clickable { onExitGroupEdit() }
                            .padding(4.dp)
                    )
                }
            }
        }

        // 全局浮动控制栏：缩放比例、复位、视觉模式切换、参考图控制
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.outlineVariant().copy(alpha = 0.3f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentCanvasWeight =
                    if (internalReferenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
                val viewCenterX = containerWidthPx / 2f
                val viewCenterY = (containerHeightPx * currentCanvasWeight) / 2f
                val centerOffset = Offset(viewCenterX, viewCenterY)

                // 缩放操作
                IconButton(onClick = { updateZoom(zoom - 0.2f, centerOffset) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Remove,
                        "-",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier.height(28.dp).width(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
                IconButton(onClick = { updateZoom(zoom + 0.2f, centerOffset) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Add,
                        "+",
                        modifier = Modifier.size(16.dp)
                    )
                }

                VerticalDivider(modifier = Modifier.height(16.dp))

                // 复位舞台
                IconButton(onClick = {
                    zoom = 1f; pan = Offset.Zero; AppLogger.d(
                    "CanvasArea",
                    "🔄 已还原舞台缩放为100%并居中"
                )
                }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                }

                VerticalDivider(modifier = Modifier.height(16.dp))

                // 切换视觉预览模式
                IconToggleButton(
                    checked = isVisualMode,
                    onCheckedChange = { onToggleVisualMode() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isVisualMode) Icons.Default.AutoFixNormal else Icons.Default.AutoFixOff,
                        "Visual",
                        modifier = Modifier.size(18.dp),
                        tint = if (isVisualMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                // 参考图控制工具
                if (referenceUri != null) {
                    VerticalDivider(modifier = Modifier.height(16.dp))
                    val isRefEnabled = internalReferenceMode != ReferenceDisplayMode.HIDDEN
                    IconToggleButton(
                        checked = isRefEnabled,
                        onCheckedChange = {
                            internalReferenceMode = if (it) ReferenceDisplayMode.SPLIT else ReferenceDisplayMode.HIDDEN
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isRefEnabled) Icons.Default.Image else Icons.Default.VisibilityOff,
                            "Toggle Ref",
                            modifier = Modifier.size(18.dp),
                            tint = if (isRefEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isRefEnabled) {
                        VerticalDivider(modifier = Modifier.height(16.dp))
                        IconToggleButton(
                            checked = internalReferenceMode == ReferenceDisplayMode.SPLIT,
                            onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.SPLIT },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.VerticalSplit,
                                "Split",
                                modifier = Modifier.size(18.dp),
                                tint = if (internalReferenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconToggleButton(
                            checked = internalReferenceMode == ReferenceDisplayMode.OVERLAY,
                            onCheckedChange = { internalReferenceMode = ReferenceDisplayMode.OVERLAY },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                "Overlay",
                                modifier = Modifier.size(18.dp),
                                tint = if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        if (internalReferenceMode == ReferenceDisplayMode.OVERLAY) {
                            Spacer(modifier = Modifier.width(4.dp)); Slider(
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

/**
 * 碰撞检测：查找用户点击位置对应的最深层模块
 * 隔离模式下限制仅能在指定组及其子级中查找
 */
private fun findHitBlock(
    blocks: List<UIBlock>,
    lx: Float,
    ly: Float,
    parentLx: Float,
    parentLy: Float,
    editingGroupId: String?
): UIBlock? {
    if (editingGroupId == null) {
        // 普通模式：从上往下（视觉上）寻找，即列表反向遍历
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

    // 隔离模式：仅在编辑组及其范围内查找
    val targetGroup = findBlockInList(blocks, editingGroupId) ?: return null
    val groupAbsPos = calculateBlockAbsolutePosition(blocks, editingGroupId) ?: Offset.Zero

    // 优先匹配子模块
    for (i in targetGroup.children.indices.reversed()) {
        val child = targetGroup.children[i]
        if (!child.isVisible) continue
        val absL = groupAbsPos.x + child.bounds.left
        val absT = groupAbsPos.y + child.bounds.top
        val absR = groupAbsPos.x + child.bounds.right
        val absB = groupAbsPos.y + child.bounds.bottom
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) return child
    }

    // 最后匹配父组自身
    val gL = groupAbsPos.x
    val gT = groupAbsPos.y
    val gR = groupAbsPos.x + targetGroup.bounds.width
    val gB = groupAbsPos.y + targetGroup.bounds.height
    if (lx >= gL && lx <= gR && ly >= gT && ly <= gB) return targetGroup
    return null
}

/**
 * 递归查找模块列表中的指定 ID 模块
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
 * 递归计算指定模块在画布上的绝对偏移位置
 */
private fun calculateBlockAbsolutePosition(
    blocks: List<UIBlock>,
    id: String,
    currentX: Float = 0f,
    currentY: Float = 0f
): Offset? {
    for (block in blocks) {
        val absX = currentX + block.bounds.left
        val absY = currentY + block.bounds.top
        if (block.id == id) return Offset(absX, absY)
        val found = calculateBlockAbsolutePosition(block.children, id, absX, absY)
        if (found != null) return found
    }
    return null
}

/**
 * 判断模块是否应置灰（当处于隔离模式且模块不在编辑路径上时）
 */
private fun shouldDim(block: UIBlock, editingGroupId: String?): Boolean {
    if (editingGroupId == null) return false
    if (block.id == editingGroupId) return false
    return !containsBlock(block, editingGroupId)
}

/**
 * 判断当前模块是否包含指定模块（递归）
 */
private fun containsBlock(currentBlock: UIBlock, targetId: String): Boolean {
    if (currentBlock.id == targetId) return true
    return currentBlock.children.any { containsBlock(it, targetId) }
}

/**
 * 扩展函数：便捷获取 OutlineVariant 颜色，处理不同 Material 主题版本兼容
 */
@Composable
private fun MaterialTheme.outlineVariant(): Color = colorScheme.outlineVariant

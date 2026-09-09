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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_exit
import geminiuiforge.composeapp.generated.resources.group_editing_indicator_prefix
import org.gemini.ui.forge.ResizeVerticalIcon
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min

/**
 * 画布区域组件：负责渲染基础 Slots 模板及已绑定的图片。
 * 支持缩放、平移、模块选中、拖拽以及参考图对比等核心交互功能。
 *
 * @param state 项目工作区状态
 * @param viewModel 项目工作区视图模型
 * @param modifier 外部修饰符
 */
@Composable
fun CanvasArea(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val pageWidth = state.currentPage?.width ?: 1080f
    val pageHeight = state.currentPage?.height ?: 1920f
    val blocks = state.currentPage?.blocks ?: emptyList()
    val editingGroupId = state.editingGroupId
    val referenceMode = state.referenceMode
    val referenceUri = state.referenceImageUri?.getAbsolutePath()
    val referenceOpacity = state.referenceOpacity
    val isVisualMode = state.isVisualMode
    val isHideOutlines = state.isHideOutlines
    val stageBackgroundColor = state.stageBackgroundColor
    val isReadOnly = false

    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var isSpacePressed by remember { mutableStateOf(false) }
    var isRightButtonDragging by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

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

    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value
    var splitWeight by remember { mutableStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (!it.isFocused) isSpacePressed = false }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Spacebar) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            isSpacePressed = true
                            true
                        }
                        KeyEventType.KeyUp -> {
                            isSpacePressed = false
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
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
                    Image(
                        bitmap = refBitmap,
                        contentDescription = "Ref",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(ResizeVerticalIcon)
                        .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta ->
                            val deltaWeight = delta / totalHeightPx
                            splitWeight = (splitWeight + deltaWeight).coerceIn(0.1f, 0.9f)
                        })
                )
            }

            val canvasWeight =
                if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
            Box(modifier = Modifier.weight(canvasWeight).fillMaxWidth().clipToBounds()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxBlockRight = blocks.maxOfOrNull { it.bounds.right } ?: 0f
                    val maxBlockBottom = blocks.maxOfOrNull { it.bounds.bottom } ?: 0f
                    val effectiveWidth = maxOf(pageWidth, maxBlockRight)
                    val effectiveHeight = maxOf(pageHeight, maxBlockBottom)

                    val baseScale = min(maxWidth.value / effectiveWidth, maxHeight.value / effectiveHeight) * 0.9f
                    val offsetX =
                        (maxWidth.value - (effectiveWidth * baseScale)) / 2 + (effectiveWidth - pageWidth) / 2 * baseScale
                    val offsetY =
                        (maxHeight.value - (effectiveHeight * baseScale)) / 2 + (effectiveHeight - pageHeight) / 2 * baseScale

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
                                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY =
                                pan.y; transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        Box(
                            modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp)
                                .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp)
                                .background(stageColor).border(
                                    BorderStroke(
                                        (1.dp / zoom) / baseScale,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                        )

                        if (referenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                            Image(
                                bitmap = refBitmap,
                                contentDescription = null,
                                alpha = referenceOpacity,
                                modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp)
                                    .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }

                        var isMultiSelectActive by remember { mutableStateOf(false) }

                        // ★ 手势参数动态快照：手势协程一律 pointerInput(Unit) 保持长效保活，
                        // 运行期通过 State 读取最新值，绝不把 blocks 作为 key，彻底杜绝坐标更新导致手势中断卡死！
                        val currentBlocksState by rememberUpdatedState(blocks)
                        val currentEditingGroupState by rememberUpdatedState(editingGroupId)
                        val currentOffsetXState by rememberUpdatedState(offsetX)
                        val currentOffsetYState by rememberUpdatedState(offsetY)
                        val currentBaseScaleState by rememberUpdatedState(baseScale)
                        val currentIsReadOnlyState by rememberUpdatedState(isReadOnly)
                        val currentIsSpacePressedState by rememberUpdatedState(isSpacePressed)
                        val currentDensityState by rememberUpdatedState(density)
                        val currentSelectedBlockIdsState by rememberUpdatedState(state.selectedBlockIds)

                        Box(
                            modifier = Modifier.fillMaxSize()
                                .pointerHoverIcon(if (isSpacePressed || isRightButtonDragging) PointerIcon.Hand else PointerIcon.Default)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            isMultiSelectActive = event.keyboardModifiers.isShiftPressed ||
                                                    event.keyboardModifiers.isCtrlPressed ||
                                                    event.keyboardModifiers.isMetaPressed

                                            // ★ 遵照用户要求：仅在画布区域鼠标按下时才请求获取焦点，移动时不随意抢焦点
                                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                                runCatching { focusRequester.requestFocus() }
                                            }
                                        }
                                    }
                                }
                                // ★ 专用鼠标右键平移画布手势（无需 awaitFirstDown，原生帧事件流即时响应）
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.buttons.isSecondaryPressed) {
                                                isRightButtonDragging = true
                                                if (event.type == PointerEventType.Move) {
                                                    val change = event.changes.firstOrNull()
                                                    if (change != null) {
                                                        val delta = change.position - change.previousPosition
                                                        pan += delta
                                                        change.consume()
                                                    }
                                                }
                                            } else {
                                                if (isRightButtonDragging) {
                                                    isRightButtonDragging = false
                                                }
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            if (currentIsSpacePressedState || isRightButtonDragging) return@detectTapGestures
                                            val curDensity = currentDensityState
                                            val lx = (offset.x / curDensity.density - currentOffsetXState) / currentBaseScaleState
                                            val ly = (offset.y / curDensity.density - currentOffsetYState) / currentBaseScaleState
                                            val hitBlock =
                                                currentBlocksState.findHitBlock(lx, ly, 0f, 0f, currentEditingGroupState)
                                            if (hitBlock != null) viewModel.onBlockDoubleClicked(hitBlock.id) else if (currentEditingGroupState != null) viewModel.exitGroupEdit() else viewModel.onBlockClicked(
                                                null,
                                                false
                                            )
                                        },
                                        onTap = { offset ->
                                            if (currentIsSpacePressedState || isRightButtonDragging) return@detectTapGestures
                                            val curDensity = currentDensityState
                                            val lx = (offset.x / curDensity.density - currentOffsetXState) / currentBaseScaleState
                                            val ly = (offset.y / curDensity.density - currentOffsetYState) / currentBaseScaleState
                                            val hitBlock =
                                                currentBlocksState.findHitBlock(lx, ly, 0f, 0f, currentEditingGroupState)
                                            viewModel.onBlockClicked(hitBlock?.id, isMultiSelectActive)
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    var dragTargetId: String? = null
                                    var isPanningStage = false
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            runCatching { focusRequester.requestFocus() }
                                            if (currentIsReadOnlyState || isRightButtonDragging) return@detectDragGestures
                                            if (currentIsSpacePressedState) {
                                                dragTargetId = null
                                                isPanningStage = true
                                                isInteractingWithBlock = false
                                            } else {
                                                val curDensity = currentDensityState
                                                val lx = (offset.x / curDensity.density - currentOffsetXState) / currentBaseScaleState
                                                val ly = (offset.y / curDensity.density - currentOffsetYState) / currentBaseScaleState
                                                val hitBlock =
                                                    currentBlocksState.findHitBlock(lx, ly, 0f, 0f, currentEditingGroupState)
                                                if (hitBlock != null) {
                                                    dragTargetId = hitBlock.id
                                                    isPanningStage = false
                                                    isInteractingWithBlock = true
                                                    viewModel.historyManager.saveSnapshot("拖动模块位置")
                                                } else {
                                                    dragTargetId = null
                                                    isPanningStage = true
                                                    isInteractingWithBlock = false
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (currentIsReadOnlyState || isRightButtonDragging) return@detectDragGestures
                                            change.consume()
                                            if (isPanningStage || currentIsSpacePressedState) {
                                                pan += dragAmount
                                            } else if (dragTargetId != null) {
                                                val curDensity = currentDensityState
                                                val logicalDx = dragAmount.x / curDensity.density / currentBaseScaleState
                                                val logicalDy = dragAmount.y / curDensity.density / currentBaseScaleState
                                                if (currentSelectedBlockIdsState.contains(dragTargetId)) {
                                                    viewModel.layoutEditor.moveBlocksBy(currentSelectedBlockIdsState, logicalDx, logicalDy)
                                                } else {
                                                    viewModel.layoutEditor.moveBlockBy(dragTargetId!!, logicalDx, logicalDy)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            dragTargetId = null; isPanningStage = false; isInteractingWithBlock = false
                                        },
                                        onDragCancel = {
                                            dragTargetId = null; isPanningStage = false; isInteractingWithBlock = false
                                        }
                                    )
                                }
                        ) {
                            blocks.forEach { block ->
                                RenderBlock(
                                    block = block,
                                    parentRenderX = offsetX,
                                    parentRenderY = offsetY,
                                    parentLogicX = 0f,
                                    parentLogicY = 0f,
                                    baseScale = baseScale,
                                    zoom = zoom,
                                    state = state,
                                    refBitmap = refBitmap,
                                    pageWidth = pageWidth,
                                    pageHeight = pageHeight
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editingGroupId != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 68.dp),
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
                    Text(
                        text = "${stringResource(Res.string.group_editing_indicator_prefix)} $editingGroupId",
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
                        modifier = Modifier.clickable { viewModel.exitGroupEdit() }.padding(4.dp)
                    )
                }
            }
        }

        val currentCanvasWeight = if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) (1f - splitWeight) else 1f
        CanvasFloatingControlBar(
            zoom = zoom,
            updateZoom = ::updateZoom,
            onResetZoom = { zoom = 1f; pan = Offset.Zero },
            centerOffset = Offset(containerWidthPx / 2f, (containerHeightPx * currentCanvasWeight) / 2f),
            state = state,
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

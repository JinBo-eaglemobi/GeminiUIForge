package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*

private enum class DragTarget {
    NONE, PAN, LEFT, TOP, RIGHT, BOTTOM
}

private enum class EditorTab {
    CROP, BAKE
}

/**
 * 统一图片编辑器弹窗
 * 整合了“适配裁剪区域”和“物理加工 (烘焙)”功能。
 * 支持链式处理：裁剪后的结果可直接进入加工模式，反之亦然。
 */
@Composable
fun ImageEditorDialog(
    block: UIBlock? = null,
    initialImageUri: String? = null,
    targetWidth: Float? = null,
    targetHeight: Float? = null,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray, ImageResizeMode, NinePatchConfig) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 基础参数初始化
    val finalTargetW = targetWidth ?: block?.bounds?.width ?: 100f
    val finalTargetH = targetHeight ?: block?.bounds?.height ?: 100f
    val targetRatio = finalTargetW / finalTargetH

    // 核心状态：当前处理中的图片字节流
    var currentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isInitializing by remember { mutableStateOf(true) }

    // 初始化加载
    LaunchedEffect(Unit) {
        val uri = initialImageUri ?: block?.currentImageUri?.getAbsolutePath()
        if (uri != null) {
            currentBytes = readLocalFileBytes(uri)
        }
        isInitializing = false
    }

    // 基于当前字节流生成的预览位图
    val imageBitmap by produceState<ImageBitmap?>(null, currentBytes) {
        value = currentBytes?.toImageBitmap()
    }

    // 编辑器状态
    var selectedTab by remember { mutableStateOf(if (initialImageUri != null) EditorTab.CROP else EditorTab.BAKE) }
    var isProcessing by remember { mutableStateOf(false) }

    // --- 烘焙模式状态 ---
    var bakeMode by remember { mutableStateOf(block?.resizeMode ?: ImageResizeMode.STRETCH) }
    var ninePatchConfig by remember(imageBitmap) {
        mutableStateOf(
            if (imageBitmap != null && (block?.ninePatchConfig == null || (block.ninePatchConfig.left == 0 && block.ninePatchConfig.right == 0))) {
                NinePatchConfig(imageBitmap!!.width / 3, imageBitmap!!.height / 3, imageBitmap!!.width / 3, imageBitmap!!.height / 3)
            } else {
                block?.ninePatchConfig ?: NinePatchConfig(0, 0, 0, 0)
            }
        )
    }
    var canvasWidth by remember { mutableStateOf(finalTargetW.toInt().coerceAtLeast(1)) }
    var canvasHeight by remember { mutableStateOf(finalTargetH.toInt().coerceAtLeast(1)) }
    var contentWidth by remember { mutableStateOf(finalTargetW.toInt().coerceAtLeast(1)) }
    var contentHeight by remember { mutableStateOf(finalTargetH.toInt().coerceAtLeast(1)) }

    // --- 裁剪模式状态 ---
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    // 当容器尺寸或位图变化时，重新初始化裁剪框
    LaunchedEffect(containerSize, imageBitmap, selectedTab) {
        if (selectedTab != EditorTab.CROP) return@LaunchedEffect
        val img = imageBitmap ?: return@LaunchedEffect
        if (containerSize.width <= 0 || containerSize.height <= 0) return@LaunchedEffect

        val containerRatio = containerSize.width / containerSize.height
        val imgRatio = img.width.toFloat() / img.height
        
        val displayW: Float
        val displayH: Float
        if (imgRatio > containerRatio) {
            displayW = containerSize.width
            displayH = displayW / imgRatio
        } else {
            displayH = containerSize.height
            displayW = displayH * imgRatio
        }
        
        val left = (containerSize.width - displayW) / 2f
        val top = (containerSize.height - displayH) / 2f
        imageDisplayRect = Rect(left, top, left + displayW, top + displayH)

        val initialW: Float
        val initialH: Float
        if (displayW / displayH > targetRatio) {
            initialH = displayH * 0.9f
            initialW = initialH * targetRatio
        } else {
            initialW = displayW * 0.9f
            initialH = initialW / targetRatio
        }
        
        cropSize = Size(initialW, initialH)
        cropOffset = Offset(
            imageDisplayRect.left + (displayW - initialW) / 2f,
            imageDisplayRect.top + (displayH - initialH) / 2f
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.98f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isInitializing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    // [左侧] 主预览与交互区
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF151515))
                            .clipToBounds()
                            .onGloballyPositioned { containerSize = it.size.toSize() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            if (selectedTab == EditorTab.BAKE) {
                                EditorStage(
                                    imageBitmap = imageBitmap!!, mode = bakeMode, config = ninePatchConfig,
                                    canvasW = canvasWidth, canvasH = canvasHeight, contentW = contentWidth, contentH = contentHeight,
                                    onConfigChange = { ninePatchConfig = it }
                                )
                            } else {
                                // 裁剪交互层
                                Image(
                                    bitmap = imageBitmap!!,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                
                                if (imageDisplayRect != Rect.Zero && cropSize.width > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset { IntOffset(cropOffset.x.roundToInt(), cropOffset.y.roundToInt()) }
                                            .size(
                                                width = with(density) { cropSize.width.toDp() },
                                                height = with(density) { cropSize.height.toDp() }
                                            )
                                            .border(2.dp, Color.Cyan, RoundedCornerShape(2.dp))
                                            .background(Color.Cyan.copy(alpha = 0.15f))
                                            .pointerInput(imageDisplayRect) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val newX = (cropOffset.x + dragAmount.x).coerceIn(
                                                        imageDisplayRect.left, 
                                                        imageDisplayRect.right - cropSize.width
                                                    )
                                                    val newY = (cropOffset.y + dragAmount.y).coerceIn(
                                                        imageDisplayRect.top, 
                                                        imageDisplayRect.bottom - cropSize.height
                                                    )
                                                    cropOffset = Offset(newX, newY)
                                                }
                                            }
                                    ) {
                                        // 右下角缩放手柄
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(32.dp)
                                                .background(Color.Cyan, RoundedCornerShape(topStart = 8.dp))
                                                .pointerInput(imageDisplayRect) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val down = awaitFirstDown()
                                                            var pointerId = down.id
                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.find { it.id == pointerId } ?: break
                                                                if (change.changedToUp()) break
                                                                val isAlt = event.keyboardModifiers.isAltPressed
                                                                val dragAmount = change.positionChange()
                                                                change.consume()
                                                                
                                                                val currentS = cropSize
                                                                val currentO = cropOffset
                                                                
                                                                if (isAlt) {
                                                                    // 居中缩放
                                                                    val delta = if (abs(dragAmount.x) > abs(dragAmount.y)) dragAmount.x else dragAmount.y * targetRatio
                                                                    val maxDX = minOf(currentO.x - imageDisplayRect.left, imageDisplayRect.right - (currentO.x + currentS.width))
                                                                    val maxDY = minOf(currentO.y - imageDisplayRect.top, imageDisplayRect.bottom - (currentO.y + currentS.height))
                                                                    val safeD = delta.coerceIn(-(currentS.width/2 - 20f), minOf(maxDX, maxDY * targetRatio))
                                                                    cropSize = Size(currentS.width + 2*safeD, (currentS.width + 2*safeD) / targetRatio)
                                                                    cropOffset = Offset(currentO.x - safeD, currentO.y - safeD/targetRatio)
                                                                } else {
                                                                    // 边缘缩放
                                                                    val maxW = imageDisplayRect.right - currentO.x
                                                                    val maxH = imageDisplayRect.bottom - currentO.y
                                                                    var dX = dragAmount.x
                                                                    var dY = dX / targetRatio
                                                                    if (currentS.width + dX > maxW) { dX = maxW - currentS.width; dY = dX / targetRatio }
                                                                    if (currentS.height + dY > maxH) { dY = maxH - currentS.height; dX = dY * targetRatio }
                                                                    val finalW = (currentS.width + dX).coerceAtLeast(40f)
                                                                    cropSize = Size(finalW, finalW / targetRatio)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                        ) {
                                            Icon(Icons.Default.AspectRatio, null, Modifier.size(16.dp).align(Alignment.Center), tint = Color.Black)
                                        }
                                    }
                                }
                            }
                            
                            // 尺寸信息
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                val sizeText = stringResource(Res.string.editor_current_base_size)
                                    .replace("{0}", imageBitmap!!.width.toString())
                                    .replace("{1}", imageBitmap!!.height.toString())
                                Text(sizeText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(stringResource(Res.string.editor_no_image), color = Color.Gray)
                        }
                        
                        if (isProcessing) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // [右侧] 控制面板
                    Surface(
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            // 标签页切换
                            TabRow(selectedTabIndex = selectedTab.ordinal) {
                                Tab(selected = selectedTab == EditorTab.CROP, onClick = { selectedTab = EditorTab.CROP }) {
                                    Box(Modifier.padding(12.dp)) { Text(stringResource(Res.string.editor_tab_crop)) }
                                }
                                Tab(selected = selectedTab == EditorTab.BAKE, onClick = { selectedTab = EditorTab.BAKE }) {
                                    Box(Modifier.padding(12.dp)) { Text(stringResource(Res.string.editor_tab_bake)) }
                                }
                            }

                            Column(Modifier.weight(1f).padding(16.dp).verticalScroll(rememberScrollState())) {
                                when (selectedTab) {
                                    EditorTab.CROP -> {
                                        Text(stringResource(Res.string.editor_crop_options), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(16.dp))
                                        Text(stringResource(Res.string.editor_target_ratio, ((targetRatio * 100).toInt() / 100f).toString()), style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(24.dp))
                                        
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val bytes = currentBytes ?: return@launch
                                                    val bounds = getNonTransparentBounds(bytes)
                                                    val img = imageBitmap ?: return@launch
                                                    if (bounds != null && imageDisplayRect != Rect.Zero) {
                                                        val scaleX = imageDisplayRect.width / img.width.toFloat()
                                                        val scaleY = imageDisplayRect.height / img.height.toFloat()
                                                        
                                                        // 内容在显示区域中的绝对位置
                                                        val subjL = imageDisplayRect.left + bounds.left * scaleX
                                                        val subjT = imageDisplayRect.top + bounds.top * scaleY
                                                        val subjW = bounds.width * scaleX
                                                        val subjH = bounds.height * scaleY
                                                        
                                                        // 计算内容中心
                                                        val contentCenterX = subjL + subjW / 2f
                                                        val contentCenterY = subjT + subjH / 2f
                                                        
                                                        // 初始选框尺寸 (覆盖内容并保持比例)
                                                        var nW = if (subjW / subjH > targetRatio) subjW else subjH * targetRatio
                                                        var nH = nW / targetRatio
                                                        
                                                        // 增加一点呼吸边距 (5%)
                                                        nW *= 1.05f
                                                        nH *= 1.05f
                                                        
                                                        // 限制选框不超出图片显示边界
                                                        if (nW > imageDisplayRect.width) {
                                                            nW = imageDisplayRect.width
                                                            nH = nW / targetRatio
                                                        }
                                                        if (nH > imageDisplayRect.height) {
                                                            nH = imageDisplayRect.height
                                                            nW = nH * targetRatio
                                                        }
                                                        
                                                        // 计算选框左上角坐标，使其中心对齐内容中心
                                                        var nX = contentCenterX - nW / 2f
                                                        var nY = contentCenterY - nH / 2f
                                                        
                                                        // 最终边界修正，确保不移出图片
                                                        nX = nX.coerceIn(imageDisplayRect.left, imageDisplayRect.right - nW)
                                                        nY = nY.coerceIn(imageDisplayRect.top, imageDisplayRect.bottom - nH)
                                                        
                                                        cropSize = Size(nW, nH)
                                                        cropOffset = Offset(nX, nY)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.medium,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(Res.string.editor_action_auto_fit))
                                        }
                                        
                                        Spacer(Modifier.height(12.dp))
                                        
                                        OutlinedButton(
                                            onClick = {
                                                isProcessing = true
                                                coroutineScope.launch {
                                                    val bytes = currentBytes ?: return@launch
                                                    val img = imageBitmap ?: return@launch
                                                    val relX = (cropOffset.x - imageDisplayRect.left) / imageDisplayRect.width
                                                    val relY = (cropOffset.y - imageDisplayRect.top) / imageDisplayRect.height
                                                    val relW = cropSize.width / imageDisplayRect.width
                                                    val relH = cropSize.height / imageDisplayRect.height
                                                    
                                                    val result = cropImage(bytes, SerialRect(relX, relY, relX + relW, relY + relH), img.width.toFloat(), img.height.toFloat(), true, finalTargetW.toInt(), finalTargetH.toInt())
                                                    if (result != null) {
                                                        currentBytes = result
                                                        // 重置烘焙尺寸为新图尺寸
                                                        canvasWidth = finalTargetW.toInt(); canvasHeight = finalTargetH.toInt()
                                                        contentWidth = finalTargetW.toInt(); contentHeight = finalTargetH.toInt()
                                                    }
                                                    isProcessing = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.medium
                                        ) {
                                            Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(Res.string.editor_action_apply_crop))
                                        }
                                    }
                                    EditorTab.BAKE -> {
                                        Text(stringResource(Res.string.editor_bake_mode_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(16.dp))
                                        ImageResizeMode.entries.forEach { mode ->
                                            Row(Modifier.fillMaxWidth().clickable { bakeMode = mode }, verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = bakeMode == mode, onClick = { bakeMode = mode })
                                                Text(when(mode) {
                                                    ImageResizeMode.STRETCH -> stringResource(Res.string.editor_resize_stretch)
                                                    ImageResizeMode.FIT_WITH_PADDING -> stringResource(Res.string.editor_resize_fit)
                                                    ImageResizeMode.CROP_TO_FILL -> stringResource(Res.string.editor_resize_fill)
                                                    ImageResizeMode.NINE_PATCH -> stringResource(Res.string.editor_resize_nine_patch)
                                                }, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                                        Text(stringResource(Res.string.editor_canvas_size_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.height(8.dp))
                                        SizeInputRow(canvasWidth, canvasHeight, { canvasWidth = it }, { canvasHeight = it })
                                        Spacer(Modifier.height(16.dp))
                                        Text(stringResource(Res.string.editor_content_area_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.height(8.dp))
                                        SizeInputRow(contentWidth, contentHeight, { contentWidth = it }, { contentHeight = it })
                                        
                                        if (bakeMode == ImageResizeMode.NINE_PATCH) {
                                            HorizontalDivider(Modifier.padding(vertical = 16.dp))
                                            Text(stringResource(Res.string.editor_nine_patch_lines_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            Spacer(Modifier.height(12.dp))
                                            NinePatchInputGrid(ninePatchConfig, imageBitmap?.width ?: 1, imageBitmap?.height ?: 1) { ninePatchConfig = it }
                                        }
                                        
                                        Spacer(Modifier.height(24.dp))
                                        OutlinedButton(
                                            onClick = {
                                                isProcessing = true
                                                coroutineScope.launch {
                                                    val bytes = currentBytes ?: return@launch
                                                    val result = bakeNinePatchImage(bytes, canvasWidth, canvasHeight, contentWidth, contentHeight, bakeMode, ninePatchConfig)
                                                    if (result != null) {
                                                        currentBytes = result
                                                    }
                                                    isProcessing = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.medium
                                        ) {
                                            Icon(Icons.Default.Layers, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(Res.string.editor_action_apply_bake))
                                        }
                                    }
                                }
                            }

                            // 底部操作栏
                            Surface(Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text(stringResource(Res.string.prop_cancel)) }
                                    Button(
                                        onClick = {
                                            val bytes = currentBytes ?: return@Button
                                            onConfirm(bytes, bakeMode, ninePatchConfig)
                                        },
                                        Modifier.weight(1f),
                                        enabled = currentBytes != null && !isProcessing
                                    ) {
                                        Icon(Icons.Default.Check, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(Res.string.editor_action_save_final))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorStage(
    imageBitmap: ImageBitmap, mode: ImageResizeMode, config: NinePatchConfig,
    canvasW: Int, canvasH: Int, contentW: Int, contentH: Int,
    onConfigChange: (NinePatchConfig) -> Unit
) {
    var viewZoom by remember { mutableStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var isInitialized by remember { mutableStateOf(false) }
    var activeLine by remember { mutableStateOf(DragTarget.NONE) }

    var dragL by remember(config.left) { mutableStateOf(config.left.toFloat()) }
    var dragR by remember(config.right) { mutableStateOf(config.right.toFloat()) }
    var dragT by remember(config.top) { mutableStateOf(config.top.toFloat()) }
    var dragB by remember(config.bottom) { mutableStateOf(config.bottom.toFloat()) }

    Canvas(
        modifier = Modifier.fillMaxSize().onGloballyPositioned {
            if (!isInitialized && it.size.width > 0) {
                val fitScale = minOf(it.size.width.toFloat() / canvasW, it.size.height.toFloat() / canvasH) * 0.9f
                viewZoom = if (fitScale < 1f) fitScale else 1f
                viewOffset = Offset((it.size.width - canvasW * viewZoom) / 2f, (it.size.height - canvasH * viewZoom) / 2f)
                isInitialized = true
            }
        }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.first().scrollDelta.y
                        val factor = if (delta > 0) 0.9f else 1.1f
                        val centroid = event.changes.first().position
                        val oldZoom = viewZoom
                        viewZoom = (viewZoom * factor).coerceIn(0.05f, 100f)
                        viewOffset = centroid - (centroid - viewOffset) * (viewZoom / oldZoom)
                        event.changes.first().consume()
                    }
                }
            }
        }.pointerInput(mode, viewZoom, viewOffset, canvasW, canvasH, contentW, contentH) {
            detectDragGestures(
                onDragStart = { start ->
                    if (mode == ImageResizeMode.NINE_PATCH) {
                        val cX = viewOffset.x + (canvasW * viewZoom - contentW * viewZoom) / 2f
                        val cY = viewOffset.y + (canvasH * viewZoom - contentH * viewZoom) / 2f
                        val tx = (start.x - cX) / viewZoom
                        val ty = (start.y - cY) / viewZoom
                        val slop = 20f / viewZoom
                        activeLine = when {
                            abs(tx - config.left) < slop -> DragTarget.LEFT
                            abs(tx - (contentW - config.right)) < slop -> DragTarget.RIGHT
                            abs(ty - config.top) < slop -> DragTarget.TOP
                            abs(ty - (contentH - config.bottom)) < slop -> DragTarget.BOTTOM
                            else -> DragTarget.PAN
                        }
                    } else activeLine = DragTarget.PAN
                },
                onDragEnd = { activeLine = DragTarget.NONE },
                onDrag = { change, amt ->
                    change.consume()
                    if (activeLine == DragTarget.PAN) {
                        viewOffset += amt
                    } else if (mode == ImageResizeMode.NINE_PATCH) {
                        val dx = amt.x / viewZoom; val dy = amt.y / viewZoom
                        when (activeLine) {
                            DragTarget.LEFT -> dragL = (dragL + dx).coerceIn(0f, contentW - dragR - 10f)
                            DragTarget.RIGHT -> dragR = (dragR - dx).coerceIn(0f, contentW - dragL - 10f)
                            DragTarget.TOP -> dragT = (dragT + dy).coerceIn(0f, contentH - dragB - 10f)
                            DragTarget.BOTTOM -> dragB = (dragB - dy).coerceIn(0f, contentH - dragT - 10f)
                            else -> {}
                        }
                        onConfigChange(NinePatchConfig(dragL.toInt(), dragT.toInt(), dragR.toInt(), dragB.toInt()))
                    }
                }
            )
        }
    ) {
        if (!isInitialized) return@Canvas
        val dCanW = canvasW * viewZoom; val dCanH = canvasH * viewZoom
        drawRect(Color.Black.copy(alpha=0.5f), viewOffset, Size(dCanW, dCanH))
        clipRect(viewOffset.x, viewOffset.y, viewOffset.x + dCanW, viewOffset.y + dCanH) {
            drawCheckerboard(Rect(viewOffset, Size(dCanW, dCanH)))
            val dConW = contentW * viewZoom; val dConH = contentH * viewZoom
            val cX = viewOffset.x + (dCanW - dConW) / 2f; val cY = viewOffset.y + (dCanH - dConH) / 2f
            renderProcessed(imageBitmap!!, mode, config, cX, cY, dConW, dConH)
            if (mode == ImageResizeMode.NINE_PATCH) {
                val lL = cX + config.left * viewZoom; val lR = cX + (contentW - config.right) * viewZoom
                val lT = cY + config.top * viewZoom; val lB = cY + (contentH - config.bottom) * viewZoom
                fun dG(s: Offset, e: Offset, a: Boolean) = drawLine(if(a) Color.Cyan else Color.Green, s, e, if(a) 3.dp.toPx() else 1.dp.toPx())
                dG(Offset(lL, cY), Offset(lL, cY + dConH), activeLine == DragTarget.LEFT)
                dG(Offset(lR, cY), Offset(lR, cY + dConH), activeLine == DragTarget.RIGHT)
                dG(Offset(cX, lT), Offset(cX + dConW, lT), activeLine == DragTarget.TOP)
                dG(Offset(cX, lB), Offset(cX + dConW, lB), activeLine == DragTarget.BOTTOM)
            }
        }
        drawRect(Color.White.copy(alpha=0.3f), viewOffset, Size(dCanW, dCanH), style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.renderProcessed(img: ImageBitmap, mode: ImageResizeMode, conf: NinePatchConfig, x: Float, y: Float, w: Float, h: Float) {
    clipRect(x, y, x + w, y + h) {
        when (mode) {
            ImageResizeMode.STRETCH ->
                drawImage(img, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
            ImageResizeMode.FIT_WITH_PADDING -> {
                val s = minOf(w / img.width, h / img.height); val dw = img.width * s; val dh = img.height * s
                drawImage(img, dstOffset = IntOffset((x + (w - dw)/2).toInt(), (y + (h - dh)/2).toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
            }
            ImageResizeMode.CROP_TO_FILL -> {
                val s = maxOf(w / img.width, h / img.height); val dw = img.width * s; val dh = img.height * s
                drawImage(img, dstOffset = IntOffset((x + (w - dw)/2).toInt(), (y + (h - dh)/2).toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
            }
            ImageResizeMode.NINE_PATCH ->
                drawNP(img, conf, x, y, w, h)
        }
    }
}

private fun DrawScope.drawNP(img: ImageBitmap, c: NinePatchConfig, dx: Float, dy: Float, dw: Float, dh: Float) {
    val sw = img.width; val sh = img.height; val l = c.left; val t = c.top; val r = c.right; val b = c.bottom
    fun dP(sx: Int, sy: Int, sW: Int, sH: Int, pX: Float, pY: Float, pW: Float, pH: Float) {
        if (sW <= 0 || sH <= 0 || pW <= 0 || pH <= 0) return
        drawImage(img, IntOffset(sx, sy), IntSize(sW, sH), IntOffset((dx + pX).toInt(), (dy + pY).toInt()), IntSize(pW.toInt(), pH.toInt()))
    }
    dP(0, 0, l, t, 0f, 0f, l.toFloat(), t.toFloat())
    dP(l, 0, sw - l - r, t, l.toFloat(), 0f, dw - l - r, t.toFloat())
    dP(sw - r, 0, r, t, dw - r, 0f, r.toFloat(), t.toFloat())
    dP(0, t, l, sh - t - b, 0f, t.toFloat(), l.toFloat(), dh - t - b)
    dP(l, t, sw - l - r, sh - t - b, l.toFloat(), t.toFloat(), dw - l - r, dh - t - b)
    dP(sw - r, t, r, sh - t - b, dw - r, t.toFloat(), r.toFloat(), dh - t - b)
    dP(0, sh - b, l, b, 0f, dh - b, l.toFloat(), b.toFloat())
    dP(l, sh - b, sw - l - r, b, l.toFloat(), dh - b, dw - l - r, b.toFloat())
    dP(sw - r, sh - b, r, b, dw - r, dh - b, r.toFloat(), b.toFloat())
}

private fun DrawScope.drawCheckerboard(rect: Rect) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        val s = 12.dp.toPx()
        val cols = ceil(rect.width / s).toInt() + 1; val rows = ceil(rect.height / s).toInt() + 1
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if ((i + j) % 2 == 0) drawRect(Color.LightGray.copy(alpha=0.3f), Offset(rect.left + j*s, rect.top + i*s), Size(s, s))
            }
        }
    }
}

@Composable
private fun SizeInputRow(vW: Int, vH: Int, onW: (Int) -> Unit, onH: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = vW.toString(), onValueChange = { onW(it.toIntOrNull() ?: vW) }, label = { Text(stringResource(Res.string.label_width_short)) }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(value = vH.toString(), onValueChange = { onH(it.toIntOrNull() ?: vH) }, label = { Text(stringResource(Res.string.label_height_short)) }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

@Composable
private fun NinePatchInputGrid(c: NinePatchConfig, iW: Int, iH: Int, onC: (NinePatchConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = c.left.toString(), onValueChange = { onC(c.copy(left = (it.toIntOrNull() ?: 0).coerceIn(0, iW / 2))) }, label = { Text(stringResource(Res.string.label_left_short)) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = c.right.toString(), onValueChange = { onC(c.copy(right = (it.toIntOrNull() ?: 0).coerceIn(0, iW / 2))) }, label = { Text(stringResource(Res.string.label_right_short)) }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = c.top.toString(), onValueChange = { onC(c.copy(top = (it.toIntOrNull() ?: 0).coerceIn(0, iH / 2))) }, label = { Text(stringResource(Res.string.label_top_short)) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = c.bottom.toString(), onValueChange = { onC(c.copy(bottom = (it.toIntOrNull() ?: 0).coerceIn(0, iH / 2))) }, label = { Text(stringResource(Res.string.label_bottom_short)) }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}

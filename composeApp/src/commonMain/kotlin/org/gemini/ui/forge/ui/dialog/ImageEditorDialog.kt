package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.model.ui.ImageResizeMode
import org.gemini.ui.forge.model.ui.NinePatchConfig
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.decodeToBitmap
import kotlin.math.abs
import kotlin.math.ceil

private enum class DragTarget {
    NONE, PAN, LEFT, TOP, RIGHT, BOTTOM
}

@Composable
fun ImageEditorDialog(
    block: UIBlock,
    onDismiss: () -> Unit,
    onSaveConfig: (NinePatchConfig) -> Unit,
    onBakeImage: (ImageResizeMode, NinePatchConfig, Int, Int, Int, Int) -> Unit
) {
    val imageBitmapState = produceState<ImageBitmap?>(null, block.currentImageUri) {
        value = block.currentImageUri?.decodeToBitmap()
    }
    val imageBitmap = imageBitmapState.value 

    LaunchedEffect(imageBitmap) {
        if (imageBitmap != null) {
            AppLogger.d("ImageEditor", "✅ 编辑器图像已加载完毕, 尺寸: ${imageBitmap.width}x${imageBitmap.height}")
        }
    }

    var editMode by remember { mutableStateOf(block.resizeMode) }
    var ninePatchConfig by remember(imageBitmap) { 
        mutableStateOf(
            if (imageBitmap != null && block.ninePatchConfig.left == 0 && block.ninePatchConfig.right == 0 && block.ninePatchConfig.top == 0 && block.ninePatchConfig.bottom == 0) {
                AppLogger.d("ImageEditor", "✨ 初始化默认九宫格边界为 1/3")
                NinePatchConfig(imageBitmap.width / 3, imageBitmap.height / 3, imageBitmap.width / 3, imageBitmap.height / 3)
            } else {
                block.ninePatchConfig
            }
        ) 
    }
    
    var canvasWidth by remember { mutableStateOf(block.bounds.width.toInt().coerceAtLeast(1)) }
    var canvasHeight by remember { mutableStateOf(block.bounds.height.toInt().coerceAtLeast(1)) }
    
    var contentWidth by remember { mutableStateOf(block.bounds.width.toInt().coerceAtLeast(1)) }
    var contentHeight by remember { mutableStateOf(block.bounds.height.toInt().coerceAtLeast(1)) }

    Dialog(
        onDismissRequest = {
            AppLogger.d("ImageEditor", "关闭编辑器弹窗")
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.98f),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF151515)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        EditorStage(
                            imageBitmap = imageBitmap, mode = editMode, config = ninePatchConfig,
                            canvasW = canvasWidth, canvasH = canvasHeight, contentW = contentWidth, contentH = contentHeight,
                            onConfigChange = { ninePatchConfig = it }
                        )
                        // 在左上角显示原图尺寸
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "原图尺寸: ${imageBitmap.width} x ${imageBitmap.height}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("正在加载图片...", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("物理加工 (Baking)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))

                        Text("加工模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        ImageResizeMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { 
                                AppLogger.d("ImageEditor", "👆 切换加工模式: $mode")
                                editMode = mode 
                            }) {
                                RadioButton(selected = editMode == mode, onClick = { 
                                    AppLogger.d("ImageEditor", "👆 切换加工模式: $mode")
                                    editMode = mode 
                                })
                                Text(when(mode) {
                                    ImageResizeMode.STRETCH -> "强制拉伸"
                                    ImageResizeMode.FIT_WITH_PADDING -> "等比补白"
                                    ImageResizeMode.CROP_TO_FILL -> "等比铺满"
                                    ImageResizeMode.NINE_PATCH -> "九宫格拉伸"
                                }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Text("1. 画布尺寸 (物理文件)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        SizeInputRow(canvasWidth, canvasHeight, { 
                            AppLogger.d("ImageEditor", "✏️ 修改画布宽度: $it")
                            canvasWidth = it 
                        }, { 
                            AppLogger.d("ImageEditor", "✏️ 修改画布高度: $it")
                            canvasHeight = it 
                        })
                        
                        Spacer(Modifier.height(16.dp))
                        Text("2. 内容比例 (内存放大)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        SizeInputRow(contentWidth, contentHeight, { 
                            AppLogger.d("ImageEditor", "✏️ 修改内容宽度: $it")
                            contentWidth = it 
                        }, { 
                            AppLogger.d("ImageEditor", "✏️ 修改内容高度: $it")
                            contentHeight = it 
                        })
                        
                        if (editMode == ImageResizeMode.NINE_PATCH) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            Text("九宫格边界线 (像素)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            val imgW = imageBitmap?.width ?: 1
                            val imgH = imageBitmap?.height ?: 1
                            NinePatchInputGrid(ninePatchConfig, imgW, imgH) { 
                                AppLogger.d("ImageEditor", "✏️ 手动输入九宫格配置: $it")
                                ninePatchConfig = it 
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.height(24.dp))
                        
                        Button(
                            onClick = { 
                                AppLogger.i("ImageEditor", "🚀 点击保存新图片！参数 - 模式:$editMode, 画布:${canvasWidth}x${canvasHeight}, 内容:${contentWidth}x${contentHeight}, 九宫格配置:$ninePatchConfig")
                                onBakeImage(editMode, ninePatchConfig, canvasWidth, canvasHeight, contentWidth, contentHeight) 
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = AppShapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = imageBitmap != null
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存新图片")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            AppLogger.d("ImageEditor", "点击取消修改")
                            onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Text("取消修改") }
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
                AppLogger.d("ImageEditor", "👁️ 视口初始化完毕: Zoom=$viewZoom, Offset=$viewOffset")
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
                        AppLogger.d("ImageEditor", "🔍 鼠标滚轮缩放: 新Zoom=$viewZoom")
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
                    
                    AppLogger.d("ImageEditor", "🖐️ 手势拖拽开始, 击中目标: $activeLine")
                },
                onDragEnd = { 
                    AppLogger.d("ImageEditor", "🖐️ 手势拖拽结束. 最新配置: L=${dragL.toInt()}, T=${dragT.toInt()}, R=${dragR.toInt()}, B=${dragB.toInt()}")
                    activeLine = DragTarget.NONE 
                },
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
            renderProcessed(imageBitmap, mode, config, cX, cY, dConW, dConH)
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
        OutlinedTextField(value = vW.toString(), onValueChange = { onW(it.toIntOrNull() ?: vW) }, label = { Text("宽") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(value = vH.toString(), onValueChange = { onH(it.toIntOrNull() ?: vH) }, label = { Text("高") }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

@Composable
private fun NinePatchInputGrid(c: NinePatchConfig, iW: Int, iH: Int, onC: (NinePatchConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = c.left.toString(), onValueChange = { onC(c.copy(left = (it.toIntOrNull() ?: 0).coerceIn(0, iW / 2))) }, label = { Text("左") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = c.right.toString(), onValueChange = { onC(c.copy(right = (it.toIntOrNull() ?: 0).coerceIn(0, iW / 2))) }, label = { Text("右") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = c.top.toString(), onValueChange = { onC(c.copy(top = (it.toIntOrNull() ?: 0).coerceIn(0, iH / 2))) }, label = { Text("上") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = c.bottom.toString(), onValueChange = { onC(c.copy(bottom = (it.toIntOrNull() ?: 0).coerceIn(0, iH / 2))) }, label = { Text("下") }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}
package org.gemini.ui.forge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import geminiuiforge.composeapp.generated.resources.Res
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.SerialRect
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.viewmodel.ReferenceDisplayMode
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    
    // 参考图显示增强
    referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    referenceUri: String? = null,
    referenceOpacity: Float = 0.4f,
    
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 异步加载参考图位图
    val refBitmapState = produceState<ImageBitmap?>(null, referenceUri) {
        value = referenceUri?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        // 模式 A：分屏对照 (参考图在上)
        if (referenceMode == ReferenceDisplayMode.SPLIT && refBitmap != null) {
            Surface(
                modifier = Modifier.weight(0.35f).fillMaxWidth().padding(8.dp),
                color = Color.Black,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Image(bitmap = refBitmap, contentDescription = "Ref", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }

        // 主舞台区域
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val baseScale = min(maxWidth.value / pageWidth, maxHeight.value / pageHeight) * 0.9f
                val offsetX = (maxWidth.value - (pageWidth * baseScale)) / 2
                val offsetY = (maxHeight.value - (pageHeight * baseScale)) / 2

                // 核心容器：承载 UI 块、点击手势、参考蒙层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(blocks) {
                            detectTapGestures { offset ->
                                // 点击坐标还原：(屏幕像素 - 边距像素 - 平移像素) / (总缩放比例 * 密度)
                                val lx = ((offset.x / density.density - offsetX - pan.x / density.density) / (baseScale * zoom))
                                val ly = ((offset.y / density.density - offsetY - pan.y / density.density) / (baseScale * zoom))
                                
                                val hitBlock = blocks.findLast { b ->
                                    lx >= b.bounds.left && lx <= b.bounds.right &&
                                    ly >= b.bounds.top && ly <= b.bounds.bottom
                                }
                                onBlockClicked(hitBlock?.id ?: "")
                            }
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
                    if (referenceMode == ReferenceDisplayMode.OVERLAY && refBitmap != null) {
                        Image(
                            bitmap = refBitmap,
                            contentDescription = null,
                            alpha = referenceOpacity,
                            modifier = Modifier
                                .offset(x = offsetX.dp, y = offsetY.dp)
                                .size(width = (pageWidth * baseScale).dp, height = (pageHeight * baseScale).dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    // 2. 渲染 UI 块集合
                    blocks.forEach { block ->
                        val isSelected = block.id == selectedBlockId
                        val imageBitmapState = produceState<ImageBitmap?>(null, block.currentImageUri) {
                            value = block.currentImageUri?.decodeBase64ToBitmap()
                        }
                        val imageBitmap = imageBitmapState.value

                        Box(
                            modifier = Modifier
                                .offset(x = (offsetX + block.bounds.left * baseScale).dp, y = (offsetY + block.bounds.top * baseScale).dp)
                                .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if(isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) (2.dp / zoom) else (0.5.dp / zoom),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageBitmap != null) {
                                Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (block.currentImageUri != null) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
                            } else {
                                Text(text = stringResource(block.type.getDisplayNameRes()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // 悬浮工具条
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.1f) }, Modifier.size(28.dp)) { Text("-") }
                    Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { zoom = (zoom + 0.2f).coerceAtMost(5f) }, Modifier.size(28.dp)) { Text("+") }
                    VerticalDivider(Modifier.height(16.dp))
                    IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) }
                }
            }
        }
    }
}

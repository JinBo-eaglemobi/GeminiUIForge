package org.gemini.ui.forge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    isSelectionMode: Boolean = false, // 框选模式开关
    onAreaSelected: (SerialRect) -> Unit = {}, // 框选完成回调
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 内部选区临时状态
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val scaleX = maxWidth.value / pageWidth
            val scaleY = maxHeight.value / pageHeight
            val baseScale = min(scaleX, scaleY) * 0.9f
            
            val offsetX = (maxWidth.value - (pageWidth * baseScale)) / 2
            val offsetY = (maxHeight.value - (pageHeight * baseScale)) / 2

            // 主交互容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 1. 处理框选或平移缩放
                    .pointerInput(isSelectionMode) {
                        if (isSelectionMode) {
                            detectDragGestures(
                                onDragStart = { selectionStart = it; selectionEnd = it },
                                onDrag = { change, _ -> selectionEnd = change.position; change.consume() },
                                onDragEnd = {
                                    val start = selectionStart
                                    val end = selectionEnd
                                    if (start != null && end != null) {
                                        // 坐标转换逻辑：(屏幕坐标 - 居中偏移 - 当前平移) / (基础缩放 * 缩放倍率)
                                        fun toLogical(offset: Offset): Offset {
                                            val lx = (offset.x / density.density - offsetX - pan.x) / (baseScale * zoom)
                                            val ly = (offset.y / density.density - offsetY - pan.y) / (baseScale * zoom)
                                            return Offset(lx.coerceIn(0f, pageWidth), ly.coerceIn(0f, pageHeight))
                                        }
                                        val lStart = toLogical(start)
                                        val lEnd = toLogical(end)
                                        onAreaSelected(SerialRect(
                                            left = min(lStart.x, lEnd.x),
                                            top = min(lStart.y, lEnd.y),
                                            right = maxOf(lStart.x, lEnd.x),
                                            bottom = maxOf(lStart.y, lEnd.y)
                                        ))
                                    }
                                    selectionStart = null
                                    selectionEnd = null
                                },
                                onDragCancel = { selectionStart = null; selectionEnd = null }
                            )
                        } else {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(0.1f, 5f)
                                pan += panChange
                            }
                        }
                    }
                    // 2. 处理滚轮缩放
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) {
                                        val zoomFactor = if (delta > 0) 0.9f else 1.1f
                                        zoom = (zoom * zoomFactor).coerceIn(0.1f, 5f)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = pan.x,
                        translationY = pan.y
                    )
            ) {
                // 渲染 UI 块
                blocks.forEach { block ->
                    val isSelected = block.id == selectedBlockId
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (offsetX + block.bounds.left * baseScale).dp,
                                y = (offsetY + block.bounds.top * baseScale).dp
                            )
                            .size(
                                width = (block.bounds.width * baseScale).dp,
                                height = (block.bounds.height * baseScale).dp
                            )
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .border(
                                width = if (isSelected) (2.dp / zoom) else (0.5.dp / zoom),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                            .clickable(enabled = !isSelectionMode) { onBlockClicked(block.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (block.currentImageUri != null) {
                            block.currentImageUri.decodeBase64ToBitmap()?.let {
                                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        } else {
                            Text(text = stringResource(block.type.getDisplayNameRes()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // 选区 Overlay (在 graphicsLayer 之外渲染，确保它跟随屏幕坐标而不是画布坐标)
            if (isSelectionMode && selectionStart != null && selectionEnd != null) {
                val s = selectionStart!!
                val e = selectionEnd!!
                Box(
                    modifier = Modifier
                        .offset(x = (min(s.x, e.x) / density.density).dp, y = (min(s.y, e.y) / density.density).dp)
                        .size(width = (abs(e.x - s.x) / density.density).dp, height = (abs(e.y - s.y) / density.density).dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.primary)
                )
            }
        }

        // 控制条
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.1f) }, modifier = Modifier.size(28.dp)) {
                    Text("-", style = MaterialTheme.typography.titleMedium)
                }
                Text(text = "${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { zoom = (zoom + 0.2f).coerceAtMost(5f) }, modifier = Modifier.size(28.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
                VerticalDivider(modifier = Modifier.height(16.dp))
                IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

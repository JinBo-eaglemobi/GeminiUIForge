package org.gemini.ui.forge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.ui.getDisplayNameRes
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CanvasArea(
    pageWidth: Float,
    pageHeight: Float,
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 外层容器：负责裁剪和底色
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

            // 手势监听与变换容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isSelected) (3.dp / zoom) else (1.dp / zoom), // 补偿 zoom 导致的边框变粗
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onBlockClicked(block.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (block.currentImageUri != null) {
                            val bitmap = block.currentImageUri.decodeBase64ToBitmap()
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(text = "Error", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                text = stringResource(block.type.getDisplayNameRes()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 顶部居中药丸形控制条 (保留)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { zoom = (zoom - 0.1f).coerceAtLeast(0.1f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("-", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                text = "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { zoom = (zoom + 0.1f).coerceAtMost(5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Box(modifier = Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colorScheme.outlineVariant))

            IconButton(
                onClick = {
                    zoom = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset View",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
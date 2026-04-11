package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.utils.getImageSize
import kotlin.math.roundToInt

/**
 * 资源二次裁剪对话框 (弹窗 B)
 * 支持基于模块比例的等比缩放裁剪。
 */
@Composable
fun AssetCropDialog(
    imageUri: String,
    targetWidth: Float,
    targetHeight: Float,
    onConfirm: (SerialRect) -> Unit,
    onDismiss: () -> Unit
) {
    val imageBitmapState = produceState<ImageBitmap?>(null, imageUri) {
        value = imageUri.decodeBase64ToBitmap()
    }
    val imageBitmap = imageBitmapState.value
    val density = LocalDensity.current

    var imageSize by remember { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(imageUri) {
        imageSize = getImageSize(imageUri)?.let { IntSize(it.first, it.second) }
    }

    // 状态记录 (全部使用物理像素 px)
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    val targetRatio = targetWidth / targetHeight

    // 当容器大小或图片大小确定后，计算图片的实际显示区域并初始化裁剪框
    LaunchedEffect(containerSize, imageSize) {
        val img = imageSize ?: return@LaunchedEffect
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
        val newDisplayRect = Rect(left, top, left + displayW, top + displayH)
        imageDisplayRect = newDisplayRect

        // 初始选择框：在图片区域内取最大可能的 targetRatio 矩形
        val initialW: Float
        val initialH: Float
        if (displayW / displayH > targetRatio) {
            initialH = displayH * 0.8f
            initialW = initialH * targetRatio
        } else {
            initialW = displayW * 0.8f
            initialH = initialW / targetRatio
        }
        
        cropSize = Size(initialW, initialH)
        cropOffset = Offset(
            newDisplayRect.left + (displayW - initialW) / 2f,
            newDisplayRect.top + (displayH - initialH) / 2f
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("适配裁剪区域", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "目标比例: ${String.format("%.2f", targetRatio)} | 图片: ${imageSize?.width ?: 0}x${imageSize?.height ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // 核心交互区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.1f))
                        .clipToBounds()
                        .onGloballyPositioned { containerSize = it.size.toSize() },
                    contentAlignment = Alignment.TopStart // 必须 TopStart，否则 offset 会乱
                ) {
                    if (imageBitmap != null) {
                        // 底图 (始终居中 Fit)
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // 裁剪框 (仅在 imageDisplayRect 准备好后显示)
                        if (imageDisplayRect != Rect.Zero && cropSize.width > 0) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(cropOffset.x.roundToInt(), cropOffset.y.roundToInt()) }
                                    .size(
                                        width = with(density) { cropSize.width.toDp() },
                                        height = with(density) { cropSize.height.toDp() }
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 12.dp))
                                        .pointerInput(imageDisplayRect, cropOffset) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                // 限制放大不能超过图片右侧和底侧边界
                                                val maxW = imageDisplayRect.right - cropOffset.x
                                                val maxH = imageDisplayRect.bottom - cropOffset.y
                                                
                                                var deltaX = dragAmount.x
                                                var deltaY = deltaX / targetRatio
                                                
                                                if (cropSize.width + deltaX > maxW) {
                                                    deltaX = maxW - cropSize.width
                                                    deltaY = deltaX / targetRatio
                                                }
                                                if (cropSize.height + deltaY > maxH) {
                                                    deltaY = maxH - cropSize.height
                                                    deltaX = deltaY * targetRatio
                                                }
                                                
                                                val finalW = (cropSize.width + deltaX).coerceAtLeast(40f)
                                                val finalH = finalW / targetRatio
                                                
                                                cropSize = Size(finalW, finalH)
                                            }
                                        }
                                ) {
                                    Icon(
                                        Icons.Default.AspectRatio, 
                                        null, 
                                        modifier = Modifier.size(18.dp).align(Alignment.Center), 
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = AppShapes.medium) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            // 最终映射到图片的 0..1 归一化坐标
                            val relX = (cropOffset.x - imageDisplayRect.left) / imageDisplayRect.width
                            val relY = (cropOffset.y - imageDisplayRect.top) / imageDisplayRect.height
                            val relW = cropSize.width / imageDisplayRect.width
                            val relH = cropSize.height / imageDisplayRect.height
                            
                            onConfirm(SerialRect(relX, relY, relX + relW, relY + relH))
                        },
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("确定裁剪并应用")
                    }
                }
            }
        }
    }
}

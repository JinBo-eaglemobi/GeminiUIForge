package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
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
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.gemini.ui.forge.utils.getImageSize
import kotlin.math.roundToInt

/**
 * 资源二次裁剪对话框 (弹窗 B)
 * 支持基于模块比例的等比缩放裁剪，以及一键去透明背景功能。
 */
@Composable
fun AssetCropDialog(
    imageUri: String,
    targetWidth: Float,
    targetHeight: Float,
    onConfirm: (SerialRect) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 核心改进：内部管理当前显示的图片 URI，支持在弹窗内“进化”图片
    var currentDisplayUri by remember { mutableStateOf(imageUri) }

    val imageBitmapState = produceState<ImageBitmap?>(null, currentDisplayUri) {
        value = currentDisplayUri.decodeBase64ToBitmap()
    }
    val imageBitmap = imageBitmapState.value

    var imageSize by remember { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(currentDisplayUri) {
        imageSize = getImageSize(currentDisplayUri)?.let { IntSize(it.first, it.second) }
    }

    // 状态记录 (物理像素 px)
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    val targetRatio = targetWidth / targetHeight

    // 当容器、图片尺寸或图片源变化时，重新初始化布局
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

        // 初始选择框
        val initialW: Float
        val initialH: Float
        if (displayW / displayH > targetRatio) {
            initialH = displayH * 0.85f
            initialW = initialH * targetRatio
        } else {
            initialW = displayW * 0.85f
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
                    "目标比例: ${((targetRatio * 100).toInt() / 100f)} | 当前底图: ${imageSize?.width ?: 0}x${imageSize?.height ?: 0}",
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
                    contentAlignment = Alignment.TopStart
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

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
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 12.dp))
                                        .pointerInput(imageDisplayRect, cropOffset) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
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
                                    Icon(Icons.Default.AspectRatio, null, modifier = Modifier.size(18.dp).align(Alignment.Center), tint = Color.White)
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val bounds = org.gemini.ui.forge.utils.getNonTransparentBounds(currentDisplayUri)
                                    val imgSize = imageSize
                                    if (bounds != null && imgSize != null && imageDisplayRect != Rect.Zero) {
                                        val scaleX = imageDisplayRect.width / imgSize.width.toFloat()
                                        val scaleY = imageDisplayRect.height / imgSize.height.toFloat()

                                        val subjLeft = imageDisplayRect.left + bounds.left * scaleX
                                        val subjTop = imageDisplayRect.top + bounds.top * scaleY
                                        val subjWidth = (bounds.right - bounds.left) * scaleX
                                        val subjHeight = (bounds.bottom - bounds.top) * scaleY

                                        val subjCenterX = subjLeft + subjWidth / 2f
                                        val subjCenterY = subjTop + subjHeight / 2f

                                        var newCropW: Float
                                        var newCropH: Float

                                        if (subjWidth / subjHeight > targetRatio) {
                                            newCropW = subjWidth
                                            newCropH = subjWidth / targetRatio
                                        } else {
                                            newCropH = subjHeight
                                            newCropW = subjHeight * targetRatio
                                        }

                                        // 增加微小的呼吸边距 (例如 5%)
                                        newCropW *= 1.05f
                                        newCropH *= 1.05f

                                        // 限制在显示图片框内
                                        if (newCropW > imageDisplayRect.width) {
                                            newCropW = imageDisplayRect.width
                                            newCropH = newCropW / targetRatio
                                        }
                                        if (newCropH > imageDisplayRect.height) {
                                            newCropH = imageDisplayRect.height
                                            newCropW = newCropH * targetRatio
                                        }

                                        var newOffX = subjCenterX - newCropW / 2f
                                        var newOffY = subjCenterY - newCropH / 2f

                                        // 防止越界
                                        if (newOffX < imageDisplayRect.left) newOffX = imageDisplayRect.left
                                        if (newOffY < imageDisplayRect.top) newOffY = imageDisplayRect.top
                                        if (newOffX + newCropW > imageDisplayRect.right) newOffX = imageDisplayRect.right - newCropW
                                        if (newOffY + newCropH > imageDisplayRect.bottom) newOffY = imageDisplayRect.bottom - newCropH

                                        cropSize = Size(newCropW, newCropH)
                                        cropOffset = Offset(newOffX, newOffY)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = AppShapes.medium
                        ) {
                            Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("自动适配")
                        }
                    }

                    Row {
                        TextButton(onClick = onDismiss, shape = AppShapes.medium) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val relX = (cropOffset.x - imageDisplayRect.left) / imageDisplayRect.width
                                val relY = (cropOffset.y - imageDisplayRect.top) / imageDisplayRect.height
                                val relW = cropSize.width / imageDisplayRect.width
                                val relH = cropSize.height / imageDisplayRect.height
                                
                                // 提交时传递 internalImageUri，确保裁剪的是处理后的图
                                onConfirm(SerialRect(relX, relY, relX + relW, relY + relH))
                            },
                            shape = AppShapes.medium
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(4.dp))
                            Text("确定裁剪并应用")
                        }
                    }
                }
            }
        }
    }
}

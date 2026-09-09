package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect

/**
 * 通用的图片区域选择器组件
 * 支持绘制新选区、拖动已有选区位置，并自动限制在图片边界内
 */
@Composable
fun ImageAreaSelector(
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    modifier: Modifier = Modifier,
    selectionColor: Color = Color.Green,
    initialRect: SerialRect? = null,
    onSelectionChange: (SerialRect?) -> Unit
) {
    var selectedRect by remember(initialRect) { mutableStateOf(initialRect) }
    var isMoving by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = maxWidth
        val containerH = maxHeight
        val imageAspectRatio = pageWidth / pageHeight
        val containerAspectRatio = containerW.value / containerH.value

        val displayW: Float
        val displayH: Float
        if (imageAspectRatio > containerAspectRatio) {
            displayW = containerW.value
            displayH = displayW / imageAspectRatio
        } else {
            displayH = containerH.value
            displayW = displayH * imageAspectRatio
        }

        val offsetX = (containerW.value - displayW) / 2
        val offsetY = (containerH.value - displayH) / 2

        AsyncImage(
            model = imageUri?.getAbsolutePath(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(pageWidth, pageHeight, density, offsetX, offsetY, displayW, displayH) {
            detectDragGestures(
                onDragStart = { offset ->
                    val lx = (((offset.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth).coerceIn(0f, pageWidth)
                    val ly = (((offset.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight).coerceIn(0f, pageHeight)

                    val current = selectedRect
                    if (current != null && lx >= kotlin.math.min(current.left, current.right) && lx <= kotlin.math.max(current.left, current.right)
                        && ly >= kotlin.math.min(current.top, current.bottom) && ly <= kotlin.math.max(current.top, current.bottom)
                    ) {
                        isMoving = true
                    } else {
                        isMoving = false
                        selectedRect = SerialRect(lx, ly, lx, ly)
                        onSelectionChange(selectedRect)
                    }
                },
                onDrag = { change, dragAmount ->
                    if (isMoving) {
                        val dx = (dragAmount.x / with(density) { displayW.dp.toPx() }) * pageWidth
                        val dy = (dragAmount.y / with(density) { displayH.dp.toPx() }) * pageHeight
                        selectedRect?.let { rect ->
                            var newLeft = rect.left + dx
                            var newRight = rect.right + dx
                            var newTop = rect.top + dy
                            var newBottom = rect.bottom + dy

                            val minLX = kotlin.math.min(newLeft, newRight)
                            val maxLX = kotlin.math.max(newLeft, newRight)
                            val minLY = kotlin.math.min(newTop, newBottom)
                            val maxLY = kotlin.math.max(newTop, newBottom)

                            if (minLX < 0) {
                                val shift = -minLX
                                newLeft += shift
                                newRight += shift
                            } else if (maxLX > pageWidth) {
                                val shift = pageWidth - maxLX
                                newLeft += shift
                                newRight += shift
                            }

                            if (minLY < 0) {
                                val shift = -minLY
                                newTop += shift
                                newBottom += shift
                            } else if (maxLY > pageHeight) {
                                val shift = pageHeight - maxLY
                                newTop += shift
                                newBottom += shift
                            }
                            selectedRect = SerialRect(newLeft, newTop, newRight, newBottom)
                            onSelectionChange(selectedRect)
                        }
                    } else {
                        val lx = (((change.position.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth).coerceIn(0f, pageWidth)
                        val ly = (((change.position.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight).coerceIn(0f, pageHeight)
                        selectedRect = selectedRect?.copy(right = lx, bottom = ly)
                        onSelectionChange(selectedRect)
                    }
                }
            )
        }) {
            selectedRect?.let { rect ->
                val left = with(density) { (offsetX + (rect.left / pageWidth) * displayW).dp.toPx() }
                val top = with(density) { (offsetY + (rect.top / pageHeight) * displayH).dp.toPx() }
                val right = with(density) { (offsetX + (rect.right / pageWidth) * displayW).dp.toPx() }
                val bottom = with(density) { (offsetY + (rect.bottom / pageHeight) * displayH).dp.toPx() }
                val rectTopLeft = Offset(kotlin.math.min(left, right), kotlin.math.min(top, bottom))
                val rectSize = Size(kotlin.math.abs(right - left), kotlin.math.abs(bottom - top))
                drawRect(
                    color = selectionColor,
                    topLeft = rectTopLeft,
                    size = rectSize,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawRect(color = selectionColor.copy(alpha = 0.2f), topLeft = rectTopLeft, size = rectSize)
            }
        }
    }
}

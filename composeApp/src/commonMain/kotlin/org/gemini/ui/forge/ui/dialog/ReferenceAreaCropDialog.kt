package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 设置区域参考图对话框
 */
@Composable
fun ReferenceAreaCropDialog(
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (SerialRect) -> Unit
) {
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("设置区域参考图", style = MaterialTheme.typography.headlineSmall)
                Text("框选当前模块在原图上的对应区域，生成时将仅以该区域作为参考图。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.05f))
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val lx =
                                        ((offset.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth
                                    val ly =
                                        ((offset.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight
                                    selectedRect = SerialRect(lx, ly, lx, ly)
                                },
                                onDrag = { change, _ ->
                                    val lx =
                                        ((change.position.x - with(density) { offsetX.dp.toPx() }) / with(density) { displayW.dp.toPx() }) * pageWidth
                                    val ly =
                                        ((change.position.y - with(density) { offsetY.dp.toPx() }) / with(density) { displayH.dp.toPx() }) * pageHeight
                                    selectedRect = selectedRect?.copy(right = lx, bottom = ly)
                                }
                            )
                        }) {
                            selectedRect?.let { rect ->
                                val left = with(density) { (offsetX + (rect.left / pageWidth) * displayW).dp.toPx() }
                                val top = with(density) { (offsetY + (rect.top / pageHeight) * displayH).dp.toPx() }
                                val right = with(density) { (offsetX + (rect.right / pageWidth) * displayW).dp.toPx() }
                                val bottom =
                                    with(density) { (offsetY + (rect.bottom / pageHeight) * displayH).dp.toPx() }
                                val rectTopLeft = Offset(kotlin.math.min(left, right), kotlin.math.min(top, bottom))
                                val rectSize = Size(kotlin.math.abs(right - left), kotlin.math.abs(bottom - top))
                                drawRect(
                                    color = Color.Green,
                                    topLeft = rectTopLeft,
                                    size = rectSize,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawRect(color = Color.Green.copy(alpha = 0.2f), topLeft = rectTopLeft, size = rectSize)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { selectedRect?.let { onConfirm(it) } },
                        enabled = selectedRect != null,
                        shape = AppShapes.medium
                    ) { Text("保存局部参考") }
                }
            }
        }
    }
}
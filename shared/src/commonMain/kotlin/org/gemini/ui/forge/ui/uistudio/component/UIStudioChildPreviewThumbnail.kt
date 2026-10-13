package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * 实时预览父容器中渲染各个子图元真实切片画面的微缩组件。
 *
 * 遵循一文件一 Composable 铁律，异步加载子图元切片图片并在微缩父容器画布中按局部坐标呈现真实视觉。
 */
@Composable
fun UIStudioChildPreviewThumbnail(
    child: UIBlock,
    scale: Float,
    inheritedOpacity: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(child.currentImageUri?.relativePath, child.referenceImage?.relativePath) {
        mutableStateOf<ImageBitmap?>(null)
    }

    val imagePath = child.currentImageUri?.relativePath ?: child.referenceImage?.relativePath

    LaunchedEffect(imagePath, child.id) {
        withContext(Dispatchers.Default) {
            val bytes = if (!imagePath.isNullOrBlank()) {
                readLocalFileBytes(imagePath) ?: try {
                    TemplateFile(imagePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            } else null
            if (bytes != null) {
                bitmap = bytes.toImageBitmap()
            }
        }
    }

    val childX = child.bounds.left * scale
    val childY = child.bounds.top * scale
    val childW = (child.bounds.right - child.bounds.left).coerceAtLeast(1f) * scale
    val childH = (child.bounds.bottom - child.bounds.top).coerceAtLeast(1f) * scale

    Box(
        modifier = modifier
            .offset(x = childX.dp, y = childY.dp)
            .size(childW.dp, childH.dp)
            .graphicsLayer {
                scaleX = child.effectiveScaleX
                scaleY = child.effectiveScaleY
                alpha = (child.effectiveOpacity * inheritedOpacity).coerceIn(0.0f, 1.0f)
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF38BDF8).copy(alpha = 0.25f))
                    .border(0.5.dp, Color(0xFF38BDF8), RoundedCornerShape(1.dp))
            )
        }
    }
}

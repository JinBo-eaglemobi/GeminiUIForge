package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * UI Studio 独立图元项渲染组件
 *
 * 负责在画布逻辑坐标系下渲染单个已切片/已入库的图元实体，展示真实的切片图像与工程级边界药丸。
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun UIStudioBlockItem(
    block: UIBlock,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember(block.id, block.currentImageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(block.id, block.currentImageUri) {
        withContext(Dispatchers.Default) {
            val file = block.currentImageUri ?: block.referenceImage
            val bytes = file?.readBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                try {
                    imageBitmap = bytes.toImageBitmap()
                } catch (_: Exception) {
                    imageBitmap = null
                }
            }
        }
    }

    val b = block.bounds
    val width = (b.right - b.left).coerceAtLeast(1f)
    val height = (b.bottom - b.top).coerceAtLeast(1f)
    val displayName = block.userPromptZh.ifBlank { block.userPromptEn.ifBlank { block.id } }

    Box(
        modifier = modifier
            .offset(x = b.left.dp, y = b.top.dp)
            .size(width = width.dp, height = height.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFFFF9800) else Color(0xFF00E5FF).copy(alpha = 0.5f),
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // 渲染切片图片本体
        val bmp = imageBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            // 无图占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E5FF).copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

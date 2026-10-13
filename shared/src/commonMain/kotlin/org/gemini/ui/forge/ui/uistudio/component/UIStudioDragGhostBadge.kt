package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap
import kotlin.math.roundToInt

/**
 * 素材拖拽过程中跟随鼠标指针实时移动的浮动镜像徽标 (Drag Ghost Badge)。
 *
 * 在光标右下方偏移呈现 36×36dp 微缩图与素材名称胶囊，提供直观的拖拽抓取反馈。
 */
@Composable
fun UIStudioDragGhostBadge(
    asset: UIAssetItem?,
    cursorPos: Offset?,
    modifier: Modifier = Modifier
) {
    if (asset == null || cursorPos == null) return

    var bitmap by remember(asset.relativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(asset.relativePath) {
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(asset.relativePath)
                ?: try {
                    TemplateFile(asset.relativePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            if (bytes != null) {
                bitmap = bytes.toImageBitmap()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Surface(
            color = Color(0xEE0F172A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .offset {
                    IntOffset(
                        (cursorPos.x + 14f).roundToInt(),
                        (cursorPos.y + 14f).roundToInt()
                    )
                }
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(8.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // 微缩切片图
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = asset.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${asset.width} × ${asset.height} px",
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8) // 高亮青蓝
                    )
                }
            }
        }
    }
}

package org.gemini.ui.forge.ui.feature.editor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.utils.decodeBase64ToBitmap

/**
 * AI 生成候选图片的展示列表组件
 * @param candidates Base64 图片数据列表
 * @param onImageSelected 当用户点击选中某张图片时的回调
 */
@Composable
fun CandidateGallery(
    candidates: List<String>,
    onImageSelected: (String) -> Unit
) {
    if (candidates.isEmpty()) return

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "AI 生成候选资源",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates) { base64 ->
                // 异步加载候选项图片，防止 UI 卡顿
                val imageBitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = base64) {
                    value = base64.decodeBase64ToBitmap()
                }
                val bitmap = imageBitmapState.value

                Card(
                    modifier = Modifier
                        .size(120.dp)
                        .clickable { onImageSelected(base64) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Generated candidate",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // 加载中状态
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

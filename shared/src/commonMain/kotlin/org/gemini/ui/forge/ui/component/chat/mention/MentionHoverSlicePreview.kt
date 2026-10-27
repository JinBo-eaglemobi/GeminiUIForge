package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.mention_preview_no_bound_asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * 悬停预览浮层中渲染普通图元（BLOCK）切片画面与几何属性的组件 (一文件一 Composable)
 */
@Composable
fun MentionHoverSlicePreview(
    item: MentionItem,
    assetPath: String?,
    payload: MentionPayload.BlockPayload,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(assetPath) { mutableStateOf(!assetPath.isNullOrBlank()) }

    LaunchedEffect(assetPath) {
        if (assetPath.isNullOrBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(assetPath) ?: try {
                TemplateFile(assetPath).readBytes()
            } catch (_: Exception) {
                null
            }
            bitmap = bytes?.toImageBitmap()
            isLoading = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. 深色视口
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CropSquare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.mention_preview_no_bound_asset),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // 2. 下方信息卡片
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "类型: ${payload.type}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${payload.width.toInt()} × ${payload.height.toInt()} px",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

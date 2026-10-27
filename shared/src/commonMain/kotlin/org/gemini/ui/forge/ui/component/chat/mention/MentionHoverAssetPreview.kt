package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
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
import geminiuiforge.composeapp.generated.resources.mention_preview_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * 悬停预览浮层中渲染素材（ASSET）切片与尺寸信息的组件 (一文件一 Composable)
 */
@Composable
fun MentionHoverAssetPreview(
    item: MentionItem,
    modifier: Modifier = Modifier
) {
    val payload = item.payload as? MentionPayload.AssetPayload
    val rawPath = payload?.filePath ?: item.previewImagePath.orEmpty()

    var bitmap by remember(rawPath) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(rawPath) { mutableStateOf(true) }

    LaunchedEffect(rawPath) {
        if (rawPath.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(rawPath) ?: try {
                TemplateFile(rawPath).readBytes()
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
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.mention_preview_unavailable),
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
                    if (!item.subtitle.isNullOrBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (payload != null && payload.width > 0 && payload.height > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${payload.width} × ${payload.height} px",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

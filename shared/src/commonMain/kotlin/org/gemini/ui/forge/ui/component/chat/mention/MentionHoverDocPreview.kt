package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.mention_preview_doc_empty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * 悬停预览浮层中渲染文档（DOC）首段文本摘录与文件信息的组件 (一文件一 Composable)
 */
@Composable
fun MentionHoverDocPreview(
    doc: MentionPayload.DocPayload,
    modifier: Modifier = Modifier
) {
    var textExcerpt by remember(doc.absolutePath) { mutableStateOf<String?>(null) }
    var isLoading by remember(doc.absolutePath) { mutableStateOf(true) }

    LaunchedEffect(doc.absolutePath) {
        isLoading = true
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(doc.absolutePath)
            if (bytes != null && bytes.isNotEmpty()) {
                val maxBytes = minOf(bytes.size, 8192)
                val rawStr = try {
                    bytes.sliceArray(0 until maxBytes).decodeToString()
                } catch (_: Exception) {
                    null
                }
                textExcerpt = rawStr?.trim()?.take(400)
            } else {
                textExcerpt = null
            }
            isLoading = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. 文档正文摘录视口
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
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                when {
                    isLoading -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    !textExcerpt.isNullOrBlank() -> {
                        Text(
                            text = textExcerpt!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    else -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(Res.string.mention_preview_doc_empty),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 下方文件信息卡片
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
                        text = doc.fileName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = doc.absolutePath,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "DOC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

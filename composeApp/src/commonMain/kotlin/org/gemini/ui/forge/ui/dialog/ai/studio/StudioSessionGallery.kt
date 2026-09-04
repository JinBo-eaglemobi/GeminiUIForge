package org.gemini.ui.forge.ui.dialog.ai.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室右侧：当前会话历史生成资产画廊抽屉
 */
@Composable
fun StudioSessionGallery(
    generatedImages: List<String>,
    onImageClick: (String) -> Unit,
    onApplyImage: (String) -> Unit,
    onVariantImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val applyTip = stringResource(Res.string.ai_studio_btn_apply)
    val variantTip = stringResource(Res.string.ai_studio_btn_variant)

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            // 顶栏标题
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(spacing.extraSmall))
                Text(
                    text = "本会话生成产物 (${generatedImages.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (generatedImages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无生成产物",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    itemsIndexed(generatedImages) { index, imgUri ->
                        Surface(
                            shape = AppShapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(AppShapes.small)
                                        .background(Color.Black.copy(alpha = 0.05f))
                                        .clickable { onImageClick(imgUri) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = imgUri,
                                        contentDescription = "Historical Asset $index",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    // 序号微标
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = AppShapes.extraSmall,
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                // 操作按钮栏
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onVariantImage(imgUri) },
                                        modifier = Modifier.weight(1f).height(24.dp).tip(variantTip),
                                        shape = AppShapes.extraSmall,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("微调", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = { onApplyImage(imgUri) },
                                        modifier = Modifier.weight(1f).height(24.dp).tip(applyTip),
                                        shape = AppShapes.extraSmall,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("应用", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

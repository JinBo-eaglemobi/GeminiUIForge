package org.gemini.ui.forge.ui.dialog.asset

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.getImageSize
import kotlin.math.abs

/**
 * 资产选择对话框（支持本地去背景、参考图切片智能过滤与打开文件夹）。
 */
@Composable
fun AssetSelectionDialog(
    title: String,
    candidates: List<TemplateFile>,
    initialSelectedUri: TemplateFile? = null,
    targetWidth: Float = 0f,
    targetHeight: Float = 0f,
    isProcessing: Boolean = false,
    baseDirectoryPath: String? = null,
    onImageSelected: (TemplateFile) -> Unit,
    onCropRequested: (TemplateFile) -> Unit = {},
    onDeleteImages: (List<TemplateFile>) -> Unit,
    onClearAll: () -> Unit,
    onBatchRemoveBg: (List<TemplateFile>) -> Unit = {},
    onDismiss: () -> Unit
) {
    // 基础状态
    var tempSelectedUri by remember(initialSelectedUri) { mutableStateOf(initialSelectedUri) }
    var showCropSliceFilter by remember { mutableStateOf(false) } // 默认不显示参考切片

    val spacing = LocalAppSpacing.current
    val targetRatio = if (targetHeight > 0) targetWidth / targetHeight else 1f

    // 智能过滤：默认隐藏 ref_crop_ 开头的辅助参考切片
    val filteredCandidates = remember(candidates, showCropSliceFilter) {
        if (showCropSliceFilter) {
            candidates
        } else {
            candidates.filterNot { file ->
                val name = file.relativePath.substringAfterLast("/").substringAfterLast("\\")
                name.startsWith("ref_crop_", ignoreCase = true) || name.startsWith("ref_", ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 1. 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(spacing.small))
                            // 显示参考切片过滤切换开关
                            FilterChip(
                                selected = showCropSliceFilter,
                                onClick = { showCropSliceFilter = !showCropSliceFilter },
                                label = { Text("显示参考切片", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(12.dp)) },
                                modifier = Modifier.height(26.dp).tip("切换是否展示系统裁剪生成的参考底图切片")
                            )
                        }
                        if (targetWidth > 0 && targetHeight > 0) {
                            Text(
                                text = "目标尺寸: ${targetWidth.toInt()}x${targetHeight.toInt()} (比例: ${((targetRatio * 100).toInt() / 100f)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.tip("关闭")
                    ) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. 候选列表网格
                if (filteredCandidates.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (candidates.isNotEmpty() && !showCropSliceFilter) "已过滤隐藏参考切片，暂无正式生成资产 (可点击上方切换)" else "暂无历史生成资产",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCandidates) { file ->
                            val isSelected = tempSelectedUri == file
                            val isPng = file.relativePath.endsWith(".png", ignoreCase = true)
                            val isRef = file.relativePath.contains("ref_", ignoreCase = true)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(AppShapes.medium)
                                    .clickable { tempSelectedUri = file }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = AppShapes.medium
                                    ),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = file.getAbsolutePath(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )

                                    // 选中对勾
                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = AppShapes.extraSmall,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        ) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    // 底部格式标签
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = AppShapes.extraSmall,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    ) {
                                        Text(
                                            text = if (isRef) "REF" else if (isPng) "PNG" else "JPG",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isPng) Color(0xFF69F0AE) else Color.White,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        // 本地去背景按钮
                        val selectedIsJpg = tempSelectedUri?.let { !it.relativePath.endsWith(".png", ignoreCase = true) } == true
                        if (selectedIsJpg) {
                            Button(
                                onClick = { tempSelectedUri?.let { onBatchRemoveBg(listOf(it)) } },
                                enabled = !isProcessing && tempSelectedUri != null,
                                shape = AppShapes.small,
                                modifier = Modifier.height(36.dp).tip("通过本地 AI 模型将该图片背景去除，生成透明 PNG")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (isProcessing) "去背中..." else "本地去除背景")
                            }
                        }

                        // 在资源管理器中显示
                        OutlinedButton(
                            onClick = { tempSelectedUri?.let { getPlatform().openInFileExplorer(it.getAbsolutePath()) } },
                            enabled = tempSelectedUri != null,
                            shape = AppShapes.small,
                            modifier = Modifier.height(36.dp).tip("在本地操作系统资源管理器中定位并选中此文件")
                        ) {
                            Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("在文件夹中显示")
                        }

                        // 删除选中
                        TextButton(
                            onClick = { tempSelectedUri?.let { onDeleteImages(listOf(it)); tempSelectedUri = null } },
                            enabled = tempSelectedUri != null,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.small,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除选中")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        TextButton(onClick = onClearAll, enabled = candidates.isNotEmpty() && !isProcessing) {
                            Text("清空全部", color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            onClick = { tempSelectedUri?.let { onImageSelected(it); onDismiss() } },
                            enabled = tempSelectedUri != null && !isProcessing,
                            shape = AppShapes.small,
                            modifier = Modifier.height(36.dp).tip("将选中的图片应用到当前模块")
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("应用选择")
                        }
                    }
                }
            }
        }
    }
}

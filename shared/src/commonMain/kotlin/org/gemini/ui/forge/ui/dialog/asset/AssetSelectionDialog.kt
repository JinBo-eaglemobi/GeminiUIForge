package org.gemini.ui.forge.ui.dialog.asset

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.getImageSize

/**
 * 资产选择对话框（Bento 美学卡片版：显著模型徽章、真实尺寸与体积、本地去背与目录定位）。
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
    var showCropSliceFilter by remember { mutableStateOf(false) } // 默认隐藏辅助参考切片
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteSingleConfirmDialog by remember { mutableStateOf(false) }

    val spacing = LocalAppSpacing.current
    val targetRatio = if (targetHeight > 0) targetWidth / targetHeight else 1f

    // 智能过滤：仅默认隐藏 crop_ref_ 开头的辅助参考切片，所有 AI 渲染图及自身切割图默认全量展示
    val filteredCandidates = remember(candidates, showCropSliceFilter) {
        if (showCropSliceFilter) {
            candidates
        } else {
            candidates.filterNot { file ->
                val name = file.relativePath.substringAfterLast("/").substringAfterLast("\\")
                name.startsWith("crop_ref_", ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 1. 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
                                modifier = Modifier.height(28.dp).tip("切换是否展示系统裁剪生成的辅助参考底图切片")
                            )
                        }
                        if (targetWidth > 0 && targetHeight > 0) {
                            Text(
                                text = "目标模块尺寸: ${targetWidth.toInt()}×${targetHeight.toInt()} px (比例: ${((targetRatio * 100).toInt() / 100f)})",
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

                // 2. 候选列表网格 (Bento 资产卡片)
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
                        columns = GridCells.Adaptive(175.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCandidates, key = { it.relativePath }) { file ->
                            val isSelected = tempSelectedUri == file
                            AssetBentoCard(
                                file = file,
                                isSelected = isSelected,
                                onClick = { tempSelectedUri = file }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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

                        // 在资源管理器中显示（未选中时直接打开资产目录本身，选中时高亮定位具体文件）
                        val hasFiles = candidates.isNotEmpty()
                        OutlinedButton(
                            onClick = {
                                if (tempSelectedUri != null) {
                                    getPlatform().openInFileExplorer(tempSelectedUri!!.getAbsolutePath())
                                } else {
                                    val firstFileAbs = candidates.firstOrNull()?.getAbsolutePath()
                                    if (!firstFileAbs.isNullOrBlank()) {
                                        val parentDir = firstFileAbs.replace('\\', '/').substringBeforeLast('/')
                                        getPlatform().openInFileExplorer(parentDir)
                                    }
                                }
                            },
                            enabled = hasFiles,
                            shape = AppShapes.small,
                            modifier = Modifier.height(36.dp).tip(
                                if (tempSelectedUri != null) "在本地操作系统资源管理器中定位并高亮选中此文件"
                                else "在本地操作系统资源管理器中直接打开该模块的资产文件夹"
                            )
                        ) {
                            Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("在文件夹中显示")
                        }

                        // 删除选中（强制弹出二次确认）
                        TextButton(
                            onClick = { showDeleteSingleConfirmDialog = true },
                            enabled = tempSelectedUri != null && !isProcessing,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.small,
                            modifier = Modifier.height(36.dp).tip("从本地物理磁盘永久删除该选中的图片")
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
                        // 清空全部（强制弹出二次确认）
                        TextButton(
                            onClick = { showClearAllConfirmDialog = true },
                            enabled = candidates.isNotEmpty() && !isProcessing,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.tip("清空该模块在磁盘上的所有历史图片")
                        ) {
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

        // 1. 删除单个选中图片的二次确认弹窗
        if (showDeleteSingleConfirmDialog && tempSelectedUri != null) {
            val fileName = tempSelectedUri?.relativePath?.substringAfterLast("/")?.substringAfterLast("\\") ?: "此图片"
            AppConfirmDialog(
                title = "确认删除资产",
                message = "确定要从本地磁盘永久删除文件「$fileName」吗？此操作无法撤销！",
                confirmText = "确认删除",
                isDestructive = true,
                onConfirm = {
                    tempSelectedUri?.let { onDeleteImages(listOf(it)) }
                    tempSelectedUri = null
                    showDeleteSingleConfirmDialog = false
                },
                onDismiss = { showDeleteSingleConfirmDialog = false }
            )
        }

        // 2. 清空全部历史资产的二次确认弹窗（破坏性操作红线）
        if (showClearAllConfirmDialog) {
            AppConfirmDialog(
                title = "⚠️ 清空全部历史资产警告",
                message = "确定要清空该模块的所有历史生成资产吗？\n当前列表中的所有图片文件将从本地物理磁盘永久删除，无法恢复！",
                confirmText = "确认永久清空",
                isDestructive = true,
                onConfirm = {
                    onClearAll()
                    tempSelectedUri = null
                    showClearAllConfirmDialog = false
                    onDismiss()
                },
                onDismiss = { showClearAllConfirmDialog = false }
            )
        }
    }
}

/**
 * 资产 Bento 卡片：显著模型来源徽章、物理分辨率、文件体积与选中指示
 */
@Composable
private fun AssetBentoCard(
    file: TemplateFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val fileName = remember(file.relativePath) {
        file.relativePath.substringAfterLast("/").substringAfterLast("\\")
    }
    val isPng = file.relativePath.endsWith(".png", ignoreCase = true)

    // 读取真实物理尺寸与体积
    val metaState = produceState(initialValue = "" to "", file.relativePath) {
        try {
            val absPath = file.getAbsolutePath()
            val dim = getImageSize(absPath)
            val bytes = file.readBytes()
            val dimStr = if (dim != null && dim.first > 0) "${dim.first}×${dim.second}" else ""
            val sizeStr = if (bytes != null) "${(bytes.size / 1024f * 10).toInt() / 10f} KB" else ""
            value = dimStr to sizeStr
        } catch (e: Exception) {
            value = "" to ""
        }
    }
    val (dimText, sizeText) = metaState.value
    val modelBadge = getModelSourceBadge(fileName)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .clickable(onClick = onClick)
            .tip("名称: $fileName\n路径: ${file.getAbsolutePath()}"),
        shape = AppShapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // 1. 上部高清图像预览区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(AppShapes.small)
                    .background(Color.Black.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file.getAbsolutePath(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )

                // 显著模型来源徽章（左上角）
                Surface(
                    color = modelBadge.bgColor,
                    shape = AppShapes.extraSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                ) {
                    Text(
                        text = modelBadge.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = modelBadge.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // 选中对勾状态（右上角）
                if (isSelected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = AppShapes.extraSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 2. 底部原生信息区（纵向 3 行排布，分辨率独占一行，彻底杜绝截断）
            // 第 1 行：格式胶囊标签与文件体积
            Row(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPng) "PNG" else "JPG",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isPng) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp
                )
                if (sizeText.isNotBlank()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // 第 2 行：物理分辨率独占整行（核心视觉，100% 完整显示无截断）
            Text(
                text = if (dimText.isNotBlank()) "$dimText px" else "分辨率未知",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().tip("物理分辨率: ${if (dimText.isNotBlank()) "$dimText 像素" else "未知"}")
            )

            Spacer(Modifier.height(3.dp))

            // 第 3 行：文件名截短展示 (固定 1 行，带 Tooltip)
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().tip("文件名: $fileName\n路径: ${file.getAbsolutePath()}")
            )
        }
    }
}

/** 模型来源徽章数据类 */
private data class ModelSourceBadge(val label: String, val bgColor: Color, val textColor: Color)

private fun getModelSourceBadge(fileName: String): ModelSourceBadge {
    val lower = fileName.lowercase()
    return when {
        lower.startsWith("chat_gen_") || lower.startsWith("gen_") -> ModelSourceBadge(
            label = "Nano Banana",
            bgColor = Color(0xFF1B5E20),
            textColor = Color(0xFFA5D6A7)
        )
        lower.startsWith("nobg_") -> ModelSourceBadge(
            label = "本地去背",
            bgColor = Color(0xFF4A148C),
            textColor = Color(0xFFE1BEE7)
        )
        lower.startsWith("baked_") -> ModelSourceBadge(
            label = "烘焙切图",
            bgColor = Color(0xFFE65100),
            textColor = Color(0xFFFFE0B2)
        )
        lower.startsWith("crop_ref_") || lower.startsWith("ref_") -> ModelSourceBadge(
            label = "参考切片",
            bgColor = Color(0xFF37474F),
            textColor = Color(0xFFCFD8DC)
        )
        else -> ModelSourceBadge(
            label = "独立资产",
            bgColor = Color(0xFF0D47A1),
            textColor = Color(0xFFBBDEFB)
        )
    }
}

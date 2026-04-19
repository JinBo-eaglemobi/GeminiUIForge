package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import androidx.compose.foundation.combinedClickable
import org.gemini.ui.forge.utils.getImageSize
import kotlin.math.abs

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed

/**
 * 资源选择与管理弹窗 (弹窗 A)
 */
@Composable
fun AssetSelectionDialog(
    title: String,
    candidates: List<String>,
    initialSelectedUri: String? = null,
    targetWidth: Float = 0f,
    targetHeight: Float = 0f,
    isProcessing: Boolean = false, // 新增：正在处理的指示
    onImageSelected: (String) -> Unit,
    onCropRequested: (String) -> Unit = {}, // 新增：请求裁剪
    onDeleteImages: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onBatchRemoveBg: (List<String>) -> Unit = {}, // 新增：批量抠图
    onDismiss: () -> Unit
) {
    // 基础状态
    var tempSelectedUri by remember { mutableStateOf(initialSelectedUri) }
    
    // 目标比例
    val targetRatio = if (targetHeight > 0) targetWidth / targetHeight else 1f

    // 辅助：获取带尺寸信息的候选列表并排序
    var sortedCandidates by remember { mutableStateOf<List<Pair<String, Pair<Int, Int>?>>>(emptyList()) }
    LaunchedEffect(candidates) {
        val withSize = candidates.map { it to getImageSize(it) }
        sortedCandidates = withSize.sortedBy { (_, size) ->
            if (size == null) 2f else {
                val ratio = size.first.toFloat() / size.second
                abs(ratio - targetRatio)
            }
        }
    }

    // 多选管理状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val multiSelectedUris = remember { mutableStateListOf<String>() }
    
    // Shift 按键状态跟踪
    var isShiftPressed by remember { mutableStateOf(false) }

    // 辅助函数：执行删除
    fun executeDeletion(uris: List<String>) {
        onDeleteImages(uris)
        if (tempSelectedUri in uris) {
            tempSelectedUri = null
        }
        multiSelectedUris.removeAll(uris)
        if (multiSelectedUris.isEmpty()) isMultiSelectMode = false
    }

    // 判断是否全为 JPG
    val isAllSelectedJpg = remember(multiSelectedUris.size) {
        multiSelectedUris.isNotEmpty() && multiSelectedUris.all { 
            it.contains(".jpg", ignoreCase = true) || it.contains(".jpeg", ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            isShiftPressed = event.keyboardModifiers.isShiftPressed
                        }
                    }
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isMultiSelectMode) "批量管理 (${multiSelectedUris.size})" else title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (!isMultiSelectMode && targetWidth > 0) {
                            Text(
                                "目标尺寸: ${targetWidth.toInt()}x${targetHeight.toInt()} (比例: ${((targetRatio * 100).toInt() / 100f)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isMultiSelectMode) {
                        IconButton(onClick = { 
                            multiSelectedUris.clear()
                            multiSelectedUris.addAll(candidates) 
                        }) {
                            Icon(Icons.Default.SelectAll, "全选")
                        }
                        IconButton(onClick = { multiSelectedUris.clear() }) {
                            Icon(Icons.Default.Deselect, "全不选")
                        }
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 8.dp))
                        IconButton(
                            onClick = { isMultiSelectMode = false; multiSelectedUris.clear() }
                        ) {
                            Icon(Icons.Default.Close, "退出多选")
                        }
                    } else {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 内容网格
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (candidates.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无可用资源", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedCandidates) { (uri, size) ->
                                val isChosenInMulti = multiSelectedUris.contains(uri)
                                val isSelectedInSingle = !isMultiSelectMode && tempSelectedUri == uri
                                
                                val imageBitmapState = produceState<ImageBitmap?>(null, uri) {
                                    value = uri.decodeBase64ToBitmap()
                                }
                                val bitmap = imageBitmapState.value

                                // 比例匹配检查
                                val isAdapted = if (size == null) false else {
                                    val ratio = size.first.toFloat() / size.second
                                    abs(ratio - targetRatio) < 0.05
                                }

                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            onClick = {
                                                if (isShiftPressed && !isMultiSelectMode) {
                                                    isMultiSelectMode = true
                                                    multiSelectedUris.add(uri)
                                                } else if (isMultiSelectMode) {
                                                    if (isChosenInMulti) multiSelectedUris.remove(uri) else multiSelectedUris.add(uri)
                                                } else {
                                                    tempSelectedUri = if (isSelectedInSingle) null else uri
                                                }
                                            },
                                            onDoubleClick = {
                                                if (!isMultiSelectMode) {
                                                    tempSelectedUri = uri
                                                    val isAdapted = if (size == null) false else {
                                                        val ratio = size.first.toFloat() / size.second
                                                        abs(ratio - targetRatio) < 0.05
                                                    }

                                                    if (isAdapted && size?.first == targetWidth.toInt() && size.second == targetHeight.toInt()) {
                                                        onImageSelected(uri)
                                                        onDismiss()
                                                    } else {
                                                        onCropRequested(uri)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (!isMultiSelectMode) {
                                                    isMultiSelectMode = true
                                                    multiSelectedUris.add(uri)
                                                }
                                            }
                                        )
                                        .border(
                                            width = if (isChosenInMulti || isSelectedInSingle) 3.dp else if (isSelectedInSingle) 1.dp else 0.dp,
                                            color = when {
                                                isChosenInMulti -> MaterialTheme.colorScheme.error
                                                isSelectedInSingle -> MaterialTheme.colorScheme.primary
                                                else -> Color.Transparent
                                            },
                                            shape = AppShapes.medium
                                        ),
                                    shape = AppShapes.medium,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isChosenInMulti || isSelectedInSingle) 8.dp else 2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit,
                                                alpha = if (isChosenInMulti || isSelectedInSingle || !isMultiSelectMode) 1.0f else 0.5f
                                            )
                                            
                                            // 右下角尺寸信息叠加
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                                shape = RoundedCornerShape(2.dp)
                                            ) {
                                                val format = if (uri.contains(".png", ignoreCase = true)) "PNG" else "JPG"
                                                Text(
                                                    text = if (size != null) "${size.first}x${size.second} ($format)" else "未知尺寸",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    color = if (isAdapted) Color.Green else Color.White
                                                )
                                            }

                                            if (uri == initialSelectedUri) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("使用中", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp), color = Color.White)
                                                }
                                            }

                                            if (isChosenInMulti || isSelectedInSingle) {
                                                Icon(
                                                    imageVector = if (isMultiSelectMode) Icons.Default.DeleteSweep else Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (isMultiSelectMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
                                                )
                                            }
                                        } else {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底栏
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isMultiSelectMode) {
                        Button(
                            onClick = { executeDeletion(multiSelectedUris.toList()) },
                            enabled = multiSelectedUris.isNotEmpty() && !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.medium
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("删除选中 (${multiSelectedUris.size})")
                        }
                        
                        if (isAllSelectedJpg) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val urisToProcess = multiSelectedUris.toList()
                                    isMultiSelectMode = false
                                    multiSelectedUris.clear()
                                    // 不关闭弹窗，等待进度完成
                                    onBatchRemoveBg(urisToProcess)
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = AppShapes.medium
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isProcessing) "处理中..." else "本地去背景")
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { isMultiSelectMode = false; multiSelectedUris.clear() }, enabled = !isProcessing, shape = AppShapes.medium) {
                            Text("退出管理")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tempSelectedUri != null) {
                                TextButton(
                                    onClick = { tempSelectedUri = null },
                                    enabled = !isProcessing,
                                    shape = AppShapes.medium
                                ) {
                                    Text("取消选中")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = { executeDeletion(listOf(tempSelectedUri!!)) },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                    shape = AppShapes.medium,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("立即删除", style = MaterialTheme.typography.labelLarge)
                                }
                                
                                val isJpg = tempSelectedUri?.let { it.contains(".jpg", ignoreCase = true) || it.contains(".jpeg", ignoreCase = true) } == true
                                if (isJpg) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val uriToProcess = tempSelectedUri!!
                                            // 不关闭弹窗，等待进度完成
                                            onBatchRemoveBg(listOf(uriToProcess))
                                        },
                                        enabled = !isProcessing,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = AppShapes.medium,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        if (isProcessing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isProcessing) "处理中..." else "本地去背景", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                onClearAll()
                                onDismiss()
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.medium
                        ) {
                            Text("清理全部候选")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = onDismiss, enabled = !isProcessing, shape = AppShapes.medium) {
                            Text("取消")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val uri = tempSelectedUri ?: return@Button
                                val size = sortedCandidates.find { it.first == uri }?.second
                                val isAdapted = if (size == null) false else {
                                    val ratio = size.first.toFloat() / size.second
                                    abs(ratio - targetRatio) < 0.05
                                }

                                if (isAdapted && size?.first == targetWidth.toInt() && size.second == targetHeight.toInt()) {
                                    onImageSelected(uri)
                                    onDismiss()
                                } else {
                                    // 需要裁剪适配
                                    onCropRequested(uri)
                                }
                            },
                            enabled = tempSelectedUri != null && !isProcessing,
                            shape = AppShapes.medium
                        ) {
                            Text("应用选择")
                        }
                    }
                }            }
        }
    }
}

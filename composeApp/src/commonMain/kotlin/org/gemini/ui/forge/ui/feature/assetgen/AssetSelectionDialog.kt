package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/**
 * 资源选择与管理弹窗 (弹窗 A)
 */
@Composable
fun AssetSelectionDialog(
    title: String,
    candidates: List<String>,
    initialSelectedUri: String? = null,
    onImageSelected: (String) -> Unit,
    onDeleteImages: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    // 基础状态
    var tempSelectedUri by remember { mutableStateOf(initialSelectedUri) }
    
    // 多选管理状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val multiSelectedUris = remember { mutableStateListOf<String>() }

    // 辅助函数：执行删除
    fun executeDeletion(uris: List<String>) {
        onDeleteImages(uris)
        if (tempSelectedUri in uris) {
            tempSelectedUri = null
        }
        multiSelectedUris.removeAll(uris)
        if (multiSelectedUris.isEmpty()) isMultiSelectMode = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
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
                    Text(
                        text = if (isMultiSelectMode) "批量管理 (${multiSelectedUris.size})" else title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )

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
                            items(candidates) { uri ->
                                val isChosenInMulti = multiSelectedUris.contains(uri)
                                val isSelectedInSingle = !isMultiSelectMode && tempSelectedUri == uri
                                
                                val imageBitmapState = produceState<ImageBitmap?>(null, uri) {
                                    value = uri.decodeBase64ToBitmap()
                                }
                                val bitmap = imageBitmapState.value

                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            onClick = {
                                                if (isMultiSelectMode) {
                                                    if (isChosenInMulti) multiSelectedUris.remove(uri) else multiSelectedUris.add(uri)
                                                } else {
                                                    tempSelectedUri = if (isSelectedInSingle) null else uri
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
                                            width = if (isChosenInMulti || isSelectedInSingle) 3.dp else 0.dp,
                                            color = if (isChosenInMulti) MaterialTheme.colorScheme.error else if (isSelectedInSingle) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                                contentScale = ContentScale.Crop,
                                                alpha = if (isChosenInMulti || isSelectedInSingle || !isMultiSelectMode) 1.0f else 0.5f
                                            )
                                            
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
                                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp)
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
                            enabled = multiSelectedUris.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.medium
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("删除选中 (${multiSelectedUris.size})")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { isMultiSelectMode = false; multiSelectedUris.clear() }, shape = AppShapes.medium) {
                            Text("退出管理")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tempSelectedUri != null) {
                                TextButton(
                                    onClick = { tempSelectedUri = null },
                                    shape = AppShapes.medium
                                ) {
                                    Text("取消选中")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = { executeDeletion(listOf(tempSelectedUri!!)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                    shape = AppShapes.medium,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("立即删除", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                onClearAll()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = AppShapes.medium
                        ) {
                            Text("清理全部候选")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = onDismiss, shape = AppShapes.medium) {
                            Text("取消")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                onImageSelected(tempSelectedUri ?: "")
                                onDismiss()
                            },
                            shape = AppShapes.medium
                        ) {
                            Text("应用选择")
                        }
                    }
                }            }
        }
    }
}

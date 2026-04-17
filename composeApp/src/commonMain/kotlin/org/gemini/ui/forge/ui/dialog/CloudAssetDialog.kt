package org.gemini.ui.forge.ui.dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import org.gemini.ui.forge.service.CloudAssetManager

import androidx.compose.material.icons.filled.Add
import org.gemini.ui.forge.utils.rememberImagePicker

import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ui.theme.AppShapes

@Composable
fun CloudAssetDialog(
    cloudAssetManager: CloudAssetManager,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val assets by cloudAssetManager.assets.collectAsState()
    var isSyncing by remember { mutableStateOf(false) }
    
    // 选中的文件 ID 集合
    val selectedFileNames = remember { mutableStateListOf<String>() }
    
    // 正在处理中的文件（删除或上传确认中）
    val processingIds = remember { mutableStateListOf<String>() }
    
    // 正在上传的任务列表：Map<FileName, Pair<Progress, Status>>
    val uploadingTasks = remember { mutableStateMapOf<String, Pair<Float, String>>() }

    val uploadPrepStr = stringResource(Res.string.cloud_assets_uploading_prepare)
    
    // 图片选择器逻辑：并发上传优化
    val imagePicker = rememberImagePicker { uris ->
        coroutineScope.launch {
            // 并发启动所有上传任务
            coroutineScope {
                uris.map { uri ->
                    async {
                        val displayName = uri.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "unnamed_image" }
                        try {
                            val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(uri)
                            if (bytes != null) {
                                val mimeType = org.gemini.ui.forge.utils.getMimeType(uri)
                                // 初始化进度
                                uploadingTasks[displayName] = 0f to uploadPrepStr
                                
                                cloudAssetManager.getOrUploadFile(displayName, bytes, mimeType) { progress, status ->
                                    uploadingTasks[displayName] = progress to status
                                }
                            }
                        } catch (e: Exception) {
                            val errorMsg = org.jetbrains.compose.resources.getString(Res.string.cloud_assets_upload_failed, e.message ?: "Unknown")
                            uploadingTasks[displayName] = 0f to errorMsg
                        } finally {
                            // 上传成功或彻底失败后停留 1.5 秒再从“上传列表”移除，让用户看一眼结果
                            delay(1500L)
                            uploadingTasks.remove(displayName)
                        }
                    }
                }.awaitAll()
            }
            
            cloudAssetManager.syncFiles()
        }
    }

    // 弹窗打开时自动同步一次
    LaunchedEffect(Unit) {
        isSyncing = true
        cloudAssetManager.syncFiles()
        isSyncing = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.cloud_assets_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedFileNames.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.cloud_assets_selected, selectedFileNames.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 批量删除按钮
                        if (selectedFileNames.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val toDelete = selectedFileNames.toList()
                                        selectedFileNames.clear()
                                        processingIds.addAll(toDelete)
                                        
                                        // 并发删除
                                        coroutineScope {
                                            toDelete.map { name ->
                                                async { cloudAssetManager.deleteFile(name) }
                                            }.awaitAll()
                                        }
                                        
                                        processingIds.removeAll(toDelete)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Batch Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        // 上传按钮
                        IconButton(onClick = { imagePicker() }) {
                            Icon(Icons.Default.Add, contentDescription = "Upload", tint = MaterialTheme.colorScheme.primary)
                        }

                        // 刷新按钮
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isSyncing = true
                                    cloudAssetManager.syncFiles()
                                    isSyncing = false
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List
                if (assets.isEmpty() && uploadingTasks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.cloud_assets_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 先显示正在上传的任务
                        uploadingTasks.forEach { (name, info) ->
                            item(key = "upload_$name") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { info.first },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(info.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (assets.isNotEmpty()) {
                            item {
                                // 全选控制行
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        if (selectedFileNames.size == assets.size) {
                                            selectedFileNames.clear()
                                        } else {
                                            selectedFileNames.clear()
                                            selectedFileNames.addAll(assets.map { it.name })
                                        }
                                    }.padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedFileNames.size == assets.size && assets.isNotEmpty(),
                                        onCheckedChange = null
                                    )
                                    Text(stringResource(Res.string.cloud_assets_select_all), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }

                        // 2. 显示已有资产
                        items(assets, key = { it.name }) { asset ->
                            CloudAssetItem(
                                asset = asset,
                                isSelected = selectedFileNames.contains(asset.name),
                                isProcessing = processingIds.contains(asset.name),
                                onSelectToggle = {
                                    if (selectedFileNames.contains(asset.name)) {
                                        selectedFileNames.remove(asset.name)
                                    } else {
                                        selectedFileNames.add(asset.name)
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        processingIds.add(asset.name)
                                        cloudAssetManager.deleteFile(asset.name)
                                        processingIds.remove(asset.name)
                                        selectedFileNames.remove(asset.name)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        shape = AppShapes.medium
                    ) {
                        Text(stringResource(Res.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
fun CloudAssetItem(
    asset: org.gemini.ui.forge.model.api.gemini.file.GeminiFile,
    isSelected: Boolean,
    isProcessing: Boolean,
    onSelectToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isProcessing) { onSelectToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).alpha(if (isProcessing) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        enabled = !isProcessing
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = asset.displayName ?: stringResource(Res.string.asset_unnamed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ID: ${asset.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (asset.state) {
                                "ACTIVE" -> Color(0xFF4CAF50)
                                "PROCESSING" -> Color(0xFFFF9800)
                                "FAILED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = asset.state ?: "UNKNOWN",
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.cloud_assets_expire, org.gemini.ui.forge.formatIsoTime(asset.expirationTime)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                IconButton(onClick = onDelete, enabled = !isProcessing) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (isProcessing) Color.Gray else MaterialTheme.colorScheme.error)
                }
            }

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}


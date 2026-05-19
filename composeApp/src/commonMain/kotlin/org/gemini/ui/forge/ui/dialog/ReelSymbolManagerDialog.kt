package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.getCurrentTimeMillis

@Composable
fun ReelSymbolManagerDialog(
    props: BlockProperties.ReelProperties,
    onDismiss: () -> Unit,
    onPropertiesChanged: (BlockProperties) -> Unit,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    state: ProjectWorkspaceState
) {
    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<UIBlock?>(null) }

    var newItemPromptZh by remember { mutableStateOf("") }
    var newItemPromptEn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.9f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("转轴符号集管理器")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { 
                    editingItem = null
                    newItemPromptZh = ""
                    newItemPromptEn = ""
                    showAddItemDialog = true 
                }) {
                    Icon(Icons.Default.AddCircle, "添加", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            if (props.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("暂无符号元素，请点击右上角添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(props.items.size) { index ->
                        val item = props.items[index]
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = AppShapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 元素预览图
                                Box(Modifier.size(44.dp).clip(AppShapes.extraSmall).background(Color.Black.copy(alpha = 0.05f))) {
                                    if (item.currentImageUri != null) {
                                        AsyncImage(
                                            model = item.currentImageUri.getAbsolutePath(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(Icons.Default.ImageNotSupported, null, Modifier.size(20.dp).align(Alignment.Center), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(item.userPromptZh.ifBlank { item.id }, style = MaterialTheme.typography.labelMedium)
                                    Text(item.userPromptEn.ifBlank { "No English Prompt" }, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.outline)
                                }

                                // 编辑按钮
                                IconButton(onClick = {
                                    editingItem = item
                                    newItemPromptZh = item.userPromptZh
                                    newItemPromptEn = item.userPromptEn
                                    showAddItemDialog = true
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }

                                Spacer(Modifier.width(4.dp))

                                // 生成按钮
                                IconButton(onClick = {
                                    viewModel.assetManager.selectReelItem(item.id)
                                    viewModel.assetGen.onRequestGeneration(apiKey, item.fullPrompt)
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                }

                                Spacer(Modifier.width(4.dp))

                                // 删除按钮
                                IconButton(onClick = {
                                    val newItems = props.items.toMutableList().apply { removeAt(index) }
                                    onPropertiesChanged(props.copy(items = newItems))
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("完成") }
        }
    )

    // 新增/编辑元素对话框 (在管理器之上弹出)
    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text(if (editingItem == null) "新增符号元素 (SYMBOL)" else "编辑符号元素") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectAllOutlinedTextField(
                        value = newItemPromptZh,
                        onValueChange = { newItemPromptZh = it },
                        label = { Text("中文描述/名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    SelectAllOutlinedTextField(
                        value = newItemPromptEn,
                        onValueChange = { newItemPromptEn = it },
                        label = { Text("英文 Prompt (生图核心)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetItem = if (editingItem != null) {
                        editingItem!!.copy(
                            userPromptZh = newItemPromptZh,
                            userPromptEn = newItemPromptEn
                        )
                    } else {
                        UIBlock(
                            id = "sym_${getCurrentTimeMillis()}",
                            type = UIBlockType.SYMBOL, // 强制设置为 SYMBOL
                            bounds = SerialRect(0f, 0f, 100f, 100f),
                            userPromptZh = newItemPromptZh,
                            userPromptEn = newItemPromptEn
                        )
                    }

                    val newItems = if (editingItem != null) {
                        props.items.map { if (it.id == editingItem!!.id) targetItem else it }
                    } else {
                        props.items + targetItem
                    }

                    onPropertiesChanged(props.copy(items = newItems))
                    showAddItemDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("取消") }
            }
        )
    }
}

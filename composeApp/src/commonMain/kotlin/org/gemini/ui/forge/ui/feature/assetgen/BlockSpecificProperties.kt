package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

@Composable
fun BlockSpecificProperties(
    blockType: UIBlockType,
    properties: BlockProperties?,
    apiKey: String,
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    onShowPressedHistory: () -> Unit = {},
    onShowDisabledHistory: () -> Unit = {},
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    when (blockType) {
        UIBlockType.BUTTON -> {
            val props = properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
            Text("按钮属性配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            SelectAllOutlinedTextField(
                value = props.text,
                onValueChange = { onPropertiesChanged(props.copy(text = it)) },
                label = { Text("按钮文案 (选填)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isGenerating
            )
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = props.isMultiState,
                    onCheckedChange = { onPropertiesChanged(props.copy(isMultiState = it)) },
                    enabled = !state.isGenerating
                )
                Spacer(Modifier.width(LocalAppSpacing.current.small))
                Text("启用多态资源 (点击/禁用)", style = MaterialTheme.typography.bodySmall)
            }
            
            if (props.isMultiState) {
                Spacer(Modifier.height(LocalAppSpacing.current.small))
                val hasBaseImage = state.selectedBlock?.currentImageUri != null
                
                if (hasBaseImage) {
                    Button(
                        onClick = { viewModel.openButtonGenDialog() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(LocalAppSpacing.current.medium))
                        Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                        Text("👉 准备多态生成 (编辑与预览)")
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Text("当前绑定的多态资源", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(LocalAppSpacing.current.extraSmall))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Pressed State Preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = AppShapes.small,
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (props.pressedUri != null) {
                                            AsyncImage(
                                                model = props.pressedUri.getAbsolutePath(),
                                                contentDescription = "Pressed State",
                                                modifier = Modifier.fillMaxSize().padding(LocalAppSpacing.current.extraSmall),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = onShowPressedHistory,
                                    modifier = Modifier.align(Alignment.TopEnd).size(LocalAppSpacing.current.large).padding(LocalAppSpacing.current.extraSmall)
                                ) {
                                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("点击态", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
                        }
                        
                        // Disabled State Preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = AppShapes.small,
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (props.disabledUri != null) {
                                            AsyncImage(
                                                model = props.disabledUri.getAbsolutePath(),
                                                contentDescription = "Disabled State",
                                                modifier = Modifier.fillMaxSize().padding(LocalAppSpacing.current.extraSmall),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = onShowDisabledHistory,
                                    modifier = Modifier.align(Alignment.TopEnd).size(LocalAppSpacing.current.large).padding(LocalAppSpacing.current.extraSmall)
                                ) {
                                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("禁用态", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = AppShapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = LocalAppSpacing.current.extraSmall)
                    ) {
                        Text(
                            text = "请先在下方生成或绑定一张普通状态的按钮图片作为基准参照图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(LocalAppSpacing.current.small)
                        )
                    }
                }
            }
        }
        UIBlockType.VIEW -> {
            val props = properties as? BlockProperties.ViewProperties ?: BlockProperties.ViewProperties()
            Text("视图配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            org.gemini.ui.forge.ui.component.ColorPickerField(
                label = "背景色 (Hex)",
                hexColor = props.backgroundColor,
                onColorChanged = { onPropertiesChanged(props.copy(backgroundColor = it)) }
            )
        }
        UIBlockType.TEXT -> {
            val props = properties as? BlockProperties.TextProperties ?: BlockProperties.TextProperties()
            Text("文本配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            SelectAllOutlinedTextField(
                value = props.text,
                onValueChange = { onPropertiesChanged(props.copy(text = it)) },
                label = { Text("文本内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            org.gemini.ui.forge.ui.component.TextStyleToolbar(
                isBold = props.isBold,
                onBoldChanged = { onPropertiesChanged(props.copy(isBold = it)) },
                isItalic = props.isItalic,
                onItalicChanged = { onPropertiesChanged(props.copy(isItalic = it)) },
                horizontalAlign = props.horizontalAlign,
                onHorizontalAlignChanged = { onPropertiesChanged(props.copy(horizontalAlign = it)) },
                verticalAlign = props.verticalAlign,
                onVerticalAlignChanged = { onPropertiesChanged(props.copy(verticalAlign = it)) }
            )
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                org.gemini.ui.forge.ui.component.ColorPickerField(
                    label = "文本颜色",
                    hexColor = props.textColor,
                    onColorChanged = { onPropertiesChanged(props.copy(textColor = it)) },
                    modifier = Modifier.weight(1.5f)
                )
                SelectAllOutlinedTextField(
                    value = props.textSize.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(textSize = it.toIntOrNull() ?: props.textSize)) },
                    label = { Text("字号") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                org.gemini.ui.forge.ui.component.ColorPickerField(
                    label = "描边颜色",
                    hexColor = props.strokeColor,
                    onColorChanged = { onPropertiesChanged(props.copy(strokeColor = it)) },
                    modifier = Modifier.weight(1.5f)
                )
                SelectAllOutlinedTextField(
                    value = props.strokeWidth.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(strokeWidth = it.toFloatOrNull() ?: props.strokeWidth)) },
                    label = { Text("描边宽度") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        UIBlockType.INPUT -> {
            val props = properties as? BlockProperties.InputProperties ?: BlockProperties.InputProperties()
            Text("输入框配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            SelectAllOutlinedTextField(
                value = props.hintText,
                onValueChange = { onPropertiesChanged(props.copy(hintText = it)) },
                label = { Text("默认提示文案 (Hint)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            org.gemini.ui.forge.ui.component.TextStyleToolbar(
                isBold = props.isBold,
                onBoldChanged = { onPropertiesChanged(props.copy(isBold = it)) },
                isItalic = props.isItalic,
                onItalicChanged = { onPropertiesChanged(props.copy(isItalic = it)) },
                horizontalAlign = props.horizontalAlign,
                onHorizontalAlignChanged = { onPropertiesChanged(props.copy(horizontalAlign = it)) },
                verticalAlign = props.verticalAlign,
                onVerticalAlignChanged = { onPropertiesChanged(props.copy(verticalAlign = it)) }
            )
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                org.gemini.ui.forge.ui.component.ColorPickerField(
                    label = "文本颜色",
                    hexColor = props.textColor,
                    onColorChanged = { onPropertiesChanged(props.copy(textColor = it)) },
                    modifier = Modifier.weight(1.5f)
                )
                SelectAllOutlinedTextField(
                    value = props.textSize.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(textSize = it.toIntOrNull() ?: props.textSize)) },
                    label = { Text("字号") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                org.gemini.ui.forge.ui.component.ColorPickerField(
                    label = "描边颜色",
                    hexColor = props.strokeColor,
                    onColorChanged = { onPropertiesChanged(props.copy(strokeColor = it)) },
                    modifier = Modifier.weight(1.5f)
                )
                SelectAllOutlinedTextField(
                    value = props.strokeWidth.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(strokeWidth = it.toFloatOrNull() ?: props.strokeWidth)) },
                    label = { Text("描边宽") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
            SelectAllOutlinedTextField(
                value = if (props.maxLength == -1) "" else props.maxLength.toString(),
                onValueChange = { 
                    val maxLen = if (it.isBlank()) -1 else it.toIntOrNull() ?: props.maxLength
                    onPropertiesChanged(props.copy(maxLength = maxLen)) 
                },
                label = { Text("最大输入长度 (-1表示不限)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        UIBlockType.IMAGE, UIBlockType.SYMBOL, UIBlockType.BACKGROUND, UIBlockType.LOADER -> {
            // 图片和基本块暂无特定的扩充属性，缩放等选项已移至顶层通用图片设置
        }
        UIBlockType.REEL -> {
            val props = properties as? BlockProperties.ReelProperties ?: BlockProperties.ReelProperties()
            var showAddItemDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var editingItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<org.gemini.ui.forge.model.ui.UIBlock?>(null) }
            
            var newItemName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var newItemPromptZh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var newItemPromptEn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

            Text("转轴核心配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(LocalAppSpacing.current.small))

            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                SelectAllOutlinedTextField(
                    value = props.rows.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(rows = it.toIntOrNull() ?: props.rows)) },
                    label = { Text("行数") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isGenerating
                )
                SelectAllOutlinedTextField(
                    value = props.columns.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(columns = it.toIntOrNull() ?: props.columns)) },
                    label = { Text("列数") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isGenerating
                )
            }

            Spacer(Modifier.height(LocalAppSpacing.current.small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = props.showBackground,
                    onCheckedChange = { onPropertiesChanged(props.copy(showBackground = it)) },
                    enabled = !state.isGenerating
                )
                Text("显示外框背景板", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = LocalAppSpacing.current.small).alpha(0.3f))

            // 元素管理部分
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("元素项集 (Symbols)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { 
                        newItemName = ""
                        newItemPromptZh = ""
                        newItemPromptEn = ""
                        editingItem = null
                        showAddItemDialog = true 
                    },
                    enabled = !state.isGenerating,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (props.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("暂无元素，请点击上方按钮添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    props.items.forEachIndexed { index, item ->
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
                                    newItemName = item.userPromptZh // 借用名称
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

            // 新增/编辑元素对话框
            if (showAddItemDialog) {
                AlertDialog(
                    onDismissRequest = { showAddItemDialog = false },
                    title = { Text(if (editingItem == null) "新增转轴元素" else "编辑转轴元素") },
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
                                org.gemini.ui.forge.model.ui.UIBlock(
                                    id = "item_${org.gemini.ui.forge.getCurrentTimeMillis()}",
                                    type = UIBlockType.SYMBOL,
                                    bounds = org.gemini.ui.forge.model.ui.SerialRect(0f, 0f, 100f, 100f),
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
        else -> {
            // 其他类型保持原样
        }
    }
}

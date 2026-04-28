package org.gemini.ui.forge.ui.feature.assetgen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.TemplateAssetGenState
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.TemplateAssetGenViewModel

@Composable
fun BlockSpecificProperties(
    blockType: UIBlockType,
    properties: BlockProperties?,
    apiKey: String,
    viewModel: TemplateAssetGenViewModel,
    state: TemplateAssetGenState,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    when (blockType) {
        UIBlockType.BUTTON -> {
            val props = properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
            Text("按钮多态配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = props.isMultiState,
                    onCheckedChange = { onPropertiesChanged(props.copy(isMultiState = it)) },
                    enabled = !state.isGenerating
                )
                Spacer(Modifier.width(8.dp))
                Text("启用多态资源 (点击/禁用)", style = MaterialTheme.typography.bodySmall)
            }
            
            if (props.isMultiState) {
                Spacer(Modifier.height(8.dp))
                val hasBaseImage = state.selectedBlock?.currentImageUri != null
                
                if (hasBaseImage) {
                    Button(
                        onClick = { viewModel.generateDerivedButtonStates(apiKey) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("👉 参照当前图片生成点击与禁用态")
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Text("衍生资源预览", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Pressed State Preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                shape = AppShapes.small,
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (props.pressedUri != null) {
                                        AsyncImage(
                                            model = props.pressedUri.getAbsolutePath(),
                                            contentDescription = "Pressed State",
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text("点击态", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        // Disabled State Preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                shape = AppShapes.small,
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (props.disabledUri != null) {
                                        AsyncImage(
                                            model = props.disabledUri.getAbsolutePath(),
                                            contentDescription = "Disabled State",
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text("禁用态", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = AppShapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "请先在下方生成或绑定一张普通状态的按钮图片作为基准参照图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
        UIBlockType.VIEW -> {
            val props = properties as? BlockProperties.ViewProperties ?: BlockProperties.ViewProperties()
            Text("视图配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = props.backgroundColor,
                onValueChange = { onPropertiesChanged(props.copy(backgroundColor = it)) },
                label = { Text("背景色 (例如: #FFFFFF)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        UIBlockType.TEXT -> {
            val props = properties as? BlockProperties.TextProperties ?: BlockProperties.TextProperties()
            Text("文本配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = props.text,
                onValueChange = { onPropertiesChanged(props.copy(text = it)) },
                label = { Text("文本内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = props.textColor,
                    onValueChange = { onPropertiesChanged(props.copy(textColor = it)) },
                    label = { Text("文本颜色") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = props.textSize.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(textSize = it.toIntOrNull() ?: props.textSize)) },
                    label = { Text("字号") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        UIBlockType.INPUT -> {
            val props = properties as? BlockProperties.InputProperties ?: BlockProperties.InputProperties()
            Text("输入框配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = props.hintText,
                onValueChange = { onPropertiesChanged(props.copy(hintText = it)) },
                label = { Text("默认提示文案 (Hint)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = props.textColor,
                    onValueChange = { onPropertiesChanged(props.copy(textColor = it)) },
                    label = { Text("文本颜色") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = props.textSize.toString(),
                    onValueChange = { onPropertiesChanged(props.copy(textSize = it.toIntOrNull() ?: props.textSize)) },
                    label = { Text("字号") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
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
        else -> {            // 其他类型保持原样
        }
    }
}

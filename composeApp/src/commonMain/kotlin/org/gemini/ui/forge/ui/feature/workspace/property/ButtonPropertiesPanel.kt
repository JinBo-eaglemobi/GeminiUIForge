package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * 按钮组件的专属属性面板
 */
@Composable
fun ButtonPropertiesPanel(
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    selectedBlock: UIBlock,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    val props = selectedBlock.properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
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
        val hasBaseImage = selectedBlock.currentImageUri != null
        
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
                            onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_pressed") },
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
                            onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_disabled") },
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

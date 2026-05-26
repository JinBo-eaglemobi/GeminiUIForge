package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * SPIN_BUTTON (旋转大按钮) 组件的专属属性面板
 */
@Composable
fun SpinButtonPropertiesPanel(
    viewModel: ProjectWorkspaceViewModel,
    selectedBlock: UIBlock
) {
    val props = selectedBlock.properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
    
    Text("旋转按钮属性配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    
    // 提示信息板
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        shape = AppShapes.small,
        modifier = Modifier.fillMaxWidth().padding(bottom = LocalAppSpacing.current.small)
    ) {
        Text(
            text = "旋转按钮共有 2 个状态图片：一个是默认状态图片，一个是被选择后显示的图片。您可以随时从历史记录中进行绑定和管理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(LocalAppSpacing.current.small)
        )
    }
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // 默认状态 (对应 spinUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = AppShapes.small,
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (props.spinUri != null) {
                            AsyncImage(
                                model = props.spinUri.getAbsolutePath(),
                                contentDescription = "Default State",
                                modifier = Modifier.fillMaxSize().padding(LocalAppSpacing.current.extraSmall),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(
                    onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_spin") },
                    modifier = Modifier.align(Alignment.TopEnd).size(LocalAppSpacing.current.large).padding(LocalAppSpacing.current.extraSmall)
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("默认状态 (默认图片)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
        }
        
        // 选择后状态 (对应 stopUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = AppShapes.small,
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (props.stopUri != null) {
                            AsyncImage(
                                model = props.stopUri.getAbsolutePath(),
                                contentDescription = "Selected State",
                                modifier = Modifier.fillMaxSize().padding(LocalAppSpacing.current.extraSmall),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("暂无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(
                    onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_stop") },
                    modifier = Modifier.align(Alignment.TopEnd).size(LocalAppSpacing.current.large).padding(LocalAppSpacing.current.extraSmall)
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("选择后状态 (被选中)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
        }
    }
}

package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * SPIN_BUTTON (旋转大按钮) 组件的专属属性面板。
 * 
 * 1. 属性面板展示两个状态的紧凑缩略图片，支持点击看大图。
 * 2. 底部提供“👉 配置并生图 (AI 绘制)”的跳转生图配置提示。
 */
@Composable
fun SpinButtonPropertiesPanel(
    viewModel: ProjectWorkspaceViewModel,
    selectedBlock: UIBlock
) {
    val props = selectedBlock.properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
    var bigImageToShow by remember { mutableStateOf<TemplateFile?>(null) }
    
    if (bigImageToShow != null) {
        Dialog(onDismissRequest = { bigImageToShow = null }) {
            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "查看高清大图",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = bigImageToShow!!.getAbsolutePath(),
                            contentDescription = "Big Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { bigImageToShow = null },
                        shape = AppShapes.small,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    Text("旋转按钮属性配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    
    // 提示信息板
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        shape = AppShapes.small,
        modifier = Modifier.fillMaxWidth().padding(bottom = LocalAppSpacing.current.small)
    ) {
        Text(
            text = "旋转按钮共有 2 个状态图片：一个是默认状态 (Spin)，一个是停止状态 (Stop)。点击缩略图可查看大图，可在下方“AI资产生成”面板切换状态并配置生图。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(LocalAppSpacing.current.small)
        )
    }
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // 默认状态 (对应 selectedBlock.currentImageUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)) {
                Card(
                    modifier = Modifier.fillMaxSize().clickable { if (selectedBlock.currentImageUri != null) bigImageToShow = selectedBlock.currentImageUri },
                    shape = AppShapes.small,
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (selectedBlock.currentImageUri != null) {
                            AsyncImage(
                                model = selectedBlock.currentImageUri.getAbsolutePath(),
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
            Text("默认状态 (Spin)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
        }
        
        // 选择后状态 (对应 stopUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)) {
                Card(
                    modifier = Modifier.fillMaxSize().clickable { if (props.stopUri != null) bigImageToShow = props.stopUri },
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
            Text("停止状态 (Stop)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
        }
    }
    
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    
    Button(
        onClick = { /* 提示用户到下方的资产生成面板即可 */ },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = AppShapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("👉 在下方【AI 资产生成】配置并生图", style = MaterialTheme.typography.labelSmall)
    }
}

package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import org.gemini.ui.forge.data.readBytesInternal
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.ui.dialog.asset.ResourceBindingDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.utils.looseJson

/**
 * SPIN_BUTTON (旋转大按钮) 组件的专属属性面板。
 * 
 * 1. 属性面板展示两个状态的紧凑缩略图片，支持点击看大图。
 * 2. 增强历史记录按钮尺寸与视觉呈现，使其更大且更容易点击。
 * 3. 当状态有生成图片时，支持从当前按钮绑定资源的子层级（子类）中单独绑定资源 ID。
 * 4. 底部提供“👉 配置并生图 (AI 绘制)”的跳转生图配置提示。
 */
@Composable
fun SpinButtonPropertiesPanel(
    viewModel: ProjectWorkspaceViewModel,
    selectedBlock: UIBlock
) {
    val props = selectedBlock.properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
    val state by viewModel.state.collectAsState()
    
    var bigImageToShow by remember { mutableStateOf<TemplateFile?>(null) }
    var showSpinBindingDialog by remember { mutableStateOf(false) }
    var showStopBindingDialog by remember { mutableStateOf(false) }

    // 级联加载解析当前最新的静态资源绑定元数据 JSON 
    var configData by remember { mutableStateOf<List<ResourceItem>>(emptyList()) }
    LaunchedEffect(state.resourceConfigPath, state.resourceConfigRefreshTrigger) {
        val path = state.resourceConfigPath
        configData = if (path.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val bytes = readBytesInternal(path)
                if (bytes == null) {
                    emptyList()
                } else {
                    val content = bytes.decodeToString()
                    looseJson.decodeFromString<List<ResourceItem>>(content)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
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

    // 提示信息板
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        shape = AppShapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "旋转按钮共有 2 个状态图片：一个是默认状态 (Spin)，一个是停止状态 (Stop)。点击缩略图可查看大图，可在下方“AI资产生成”面板切换状态并配置生图。如果状态有了对应的图片，您可以为其绑定更深层级的子状态资源ID。",
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
                
                // 优化历史记录按钮：使用 FilledTonalIconButton，高亮质感，更易点击
                FilledTonalIconButton(
                    onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_spin") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(LocalAppSpacing.current.extraSmall)
                        .size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.History, 
                        contentDescription = "历史记录", 
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text("默认状态 (Spin)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
            
            // 如果默认状态有图片资源，则单独绑定其子状态资源ID
            if (selectedBlock.currentImageUri != null) {
                Spacer(Modifier.height(LocalAppSpacing.current.extraSmall))
                if (selectedBlock.resourceBindingPath.isNotEmpty()) {
                    val hasBinding = props.spinResourceBindingPath.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSpinBindingDialog = true },
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = AppShapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val displayName = if (hasBinding) props.spinResourceBindingPath.last() else "绑定子资源"
                            Text(displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        if (hasBinding) {
                            IconButton(
                                onClick = {
                                    val updatedProps = props.copy(spinResourceBindingPath = emptyList())
                                    viewModel.assetManager.updateBlockProperties(selectedBlock.id, updatedProps)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, "解绑", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "💡 需先绑定主资源ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
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
                
                // 优化历史记录按钮：使用 FilledTonalIconButton，高亮质感，更易点击
                FilledTonalIconButton(
                    onClick = { viewModel.showHistoricalDialog(selectedBlock.id + "_stop") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(LocalAppSpacing.current.extraSmall)
                        .size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.History, 
                        contentDescription = "历史记录", 
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text("停止状态 (Stop)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall))
            
            // 如果停止状态有图片资源，则单独绑定其子状态资源ID
            if (props.stopUri != null) {
                Spacer(Modifier.height(LocalAppSpacing.current.extraSmall))
                if (selectedBlock.resourceBindingPath.isNotEmpty()) {
                    val hasBinding = props.stopResourceBindingPath.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStopBindingDialog = true },
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = AppShapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val displayName = if (hasBinding) props.stopResourceBindingPath.last() else "绑定子资源"
                            Text(displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        if (hasBinding) {
                            IconButton(
                                onClick = {
                                    val updatedProps = props.copy(stopResourceBindingPath = emptyList())
                                    viewModel.assetManager.updateBlockProperties(selectedBlock.id, updatedProps)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, "解绑", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "💡 需先绑定主资源ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    
    Spacer(Modifier.height(LocalAppSpacing.current.small))
    
    Button(
        onClick = { viewModel.updateState { it.copy(activePropertyTab = 1) } },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = AppShapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("👉 【AI 资产生成】配置并生图", style = MaterialTheme.typography.labelSmall)
    }

    // Spin 默认状态单独子资源绑定弹窗
    if (showSpinBindingDialog && configData.isNotEmpty()) {
        // 构建临时虚拟 Block，用于存储子状态的 binding
        val fakeStateBlock = remember(selectedBlock, props.spinResourceBindingPath) {
            UIBlock(
                id = selectedBlock.id + "_spin",
                type = selectedBlock.type,
                bounds = selectedBlock.bounds,
                resourceBindingPath = props.spinResourceBindingPath
            )
        }
        // 构建父 Block，传递整个大按钮绑定的 resourceBindingPath
        val parentBlock = remember(selectedBlock) {
            UIBlock(
                id = selectedBlock.id,
                type = selectedBlock.type,
                bounds = selectedBlock.bounds,
                resourceBindingPath = selectedBlock.resourceBindingPath
            )
        }
        
        ResourceBindingDialog(
            block = fakeStateBlock,
            parentBlock = parentBlock,
            configData = configData,
            onDismiss = { showSpinBindingDialog = false },
            onConfirm = { path ->
                val updatedProps = props.copy(spinResourceBindingPath = path)
                viewModel.assetManager.updateBlockProperties(selectedBlock.id, updatedProps)
                showSpinBindingDialog = false
            }
        )
    }

    // Stop 停止状态单独子资源绑定弹窗
    if (showStopBindingDialog && configData.isNotEmpty()) {
        // 构建临时虚拟 Block，用于存储子状态的 binding
        val fakeStateBlock = remember(selectedBlock, props.stopResourceBindingPath) {
            UIBlock(
                id = selectedBlock.id + "_stop",
                type = selectedBlock.type,
                bounds = selectedBlock.bounds,
                resourceBindingPath = props.stopResourceBindingPath
            )
        }
        // 构建父 Block，传递整个大按钮绑定的 resourceBindingPath
        val parentBlock = remember(selectedBlock) {
            UIBlock(
                id = selectedBlock.id,
                type = selectedBlock.type,
                bounds = selectedBlock.bounds,
                resourceBindingPath = selectedBlock.resourceBindingPath
            )
        }
        
        ResourceBindingDialog(
            block = fakeStateBlock,
            parentBlock = parentBlock,
            configData = configData,
            onDismiss = { showStopBindingDialog = false },
            onConfirm = { path ->
                val updatedProps = props.copy(stopResourceBindingPath = path)
                viewModel.assetManager.updateBlockProperties(selectedBlock.id, updatedProps)
                showStopBindingDialog = false
            }
        )
    }
}

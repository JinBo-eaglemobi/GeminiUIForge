package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.dialog.UIStudioRenameDialog
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * UI Studio 素材库树形视图面板 (Tree View)。
 *
 * 支持文件夹分层展开/折叠、微缩图、右键上下文菜单（去背、添加至画布、重命名、本地磁盘定位、复制路径、删除）。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioAssetPanel(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val library = state.assetLibrary
    val copy = rememberClipboardAction()

    var renamingFolderTarget by remember { mutableStateOf<UIAssetFolder?>(null) }
    var renamingAssetTarget by remember { mutableStateOf<UIAssetItem?>(null) }
    var deletingFolderTarget by remember { mutableStateOf<UIAssetFolder?>(null) }
    var deletingAssetTarget by remember { mutableStateOf<UIAssetItem?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            // 顶部 Header 栏：标题 + 按钮组
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Assets",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "素材库",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${library.items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.revealFolderInExplorer(null) },
                        modifier = Modifier
                            .size(28.dp)
                            .tip("在本地文件管理器中打开素材目录")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Folder",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openNewFolderDialog() },
                        modifier = Modifier
                            .size(28.dp)
                            .tip("新建素材分类文件夹")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.openImportSheetDialog() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .tip("导入外部图集并切片")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("导入", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 树形列表内容展示区域
            if (library.items.isEmpty() && library.folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "暂无素材，点击右上角「导入」切片",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. 文件夹树节点渲染
                    library.folders.forEach { folder ->
                        val isExpanded = state.expandedFolderIds.contains(folder.id)
                        val folderItems = library.getItemsInFolder(folder.id)

                        item(key = "folder_${folder.id}") {
                            var showFolderMenu by remember { mutableStateOf(false) }

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.toggleFolderExpanded(folder.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${folderItems.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = { showFolderMenu = true },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Options",
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = showFolderMenu,
                                    onDismissRequest = { showFolderMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("在本地资源管理器中打开") },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showFolderMenu = false
                                            viewModel.revealFolderInExplorer(folder.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("重命名文件夹") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showFolderMenu = false
                                            renamingFolderTarget = folder
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除文件夹", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showFolderMenu = false
                                            deletingFolderTarget = folder
                                        }
                                    )
                                }
                            }
                        }

                        // 文件夹内部的素材子项（展开时呈现）
                        if (isExpanded) {
                            items(folderItems, key = { "item_${it.id}" }) { item ->
                                AssetTreeLeafItem(
                                    item = item,
                                    isSelected = state.selectedAssetId == item.id,
                                    indentPadding = 24.dp,
                                    viewModel = viewModel,
                                    templateRepo = templateRepo,
                                    onRename = { renamingAssetTarget = it },
                                    onDelete = { deletingAssetTarget = it }
                                )
                            }
                        }
                    }

                    // 2. 根目录下直接所属的素材（未分类）
                    val rootItems = library.getItemsInFolder(null)
                    if (rootItems.isNotEmpty()) {
                        item(key = "header_root_items") {
                            Text(
                                text = "未分类素材 (${rootItems.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                            )
                        }
                        items(rootItems, key = { "root_item_${it.id}" }) { item ->
                            AssetTreeLeafItem(
                                item = item,
                                isSelected = state.selectedAssetId == item.id,
                                indentPadding = 8.dp,
                                viewModel = viewModel,
                                templateRepo = templateRepo,
                                onRename = { renamingAssetTarget = it },
                                onDelete = { deletingAssetTarget = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // 重命名文件夹弹窗
    renamingFolderTarget?.let { folder ->
        UIStudioRenameDialog(
            title = "重命名文件夹",
            initialName = folder.name,
            onConfirm = {
                viewModel.commitRenameFolder(folder.id, it)
                renamingFolderTarget = null
            },
            onDismiss = { renamingFolderTarget = null }
        )
    }

    // 重命名素材弹窗
    renamingAssetTarget?.let { asset ->
        UIStudioRenameDialog(
            title = "重命名素材",
            initialName = asset.name,
            onConfirm = {
                viewModel.commitRenameAsset(asset.id, it)
                renamingAssetTarget = null
            },
            onDismiss = { renamingAssetTarget = null }
        )
    }

    // 删除文件夹确认弹窗
    deletingFolderTarget?.let { folder ->
        AppConfirmDialog(
            title = "确认删除文件夹",
            message = "是否确认删除文件夹「${folder.name}」？该文件夹下的素材将自动移至未分类根目录。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAssetFolder(folder.id)
                deletingFolderTarget = null
            },
            onDismiss = { deletingFolderTarget = null }
        )
    }

    // 删除素材确认弹窗
    deletingAssetTarget?.let { asset ->
        AppConfirmDialog(
            title = "确认删除素材",
            message = "是否确认从磁盘彻底删除素材「${asset.name} (${asset.id})」？该操作不可撤销。",
            confirmText = "彻底删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAsset(asset.id)
                deletingAssetTarget = null
            },
            onDismiss = { deletingAssetTarget = null }
        )
    }
}

/**
 * 素材树叶子节点单项组件（带微缩图、尺寸药丸与丰富右键菜单）。
 */
@Composable
private fun AssetTreeLeafItem(
    item: UIAssetItem,
    isSelected: Boolean,
    indentPadding: androidx.compose.ui.unit.Dp,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    onRename: (UIAssetItem) -> Unit,
    onDelete: (UIAssetItem) -> Unit
) {
    val state = viewModel.state
    val copy = rememberClipboardAction()
    var bitmap by remember(item.relativePath) { mutableStateOf<ImageBitmap?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    LaunchedEffect(item.relativePath) {
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(item.relativePath)
                ?: try {
                    TemplateFile(item.relativePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            if (bytes != null) {
                bitmap = bytes.toImageBitmap()
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentPadding, end = 4.dp, top = 1.dp, bottom = 1.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { viewModel.selectAsset(item.id) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 微缩图容器
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val isMatting = state.isMattingAsset && state.mattingAssetId == item.id
                    if (bitmap != null && !isMatting) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap!!,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (isMatting) {
                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 名称与尺寸
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.width}×${item.height}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 右侧小选项触发图标
                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Menu",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // 丰富的右键上下文菜单
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("🪄 一键去除背景 (生成透明图)") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.removeAssetBackground(item.id)
                }
            )
            DropdownMenuItem(
                text = { Text("➕ 添加到当前视图画布") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.addAssetToCanvas(item, templateRepo)
                }
            )
            DropdownMenuItem(
                text = { Text("✏️ 重命名素材") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onRename(item)
                }
            )
            DropdownMenuItem(
                text = { Text("📁 在系统资源管理器中显示") },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.revealAssetInExplorer(item)
                }
            )
            DropdownMenuItem(
                text = { Text("📋 复制文件路径") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    copy(item.relativePath, "已复制素材路径: ${item.relativePath}")
                }
            )
            DropdownMenuItem(
                text = { Text("🗑️ 删除素材", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onDelete(item)
                }
            )
        }
    }
}

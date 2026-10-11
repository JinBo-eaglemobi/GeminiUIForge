package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.contextMenuClickable
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.dialog.UIStudioRenameDialog
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem

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
    val folderBoundsMap = remember { mutableStateMapOf<String, Rect>() }

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

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 一键展开/折叠全部素材文件夹
                    if (library.folders.isNotEmpty()) {
                        val allFoldersExpanded = library.folders.all { state.expandedFolderIds.contains(it.id) }
                        IconButton(
                            onClick = { viewModel.toggleAllFoldersExpanded() },
                            modifier = Modifier
                                .size(28.dp)
                                .tip(if (allFoldersExpanded) "折叠全部文件夹" else "展开全部文件夹")
                        ) {
                            Icon(
                                imageVector = if (allFoldersExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

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
                        val isHovered = state.hoveredFolderId == folder.id

                        item(key = "folder_${folder.id}") {
                            var showFolderMenu by remember { mutableStateOf(false) }

                            BoxWithConstraints {
                                val minMenuWidth = maxOf(200.dp, maxWidth * 0.85f)
                                val maxMenuWidth = maxOf(260.dp, maxWidth)

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isHovered) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = if (isHovered) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .onGloballyPositioned { coords ->
                                            folderBoundsMap[folder.id] = coords.boundsInRoot()
                                        }
                                        .contextMenuClickable(
                                            onClick = { viewModel.toggleFolderExpanded(folder.id) },
                                            onContextMenu = { showFolderMenu = true }
                                        )
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
                                            modifier = Modifier.size(20.dp).tip("更多操作")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = showFolderMenu,
                                    onDismissRequest = { showFolderMenu = false },
                                    modifier = Modifier
                                        .widthIn(min = minMenuWidth, max = maxMenuWidth)
                                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(10.dp))
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp))
                                        .padding(vertical = 4.dp)
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
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
                                UIStudioAssetTreeLeafItem(
                                    item = item,
                                    isSelected = state.selectedAssetId == item.id,
                                    indentPadding = 24.dp,
                                    viewModel = viewModel,
                                    templateRepo = templateRepo,
                                    onRename = { renamingAssetTarget = it },
                                    onDelete = { deletingAssetTarget = it },
                                    onDragHover = { rootPos ->
                                        val hitFolderId = folderBoundsMap.entries.firstOrNull { it.value.contains(rootPos) }?.key
                                        viewModel.setHoveredFolderId(hitFolderId)
                                    },
                                    onDragEnd = {
                                        val targetFolderId = state.hoveredFolderId
                                        val draggedId = state.draggedAssetId
                                        if (draggedId != null) {
                                            viewModel.moveAssetToFolder(draggedId, targetFolderId, templateRepo)
                                        }
                                        viewModel.setDraggedAssetId(null)
                                        viewModel.setHoveredFolderId(null)
                                    }
                                )
                            }
                        }
                    }

                    // 2. 根目录下直接所属的素材（不设“未分类素材”标签，平铺排列）
                    val rootItems = library.getItemsInFolder(null)
                    items(rootItems, key = { "root_item_${it.id}" }) { item ->
                        UIStudioAssetTreeLeafItem(
                            item = item,
                            isSelected = state.selectedAssetId == item.id,
                            indentPadding = 4.dp,
                            viewModel = viewModel,
                            templateRepo = templateRepo,
                            onRename = { renamingAssetTarget = it },
                            onDelete = { deletingAssetTarget = it },
                            onDragHover = { rootPos ->
                                val hitFolderId = folderBoundsMap.entries.firstOrNull { it.value.contains(rootPos) }?.key
                                viewModel.setHoveredFolderId(hitFolderId)
                            },
                            onDragEnd = {
                                val targetFolderId = state.hoveredFolderId
                                val draggedId = state.draggedAssetId
                                if (draggedId != null) {
                                    viewModel.moveAssetToFolder(draggedId, targetFolderId, templateRepo)
                                }
                                viewModel.setDraggedAssetId(null)
                                viewModel.setHoveredFolderId(null)
                            }
                        )
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

}

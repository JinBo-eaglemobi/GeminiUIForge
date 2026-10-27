package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.utils.toShortHash
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * UI Studio 素材树叶子节点单项组件（带微缩图、尺寸药丸、拖拽手柄、PC 鼠标右键与移动端更多操作菜单）。
 *
 * @param item 素材项元数据
 * @param isSelected 当前是否被选中
 * @param indentPadding 层级缩进宽度
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param onRename 重命名触发回调
 * @param onDelete 删除触发回调
 * @param onDragHover 拖拽经过全局坐标回调
 * @param onDragEnd 拖拽结束回调
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioAssetTreeLeafItem(
    item: UIAssetItem,
    isSelected: Boolean,
    indentPadding: Dp,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    onRename: (UIAssetItem) -> Unit,
    onDelete: (UIAssetItem) -> Unit,
    onDragHover: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    val copy = rememberClipboardAction()
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember(item.relativePath) { mutableStateOf<ImageBitmap?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isBeingDragged = state.draggedAssetId == item.id

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minMenuWidth = maxOf(220.dp, maxWidth)
        val maxMenuWidth = maxOf(280.dp, maxWidth)

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentPadding, end = 4.dp, top = 1.dp, bottom = 1.dp)
                .clip(RoundedCornerShape(6.dp))
                .alpha(if (isBeingDragged) 0.4f else 1.0f)
                .onGloballyPositioned { rowCoords = it }
                // 1. 鼠标右键菜单 (PointerEventPass.Initial 优先快速捕获)
                .pointerInput(item.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                showContextMenu = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                // 2. 整行整块即刻拖拽与单击融合手势 (无需对准细小手柄或长按，任意位置按下移动 >6px 即拖动)
                .pointerInput(item.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!down.pressed) return@awaitEachGesture
                        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture

                        var isDragging = false
                        var totalDistance = 0f

                        drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            totalDistance += dragAmount.getDistance()
                            if (!isDragging && totalDistance > 6f) {
                                isDragging = true
                                viewModel.setDraggedAssetId(item.id)
                            }
                            if (isDragging) {
                                rowCoords?.localToWindow(change.position)?.let { winPos ->
                                    onDragHover(winPos)
                                    viewModel.updateDragCursorPosition(winPos)
                                }
                                change.consume()
                            }
                        }

                        if (isDragging) {
                            viewModel.updateDragCursorPosition(null)
                            onDragEnd()
                        } else if (totalDistance <= 6f) {
                            viewModel.selectAsset(item.id)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .semantics {
                        onClick {
                            viewModel.selectAsset(item.id)
                            true
                        }
                    },
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
                        Image(
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

                // 素材名称（宽高移至右下侧独立预览面板展示）
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 右侧更多操作图标（触控端与移动端便捷入口）
                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier
                        .size(20.dp)
                        .tip("更多操作")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // 丰富的右键 (PC) / 长按 (移动端) 上下文菜单，自适应宽度 + 明确悬浮立体质感
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
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
                text = { Text("一键去除背景 (生成透明图)", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.removeAssetBackground(item.id)
                }
            )
            DropdownMenuItem(
                text = { Text("添加到当前视图画布", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.addAssetToCanvas(item, repo = templateRepo)
                }
            )
            DropdownMenuItem(
                text = { Text("重命名素材", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onRename(item)
                }
            )
            DropdownMenuItem(
                text = { Text("在系统资源管理器中显示", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    viewModel.revealAssetInExplorer(item)
                }
            )
            DropdownMenuItem(
                text = { Text("移动至文件夹...", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    showMoveFolderDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("复制文件路径", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    copy(item.relativePath, "已复制素材路径: ${item.relativePath}")
                }
            )
            DropdownMenuItem(
                text = { Text("复制资源哈希 (Hash)", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    val hash = item.hash?.toShortHash()
                    if (!hash.isNullOrBlank()) {
                        copy(hash, "已复制资源哈希: $hash")
                    } else {
                        coroutineScope.launch {
                            val computed = viewModel.getOrComputeAssetHash(item)?.toShortHash()
                            if (!computed.isNullOrBlank()) {
                                copy(computed, "已复制资源哈希: $computed")
                            } else {
                                org.gemini.ui.forge.utils.Toast.show("暂无可用哈希码", org.gemini.ui.forge.ui.component.ToastType.INFO)
                            }
                        }
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            DropdownMenuItem(
                text = { Text("删除素材", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onDelete(item)
                }
            )
        }
    }

    // 移动素材至文件夹弹窗
    if (showMoveFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveFolderDialog = false },
            title = {
                Text(
                    text = "移动素材至文件夹",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val folders = state.assetLibrary.folders
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "请选择目标文件夹：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 根目录选项
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.folderId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (item.folderId == null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveAssetToFolder(item.id, null, templateRepo)
                                showMoveFolderDialog = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "根目录 (无文件夹)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.folderId == null) {
                                Text(
                                    text = "当前位置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 各个文件夹选项
                    folders.forEach { folder ->
                        val isCurrent = item.folderId == folder.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveAssetToFolder(item.id, folder.id, templateRepo)
                                    showMoveFolderDialog = false
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text(
                                        text = "当前位置",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

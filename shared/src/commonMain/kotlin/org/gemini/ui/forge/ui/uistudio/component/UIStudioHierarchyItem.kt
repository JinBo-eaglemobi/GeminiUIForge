package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.DropPosition
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.getIcon
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * UI Studio 单个组件/图层树节点行项组件 (Hierarchy Item)。
 *
 * 遵循一文件一 Composable 铁律。
 * 支持树形缩进、选中高亮、可见性眼睛切换、锁定切换、拖拽手柄以及解耦的专业右键/更多菜单操作。
 *
 * @param block 当前图层组件
 * @param depth 嵌套缩进深度 (0 为根级)
 * @param isAncestorHidden 祖先节点是否处于隐藏变暗状态
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param parentBlock 直接父级组件 (用于嵌套判定与移出操作)
 * @param draggedBlockId 正在被拖拽的图层 ID
 * @param hoveredBlockId 拖拽悬浮命中的目标图层 ID
 * @param dropPosition 拖拽插入相对位置 (BEFORE / AFTER / INSIDE)
 * @param onPositioned 位置坐标回调
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioHierarchyItem(
    block: UIBlock,
    depth: Int,
    isAncestorHidden: Boolean,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    parentBlock: UIBlock? = null,
    draggedBlockId: String? = null,
    hoveredBlockId: String? = null,
    dropPosition: DropPosition = DropPosition.INSIDE,
    onPositioned: (String, Rect) -> Unit = { _, _ -> },
    onStartDrag: (String, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val isSelected = state.selectedBlockId == block.id
    var showContextMenu by remember { mutableStateOf(false) }
    val isContainerExpanded = state.expandedHierarchyBlockIds.contains(block.id)
    val hasChildren = block.children.isNotEmpty()
    val isEffectiveHidden = isAncestorHidden || !block.isVisible
    val isDragged = draggedBlockId == block.id
    val isHovered = hoveredBlockId == block.id
    val hasParent = parentBlock != null || block.parent != null

    // 真实图片缩略图异步加载（融合素材库联动与历史路径智能自愈探测）
    val assetItem = remember(block.id, state.assetLibrary.items) {
        state.assetLibrary.items.find { it.id == block.id }
    }
    val effectivePath = assetItem?.relativePath
        ?: block.currentImageUri?.relativePath
        ?: block.referenceImage?.relativePath

    var bitmap by remember(effectivePath, block.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(effectivePath, block.id) {
        if (!effectivePath.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                var bytes = readLocalFileBytes(effectivePath)
                    ?: try {
                        TemplateFile(effectivePath).readBytes()
                    } catch (_: Exception) {
                        null
                    }
                // 历史路径失效自愈探测：若读不出，尝试从规范 assets/image/{id}.png 读取
                if (bytes == null && state.project != null) {
                    val sanitized = state.project!!.projectId.replace(" ", "_")
                    val fallbackRel = "templates/$sanitized/assets/image/${block.id}.png"
                    bytes = try {
                        TemplateFile(fallbackRel).readBytes()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bytes != null) {
                    bitmap = bytes.toImageBitmap()
                } else {
                    bitmap = null
                }
            }
        } else {
            bitmap = null
        }
    }

    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val contentAlpha = when {
        isDragged -> 0.3f
        isEffectiveHidden -> 0.4f
        else -> 1.0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val hostWidth = maxWidth
            val minMenuWidth = maxOf(190.dp, hostWidth * 0.85f)
            val maxMenuWidth = maxOf(250.dp, hostWidth)

            val itemBorder = when {
                isHovered && dropPosition == DropPosition.INSIDE -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                else -> null
            }

            Surface(
                color = when {
                    isHovered && dropPosition == DropPosition.INSIDE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(6.dp),
                border = itemBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .onGloballyPositioned { coordinates ->
                        onPositioned(block.id, coordinates.boundsInWindow())
                    }
                    .combinedClickable(
                        onClick = { viewModel.selectBlock(block.id) },
                        onDoubleClick = {
                            if (block.isPureContainer || hasChildren || block.type == UIBlockType.CONTAINER) {
                                viewModel.openContainerTab(block.id)
                            }
                        }
                    )
                    .pointerInput(block.id, hasChildren, block.isPureContainer) {
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
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // BEFORE 拖拽插入提示指示线
                    if (isHovered && dropPosition == DropPosition.BEFORE) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (depth * 14 + 4).dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                    ) {
                        // 折叠 / 展开手柄
                        if (hasChildren) {
                            IconButton(
                                onClick = { viewModel.toggleHierarchyBlockExpanded(block.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = if (isContainerExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(18.dp))
                        }

                        // 6点拖拽指示手柄（独立挂载手势，避免被行项点击抢占）
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "拖拽手柄",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = if (isHovered) 0.9f else 0.4f),
                            modifier = Modifier
                                .size(16.dp)
                                .tip("拖拽此处可自由移动层级或拖入图元成组")
                                .onGloballyPositioned { handleCoordinates = it }
                                .pointerInput(block.id) {
                                    detectDragGestures(
                                        onDragStart = { localOffset ->
                                            val windowPos = handleCoordinates?.localToWindow(localOffset) ?: localOffset
                                            onStartDrag(block.id, windowPos)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val windowPos = handleCoordinates?.localToWindow(change.position) ?: change.position
                                            onDrag(windowPos)
                                        },
                                        onDragEnd = { onDragEnd() },
                                        onDragCancel = { onDragEnd() }
                                    )
                                }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // 图层真实图片缩略图 (若无图片则优雅回退为类型图标)
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .border(
                                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        RoundedCornerShape(3.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap!!,
                                    contentDescription = block.id,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(1.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = block.type.getIcon(),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // 组件名称
                        Text(
                            text = block.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // 锁定开关
                        IconButton(
                            onClick = { viewModel.toggleBlockLock(block.id, templateRepo) },
                            modifier = Modifier
                                .size(20.dp)
                                .tip(if (block.isLocked) "解除锁定" else "锁定组件 (禁止画布移动)")
                        ) {
                            Icon(
                                imageVector = if (block.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "锁定",
                                tint = if (block.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // 眼睛可见性开关
                        IconButton(
                            onClick = { viewModel.toggleBlockVisibility(block.id, templateRepo) },
                            modifier = Modifier
                                .size(20.dp)
                                .tip(if (block.isVisible) "隐藏组件" else "显示组件")
                        ) {
                            Icon(
                                imageVector = if (block.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "可见性",
                                tint = if (block.isVisible) MaterialTheme.colorScheme.outline.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // 更多操作按钮 (解耦移动端手势冲突)
                        IconButton(
                            onClick = { showContextMenu = true },
                            modifier = Modifier
                                .size(20.dp)
                                .tip("更多操作")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // AFTER 拖拽插入提示指示线
                    if (isHovered && dropPosition == DropPosition.AFTER) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // 右键 / 更多操作上下文菜单 (纯中文)
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                modifier = Modifier
                    .widthIn(min = minMenuWidth, max = maxMenuWidth)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(10.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        RoundedCornerShape(10.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(10.dp)
                    )
            ) {
                DropdownMenuItem(
                    text = { Text("上移一层", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.moveBlockUp(block.id, templateRepo)
                        showContextMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("下移一层", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.moveBlockDown(block.id, templateRepo)
                        showContextMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("置于顶层", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.VerticalAlignTop, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.bringBlockToFront(block.id, templateRepo)
                        showContextMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("置于底层", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.VerticalAlignBottom, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.sendBlockToBack(block.id, templateRepo)
                        showContextMenu = false
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // 锁定/解锁组件
                DropdownMenuItem(
                    text = { Text(if (block.isLocked) "解除锁定" else "锁定组件", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            if (block.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        viewModel.toggleBlockLock(block.id, templateRepo)
                        showContextMenu = false
                    }
                )

                // 容器操作：移入移出纯拖拽化，彻底移除移出父容器选项
                if (block.isPureContainer || hasChildren) {
                    DropdownMenuItem(
                        text = { Text("打开容器独立视图", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Layers, null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            viewModel.openContainerTab(block.id)
                            showContextMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("解散父容器", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.LayersClear, null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            viewModel.ungroupContainer(block.id, templateRepo)
                            showContextMenu = false
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                DropdownMenuItem(
                    text = { Text("重命名", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.startRenameBlock(block.id)
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("删除组件", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        viewModel.requestDeleteBlock(block.id, templateRepo)
                        showContextMenu = false
                    }
                )
            }
        }

        // 递归渲染子组件
        if (hasChildren && isContainerExpanded) {
            block.children.forEach { child ->
                UIStudioHierarchyItem(
                    block = child,
                    depth = depth + 1,
                    isAncestorHidden = isEffectiveHidden,
                    viewModel = viewModel,
                    templateRepo = templateRepo,
                    parentBlock = block,
                    draggedBlockId = draggedBlockId,
                    hoveredBlockId = hoveredBlockId,
                    dropPosition = dropPosition,
                    onPositioned = onPositioned,
                    onStartDrag = onStartDrag,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd
                )
            }
        }
    }
}

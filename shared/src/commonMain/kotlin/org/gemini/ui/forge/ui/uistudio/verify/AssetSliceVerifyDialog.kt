package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.SmartEdgeSnapper
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * 图集切图前交互式可视化预审与微调弹窗
 *
 * 遵循一文件一 Composable 铁律与五阶设计 Tokens。
 */
@Composable
fun AssetSliceVerifyDialog(
    visible: Boolean = true,
    imagePath: String,
    initialItems: List<NormalizedAssetItem>,
    autoRemoveBg: Boolean = true,
    onToggleAutoRemoveBg: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirmSlice: (List<NormalizedAssetItem>) -> Unit,
    onSaveDraft: ((List<NormalizedAssetItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val spacing = LocalAppSpacing.current
    val coroutineScope = rememberCoroutineScope()

    // 1. 图像字节与 ImageBitmap 加载状态
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember { mutableStateOf(true) }

    // 2. 可编辑的图元集合与历史快照
    val editableItems = remember(initialItems) {
        mutableStateListOf<EditableSliceItem>().apply {
            addAll(initialItems.map { EditableSliceItem.fromNormalizedItem(it) })
        }
    }
    val snapshotOriginal = remember(initialItems) {
        initialItems.map { EditableSliceItem.fromNormalizedItem(it) }
    }

    // 自动暂存草稿闭包
    fun autoPersistDraft() {
        if (editableItems.isNotEmpty()) {
            onSaveDraft?.invoke(editableItems.map { it.toNormalizedItem() })
        }
    }

    // 安全关闭并持久化草稿
    fun handleDismissWithDraft() {
        autoPersistDraft()
        onDismiss()
    }

    // 3. 选中项与交互模式
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    val currentSelectedItem = editableItems.find { it.id == selectedItemId }
    var toolMode by remember { mutableStateOf(VerifyToolMode.SELECT) }
    var isSnapping by remember { mutableStateOf(false) }

    // 左右边栏动态可调节宽度（遵循 column-resizer 规范）
    val density = LocalDensity.current
    var leftPanelWidth by remember { mutableStateOf(260.dp) }
    var rightPanelWidth by remember { mutableStateOf(280.dp) }

    // 撤销与重做历史快照栈（最大深度 50 步）
    val undoStack = remember { mutableStateListOf<List<EditableSliceItem>>() }
    val redoStack = remember { mutableStateListOf<List<EditableSliceItem>>() }

    // 记录操作快照
    fun recordSnapshot() {
        undoStack.add(editableItems.map { it.copy() })
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    // 撤销上一步
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(editableItems.map { it.copy() })
            val previous = undoStack.removeAt(undoStack.lastIndex)
            editableItems.clear()
            editableItems.addAll(previous)
            if (selectedItemId != null && editableItems.none { it.id == selectedItemId }) {
                selectedItemId = null
            }
            Toast.show("已撤销上一步操作", ToastType.INFO)
        }
    }

    // 重做恢复下一步
    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(editableItems.map { it.copy() })
            val next = redoStack.removeAt(redoStack.lastIndex)
            editableItems.clear()
            editableItems.addAll(next)
            if (selectedItemId != null && editableItems.none { it.id == selectedItemId }) {
                selectedItemId = null
            }
            Toast.show("已恢复下一步操作", ToastType.INFO)
        }
    }

    // 焦点请求器用于捕获键盘快捷键
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 4. 画布视口缩放与平移
    var scale by remember { mutableStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset(32f, 32f)) }
    var canvasViewportSize by remember { mutableStateOf(IntSize.Zero) }

    // 触发一键智能吸附校准 (快捷键: Alt+A)
    fun triggerSmartSnap() {
        if (isSnapping || editableItems.isEmpty()) return
        coroutineScope.launch(Dispatchers.Default) {
            val bytes = imageBytes ?: return@launch
            val imgW = imageBitmap?.width?.toFloat() ?: 1280f
            val imgH = imageBitmap?.height?.toFloat() ?: 720f
            recordSnapshot()
            isSnapping = true
            var snappedCount = 0

            editableItems.indices.forEach { idx ->
                val item = editableItems[idx]
                val result = SmartEdgeSnapper.snapBounds(
                    imageBytes = bytes,
                    logicalBounds = item.bounds,
                    canvasWidth = imgW,
                    canvasHeight = imgH
                )
                if (result != null) {
                    editableItems[idx] = item.copy(bounds = result.logicalRect)
                    snappedCount++
                }
            }

            isSnapping = false
            autoPersistDraft()
            Toast.show("边缘微观吸附完成，已智能校对 $snappedCount 个图元", ToastType.SUCCESS)
        }
    }

    // 重置全部图元及视口坐标至初始状态 (快捷键: Ctrl+R)
    fun triggerResetAll() {
        recordSnapshot()
        editableItems.clear()
        editableItems.addAll(snapshotOriginal.map { it.copy() })
        selectedItemId = null
        scale = 1.0f
        panOffset = Offset(32f, 32f)
        autoPersistDraft()
        Toast.show("已重置回初始解析状态与视口坐标", ToastType.INFO)
    }

    // 异步加载原图
    LaunchedEffect(imagePath) {
        isLoadingImage = true
        if (imagePath.isNotBlank()) {
            withContext(Dispatchers.Default) {
                try {
                    val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(imagePath)
                    imageBytes = bytes
                    imageBitmap = bytes?.toImageBitmap()
                } catch (_: Exception) {
                    imageBytes = null
                    imageBitmap = null
                }
            }
        } else {
            imageBytes = null
            imageBitmap = null
        }
        isLoadingImage = false
    }

    Dialog(
        onDismissRequest = { handleDismissWithDraft() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(spacing.medium),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val isCtrlOrCmd = event.isCtrlPressed || event.isMetaPressed
                            val isShift = event.isShiftPressed
                            if (isCtrlOrCmd && event.key == Key.Z) {
                                if (isShift) {
                                    redo()
                                } else {
                                    undo()
                                }
                                true
                            } else if (isCtrlOrCmd && event.key == Key.Y) {
                                redo()
                                true
                            } else if (isCtrlOrCmd && isShift && (event.key == Key.A || event.utf16CodePoint == 'a'.code || event.utf16CodePoint == 'A'.code)) {
                                triggerSmartSnap()
                                true
                            } else if (isCtrlOrCmd && (event.key == Key.R || event.utf16CodePoint == 'r'.code || event.utf16CodePoint == 'R'.code)) {
                                triggerResetAll()
                                true
                            } else if (event.key == Key.Delete || event.key == Key.Backspace) {
                                selectedItemId?.let { id ->
                                    recordSnapshot()
                                    editableItems.removeAll { it.id == id }
                                    selectedItemId = null
                                    autoPersistDraft()
                                    Toast.show("已删除图元", ToastType.INFO)
                                }
                                true
                            } else false
                        } else false
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部操作栏
                    AssetSliceVerifyTopBar(
                        imageName = imagePath.substringAfterLast('\\').substringAfterLast('/'),
                        totalCount = editableItems.size,
                        selectedItem = currentSelectedItem,
                        zoomScale = scale,
                        toolMode = toolMode,
                        isSnapping = isSnapping,
                        autoRemoveBg = autoRemoveBg,
                        onToggleAutoRemoveBg = { onToggleAutoRemoveBg?.invoke(it) },
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        onToolModeChange = { toolMode = it },
                        onResetZoom = {
                            scale = 1.0f
                            panOffset = Offset(32f, 32f)
                        },
                        onUndo = { undo() },
                        onRedo = { redo() },
                        onSmartSnap = { triggerSmartSnap() },
                        onDeleteSelected = {
                            selectedItemId?.let { id ->
                                recordSnapshot()
                                editableItems.removeAll { it.id == id }
                                selectedItemId = null
                                autoPersistDraft()
                            }
                        },
                        onResetAll = { triggerResetAll() },
                        onClose = { handleDismissWithDraft() },
                        onConfirm = {
                            onConfirmSlice(editableItems.map { it.toNormalizedItem() })
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 三栏工作台主体：左侧元件清单 | 中间交互画布 | 右侧固定属性面板
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 1. 左侧元件清单面板 (动态宽度，支持拖拽调整)
                        AssetSliceListPanel(
                            items = editableItems,
                            selectedItemId = selectedItemId,
                            modifier = Modifier.width(leftPanelWidth),
                            onSelectItem = { id ->
                                selectedItemId = id
                                if (id != null) {
                                    val target = editableItems.find { it.id == id }
                                    if (target != null) {
                                        val b = target.bounds
                                        val centerX = (b.left + b.right) / 2f
                                        val centerY = (b.top + b.bottom) / 2f
                                        val vpW = if (canvasViewportSize.width > 0) canvasViewportSize.width.toFloat() else 800f
                                        val vpH = if (canvasViewportSize.height > 0) canvasViewportSize.height.toFloat() else 600f
                                        panOffset = Offset(
                                            x = vpW / 2f - centerX * scale,
                                            y = vpH / 2f - centerY * scale
                                        )
                                    }
                                }
                            },
                            onDeleteItem = { id ->
                                recordSnapshot()
                                editableItems.removeAll { it.id == id }
                                if (selectedItemId == id) selectedItemId = null
                                autoPersistDraft()
                            }
                        )

                        // 左侧拖拽调宽手柄
                        VerticalSplitter(onDrag = { delta ->
                            val deltaDp = with(density) { delta.toDp() }
                            leftPanelWidth = (leftPanelWidth + deltaDp).coerceIn(160.dp, 500.dp)
                        })

                        // 2. 中间交互画布区域 (自适应撑满剩余宽度，强制 clipToBounds 防止溢出遮盖侧栏)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clipToBounds()
                                .onSizeChanged { canvasViewportSize = it }
                        ) {
                            if (isLoadingImage) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                AssetSliceCanvas(
                                    imageBitmap = imageBitmap,
                                    items = editableItems,
                                    selectedItemId = selectedItemId,
                                    toolMode = toolMode,
                                    scale = scale,
                                    panOffset = panOffset,
                                    onScaleChange = { scale = it },
                                    onPanChange = { panOffset = it },
                                    onSelectItem = { selectedItemId = it },
                                    onUpdateItemBounds = { id, newRect ->
                                        val idx = editableItems.indexOfFirst { it.id == id }
                                        if (idx != -1) {
                                            editableItems[idx] = editableItems[idx].copy(bounds = newRect)
                                        }
                                    },
                                    onBeforeTransform = { recordSnapshot() },
                                    onAddNewItem = { newRect ->
                                        recordSnapshot()
                                        val newId = "asset_${editableItems.size + 1}"
                                        val newItem = EditableSliceItem(
                                            id = newId,
                                            name = "新图元 ${editableItems.size + 1}",
                                            category = "自定义",
                                            blockType = UIBlockType.IMAGE,
                                            bounds = newRect
                                        )
                                        editableItems.add(newItem)
                                        selectedItemId = newId
                                        toolMode = VerifyToolMode.SELECT
                                        autoPersistDraft()
                                        Toast.show("已补录新图元: ${newItem.name}", ToastType.SUCCESS)
                                    }
                                )
                            }
                        }

                        // 右侧拖拽调宽手柄
                        VerticalSplitter(onDrag = { delta ->
                            val deltaDp = with(density) { delta.toDp() }
                            rightPanelWidth = (rightPanelWidth - deltaDp).coerceIn(180.dp, 560.dp)
                        })

                        // 3. 右侧固定属性面板 (Inspector, 动态宽度，支持拖拽调整)
                        val currentSelectedItem = editableItems.find { it.id == selectedItemId }
                        AssetSlicePropertyPanel(
                            selectedItem = currentSelectedItem,
                            modifier = Modifier.width(rightPanelWidth),
                            onDeselect = { selectedItemId = null },
                            onUpdateItem = { updated ->
                                recordSnapshot()
                                val idx = editableItems.indexOfFirst { it.id == updated.id }
                                if (idx != -1) {
                                    editableItems[idx] = updated
                                    autoPersistDraft()
                                }
                            },
                            onDeleteItem = { id ->
                                recordSnapshot()
                                editableItems.removeAll { it.id == id }
                                if (selectedItemId == id) selectedItemId = null
                                autoPersistDraft()
                            }
                        )
                    }
                }
            }
        }
    }
}

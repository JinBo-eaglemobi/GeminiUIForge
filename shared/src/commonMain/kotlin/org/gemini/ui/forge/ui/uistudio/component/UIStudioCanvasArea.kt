package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 中间核心交互画布组件。
 *
 * 功能特性：
 * 1. 默认自适应视口大小居中显示 (Fit to Viewport)，同时支持鼠标滚轮自由平滑缩放与拖拽平移；
 * 2. 选中视图时正式渲染该界面的真实像素线框尺寸与深色阴影；
 * 3. 处于活跃视图时，右下角悬浮「✨ AI Chat 智能排版」按钮。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioCanvasArea(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val currentView = state.currentView
    val density = LocalDensity.current.density

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(density) {
        viewModel.updateDensity(density)
    }

    // 默认自适应居中联动 (监听视口尺寸、视图宽高、容器状态、自适应标志以及自增触发器)
    LaunchedEffect(
        containerSize,
        currentView?.id,
        currentView?.width,
        currentView?.height,
        state.activeContainerId,
        state.fitToViewportTrigger,
        state.isFitToViewport,
        density
    ) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            if (state.isFitToViewport) {
                viewModel.fitToViewport(
                    viewportWidth = containerSize.width.toFloat(),
                    viewportHeight = containerSize.height.toFloat(),
                    density = density
                )
            } else if (state.panOffset == Offset.Zero && state.viewViewportCache[state.activeContainerId ?: state.currentViewId] == null) {
                viewModel.resetZoom(
                    viewportWidth = containerSize.width.toFloat(),
                    viewportHeight = containerSize.height.toFloat(),
                    density = density
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // 深色工业底色
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                viewModel.updateCanvasWindowBounds(SerialRect(b.left, b.top, b.right, b.bottom))
            }
            .then(
                if (state.isCanvasDragHovered) {
                    Modifier.border(2.dp, Color(0xFF818CF8), RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
    ) {
        // 画布交互与手势层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentView?.id, state.activeContainerId, state.panOffset, state.scale) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val currentV = currentView ?: return@detectTapGestures
                            val logicalX = (tapOffset.x - state.panOffset.x) / state.scale
                            val logicalY = (tapOffset.y - state.panOffset.y) / state.scale

                            if (state.activeContainerId == null) {
                                // 主视图视口下：双击父组件，新开一个标签页面显示父组件内的布局
                                val hitRoot = currentV.blocks.findLast { b ->
                                    if (!b.isVisible || b.isLocked) return@findLast false
                                    val r = b.bounds
                                    logicalX >= r.left && logicalX <= r.right &&
                                    logicalY >= r.top && logicalY <= r.bottom
                                }
                                if (hitRoot != null && (hitRoot.isPureContainer || hitRoot.children.isNotEmpty())) {
                                    viewModel.openContainerTab(hitRoot.id)
                                }
                            }
                        },
                        onTap = { tapOffset ->
                            val currentV = currentView
                            if (currentV == null) {
                                viewModel.clearSelection()
                                return@detectTapGestures
                            }
                            // 将点击绝对坐标通过当前平移与缩放矩阵逆映射至视图逻辑坐标
                            val logicalX = (tapOffset.x - state.panOffset.x) / state.scale
                            val logicalY = (tapOffset.y - state.panOffset.y) / state.scale

                            val activeContainer = state.activeContainerBlock
                            if (activeContainer != null) {
                                // 模式 A：容器独立视口中，点击可命中该容器内的直接子组件
                                val hitChild = activeContainer.children.findLast { b ->
                                    if (!b.isVisible || b.isLocked) return@findLast false
                                    val r = b.bounds
                                    logicalX >= r.left && logicalX <= r.right &&
                                    logicalY >= r.top && logicalY <= r.bottom
                                }
                                if (hitChild != null) {
                                    viewModel.selectBlock(hitChild.id)
                                } else {
                                    viewModel.selectBlock(activeContainer.id)
                                }
                            } else {
                                // 模式 B：全局主视图视口（旧版铁律）：严禁绕过父组件选中子组件，点击任何子组件返回父组件本身
                                val hitRoot = currentV.blocks.findLast { b ->
                                    if (!b.isVisible || b.isLocked) return@findLast false
                                    val r = b.bounds
                                    logicalX >= r.left && logicalX <= r.right &&
                                    logicalY >= r.top && logicalY <= r.bottom
                                }
                                if (hitRoot != null) {
                                    viewModel.selectBlock(hitRoot.id)
                                } else {
                                    // 点击脱离 Page 范围或未点中任何组件，默认清空所有选中，联动展示页面属性
                                    viewModel.clearSelection()
                                }
                            }
                        }
                    )
                }
                // 1. 鼠标滚轮缩放画布
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.firstOrNull() ?: continue
                                val delta = change.scrollDelta.y
                                if (delta != 0f) {
                                    val multiplier = if (delta > 0) 0.9f else 1.1f
                                    viewModel.onZoom(multiplier, change.position)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                // 2. 专用鼠标右键平移画布手势（严格禁止鼠标左键平移画布，释放左键供图元拖拽使用）
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.buttons.isSecondaryPressed) {
                                if (event.type == PointerEventType.Move) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val delta = change.position - change.previousPosition
                                        if (delta.x != 0f || delta.y != 0f) {
                                            viewModel.onPan(delta)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (currentView == null) {
                // 未创建或未选择视图界面时的引导空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.padding(spacing.large)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(spacing.large),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "未选择活跃视图界面",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "请在左侧边栏「视图界面」列表中点击选择或新建一个视图界面以开始排版",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { viewModel.openNewViewDialog() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("立即新建视图")
                            }
                        }
                    }
                }
            } else {
                val activeContainer = state.activeContainerBlock
                val effectiveWidth = activeContainer?.bounds?.width ?: currentView.width
                val effectiveHeight = activeContainer?.bounds?.height ?: currentView.height

                // 画布实体容器（应用图形平移与缩放矩阵）
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = state.panOffset.x
                            translationY = state.panOffset.y
                            scaleX = state.scale
                            scaleY = state.scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                        .size(
                            width = effectiveWidth.dp,
                            height = effectiveHeight.dp
                        )
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(2.dp),
                            ambientColor = Color.Black.copy(alpha = 0.6f),
                            spotColor = Color.Black.copy(alpha = 0.9f)
                        )
                ) {
                    val renderableBlocks = remember(currentView.blocks, activeContainer?.id, activeContainer?.opacity) {
                        if (activeContainer != null) {
                            // 容器独立编辑视口模式：仅渲染容器内部子组件（局部坐标，继承容器透明度）
                            collectRenderableBlocks(
                                blocks = activeContainer.children,
                                parentOffsetX = 0f,
                                parentOffsetY = 0f,
                                parentLocked = activeContainer.isLocked,
                                parentVisible = true,
                                parentOpacity = activeContainer.effectiveOpacity
                            )
                        } else {
                            // 全局主视图模式：全层级递归展平
                            collectRenderableBlocks(currentView.blocks)
                        }
                    }
                    if (renderableBlocks.isEmpty()) {
                        // 当前视图内图元为空的引导状态
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(spacing.large)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(spacing.small))
                                Text(
                                    text = "「${currentView.nameStr}」视图暂无图元",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "从左下方素材库选择素材，或点击右下角「✨ AI Chat」智能排版",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        // 渲染该视图中的所有可见图元实体（全层级递归展平绝对坐标）
                        Box(modifier = Modifier.fillMaxSize()) {
                            renderableBlocks.forEach { info ->
                                UIStudioBlockItem(
                                    block = info.block,
                                    absBounds = info.absBounds,
                                    inheritedOpacity = info.inheritedOpacity,
                                    isSelected = info.block.id == state.selectedBlockId,
                                    scale = state.scale,
                                    onClick = {
                                        if (activeContainer == null) {
                                            // 主视图模式层级门禁：严禁绕过父容器直接选中子组件，点击必须选中所属顶层根父容器
                                            val rootParent = currentView.blocks.find { root ->
                                                root.id == info.block.id || root.children.any { c ->
                                                    c.id == info.block.id || c.children.any { cc -> cc.id == info.block.id }
                                                }
                                            }
                                            val targetId = rootParent?.id ?: info.block.id
                                            viewModel.selectBlock(targetId)
                                        } else {
                                            // 容器专属独立编辑视口下：允许直接选中该容器内的子组件
                                            viewModel.selectBlock(info.block.id)
                                        }
                                    },
                                    onDoubleTap = {
                                        if (activeContainer == null) {
                                            // 主视图模式下：双击父容器，新开标签页面显示父组件内部布局
                                            val rootParent = currentView.blocks.find { root ->
                                                root.id == info.block.id || root.children.any { c ->
                                                    c.id == info.block.id || c.children.any { cc -> cc.id == info.block.id }
                                                }
                                            }
                                            val targetContainer = rootParent ?: info.block
                                            if (targetContainer.isPureContainer || targetContainer.children.isNotEmpty() || targetContainer.type == org.gemini.ui.forge.model.ui.UIBlockType.CONTAINER) {
                                                viewModel.openContainerTab(targetContainer.id)
                                            }
                                        }
                                    },
                                    onDragDelta = { dx, dy ->
                                        viewModel.moveBlockDelta(info.block.id, dx, dy)
                                    },
                                    onDragEnd = {
                                        // 移动操作已在内存中实时更新并标记 isDirty，绝不执行任何自动刷盘或 Toast 弹窗
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 独立覆盖层：右上角悬浮视口缩放与居中工具集（完全免疫底层画布平移缩放手势）
        if (currentView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.medium),
                contentAlignment = Alignment.TopEnd
            ) {
                UIStudioCanvasToolbar(viewModel = viewModel, containerSize = containerSize)
            }
        }

        // 独立覆盖层：右下角悬浮「✨ AI Chat」智能排版按钮（完全免疫底层画布平移缩放手势）
        if (currentView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.large),
                contentAlignment = Alignment.BottomEnd
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openAiChatDialog() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Chat",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "AI Chat 智能排版",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.tip("使用 AI 自然语言对话快速排版当前视图")
                )
            }
        }

        // 拖拽素材悬停高亮提示浮层
        if (state.isCanvasDragHovered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF6366F1).copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF818CF8)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (state.activeContainerId != null) {
                                "松开添加至父容器「${state.activeContainerId}」"
                            } else {
                                "松开添加至当前视图「${currentView?.id ?: ""}」"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * UI Studio 画布全层级展平图元渲染模型。
 */
private data class RenderableBlockInfo(
    val block: UIBlock,
    val absBounds: SerialRect,
    val inheritedOpacity: Float = 1.0f,
    val isInheritedLocked: Boolean = false
)

/**
 * 递归收集页面内所有可见的图元，并精准推导累加其绝对全局坐标矩形与继承锁定态及父级级联透明度。
 */
private fun collectRenderableBlocks(
    blocks: List<UIBlock>,
    parentOffsetX: Float = 0f,
    parentOffsetY: Float = 0f,
    parentLocked: Boolean = false,
    parentVisible: Boolean = true,
    parentOpacity: Float = 1.0f
): List<RenderableBlockInfo> {
    val result = mutableListOf<RenderableBlockInfo>()
    for (b in blocks) {
        val currentVisible = parentVisible && b.isVisible
        if (!currentVisible) continue

        val currentLocked = parentLocked || b.isLocked
        val absLeft = parentOffsetX + b.bounds.left
        val absTop = parentOffsetY + b.bounds.top
        val absW = b.bounds.right - b.bounds.left
        val absH = b.bounds.bottom - b.bounds.top
        val absRect = SerialRect(absLeft, absTop, absLeft + absW, absTop + absH)

        val currentCumulativeOpacity = (parentOpacity * b.effectiveOpacity).coerceIn(0.0f, 1.0f)

        result.add(
            RenderableBlockInfo(
                block = b.copy(isLocked = currentLocked),
                absBounds = absRect,
                inheritedOpacity = parentOpacity,
                isInheritedLocked = currentLocked
            )
        )

        if (b.children.isNotEmpty()) {
            result.addAll(
                collectRenderableBlocks(
                    blocks = b.children,
                    parentOffsetX = absLeft,
                    parentOffsetY = absTop,
                    parentLocked = currentLocked,
                    parentVisible = currentVisible,
                    parentOpacity = currentCumulativeOpacity
                )
            )
        }
    }
    return result
}

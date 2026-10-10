package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val currentView = state.project?.pages?.find { it.id == state.currentViewId }
        ?: state.project?.pages?.firstOrNull()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 默认自适应居中联动
    LaunchedEffect(containerSize, currentView?.id, state.isFitToViewport) {
        if (state.isFitToViewport && containerSize.width > 0 && containerSize.height > 0) {
            viewModel.fitToViewport(containerSize.width.toFloat(), containerSize.height.toFloat())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // 深色工业底色
            .clipToBounds()
            .onSizeChanged { containerSize = it }
    ) {
        // 画布交互与手势层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentView?.id, state.panOffset, state.scale) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val currentV = currentView ?: return@detectTapGestures
                            // 将点击绝对坐标通过当前平移与缩放矩阵逆映射至视图逻辑坐标
                            val logicalX = (tapOffset.x - state.panOffset.x) / state.scale
                            val logicalY = (tapOffset.y - state.panOffset.y) / state.scale

                            // 顶层优先倒序命中测试
                            val hitBlock = currentV.blocks.findLast { b ->
                                val r = b.bounds
                                logicalX >= r.left && logicalX <= r.right &&
                                logicalY >= r.top && logicalY <= r.bottom
                            }
                            viewModel.selectBlock(hitBlock?.id)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (zoom != 1.0f) {
                            viewModel.onZoom(zoom, centroid)
                        }
                        if (pan.x != 0f || pan.y != 0f) {
                            viewModel.onPan(pan)
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
                            width = currentView.width.dp,
                            height = currentView.height.dp
                        )
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(2.dp),
                            ambientColor = Color.Black.copy(alpha = 0.6f),
                            spotColor = Color.Black.copy(alpha = 0.9f)
                        )
                ) {
                    val blocks = currentView.blocks
                    if (blocks.isEmpty()) {
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
                        // 渲染该视图中的所有图元实体
                        Box(modifier = Modifier.fillMaxSize()) {
                            blocks.forEach { block ->
                                UIStudioBlockItem(
                                    block = block,
                                    isSelected = block.id == state.selectedBlockId,
                                    onClick = { viewModel.selectBlock(block.id) }
                                )
                            }
                        }
                    }
                }
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
    }
}

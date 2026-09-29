package org.gemini.ui.forge.ui.dialog.layer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.service.TemplateOverlayRenderer
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 实时模板模块分布图纯内存离屏预览弹窗 (OverlayPreviewDialog)
 *
 * 遵循 0.96f 近全屏视口规格与一文件一 Composable 铁律。
 * 纯内存离屏渲染当前页面最新图元分布标注图（与 overlay_latest.png 渲染逻辑 100% 同源且不落盘）。
 * 提供左侧扁平渲染顺序模块显隐控制列表（支持独立隐藏、Solo 隔离模式、全显/全隐）与右侧 1:1 默认原寸宽屏画板（支持滚轮缩放与拖拽平移）。
 *
 * @param projectState 当前模板工程状态
 * @param onDismiss 关闭弹窗回调
 */
@Composable
fun OverlayPreviewDialog(
    projectState: ProjectState,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    var isLoading by remember { mutableStateOf(true) }
    var renderResult by remember { mutableStateOf<TemplateOverlayRenderer.RenderMemoryResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 被隐藏图元的 ID 集合（不参与标注框线绘制）
    var hiddenBlockIds by remember { mutableStateOf(setOf<String>()) }

    // 会话级预解码底图缓存（弹窗打开时解码一次，避免反复磁盘 I/O 与大图解码）
    var cachedBaseImage by remember { mutableStateOf<SkiaImage?>(null) }

    // 弹窗首次就绪时后台异步预解码底图并长期缓存
    LaunchedEffect(projectState) {
        if (cachedBaseImage == null) {
            val base = withContext(Dispatchers.Default) {
                TemplateOverlayRenderer.loadBaseImage(projectState)
            }
            cachedBaseImage = base
        }
    }

    // 弹窗销毁时释放 Skia 底图资源
    DisposableEffect(Unit) {
        onDispose {
            try {
                cachedBaseImage?.close()
            } catch (_: Throwable) {}
        }
    }

    // 视口缩放与平移状态（默认 1:1 原寸呈现）
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // 扁平图元列表（按实际渲染顺序 DFS 深度优先先序遍历）
    val page = projectState.pages.firstOrNull()
    val flatBlocks: List<UIBlock> = remember(page) {
        if (page == null) emptyList()
        else {
            val list = mutableListOf<UIBlock>()
            fun traverse(blocks: List<UIBlock>) {
                for (b in blocks) {
                    list.add(b)
                    traverse(b.children)
                }
            }
            traverse(page.blocks.bindParents())
            list
        }
    }

    // 纯内存离屏渲染执行（响应工程状态、显隐集合变动及底图缓存就绪）
    LaunchedEffect(projectState, hiddenBlockIds, cachedBaseImage) {
        if (renderResult == null) {
            isLoading = true
        }
        errorMessage = null
        try {
            val result = withContext(Dispatchers.Default) {
                TemplateOverlayRenderer.renderOverlayMemory(
                    projectState = projectState,
                    hiddenBlockIds = hiddenBlockIds,
                    baseImage = cachedBaseImage,
                    encodePng = false
                )
            }
            if (result != null) {
                renderResult = result
            } else {
                errorMessage = "Render result is null"
            }
        } catch (e: Throwable) {
            errorMessage = e.message ?: "Unknown error"
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.medium)
            ) {
                // 1. 顶部 Header 与图元统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(Res.string.overlay_preview_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            renderResult?.let { res ->
                                Text(
                                    text = stringResource(
                                        Res.string.overlay_preview_stats,
                                        res.totalBlocks,
                                        res.businessCount,
                                        res.containerCount,
                                        res.canvasWidth,
                                        res.canvasHeight
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 视口调节与关闭按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        // 图例标签
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = stringResource(Res.string.overlay_preview_legend_business),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = Color(0xFF00FFFF)
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = Color(0xFF00FFFF).copy(alpha = 0.5f)
                            )
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = stringResource(Res.string.overlay_preview_legend_container),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = Color(0xFFFF7A00)
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = Color(0xFFFF7A00).copy(alpha = 0.5f)
                            )
                        )

                        Spacer(Modifier.width(spacing.small))

                        // 缩放百分比指示胶囊
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${(scale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // 1:1 原寸归位胶囊按钮
                        OutlinedButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .tip(stringResource(Res.string.overlay_preview_zoom_reset)),
                            shape = AppShapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "1:1",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.tip("关闭")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }

                Spacer(Modifier.height(spacing.small))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(spacing.small))

                // 2. 主体左右分栏布局（左侧扁平列表 + 右侧 1:1 原寸画板）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // 2.1 左侧面板：按渲染顺序排列的扁平图元列表
                    Surface(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                        shape = AppShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.small)
                        ) {
                            // 侧边栏工具栏
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(Res.string.overlay_preview_layer_order)} (${flatBlocks.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // 全部显示按钮
                                    TextButton(
                                        onClick = { hiddenBlockIds = emptySet() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp).tip(stringResource(Res.string.overlay_preview_show_all))
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.overlay_preview_show_all),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    // 全部隐藏按钮
                                    TextButton(
                                        onClick = {
                                            hiddenBlockIds = flatBlocks.map { it.id }.toSet()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp).tip(stringResource(Res.string.overlay_preview_hide_all))
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.overlay_preview_hide_all),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(spacing.extraSmall))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(spacing.extraSmall))

                            // 图元列表行项
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(flatBlocks, key = { it.id }) { block ->
                                    val isHidden = block.id in hiddenBlockIds
                                    val isBusiness = !block.isPureContainer && block.type != UIBlockType.CONTAINER && block.type != UIBlockType.BACKGROUND
                                    val isContainer = block.isPureContainer || block.type == UIBlockType.CONTAINER
                                    val indicatorColor = when {
                                        block.type == UIBlockType.BACKGROUND -> Color(0xFF9C27B0)
                                        isContainer -> Color(0xFFFF7A00)
                                        else -> Color(0xFF00FFFF)
                                    }

                                    Surface(
                                        shape = AppShapes.small,
                                        color = if (isHidden) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isHidden) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                hiddenBlockIds = if (isHidden) {
                                                    hiddenBlockIds - block.id
                                                } else {
                                                    hiddenBlockIds + block.id
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 显隐眼睛开关
                                            IconButton(
                                                onClick = {
                                                    hiddenBlockIds = if (isHidden) {
                                                        hiddenBlockIds - block.id
                                                    } else {
                                                        hiddenBlockIds + block.id
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp).tip(if (isHidden) "显示模块标注" else "隐藏模块标注")
                                            ) {
                                                Icon(
                                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isHidden) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                            }

                                            // 类型彩色指示条
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 4.dp, height = 22.dp)
                                                    .background(
                                                        color = if (isHidden) indicatorColor.copy(alpha = 0.3f) else indicatorColor,
                                                        shape = AppShapes.small
                                                    )
                                            )

                                            // 模块名称与 ID
                                            val displayName = block.userPromptZh.ifBlank { block.id }
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isHidden) FontWeight.Normal else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isHidden) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = block.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHidden) 0.3f else 0.6f)
                                                )
                                            }

                                            // Solo 仅看此项按钮
                                            Text(
                                                text = stringResource(Res.string.overlay_preview_solo),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isHidden) 0.5f else 0.9f),
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                        shape = AppShapes.small
                                                    )
                                                    .clickable {
                                                        val allOtherIds = flatBlocks.map { it.id }.filter { it != block.id }.toSet()
                                                        hiddenBlockIds = if (hiddenBlockIds == allOtherIds) {
                                                            emptySet() // 再次点击解除 Solo 恢复全显
                                                        } else {
                                                            allOtherIds // 仅保留当前模块
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .tip("仅保留当前模块标注，隐藏其它模块")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2.2 右侧面板：主画板 (默认 1:1 原寸 + 滚轮无级缩放 + 拖拽自由平移)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .background(Color(0xFF141418), shape = AppShapes.medium)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                            if (delta != 0f) {
                                                val factor = if (delta < 0) 1.15f else 0.85f
                                                scale = (scale * factor).coerceIn(0.05f, 10.0f)
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoading && renderResult == null -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = stringResource(Res.string.overlay_preview_rendering),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            renderResult != null -> {
                                val bitmap = renderResult!!.imageBitmap
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = stringResource(Res.string.overlay_preview_title),
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                )
                            }

                            else -> {
                                Text(
                                    text = errorMessage ?: stringResource(Res.string.overlay_preview_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.extraSmall))

                // 3. 底部轻量提示（表明零磁盘 I/O）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 纯内存离屏渲染预览，与 overlay_latest.png 算法 100% 同源，不修改任何磁盘文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "默认 1:1 原寸呈现，支持鼠标滚轮平滑缩放与拖拽平移",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

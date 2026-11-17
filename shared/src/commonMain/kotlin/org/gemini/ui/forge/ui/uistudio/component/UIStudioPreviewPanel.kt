package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.extend.copyOnClick
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.AppIconButtonVariant
import org.gemini.ui.forge.ui.component.chat.CommonImageLightbox
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 右侧独立预览面板组件 (Preview Panel)。
 *
 * 遵循一文件一 Composable 铁律：
 * - 选中素材库图片时：展示真实高清位图与分辨率宽高信息；
 * - 选中图层父组件时：直接以微缩画布渲染该父组件内部子布局的整体排版效果；
 * - 选中普通图元组件时：展示组件切片画面与几何属性；
 * - 未选择任何对象时：呈现优雅的未选择引导空状态。
 * @param assetPathResolver 哈希→素材相对路径解析器（素材库唯一账本）
 */
@Composable
fun UIStudioPreviewPanel(
    viewModel: UIStudioViewModel,
    assetPathResolver: (String?) -> String? = { null },
    assetUpdatedAtResolver: (String?) -> Long? = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.updatedAt },
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    val spacing = LocalAppSpacing.current
    val currentView = state.currentView

    // 优先判定当前选中的目标：素材优先或组件
    val selectedAsset = remember(state.selectedAssetHash, state.assetLibrary.items) {
        state.selectedAssetHash?.let { hash -> state.assetLibrary.items.find { it.hash == hash } }
    }
    val selectedBlock = remember(state.selectedBlockId, currentView?.blocks) {
        state.selectedBlockId?.let { id -> currentView?.blocks?.findBlockById(id) as? UIStudioBlock }
    }
    val studioPages = state.project?.studioPages ?: emptyList()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部 Header 标题栏
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "实时预览",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "实时预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 状态徽标提示
                    when {
                        selectedAsset != null -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "素材切片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        selectedBlock != null && (selectedBlock.type == org.gemini.ui.forge.model.ui.UIBlockType.COMPONENT || selectedBlock.type == org.gemini.ui.forge.model.ui.UIBlockType.LIST || selectedBlock.children.isNotEmpty() || selectedBlock.targetPageId != null) -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "父组件 · ${selectedBlock.children.size}个子层",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        selectedBlock != null -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "图元组件",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp
            )

            // 预览主体内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(spacing.medium),
                contentAlignment = Alignment.Center
            ) {
                when {
                    selectedAsset != null -> {
                        // 1. 预览选中的素材图片与详细尺寸
                        AssetPreviewView(asset = selectedAsset)
                    }
                    selectedBlock != null && (selectedBlock.type == org.gemini.ui.forge.model.ui.UIBlockType.COMPONENT || selectedBlock.type == org.gemini.ui.forge.model.ui.UIBlockType.LIST || selectedBlock.children.isNotEmpty() || selectedBlock.targetPageId != null) -> {
                        // 2. 预览选中的父组件渲染效果（递归穿透平级页面引用与嵌套子布局，渲染真实切片）
                        ContainerPreviewView(
                            container = selectedBlock,
                            studioPages = studioPages,
                            assetPathResolver = assetPathResolver,
                            assetUpdatedAtResolver = assetUpdatedAtResolver
                        )
                    }
                    selectedBlock != null -> {
                        // 3. 预览选中的普通组件切片
                        BlockPreviewView(
                            block = selectedBlock,
                            assetPathResolver = assetPathResolver,
                            assetUpdatedAtResolver = assetUpdatedAtResolver
                        )
                    }
                    else -> {
                        // 4. 空状态
                        EmptyPreviewPlaceholder()
                    }
                }
            }
        }
    }
}

/**
 * 素材切片预览渲染视图。
 */
@Composable
private fun AssetPreviewView(asset: UIAssetItem) {
    var bitmap by remember(asset.relativePath, asset.updatedAt) { mutableStateOf<ImageBitmap?>(null) }
    var showLightbox by remember { mutableStateOf(false) }

    LaunchedEffect(asset.relativePath, asset.updatedAt) {
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(asset.relativePath)
                ?: try {
                    TemplateFile(asset.relativePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            if (bytes != null && bytes.isNotEmpty()) {
                bitmap = bytes.toImageBitmap()
            } else {
                bitmap = null
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        // 图片画面预览视口（支持点击全屏原寸放大）
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = bitmap != null) { showLightbox = true },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = asset.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )

                    // 右上角浮现放大查看快捷按钮
                    AppIconButton(
                        imageVector = Icons.Default.ZoomIn,
                        tooltip = stringResource(Res.string.preview_open_lightbox),
                        tier = AppIconButtonTier.COMPACT,
                        variant = AppIconButtonVariant.FILLED_TONAL,
                        onClick = { showLightbox = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 下方素材信息卡片（展示名称、Hash、素材库相对路径与尺寸）
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${asset.width} × ${asset.height} px",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 结构化素材库相对路径展示（支持点击一键复制）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .copyOnClick(asset.relativePath, stringResource(Res.string.preview_path_copied))
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = stringResource(Res.string.preview_relative_path),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = asset.relativePath,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .tip("点击复制相对路径: ${asset.relativePath}")
                    )
                }

                // Hash
                Text(
                    text = "Hash: ${asset.hash}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .copyOnClick(asset.hash, "已复制素材 Hash")
                )
            }
        }
    }

    // 唤起沉浸式原寸原图灯箱
    if (showLightbox) {
        val absolutePath = remember(asset.relativePath) {
            TemplateFile(asset.relativePath).getAbsolutePath()
        }
        CommonImageLightbox(
            imageUri = absolutePath,
            onDismiss = { showLightbox = false }
        )
    }
}

/**
 * 递归收集容器内所有可渲染的切片图元（自动穿透平级页面引用 targetPageId 与嵌套容器 children）。
 * 计算每个图元相对于根容器左上角 (0, 0) 的绝对偏移量 (relLeft, relTop)。
 */
private fun collectRenderablePreviewBlocks(
    blocks: List<UIStudioBlock>,
    availablePages: List<UIStudioPage>,
    parentOffsetX: Float = 0f,
    parentOffsetY: Float = 0f,
    parentOpacity: Float = 1.0f,
    visitedPageIds: Set<String> = emptySet()
): List<UIStudioBlock> {
    val result = mutableListOf<UIStudioBlock>()
    for (b in blocks) {
        if (!b.isVisible) continue

        val relLeft = parentOffsetX + b.bounds.left
        val relTop = parentOffsetY + b.bounds.top
        val relW = (b.bounds.right - b.bounds.left).coerceAtLeast(1f)
        val relH = (b.bounds.bottom - b.bounds.top).coerceAtLeast(1f)
        val relBounds = SerialRect(relLeft, relTop, relLeft + relW, relTop + relH)
        val cumulativeOpacity = (parentOpacity * b.effectiveOpacity).coerceIn(0.0f, 1.0f)

        val targetId = b.targetPageId
        if (!targetId.isNullOrBlank()) {
            val refPage = availablePages.find { it.id == targetId }
            if (refPage != null && !visitedPageIds.contains(targetId)) {
                // 递归穿透引用页面内部的 blocks
                result.addAll(
                    collectRenderablePreviewBlocks(
                        blocks = refPage.blocks,
                        availablePages = availablePages,
                        parentOffsetX = relLeft,
                        parentOffsetY = relTop,
                        parentOpacity = cumulativeOpacity,
                        visitedPageIds = visitedPageIds + targetId
                    )
                )
            } else {
                // 若找不到引用页面，保留当前占位
                result.add(
                    b.copy(
                        bounds = relBounds,
                        opacity = cumulativeOpacity
                    )
                )
            }
        } else if (b.children.isNotEmpty()) {
            // 嵌套容器继续递归
            result.addAll(
                collectRenderablePreviewBlocks(
                    blocks = b.children,
                    availablePages = availablePages,
                    parentOffsetX = relLeft,
                    parentOffsetY = relTop,
                    parentOpacity = cumulativeOpacity,
                    visitedPageIds = visitedPageIds
                )
            )
        } else {
            // 真实叶子图元（持有 assetHash 或具体组件）
            result.add(
                b.copy(
                    bounds = relBounds,
                    opacity = cumulativeOpacity
                )
            )
        }
    }
    return result
}

/**
 * 父组件整体渲染效果预览视图（支持内部子组件微缩排版，遇到平级引用页面时自动递归穿透渲染最终切片效果）。
 */
@Composable
private fun ContainerPreviewView(
    container: UIStudioBlock,
    studioPages: List<UIStudioPage>,
    assetPathResolver: (String?) -> String?,
    assetUpdatedAtResolver: (String?) -> Long? = { null }
) {
    val boundPage = remember(container.targetPageId, studioPages) {
        container.targetPageId?.let { pageId -> studioPages.find { it.id == pageId } }
    }
    val rawW = if (container.bounds.width > 0f) container.bounds.width else (boundPage?.width ?: 100f)
    val rawH = if (container.bounds.height > 0f) container.bounds.height else (boundPage?.height ?: 100f)
    val containerW = rawW.coerceAtLeast(1f)
    val containerH = rawH.coerceAtLeast(1f)

    val initialBlocks = remember(container.id, container.targetPageId, container.children, studioPages) {
        if (!container.targetPageId.isNullOrBlank()) {
            boundPage?.blocks ?: container.children
        } else {
            container.children
        }
    }

    val renderableChildren = remember(initialBlocks, studioPages, container.effectiveOpacity) {
        collectRenderablePreviewBlocks(
            blocks = initialBlocks,
            availablePages = studioPages,
            parentOffsetX = 0f,
            parentOffsetY = 0f,
            parentOpacity = container.effectiveOpacity
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        // 父容器微缩排版画布
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val availW = maxWidth.value
                val availH = maxHeight.value
                val scale = minOf(availW / containerW, availH / containerH)

                Box(
                    modifier = Modifier
                        .size((containerW * scale).dp, (containerH * scale).dp)
                        .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                ) {
                    // 渲染容器内部递归展开的所有真实子图元（异步加载切片图片，级联继承透明度）
                    renderableChildren.forEach { child ->
                        if (child.isVisible) {
                            UIStudioChildPreviewThumbnail(
                                child = child,
                                scale = scale,
                                inheritedOpacity = 1.0f,
                                assetPathResolver = assetPathResolver,
                                assetUpdatedAtResolver = assetUpdatedAtResolver
                            )
                        }
                    }
                }
            }
        }

        // 下方容器信息卡片
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!container.targetPageId.isNullOrBlank()) "组件: ${container.id} (${container.targetPageId})" else "组件: ${container.id}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!container.targetPageId.isNullOrBlank()) "引用页面 · 渲染 ${renderableChildren.size} 个图元" else "包含 ${container.children.size} 个子组件 · 渲染 ${renderableChildren.size} 个图元",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${containerW.toInt()} × ${containerH.toInt()} px",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 普通组件切片预览视图。
 */
@Composable
private fun BlockPreviewView(
    block: UIStudioBlock,
    assetPathResolver: (String?) -> String?,
    assetUpdatedAtResolver: (String?) -> Long? = { null }
) {
    // 哈希引用模型：图元仅持有 assetHash，路径由素材库动态解析
    val imagePath = assetPathResolver(block.assetHash)
    val assetUpdatedAt = assetUpdatedAtResolver(block.assetHash)
    var bitmap by remember(imagePath, assetUpdatedAt) { mutableStateOf<ImageBitmap?>(null) }
    var showLightbox by remember { mutableStateOf(false) }
    val blockW = (block.bounds.right - block.bounds.left).toInt()
    val blockH = (block.bounds.bottom - block.bounds.top).toInt()

    LaunchedEffect(imagePath, assetUpdatedAt) {
        if (!imagePath.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                val bytes = readLocalFileBytes(imagePath)
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !imagePath.isNullOrBlank() && bitmap != null) { showLightbox = true },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = block.id,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )

                    // 右上角浮现放大查看快捷按钮
                    AppIconButton(
                        imageVector = Icons.Default.ZoomIn,
                        tooltip = stringResource(Res.string.preview_open_lightbox),
                        tier = AppIconButtonTier.COMPACT,
                        variant = AppIconButtonVariant.FILLED_TONAL,
                        onClick = { showLightbox = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "组件: ${block.id}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        val origW = bitmap?.width ?: blockW
                        val origH = bitmap?.height ?: blockH
                        Text(
                            text = "$origW × $origH px",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 结构化素材库相对路径展示（若存在素材关联）
                if (!imagePath.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .copyOnClick(imagePath, stringResource(Res.string.preview_path_copied))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(Res.string.preview_relative_path),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = imagePath,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .tip("点击复制相对路径: $imagePath")
                        )
                    }
                }

                Text(
                    text = "类型: ${block.type.name}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }

    // 唤起沉浸式原寸原图灯箱
    if (showLightbox && !imagePath.isNullOrBlank()) {
        val absolutePath = remember(imagePath) {
            if (imagePath.startsWith("/") || (imagePath.length > 2 && imagePath[1] == ':')) {
                imagePath
            } else {
                TemplateFile(imagePath).getAbsolutePath()
            }
        }
        CommonImageLightbox(
            imageUri = absolutePath,
            onDismiss = { showLightbox = false }
        )
    }
}

/**
 * 未选择对象时的引导空状态。
 */
@Composable
private fun EmptyPreviewPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "未选择预览对象",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "在左侧素材库或图层树中选择一项\n即可查看高保真预览与结构效果",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 16.sp
        )
    }
}

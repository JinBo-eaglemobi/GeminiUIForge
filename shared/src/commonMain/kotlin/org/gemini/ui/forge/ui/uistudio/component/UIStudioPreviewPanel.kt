package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap

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
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    val spacing = LocalAppSpacing.current
    val currentView = state.currentView

    // 优先判定当前选中的目标：素材优先或组件
    val selectedAsset = remember(state.selectedAssetId, state.assetLibrary.items) {
        state.selectedAssetId?.let { id -> state.assetLibrary.items.find { it.id == id } }
    }
    val selectedBlock = remember(state.selectedBlockId, currentView?.blocks) {
        state.selectedBlockId?.let { id -> currentView?.blocks?.findBlockById(id) as? UIStudioBlock }
    }

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
                        selectedBlock != null && (selectedBlock.isPureContainer || selectedBlock.children.isNotEmpty() || selectedBlock.type == UIBlockType.CONTAINER) -> {
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
                    selectedBlock != null && (selectedBlock.isPureContainer || selectedBlock.children.isNotEmpty() || selectedBlock.type == UIBlockType.CONTAINER) -> {
                        // 2. 预览选中的父组件渲染效果（渲染内部子布局）
                        ContainerPreviewView(container = selectedBlock, assetPathResolver = assetPathResolver)
                    }
                    selectedBlock != null -> {
                        // 3. 预览选中的普通组件切片
                        BlockPreviewView(block = selectedBlock, assetPathResolver = assetPathResolver)
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
    var bitmap by remember(asset.relativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(asset.relativePath) {
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(asset.relativePath)
                ?: try {
                    TemplateFile(asset.relativePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            if (bytes != null) {
                bitmap = bytes.toImageBitmap()
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        // 图片画面预览视口
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
                modifier = Modifier.fillMaxSize(),
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
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 下方素材信息卡片
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
                        text = asset.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${asset.id}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
        }
    }
}

/**
 * 父组件整体渲染效果预览视图（支持内部子组件微缩排版）。
 */
@Composable
private fun ContainerPreviewView(
    container: UIStudioBlock,
    assetPathResolver: (String?) -> String?
) {
    val containerW = container.bounds.width.coerceAtLeast(1f)
    val containerH = container.bounds.height.coerceAtLeast(1f)

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
                    // 渲染父容器内部的所有子组件（异步加载真实切片图片，级联继承父容器透明度）
                    container.children.forEach { child ->
                        if (child.isVisible) {
                            UIStudioChildPreviewThumbnail(
                                child = child,
                                scale = scale,
                                inheritedOpacity = container.effectiveOpacity,
                                assetPathResolver = assetPathResolver
                            )
                        }
                    }
                }
            }
        }

        // 下方父容器信息卡片
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
                        text = "父容器: ${container.id}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "包含 ${container.children.size} 个子组件",
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
    assetPathResolver: (String?) -> String?
) {
    // 哈希引用模型：图元仅持有 assetHash，路径由素材库动态解析
    val imagePath = assetPathResolver(block.assetHash)
    var bitmap by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    val blockW = (block.bounds.right - block.bounds.left).toInt()
    val blockH = (block.bounds.bottom - block.bounds.top).toInt()

    LaunchedEffect(imagePath) {
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
                modifier = Modifier.fillMaxSize(),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "组件: ${block.id}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "类型: ${block.type.name}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "$blockW × $blockH px",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
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

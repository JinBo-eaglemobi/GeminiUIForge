package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.calculateBlockAbsolutePosition
import org.gemini.ui.forge.utils.calculateBlockParentOffset
import org.gemini.ui.forge.utils.updateBlockInList
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * 批量资源生成对话框（宽屏呼吸感与等比缩略图优化版）。
 *
 * 允许用户浏览所有支持资源生成的 UI 模块，按照模块类型进行分组显示。
 * 每一行以独立卡片形式舒展展示该模块在原始参考图上的全局绝对映射切片缩略图（等比 Fit 居中无变形），
 * 并提供放大的"模块细化编辑"入口。
 *
 * 核心规范落地：
 * 1. 采用 92% 宽屏舒展排版，彻底告别紧凑拥挤；
 * 2. 依据全局绝对物理坐标从原底图裁剪切片，并以 ContentScale.Fit 算法等比居中渲染，彻底杜绝拉伸变形；
 * 3. 细化编辑按钮放大一倍（48dp 按钮 + 28dp 图标），并挂载 [Modifier.tip] 悬浮提示；
 * 4. 列表项间距提升至 8dp，卡片内边距提升至 16dp x 10dp，层次分明且呼吸感十足；
 * 5. 保存细化修改时使用 [updateBlockInList] 递归同步更新整棵模块树。
 *
 * @param blocks 需要展示供用户选择的 UI 模块列表
 * @param imageUri 原参考图底图对象
 * @param pageWidth 画布页面逻辑总宽
 * @param pageHeight 画布页面逻辑总高
 * @param onCancel 点击取消或关闭对话框时的回调
 * @param onStartGen 点击开始生成按钮时的回调
 * @param onUpdateBlock 用户在细化编辑弹窗中修改模块属性后的同步回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAssetGenDialog(
    blocks: List<UIBlock>,
    imageUri: TemplateFile? = null,
    pageWidth: Float = 1080f,
    pageHeight: Float = 1920f,
    onCancel: () -> Unit,
    onStartGen: (List<UIBlock>) -> Unit,
    onUpdateBlock: (UIBlock) -> Unit = {}
) {
    // 1. 内部维护 blocks 状态，支持在细化编辑后局部实时刷新
    var currentBlocks by remember(blocks) { mutableStateOf(blocks) }
    var selectedIds by remember(blocks) { mutableStateOf(blocks.map { it.id }.toSet()) }
    val groupedBlocks = remember(currentBlocks) { currentBlocks.groupBy { it.type } }

    // 2. 分类折叠状态：记录哪些类型处于展开状态（默认全展开）
    var expandedTypes by remember { mutableStateOf(groupedBlocks.keys.toSet()) }
    val listState = rememberLazyListState()
    val spacing = LocalAppSpacing.current

    // 3. 当前正在细化编辑的目标模块（非空时拉起 BlockRefinementDialog 弹窗）
    var refiningBlock by remember { mutableStateOf<UIBlock?>(null) }

    // 4. 预解码参考图底图 Bitmap，用于绘制高质量局部切片
    val referenceUriStr = imageUri?.getAbsolutePath()
    val refBitmapState = produceState<ImageBitmap?>(null, referenceUriStr) {
        value = referenceUriStr?.decodeBase64ToBitmap()
    }
    val refBitmap = refBitmapState.value

    BasicAlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Surface(
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(spacing.large)) {
                    // 对话框主标题
                    Text(
                        text = stringResource(Res.string.batch_gen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // 说明提示文本
                    Text(
                        text = stringResource(Res.string.batch_gen_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.extraSmall, bottom = spacing.medium)
                    )

                    // 快捷操作栏：全选/全取消 + 全部展开/全部折叠
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                            TextButton(
                                onClick = { selectedIds = currentBlocks.map { it.id }.toSet() },
                                modifier = Modifier.tip(stringResource(Res.string.batch_gen_select_all))
                            ) {
                                Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(stringResource(Res.string.batch_gen_select_all))
                            }
                            TextButton(
                                onClick = { selectedIds = emptySet() },
                                modifier = Modifier.tip(stringResource(Res.string.batch_gen_deselect_all))
                            ) {
                                Icon(Icons.Default.Deselect, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(stringResource(Res.string.batch_gen_deselect_all))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                            TextButton(
                                onClick = { expandedTypes = groupedBlocks.keys.toSet() },
                                modifier = Modifier.tip("展开所有分类")
                            ) {
                                Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text("全部展开")
                            }
                            TextButton(
                                onClick = { expandedTypes = emptySet() },
                                modifier = Modifier.tip("折叠所有分类")
                            ) {
                                Icon(Icons.Default.UnfoldLess, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text("全部折叠")
                            }
                        }
                    }

                    if (currentBlocks.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.batch_gen_empty))
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(end = spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = spacing.small)
                            ) {
                                groupedBlocks.forEach { (type, typeBlocks) ->
                                    val isExpanded = type in expandedTypes

                                    // 分组标题栏
                                    item(key = type.name) {
                                        val allOfTypeSelected = typeBlocks.all { it.id in selectedIds }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(AppShapes.small)
                                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                                                .clickable {
                                                    expandedTypes =
                                                        if (isExpanded) expandedTypes - type else expandedTypes + type
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 分组折叠展开指示图标
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )

                                            Spacer(Modifier.width(spacing.extraSmall))

                                            // 分组批量全选复选框
                                            IconButton(
                                                onClick = {
                                                    val ids = typeBlocks.map { it.id }
                                                    selectedIds =
                                                        if (allOfTypeSelected) selectedIds - ids.toSet() else selectedIds + ids
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .tip("勾选/取消该分类下全部模块")
                                            ) {
                                                Icon(
                                                    imageVector = if (allOfTypeSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = null,
                                                    tint = if (allOfTypeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Text(
                                                text = type.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = "${typeBlocks.count { it.id in selectedIds }}/${typeBlocks.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // 分类下展开的具体模块卡片项列表
                                    if (isExpanded) {
                                        items(typeBlocks, key = { it.id }) { block ->
                                            val isSelected = block.id in selectedIds

                                            // 累加父级绝对坐标：计算该模块在当前全局画布上的真实绝对物理坐标
                                            val absPos = currentBlocks.calculateBlockAbsolutePosition(block.id)
                                            val absX = absPos?.x ?: block.bounds.left
                                            val absY = absPos?.y ?: block.bounds.top
                                            val blockWidth = max(1f, block.bounds.width)
                                            val blockHeight = max(1f, block.bounds.height)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp)
                                                    .clip(AppShapes.medium)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                        shape = AppShapes.medium
                                                    )
                                                    .clickable {
                                                        selectedIds =
                                                            if (isSelected) selectedIds - block.id else selectedIds + block.id
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 单项勾选图标
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )

                                                Spacer(Modifier.width(14.dp))

                                                // 模块在原参考图上的局部映射切片缩略图（等比 Fit 居中无变形）
                                                BlockReferenceThumbnail(
                                                    absX = absX,
                                                    absY = absY,
                                                    absWidth = blockWidth,
                                                    absHeight = blockHeight,
                                                    refBitmap = refBitmap,
                                                    pageWidth = pageWidth,
                                                    pageHeight = pageHeight,
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(AppShapes.small)
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), AppShapes.small)
                                                )

                                                Spacer(Modifier.width(16.dp))

                                                // 模块信息文本列（展示全局绝对物理坐标）
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = block.id,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                            shape = AppShapes.small
                                                        ) {
                                                            Text(
                                                                text = block.type.name,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "位置: (${absX.toInt()}, ${absY.toInt()})  尺寸: ${blockWidth.toInt()} x ${blockHeight.toInt()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (block.userPrompt.isNotBlank()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = block.userPrompt,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.width(12.dp))

                                                // 细化编辑按钮：尺寸放大一倍（48dp 按钮 / 28dp 矢量图标），醒目且易于点击
                                                IconButton(
                                                    onClick = { refiningBlock = block },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.medium)
                                                        .tip(stringResource(Res.string.batch_gen_refine_block))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = stringResource(Res.string.batch_gen_refine_block),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 右侧垂直滚动条
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                scrollState = listState
                            )
                        }
                    }

                    // 底部取消与提交操作栏
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.large),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCancel,
                            shape = AppShapes.medium,
                            modifier = Modifier.tip(stringResource(Res.string.dialog_action_cancel))
                        ) {
                            Text(stringResource(Res.string.dialog_action_cancel))
                        }
                        Spacer(Modifier.width(spacing.small))
                        Button(
                            onClick = { onStartGen(currentBlocks.filter { it.id in selectedIds }) },
                            enabled = selectedIds.isNotEmpty(),
                            shape = AppShapes.medium,
                            modifier = Modifier.tip(stringResource(Res.string.batch_gen_start))
                        ) {
                            Text(stringResource(Res.string.batch_gen_start))
                        }
                    }
                }
            }
        }
    )

    // 模块细化编辑弹窗（基于父级累计绝对偏移量传入，内部全绝对坐标计算，保存时逆向还原相对坐标并递归同步）
    refiningBlock?.let { targetBlock ->
        val parentOffset = currentBlocks.calculateBlockParentOffset(targetBlock.id)
        BlockRefinementDialog(
            block = targetBlock,
            parentOffset = parentOffset,
            imageUri = imageUri,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            onDismiss = { refiningBlock = null },
            onConfirm = { updatedBlock ->
                currentBlocks = currentBlocks.updateBlockInList(updatedBlock.id) { updatedBlock }
                onUpdateBlock(updatedBlock)
                refiningBlock = null
            }
        )
    }
}

/**
 * 模块在原参考图上的局部映射切片缩略图组件（等比 Fit 居中无变形算法）。
 *
 * @param absX 模块在画布上的全局绝对物理 X 坐标
 * @param absY 模块在画布上的全局绝对物理 Y 坐标
 * @param absWidth 模块绝对物理宽度
 * @param absHeight 模块绝对物理高度
 * @param refBitmap 原参考底图 Bitmap 对象
 * @param pageWidth 画布页面逻辑总宽
 * @param pageHeight 画布页面逻辑总高
 * @param modifier 外部布局修饰符
 */
@Composable
private fun BlockReferenceThumbnail(
    absX: Float,
    absY: Float,
    absWidth: Float,
    absHeight: Float,
    refBitmap: ImageBitmap?,
    pageWidth: Float,
    pageHeight: Float,
    modifier: Modifier = Modifier
) {
    if (refBitmap == null) {
        // 无底图时的通用占位底座
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Canvas(modifier = modifier.background(Color(0xFF141414))) {
            val imgW = refBitmap.width.toFloat()
            val imgH = refBitmap.height.toFloat()

            // 1. 依据模块的全局绝对物理坐标计算在底图原始像素中的切片区域
            val normL = (absX / pageWidth).coerceIn(0f, 1f)
            val normT = (absY / pageHeight).coerceIn(0f, 1f)
            val normW = (absWidth / pageWidth).coerceIn(0f, 1f - normL)
            val normH = (absHeight / pageHeight).coerceIn(0f, 1f - normT)

            val srcX = (normL * imgW).toInt()
            val srcY = (normT * imgH).toInt()
            val srcW = max(1, (normW * imgW).toInt())
            val srcH = max(1, (normH * imgH).toInt())

            // 2. 标准 ContentScale.Fit 算法：等比自适应缩放并自动居中，彻底消除拉扯变形
            val srcAspect = srcW.toFloat() / srcH.toFloat()
            val canvasAspect = size.width / size.height

            val dstW: Float
            val dstH: Float
            val dstOffsetX: Float
            val dstOffsetY: Float

            if (srcAspect > canvasAspect) {
                // 切片较宽（扁长）：以宽度铺满，高度居中留边
                dstW = size.width
                dstH = size.width / srcAspect
                dstOffsetX = 0f
                dstOffsetY = (size.height - dstH) / 2f
            } else {
                // 切片较高（竖长）：以高度铺满，宽度居中留边
                dstH = size.height
                dstW = size.height * srcAspect
                dstOffsetX = (size.width - dstW) / 2f
                dstOffsetY = 0f
            }

            // 3. 执行局部像素绘制
            drawImage(
                image = refBitmap,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(dstOffsetX.toInt(), dstOffsetY.toInt()),
                dstSize = IntSize(max(1, dstW.toInt()), max(1, dstH.toInt()))
            )
        }
    }
}

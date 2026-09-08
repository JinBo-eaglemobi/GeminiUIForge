package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.TextStyled
import org.gemini.ui.forge.model.ui.NinePatchConfig
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.utils.decodeToBitmap
import org.gemini.ui.forge.utils.shouldDim
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * 将垂直方向轴映射为 Compose Alignment
 */
fun parseVerticalAlignment(vertical: String): Alignment {
    return when (vertical) {
        "TOP" -> Alignment.TopCenter
        "CENTER" -> Alignment.Center
        "BOTTOM" -> Alignment.BottomCenter
        else -> Alignment.Center
    }
}

/**
 * 将水平方向轴映射为 Compose TextAlign
 */
fun parseTextAlign(horizontal: String): TextAlign {
    return when (horizontal) {
        "LEFT" -> TextAlign.Left
        "RIGHT" -> TextAlign.Right
        else -> TextAlign.Center
    }
}

/**
 * 渲染带样式的文本组件。
 * 支持描边、加粗、斜体以及多种对齐方式。
 */
@Composable
fun RenderStyledText(
    textProperties: TextStyled,
    baseScale: Float,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val color = parseHexColor(textProperties.textColor).copy(alpha = alpha)
    val fontSize = (textProperties.textSize * baseScale).sp
    val fontWeight = if (textProperties.isBold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (textProperties.isItalic) FontStyle.Italic else FontStyle.Normal
    val textAlign = parseTextAlign(textProperties.horizontalAlign)

    val baseStyle = TextStyle(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Box(modifier = modifier) {
        if (textProperties.strokeColor.isNotEmpty() && textProperties.strokeWidth > 0f) {
            val sColor = parseHexColor(textProperties.strokeColor).copy(alpha = alpha)
            Text(
                text = textProperties.text,
                color = sColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textAlign = textAlign,
                lineHeight = fontSize,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                style = baseStyle.copy(
                    drawStyle = Stroke(miter = 10f, width = textProperties.strokeWidth * baseScale)
                )
            )
        }

        Text(
            text = textProperties.text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign,
            lineHeight = fontSize,
            softWrap = false,
            maxLines = 1,
            style = baseStyle,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 递归渲染 UI 模块组件（双轨独立坐标系：屏幕渲染位移 vs 逻辑画板绝对坐标）。
 *
 * @param block 当前要渲染的 UI 模块数据对象
 * @param parentRenderX 父容器在屏幕视口物理坐标系下的绝对 X 偏移（包含 offsetX 居中偏移和 baseScale 缩放）
 * @param parentRenderY 父容器在屏幕视口物理坐标系下的绝对 Y 偏移（包含 offsetY 居中偏移和 baseScale 缩放）
 * @param parentLogicX 父容器在逻辑画板上的绝对 X 坐标（完全脱钩于屏幕视口居中偏移量，顶层为 0f）
 * @param parentLogicY 父容器在逻辑画板上的绝对 Y 坐标（完全脱钩于屏幕视口居中偏移量，顶层为 0f）
 * @param baseScale 画布的基础缩放比例（适配不同屏幕尺寸）
 * @param zoom 用户的实时缩放倍数
 * @param state 项目工作区状态
 * @param refBitmap 预解码的全局参考图 Bitmap，用于无图时无缝满格渲染切片
 * @param pageWidth 画布页面逻辑总宽
 * @param pageHeight 画布页面逻辑总高
 */
@Composable
fun RenderBlock(
    block: UIBlock,
    parentRenderX: Float,
    parentRenderY: Float,
    parentLogicX: Float = 0f,
    parentLogicY: Float = 0f,
    baseScale: Float,
    zoom: Float,
    state: ProjectWorkspaceState,
    refBitmap: ImageBitmap? = null,
    pageWidth: Float = 1080f,
    pageHeight: Float = 1920f
) {
    if (!block.isVisible) return

    val editingGroupId = state.editingGroupId
    val isVisualMode = state.isVisualMode
    val isHideOutlines = state.isHideOutlines
    val selectedBlockId = state.selectedBlockId
    val selectedBlockIds = state.selectedBlockIds

    val isSelected = block.id == selectedBlockId || selectedBlockIds.contains(block.id)
    val isDimmed = block.shouldDim(editingGroupId)

    // 1. 异步加载已生成的专属 AI 图像成品（第一级）
    val currentUri = block.currentImageUri
    val hasBoundAsset = currentUri != null
    val imageBitmapState =
        produceState<ImageBitmap?>(null, currentUri, currentUri?.relativePath) { 
            value = currentUri?.decodeToBitmap() 
        }
    val imageBitmap = imageBitmapState.value

    // 2. 异步加载已设置并保存的该模块专属参考切片图（第二级）
    val refUri = block.referenceImage
    val customRefBitmapState =
        produceState<ImageBitmap?>(null, refUri, refUri?.relativePath) {
            value = refUri?.decodeToBitmap()
        }
    val customRefBitmap = customRefBitmapState.value

    // 3. 屏幕物理渲染坐标（供 Modifier.offset 使用）
    val currentRenderX = parentRenderX + block.bounds.left * baseScale
    val currentRenderY = parentRenderY + block.bounds.top * baseScale

    // 4. 逻辑绝对物理坐标（直接访问内建绝对矩形，自推导所有父级位移）
    val absBounds = block.absoluteBounds
    val absLeft = absBounds.left
    val absTop = absBounds.top
    val absWidth = absBounds.width
    val absHeight = absBounds.height

    // 5. 是否有可用参考图切片（第三级：仅在无成品图且无专属参考图时，由合规类型从底图按全局绝对坐标现场截取呈现）
    val hasRefSlice = block.type.supportsReferenceSlice &&
            !hasBoundAsset &&
            imageBitmap == null &&
            customRefBitmap == null &&
            refBitmap != null

    // 6. 视觉状态判断
    val hasVisualImage = imageBitmap != null || customRefBitmap != null || hasRefSlice
    val hidePlaceholder = isVisualMode && hasVisualImage
    val selectionColor = Color(0xFF00E5FF) // 高亮鲜明电光蓝，深浅背景均 100% 夺目

    // 解析 VIEW 类型的自定义背景色
    val viewBgColor = if (block.type == UIBlockType.VIEW) {
        val vp = block.properties as? BlockProperties.ViewProperties
        if (!vp?.backgroundColor.isNullOrEmpty()) parseHexColor(vp.backgroundColor) else null
    } else null

    val actualBgColor = when {
        isHideOutlines && !isSelected -> Color.Transparent
        viewBgColor != null -> viewBgColor
        hidePlaceholder -> Color.Transparent
        hasRefSlice || customRefBitmap != null -> Color.Transparent // 图片存在时透明背景，满格渲染
        isSelected -> selectionColor.copy(alpha = 0.22f)
        isDimmed -> Color.Black.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }

    // 6. 渲染模块容器：处理位移、大小、背景和边框
    val strokeWidth = if (isSelected) (2.5.dp / zoom).coerceIn(2.dp, 4.dp) else (1.dp / zoom)
    Box(
        modifier = Modifier
            .offset(x = currentRenderX.dp, y = currentRenderY.dp)
            .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
            .clip(RoundedCornerShape(2.dp))
            .background(actualBgColor)
            .then(
                if ((isHideOutlines || hidePlaceholder || viewBgColor != null) && !isSelected) Modifier
                else Modifier.border(
                    width = strokeWidth,
                    color = if (isSelected) selectionColor
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val reelProps = if (block.type == UIBlockType.REEL) block.properties as? BlockProperties.ReelProperties else null
        val showReelBg = reelProps?.showBackground != false

        if (imageBitmap != null && showReelBg) {
            // 优先级 1：已生成的正式 AI 图像成品
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else if (customRefBitmap != null && showReelBg) {
            // 优先级 2：该模块专属设置并保存过的参考切片图（固定专属图，不随模块位移发生原图错位）
            Image(
                bitmap = customRefBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else if (hasRefSlice && showReelBg) {
            // 优先级 3：尚未设置专属参考图时，依据模块当前的全局绝对坐标从全景底图中现场切片呈现
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imgW = refBitmap.width.toFloat()
                val imgH = refBitmap.height.toFloat()

                val normL = (absLeft / pageWidth).coerceIn(0f, 1f)
                val normT = (absTop / pageHeight).coerceIn(0f, 1f)
                val normW = (absWidth / pageWidth).coerceIn(0f, 1f - normL)
                val normH = (absHeight / pageHeight).coerceIn(0f, 1f - normT)

                val srcX = (normL * imgW).toInt()
                val srcY = (normT * imgH).toInt()
                val srcW = max(1, (normW * imgW).toInt())
                val srcH = max(1, (normH * imgH).toInt())

                // 模块容器比例与切片比例天然完全对齐，直接满格绘制，彻底消灭黑边
                drawImage(
                    image = refBitmap,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        } else if (block.currentImageUri != null && showReelBg) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
        } else if (block.type == UIBlockType.SYMBOL) {
            val fallbackText = block.userPromptZh.ifBlank { block.userPromptEn }.ifBlank { "Symbol" }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // 渲染文本组件内容
        if (block.type == UIBlockType.TEXT) {
            val textProps = block.properties as? BlockProperties.TextProperties
            if (textProps != null && textProps.text.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = parseVerticalAlignment(textProps.verticalAlign)
                ) {
                    RenderStyledText(
                        textProperties = textProps,
                        baseScale = baseScale
                    )
                }
            }
        } else if (block.type == UIBlockType.INPUT) {
            val inputProps = block.properties as? BlockProperties.InputProperties
            if (inputProps != null && inputProps.hintText.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = (4 * baseScale).dp),
                    contentAlignment = parseVerticalAlignment(inputProps.verticalAlign)
                ) {
                    RenderStyledText(
                        textProperties = inputProps,
                        baseScale = baseScale,
                        alpha = 0.6f
                    )
                }
            }
        } else if (block.type == UIBlockType.REEL) {
            val reelProperties = block.properties as? BlockProperties.ReelProperties
            if (reelProperties != null) {
                val rows = reelProperties.rows.coerceAtLeast(1)
                val cols = reelProperties.columns.coerceAtLeast(1)

                if (reelProperties.items.isNotEmpty()) {
                    val randomItems = androidx.compose.runtime.remember(block.id, rows, cols, reelProperties.items, reelProperties.rollSeed) {
                        val rnd = kotlin.random.Random(block.id.hashCode() + reelProperties.rollSeed)
                        List(rows * cols) { reelProperties.items.random(rnd) }
                    }

                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        for (r in 0 until rows) {
                            androidx.compose.foundation.layout.Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (c in 0 until cols) {
                                    val item = randomItems[r * cols + c]
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.currentImageUri != null) {
                                            coil3.compose.AsyncImage(
                                                model = item.currentImageUri.getAbsolutePath(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            val fallbackText = item.userPromptZh.ifBlank { item.userPromptEn }.ifBlank { "Symbol" }
                                            Text(
                                                text = fallbackText,
                                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 3,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    val colRowCounts = reelProperties.columnRowCounts
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = (1.dp).toPx() / zoom
                        // 绘制垂直列分割线
                        for (i in 1 until cols) {
                            val x = size.width * i / cols
                            drawLine(
                                gridColor,
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x, size.height),
                                strokeWidth = sw
                            )
                        }

                        if (colRowCounts.isNotEmpty() && colRowCounts.size == cols) {
                            // 异形网格/单行/动态变轴：按每列自有的行数独立绘制水平分割线
                            for (c in 0 until cols) {
                                val cRows = colRowCounts[c].coerceAtLeast(1)
                                val colLeft = size.width * c / cols
                                val colRight = size.width * (c + 1) / cols
                                for (r in 1 until cRows) {
                                    val y = size.height * r / cRows
                                    drawLine(
                                        gridColor,
                                        start = androidx.compose.ui.geometry.Offset(colLeft, y),
                                        end = androidx.compose.ui.geometry.Offset(colRight, y),
                                        strokeWidth = sw
                                    )
                                }
                            }
                        } else {
                            // 标准规则矩阵：贯穿全宽绘制水平分割线
                            for (i in 1 until rows) {
                                val y = size.height * i / rows
                                drawLine(
                                    gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = sw
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. 显示模块占位文本：仅在无切片、无生成图且未自定义内容时显示
        val hasCustomContent = (block.type == UIBlockType.TEXT && (block.properties as? BlockProperties.TextProperties)?.text?.isNotEmpty() == true) ||
                (block.type == UIBlockType.VIEW && viewBgColor != null) ||
                (block.type == UIBlockType.REEL) || hasRefSlice

        if (!hidePlaceholder && imageBitmap == null && block.currentImageUri == null && !hasCustomContent) {
            Text(
                text = stringResource(block.type.getDisplayNameRes()),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDimmed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }

    // 8. 递归渲染子模块（正确传递累加的屏幕渲染坐标与逻辑绝对坐标）
    block.children.forEach { child ->
        RenderBlock(
            block = child,
            parentRenderX = currentRenderX,
            parentRenderY = currentRenderY,
            parentLogicX = absLeft,
            parentLogicY = absTop,
            baseScale = baseScale,
            zoom = zoom,
            state = state,
            refBitmap = refBitmap,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }
}

package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.ImageResizeMode
import org.gemini.ui.forge.model.ui.NinePatchConfig
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.decodeToBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * 递归渲染 UI 模块组件
 *
 * @param block 当前要渲染的 UI 模块数据对象
 * @param parentX 父容器的绝对 X 坐标
 * @param parentY 父容器的绝对 Y 坐标
 * @param baseScale 画布的基础缩放比例（适配不同屏幕尺寸）
 * @param zoom 用户的实时缩放倍数
 * @param isSelected 当前模块是否被选中
 * @param isDimmed 是否因为处于隔离编辑模式而被置灰显示
 * @param isVisualMode 是否处于视觉预览模式（隐藏线框）
 * @param density 屏幕密度，用于 dp 转换
 * @param selectedBlockId 当前全局选中的模块 ID
 * @param editingGroupId 当前正在编辑的分组 ID
 */
@Composable
fun RenderBlock(
    block: UIBlock,
    parentX: Float,
    parentY: Float,
    baseScale: Float,
    zoom: Float,
    isSelected: Boolean,
    isDimmed: Boolean, // 是否因为处于隔离模式而被置灰
    isVisualMode: Boolean,
    density: Density,
    selectedBlockId: String?,
    editingGroupId: String?
) {
    if (!block.isVisible) return

    // 1. 异步加载图片位图：根据模块关联的 URI 加载图片
    val imageBitmapState =
        produceState<ImageBitmap?>(null, block.currentImageUri) { value = block.currentImageUri?.decodeToBitmap() }
    val imageBitmap = imageBitmapState.value

    // 2. 计算当前模块在画布上的物理坐标：父坐标 + 模块相对偏移 * 基础缩放
    val currentX = parentX + block.bounds.left * baseScale
    val currentY = parentY + block.bounds.top * baseScale

    // 3. 视觉状态判断：视觉模式且有图片时隐藏占位线框
    val hidePlaceholder = isVisualMode && imageBitmap != null

    val selectionColor = Color(0xFF18A0FB) // Figma 风格的专业选中蓝

    // 4. 渲染模块容器：处理位移、大小、背景和边框
    Box(
        modifier = Modifier
            .offset(x = currentX.dp, y = currentY.dp)
            .size(width = (block.bounds.width * baseScale).dp, height = (block.bounds.height * baseScale).dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (hidePlaceholder) Color.Transparent
                else if (isSelected) selectionColor.copy(alpha = 0.15f)
                else if (isDimmed) Color.Black.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .then(
                if (hidePlaceholder && !isSelected) Modifier
                else Modifier.border(
                    width = (1.dp / zoom), // 关键：边框粗细除以缩放比例，确保在任何缩放级别下线条视觉宽度一致
                    color = if (isSelected) selectionColor
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            // 渲染已固化好的 AI 图像成品。
            // 由于图片已经在编辑器中按照 bounds 进行了拉伸、补白或九宫格固化，
            // 这里的渲染逻辑应保持最简。
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else if (block.currentImageUri != null) {
            // 图片加载中的反馈
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
        }

        // 5. 显示模块占位文本：仅在未隐藏且无图片时显示模块类型
        if (!hidePlaceholder && imageBitmap == null && block.currentImageUri == null) {
            Text(
                text = stringResource(block.type.getDisplayNameRes()),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDimmed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }

    // 6. 递归渲染子模块：保持层级关系并将当前模块坐标作为父坐标传递
    block.children.forEach { child ->
        RenderBlock(
            block = child,
            parentX = currentX,
            parentY = currentY,
            baseScale = baseScale,
            zoom = zoom,
            isSelected = child.id == selectedBlockId,
            isDimmed = isDimmed,
            isVisualMode = isVisualMode,
            density = density,
            selectedBlockId = selectedBlockId,
            editingGroupId = editingGroupId
        )
    }
}

/**
 * 手动绘制九宫格图片
 */
private fun DrawScope.drawNinePatch(image: ImageBitmap, config: NinePatchConfig) {
    val srcW = image.width
    val srcH = image.height
    val dstW = size.width.toInt()
    val dstH = size.height.toInt()

    val l = config.left
    val t = config.top
    val r = config.right
    val b = config.bottom

    // 这里的逻辑需要处理目标尺寸小于边距的情况，简单起见直接按比例缩减或截断
    // 1. Top-Left
    drawImage(image, 
        srcOffset = IntOffset(0, 0), srcSize = IntSize(l, t),
        dstOffset = IntOffset(0, 0), dstSize = IntSize(l, t))
    // 2. Top-Center
    drawImage(image,
        srcOffset = IntOffset(l, 0), srcSize = IntSize(srcW - l - r, t),
        dstOffset = IntOffset(l, 0), dstSize = IntSize(dstW - l - r, t))
    // 3. Top-Right
    drawImage(image,
        srcOffset = IntOffset(srcW - r, 0), srcSize = IntSize(r, t),
        dstOffset = IntOffset(dstW - r, 0), dstSize = IntSize(r, t))
    
    // 4. Middle-Left
    drawImage(image,
        srcOffset = IntOffset(0, t), srcSize = IntSize(l, srcH - t - b),
        dstOffset = IntOffset(0, t), dstSize = IntSize(l, dstH - t - b))
    // 5. Middle-Center
    drawImage(image,
        srcOffset = IntOffset(l, t), srcSize = IntSize(srcW - l - r, srcH - t - b),
        dstOffset = IntOffset(l, t), dstSize = IntSize(dstW - l - r, dstH - t - b))
    // 6. Middle-Right
    drawImage(image,
        srcOffset = IntOffset(srcW - r, t), srcSize = IntSize(r, srcH - t - b),
        dstOffset = IntOffset(dstW - r, t), dstSize = IntSize(r, dstH - t - b))

    // 7. Bottom-Left
    drawImage(image,
        srcOffset = IntOffset(0, srcH - b), srcSize = IntSize(l, b),
        dstOffset = IntOffset(0, dstH - b), dstSize = IntSize(l, b))
    // 8. Bottom-Center
    drawImage(image,
        srcOffset = IntOffset(l, srcH - b), srcSize = IntSize(srcW - l - r, b),
        dstOffset = IntOffset(l, dstH - b), dstSize = IntSize(dstW - l - r, b))
    // 9. Bottom-Right
    drawImage(image,
        srcOffset = IntOffset(srcW - r, srcH - b), srcSize = IntSize(r, b),
        dstOffset = IntOffset(dstW - r, dstH - b), dstSize = IntSize(r, b))
}

package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.utils.calculateBlockAbsoluteBounds
import org.gemini.ui.forge.utils.findBlockById
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 顶层独立高亮选框与尺寸手柄浮层组件 (BlockSelectionOverlay)
 *
 * 架构核心：
 * 1. 【解耦绝对置顶】：以 [Modifier.zIndex(1000f)] 绝对置顶于画布全部渲染模块与嵌套子组件之上，
 *    彻底根绝复合容器或父级边框被子图元切片图片、贴图或同级兄弟图层压盖遮挡的底层缺陷；
 * 2. 【零手势拦截】：未挂载任何点击或拖拽手势消费者修饰符，指针事件 100% 自然穿透到底层画布手势检测管线；
 * 3. 【双层高对比度外框】：外层 1px 黑色深阴影底衬 + 内层 2.5px 霓虹青色 (#00E5FF) 高亮主线，
 *    在纯黑、纯白或任何高对比度游戏美术原图底色上均保证最高辨识度；
 * 4. 【8 向控制点手柄】：角点与四边中点呈现 Figma 风格白底青框控制手柄；
 * 5. 【智能防遮挡 HUD 尺寸药丸】：在选框外侧自动居中展示图元逻辑宽高（W × H），贴近视口底部时自动向上翻转。
 */
@Composable
fun BlockSelectionOverlay(
    blocks: List<UIBlock>,
    selectedBlockIds: Set<String>,
    selectedBlock: UIBlock?,
    parentRenderX: Float,
    parentRenderY: Float,
    baseScale: Float,
    zoom: Float,
    pageWidth: Float,
    pageHeight: Float,
    modifier: Modifier = Modifier
) {
    // 1. 过滤出所有当前处于选中状态的目标模块 ID 集合
    val targetIds = remember(selectedBlockIds, selectedBlock) {
        if (selectedBlockIds.isNotEmpty()) {
            selectedBlockIds
        } else if (selectedBlock != null) {
            setOf(selectedBlock.id)
        } else {
            emptySet()
        }
    }

    if (targetIds.isEmpty()) return

    // 2. 遍历解析出所有目标模块其实时全景绝对逻辑矩形 (SerialRect)
    val selectedItems = remember(blocks, targetIds) {
        targetIds.mapNotNull { id ->
            val block = blocks.findBlockById(id) ?: return@mapNotNull null
            val absBounds = blocks.calculateBlockAbsoluteBounds(id) ?: block.absoluteBounds
            block to absBounds
        }
    }

    if (selectedItems.isEmpty()) return

    val cyanColor = Color(0xFF00E5FF)
    val shadowColor = Color(0xCC000000)

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f)
    ) {
        // 3. 绘制所有选中项的双层高对比边框与 8 控制点手柄
        Canvas(modifier = Modifier.fillMaxSize()) {
            selectedItems.forEach { (_, absBounds) ->
                val leftDp = parentRenderX + absBounds.left * baseScale
                val topDp = parentRenderY + absBounds.top * baseScale
                val widthDp = absBounds.width * baseScale
                val heightDp = absBounds.height * baseScale

                val leftPx = leftDp.dp.toPx()
                val topPx = topDp.dp.toPx()
                val widthPx = widthDp.dp.toPx()
                val heightPx = heightDp.dp.toPx()

                if (widthPx <= 0f || heightPx <= 0f) return@forEach

                // 计算根据视口 zoom 动态缩放的线宽与手柄尺寸（保证视觉清晰不肥大）
                val mainStrokeWidth = (2.2.dp / zoom).coerceIn(1.5.dp, 3.5.dp).toPx()
                val shadowStrokeWidth = mainStrokeWidth + (1.8.dp / zoom).coerceIn(1.dp, 2.5.dp).toPx()
                val handleRadius = (4.5.dp / zoom).coerceIn(3.5.dp, 6.5.dp).toPx()
                val handleStroke = (1.5.dp / zoom).coerceIn(1.dp, 2.dp).toPx()

                // A. 双层高对比度外框：外层黑影衬底 + 内层霓虹青主线
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(widthPx, heightPx),
                    style = Stroke(width = shadowStrokeWidth)
                )
                drawRect(
                    color = cyanColor,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(widthPx, heightPx),
                    style = Stroke(width = mainStrokeWidth)
                )

                // B. 8 向控制点手柄（四角 + 四边中点）
                val cx = leftPx + widthPx / 2f
                val cy = topPx + heightPx / 2f
                val rightPx = leftPx + widthPx
                val bottomPx = topPx + heightPx

                val handlePoints = listOf(
                    Offset(leftPx, topPx),
                    Offset(cx, topPx),
                    Offset(rightPx, topPx),
                    Offset(leftPx, cy),
                    Offset(rightPx, cy),
                    Offset(leftPx, bottomPx),
                    Offset(cx, bottomPx),
                    Offset(rightPx, bottomPx)
                )

                handlePoints.forEach { pt ->
                    // 手柄黑色外边缘描边
                    drawCircle(
                        color = shadowColor,
                        radius = handleRadius + handleStroke,
                        center = pt
                    )
                    // 手柄纯白实心填充
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius,
                        center = pt
                    )
                    // 手柄青色内描边
                    drawCircle(
                        color = cyanColor,
                        radius = handleRadius,
                        center = pt,
                        style = Stroke(width = handleStroke)
                    )
                }
            }
        }

        // 4. 绘制智能防遮挡 HUD 尺寸药丸标签
        selectedItems.forEach { (_, absBounds) ->
            val wInt = abs(absBounds.width).roundToInt()
            val hInt = abs(absBounds.height).roundToInt()

            val leftDp = parentRenderX + absBounds.left * baseScale
            val topDp = parentRenderY + absBounds.top * baseScale
            val widthDp = absBounds.width * baseScale
            val heightDp = absBounds.height * baseScale

            // 智能位置推导：默认置于图元下方中央；若靠近视口底部则翻转至上方；全屏背景置于内侧顶部
            val isNearTop = absBounds.top <= 40f
            val isNearBottom = absBounds.bottom >= (pageHeight - 60f)
            val pillOffsetYDp = when {
                isNearTop && isNearBottom -> topDp + (12f / zoom)
                isNearBottom -> (topDp - (26f / zoom)).coerceAtLeast(parentRenderY)
                else -> topDp + heightDp + (6f / zoom)
            }

            Box(
                modifier = Modifier
                    .offset(x = leftDp.dp, y = pillOffsetYDp.dp)
                    .width(widthDp.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xE61A1C1E),
                    border = BorderStroke(0.6.dp, cyanColor.copy(alpha = 0.85f)),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${wInt} × ${hInt}",
                            color = cyanColor,
                            fontSize = (11f / zoom).coerceIn(10f, 13f).sp,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

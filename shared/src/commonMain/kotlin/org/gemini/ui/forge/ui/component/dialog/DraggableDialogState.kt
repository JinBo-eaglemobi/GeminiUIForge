package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 通用弹窗自由拖拽状态持有者。
 * 负责追踪当前位移、拖拽中交互态，并执行严格的视口物理安全夹具计算。
 */
class DraggableDialogState(
    initialOffset: IntOffset = IntOffset.Zero
) {
    /** 当前相对于初始居中位置的像素位移量 */
    var offset by mutableStateOf(initialOffset)
        private set

    /** 当前是否处于鼠标拖拽中（用于触发拟物海拔升阶微动效） */
    var isDragging by mutableStateOf(false)

    /** 弹窗自身物理尺寸 (px) */
    var dialogSize by mutableStateOf(IntSize.Zero)

    /** 宿主视口全景尺寸 (px) */
    var viewportSize by mutableStateOf(IntSize.Zero)

    /**
     * 根据鼠标单帧增量更新位移，并施加视口安全物理夹具 (Clamping)。
     */
    fun onDragDelta(dragAmount: Offset) {
        val currentOffset = offset
        val targetX = currentOffset.x + dragAmount.x
        val targetY = currentOffset.y + dragAmount.y

        // 视口尺寸或弹窗尺寸未就绪时直接赋值
        if (viewportSize.width <= 0 || viewportSize.height <= 0 || dialogSize.width <= 0 || dialogSize.height <= 0) {
            offset = IntOffset(targetX.roundToInt(), targetY.roundToInt())
            return
        }

        // 计算初始居中时弹窗左上角在视口中的基准原点 (Origin)
        val originX = (viewportSize.width - dialogSize.width) / 2f
        val originY = (viewportSize.height - dialogSize.height) / 2f

        // 严格安全四极约束：
        // 1. 顶部严禁出界 (Y_abs >= 0)
        val minY = -originY
        // 2. 底部保留至少 48px 标题栏在视口内 (Y_abs <= H_vp - 48px)
        val maxY = viewportSize.height - 48f - originY
        // 3. 左右至少保留 120px 抓手在视口内
        val minX = 120f - dialogSize.width - originX
        val maxX = viewportSize.width - 120f - originX

        // 执行严密数学 Clamp
        val safeMinX = minOf(minX, maxX)
        val safeMaxX = maxOf(minX, maxX)
        val safeMinY = minOf(minY, maxY)
        val safeMaxY = maxOf(minY, maxY)

        val clampedX = targetX.coerceIn(safeMinX, safeMaxX)
        val clampedY = targetY.coerceIn(safeMinY, safeMaxY)

        offset = IntOffset(clampedX.roundToInt(), clampedY.roundToInt())
    }

    /**
     * 瞬间复位居中（双击标题栏时触发）。
     */
    fun reset() {
        offset = IntOffset.Zero
    }
}

/**
 * 记忆可拖拽弹窗状态。
 */
@Composable
fun rememberDraggableDialogState(
    initialOffset: IntOffset = IntOffset.Zero
): DraggableDialogState {
    return remember { DraggableDialogState(initialOffset) }
}

package org.gemini.ui.forge.utils.geometry

import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.model.ui.SerialRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 8 方向变换控制手柄枚举
 */
enum class TransformHandle {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    val isCorner: Boolean
        get() = this == TOP_LEFT || this == TOP_RIGHT || this == BOTTOM_LEFT || this == BOTTOM_RIGHT
}

/**
 * 矩形目标几何变换纯数学库 (RectTransformHelper)
 *
 * 统一管理 8 方向控制手柄命中测试、基于光标逻辑绝对坐标直接锚定的拉伸变换（Direct Cursor Pinning），
 * 并提供 Alt 键中心对称缩放与等比锁定能力。
 */
object RectTransformHelper {

    /**
     * 8 方向手柄点击命中测试
     *
     * @param rect 目标矩形
     * @param logicalX 触发点逻辑 X
     * @param logicalY 触发点逻辑 Y
     * @param touchSlopLogical 触控/光标容差半径（逻辑像素）
     * @return 命中的手柄类型，未命中返回 null
     */
    fun hitTestHandle(
        rect: SerialRect,
        logicalX: Float,
        logicalY: Float,
        touchSlopLogical: Float
    ): TransformHandle? {
        val l = min(rect.left, rect.right)
        val r = max(rect.left, rect.right)
        val t = min(rect.top, rect.bottom)
        val b = max(rect.top, rect.bottom)

        val slopSq = touchSlopLogical * touchSlopLogical
        fun isNear(targetX: Float, targetY: Float): Boolean {
            val dx = logicalX - targetX
            val dy = logicalY - targetY
            return (dx * dx + dy * dy) <= slopSq
        }

        val cx = (l + r) / 2f
        val cy = (t + b) / 2f

        return when {
            isNear(l, t) -> TransformHandle.TOP_LEFT
            isNear(r, t) -> TransformHandle.TOP_RIGHT
            isNear(l, b) -> TransformHandle.BOTTOM_LEFT
            isNear(r, b) -> TransformHandle.BOTTOM_RIGHT
            isNear(cx, t) -> TransformHandle.TOP_CENTER
            isNear(cx, b) -> TransformHandle.BOTTOM_CENTER
            isNear(l, cy) -> TransformHandle.CENTER_LEFT
            isNear(r, cy) -> TransformHandle.CENTER_RIGHT
            else -> null
        }
    }

    /**
     * 光标绝对坐标直接锚定拉伸算法 (Direct Cursor Pinning)
     * 支持自由拉伸、Alt 键中心对称缩放与等比锁定
     *
     * @param current 当前矩形
     * @param handle 当前拖拽的手柄
     * @param cursorPos 当前光标投影在画布的逻辑绝对坐标
     * @param isAltCenterResize 是否按住 Alt 进行中心对称缩放
     * @param lockAspectRatio 是否锁定原始宽高比
     * @param maxW 最大允许宽度/页面右边界
     * @param maxH 最大允许高度/页面下边界
     * @param minSize 最小尺寸限制（默认 16px）
     * @return 计算后的全新合法矩形
     */
    fun resizeRectDirectPin(
        current: SerialRect,
        handle: TransformHandle,
        cursorPos: Offset,
        isAltCenterResize: Boolean = false,
        lockAspectRatio: Boolean = false,
        maxW: Float = Float.MAX_VALUE,
        maxH: Float = Float.MAX_VALUE,
        minSize: Float = 16f
    ): SerialRect {
        var l = min(current.left, current.right)
        var r = max(current.left, current.right)
        var t = min(current.top, current.bottom)
        var b = max(current.top, current.bottom)

        val origW = max(r - l, 1f)
        val origH = max(b - t, 1f)
        val aspectRatio = origW / origH

        val cx = (l + r) / 2f
        val cy = (t + b) / 2f

        when (handle) {
            TransformHandle.TOP_LEFT -> {
                l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                if (lockAspectRatio) {
                    val w = r - l
                    val h = b - t
                    val targetW = h * aspectRatio
                    l = (r - targetW).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                }
                if (isAltCenterResize) {
                    val dx = cx - l
                    val dy = cy - t
                    r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                    b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                }
            }
            TransformHandle.TOP_CENTER -> {
                t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                if (isAltCenterResize) {
                    val dy = cy - t
                    b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                }
            }
            TransformHandle.TOP_RIGHT -> {
                r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                t = cursorPos.y.coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                if (lockAspectRatio) {
                    val w = r - l
                    val h = b - t
                    val targetW = h * aspectRatio
                    r = (l + targetW).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                }
                if (isAltCenterResize) {
                    val dx = r - cx
                    val dy = cy - t
                    l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                    b = (cy + dy).coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                }
            }
            TransformHandle.CENTER_LEFT -> {
                l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                if (isAltCenterResize) {
                    val dx = cx - l
                    r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                }
            }
            TransformHandle.CENTER_RIGHT -> {
                r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                if (isAltCenterResize) {
                    val dx = r - cx
                    l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                }
            }
            TransformHandle.BOTTOM_LEFT -> {
                l = cursorPos.x.coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                if (lockAspectRatio) {
                    val w = r - l
                    val h = b - t
                    val targetW = h * aspectRatio
                    l = (r - targetW).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                }
                if (isAltCenterResize) {
                    val dx = cx - l
                    val dy = b - cy
                    r = (cx + dx).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                    t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                }
            }
            TransformHandle.BOTTOM_CENTER -> {
                b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                if (isAltCenterResize) {
                    val dy = b - cy
                    t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                }
            }
            TransformHandle.BOTTOM_RIGHT -> {
                r = cursorPos.x.coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                b = cursorPos.y.coerceIn((t + minSize).coerceAtMost(maxH), maxH)
                if (lockAspectRatio) {
                    val w = r - l
                    val h = b - t
                    val targetW = h * aspectRatio
                    r = (l + targetW).coerceIn((l + minSize).coerceAtMost(maxW), maxW)
                }
                if (isAltCenterResize) {
                    val dx = r - cx
                    val dy = b - cy
                    l = (cx - dx).coerceIn(0f, (r - minSize).coerceAtLeast(0f))
                    t = (cy - dy).coerceIn(0f, (b - minSize).coerceAtLeast(0f))
                }
            }
        }

        return SerialRect(l, t, r, b)
    }
}

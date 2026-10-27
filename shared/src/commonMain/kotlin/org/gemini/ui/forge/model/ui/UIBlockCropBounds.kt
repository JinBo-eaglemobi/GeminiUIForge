package org.gemini.ui.forge.model.ui

/**
 * 获取用于切片、采样或参考区域核验的有效边界矩形（遵循无损回退与安全 Padding 规范）。
 * 1. 若图元已显式指定专属 cropRect，则尊重并直接返回 cropRect（不附加额外 padding）；
 * 2. 若 cropRect 为 null（缺失）：兼容回退使用该图元的全局绝对 bounds，并自动向四周外扩 [fallbackPadding] 安全边距，
 *    同时自动约束在画布视口 [0, 0, canvasWidth, canvasHeight] 物理范围内，确保发光边框、立体外壳与边缘阴影完整不被截断。
 */
fun UIBlock.getEffectiveCropBounds(
    canvasWidth: Float = Float.MAX_VALUE,
    canvasHeight: Float = Float.MAX_VALUE,
    fallbackPadding: Float = 24f
): SerialRect {
    val explicit = this.cropRect
    if (explicit != null) {
        return explicit
    }
    val abs = this.toAbsoluteBounds()
    val paddedLeft = (abs.left - fallbackPadding).coerceAtLeast(0f)
    val paddedTop = (abs.top - fallbackPadding).coerceAtLeast(0f)
    val paddedRight = (abs.right + fallbackPadding).coerceAtMost(canvasWidth)
    val paddedBottom = (abs.bottom + fallbackPadding).coerceAtMost(canvasHeight)
    return SerialRect(paddedLeft, paddedTop, paddedRight, paddedBottom)
}

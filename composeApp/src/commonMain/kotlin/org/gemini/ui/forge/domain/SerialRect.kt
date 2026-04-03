package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 跨平台可序列化的矩形坐标类 (SerialRect)
 * 用于替代 Android/iOS 原生且无法通过 JSON 互相传递的 Rect 类。
 * @property left 矩形左边界的 X 坐标
 * @property top 矩形上边界的 Y 坐标
 * @property right 矩形右边界的 X 坐标
 * @property bottom 矩形下边界的 Y 坐标
 */
@Serializable(with = SerialRectSerializer::class)
data class SerialRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** 矩形的绝对宽度 */
    val width: Float get() = right - left
    /** 矩形的绝对高度 */
    val height: Float get() = bottom - top
}

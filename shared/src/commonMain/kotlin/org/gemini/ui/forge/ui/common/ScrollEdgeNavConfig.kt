package org.gemini.ui.forge.ui.common

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 滚动条直达首尾导航组件的配置契约。
 *
 * @param enabled 是否开启一键直达首尾胶囊能力
 * @param alignment 胶囊在滚动宿主容器（Box）内的对齐锚点，默认 Alignment.BottomEnd（右下角悬浮）
 * @param modifier 自定义外扩修饰符（用于业务端自定义内边距、偏移与层级）
 * @param showTop 是否启用直达顶部按钮（默认 true）
 * @param showBottom 是否启用直达底部按钮（默认 true）
 * @param autoHideWhenAtEdge 位于最顶端时自动隐藏顶部按钮、位于最底端时自动隐藏底部按钮（默认 true）
 * @param thresholdOffset 触发显示的滚动位移阈值（默认 40f 像素），未超出阈值时不打扰展示
 */
data class ScrollEdgeNavConfig(
    val enabled: Boolean = true,
    val alignment: Alignment = Alignment.BottomEnd,
    val modifier: Modifier = Modifier,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val autoHideWhenAtEdge: Boolean = true,
    val thresholdOffset: Float = 40f
) {
    companion object {
        /** 默认右下角悬浮配置 */
        val DEFAULT = ScrollEdgeNavConfig()

        /** 紧贴右侧居中配置 */
        val CENTER_END = ScrollEdgeNavConfig(alignment = Alignment.CenterEnd)

        /** 仅置顶配置 */
        val TOP_ONLY = ScrollEdgeNavConfig(showBottom = false)

        /** 仅置底配置 */
        val BOTTOM_ONLY = ScrollEdgeNavConfig(showTop = false)
    }
}

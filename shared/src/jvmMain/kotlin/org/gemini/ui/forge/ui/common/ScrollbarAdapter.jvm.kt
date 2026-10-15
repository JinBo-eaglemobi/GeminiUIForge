package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 专为非均质长图文混排（如长篇 AI 结构化回复与短消息交互）打造的工业级双轨平滑滚动条适配器。
 *
 * 核心治理机制：
 * 1. 严格实现 Compose 官方最新 v2 双精度 ScrollbarAdapter 协议；
 * 2. 拖拽微位移通道（abs(distance) <= viewportSize）走原生 `scrollBy`：
 *    像素级相对连续平移，零重新测量，零反向回弹，彻底消灭手势抖动与指针捕获丢失脱节缺陷；
 * 3. 大跨度瞬跳通道（abs(distance) > viewportSize）才执行 `snapTo`，兼顾远距离瞬间直达；
 * 4. 维护全局已测 Item 高度记录表与全局历史平滑平均值，总高度与滑块尺寸全程单调稳定，彻底消除忽大忽小。
 */
class SmoothLazyScrollbarAdapter(
    private val scrollState: LazyListState
) : ScrollbarAdapter {
    // 缓存已渲染过的 item 真实物理像素高度
    private val itemHeights = mutableMapOf<Int, Int>()
    private var sumOfKnownHeights: Long = 0L
    private var defaultItemHeight: Double = 90.0

    private fun updateHeights() {
        val layoutInfo = scrollState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return

        for (item in visibleItems) {
            val oldHeight = itemHeights[item.index]
            if (oldHeight != item.size) {
                if (oldHeight != null) {
                    sumOfKnownHeights -= oldHeight
                }
                itemHeights[item.index] = item.size
                sumOfKnownHeights += item.size
            }
        }
        if (itemHeights.isNotEmpty()) {
            defaultItemHeight = (sumOfKnownHeights.toDouble() / itemHeights.size).coerceAtLeast(36.0)
        }
    }

    override val viewportSize: Double
        get() = with(scrollState.layoutInfo) {
            if (orientation == Orientation.Vertical)
                viewportSize.height.toDouble()
            else
                viewportSize.width.toDouble()
        }

    val maxScrollOffset: Double
        get() = (contentSize - viewportSize).coerceAtLeast(0.0)

    override val contentSize: Double
        get() {
            updateHeights()
            val totalCount = scrollState.layoutInfo.totalItemsCount
            if (totalCount == 0) return 0.0

            var total = 0.0
            for (i in 0 until totalCount) {
                total += itemHeights[i]?.toDouble() ?: defaultItemHeight
            }
            val spacing = scrollState.layoutInfo.mainAxisItemSpacing.toDouble()
            val padding = (scrollState.layoutInfo.beforeContentPadding + scrollState.layoutInfo.afterContentPadding).toDouble()
            return total + spacing * (totalCount - 1).coerceAtLeast(0) + padding
        }

    override val scrollOffset: Double
        get() {
            updateHeights()
            val totalCount = scrollState.layoutInfo.totalItemsCount
            if (totalCount == 0) return 0.0

            val firstIndex = scrollState.firstVisibleItemIndex.coerceIn(0, totalCount)
            val spacing = scrollState.layoutInfo.mainAxisItemSpacing.toDouble()
            var offsetBefore = 0.0
            for (i in 0 until firstIndex) {
                offsetBefore += (itemHeights[i]?.toDouble() ?: defaultItemHeight) + spacing
            }
            return offsetBefore + scrollState.firstVisibleItemScrollOffset.toDouble()
        }

    // 连续拖拽手势状态追踪：杜绝频繁进入 snapTo 引发重组归零闪烁
    private var lastTargetOffset: Double = -1.0
    private var lastScrollTime: Long = 0L

    override suspend fun scrollTo(scrollOffset: Double) {
        val totalCount = scrollState.layoutInfo.totalItemsCount
        if (totalCount == 0) return

        val maxOffset =         maxScrollOffset
        val targetOffset = scrollOffset.coerceIn(0.0, maxOffset)
        val now = System.currentTimeMillis()
        val isContinuousDrag = (now - lastScrollTime) < 160L && lastTargetOffset >= 0.0

        if (isContinuousDrag) {
            // 处于连续按住滑块拖拽中：计算两帧之间的纯增量 delta
            val delta = targetOffset - lastTargetOffset
            lastTargetOffset = targetOffset
            lastScrollTime = now

            if (targetOffset <= 0.0) {
                scrollState.scrollToItem(0, 0)
            } else if (targetOffset >= maxOffset) {
                scrollState.scrollToItem(totalCount - 1, 0)
            } else if (abs(delta) > 0.001) {
                // 100% 相对平滑平移，彻底杜绝调用 scrollToItem 引发销毁重组与归零
                scrollState.scrollBy(delta.toFloat())
            }
        } else {
            // 单次点击轨道空白处的大跨度跳转，或拖拽初起笔
            lastTargetOffset = targetOffset
            lastScrollTime = now

            val current = this.scrollOffset
            val distance = targetOffset - current
            val vp = viewportSize

            if (abs(distance) <= vp) {
                scrollState.scrollBy(distance.toFloat())
            } else {
                snapTo(targetOffset)
            }
        }
    }

    private suspend fun snapTo(targetOffset: Double) {
        val totalCount = scrollState.layoutInfo.totalItemsCount
        if (totalCount == 0) return

        if (targetOffset <= 0.0) {
            scrollState.scrollToItem(0, 0)
            return
        }
        val maxOffset = maxScrollOffset
        if (targetOffset >= maxOffset) {
            scrollState.scrollToItem(totalCount - 1, 0)
            return
        }

        val ratio = (targetOffset / maxOffset).coerceIn(0.0, 1.0)
        // 依照全局进度百分比推算安全索引，双向物理夹具防坍缩
        val safeIndex = (ratio * (totalCount - 1)).toInt().coerceIn(0, totalCount - 1)
        scrollState.scrollToItem(safeIndex, 0)
    }
}

@Composable
actual fun VerticalScrollbarAdapter(
    modifier: Modifier,
    scrollState: ScrollState
) {
    VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(scrollState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color.White.copy(alpha = 0.3f),
            hoverColor = Color.White.copy(alpha = 0.6f),
            thickness = 8.dp
        )
    )
}

@Composable
actual fun VerticalScrollbarAdapter(
    modifier: Modifier,
    scrollState: LazyListState
) {
    val smoothAdapter = remember(scrollState) { SmoothLazyScrollbarAdapter(scrollState) }
    VerticalScrollbar(
        modifier = modifier,
        adapter = smoothAdapter,
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color.White.copy(alpha = 0.3f),
            hoverColor = Color.White.copy(alpha = 0.6f),
            thickness = 8.dp
        )
    )
}

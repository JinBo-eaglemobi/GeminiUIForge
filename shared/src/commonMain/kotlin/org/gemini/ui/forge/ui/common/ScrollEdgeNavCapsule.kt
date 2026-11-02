package org.gemini.ui.forge.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gemini.ui.forge.ui.component.tip

/**
 * 滚动条直达首尾双向微型操作胶囊组件。
 *
 * 遵循一文件一 Composable 铁律与 UI 设计系统规范。
 * 针对 ScrollState 与 LazyListState 提供统一的交互胶囊呈现。
 */
@Composable
fun ScrollEdgeNavCapsule(
    config: ScrollEdgeNavConfig,
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    modifier: Modifier = Modifier
) {
    if (!config.enabled) return

    val coroutineScope = rememberCoroutineScope()

    // 状态判定：是否可向上直达顶部
    val canScrollToTop by remember(scrollState, lazyListState, config) {
        derivedStateOf {
            if (!config.showTop) false
            else if (scrollState != null) {
                if (config.autoHideWhenAtEdge) scrollState.value > config.thresholdOffset
                else scrollState.value > 0
            } else if (lazyListState != null) {
                if (config.autoHideWhenAtEdge) {
                    lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > config.thresholdOffset
                } else {
                    lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
                }
            } else false
        }
    }

    // 状态判定：是否可向下直达底部
    val canScrollToBottom by remember(scrollState, lazyListState, config) {
        derivedStateOf {
            if (!config.showBottom) false
            else if (scrollState != null) {
                val max = scrollState.maxValue
                if (max <= 0) false
                else if (config.autoHideWhenAtEdge) (max - scrollState.value) > config.thresholdOffset
                else scrollState.value < max
            } else if (lazyListState != null) {
                val layoutInfo = lazyListState.layoutInfo
                val totalCount = layoutInfo.totalItemsCount
                if (totalCount <= 0) false
                else {
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (config.autoHideWhenAtEdge) lastVisible < totalCount - 1
                    else lastVisible < totalCount - 1 || lazyListState.firstVisibleItemScrollOffset < 100
                }
            } else false
        }
    }

    val isVisible = canScrollToTop || canScrollToBottom

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(28.dp)
            ) {
                if (canScrollToTop) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp, 26.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .clickable {
                                coroutineScope.launch {
                                    executeScrollToTop(scrollState, lazyListState)
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .tip("回到顶部")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to top",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (canScrollToTop && canScrollToBottom) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (canScrollToBottom) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp, 26.dp)
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .clickable {
                                coroutineScope.launch {
                                    executeScrollToBottom(scrollState, lazyListState)
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .tip("直达底部")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 集中收拢直达顶部挂起逻辑，显式声明 Unit 返回类型，彻底杜绝协程挂起恢复时的 Float 到 Unit ClassCastException。
 */
private suspend fun executeScrollToTop(
    scrollState: ScrollState?,
    lazyListState: LazyListState?
) {
    if (scrollState != null) {
        scrollState.animateScrollTo(0)
    } else if (lazyListState != null) {
        lazyListState.animateScrollToItem(0)
    }
}

/**
 * 集中收拢直达底部挂起逻辑，显式声明 Unit 返回类型，彻底杜绝协程挂起恢复时的 Float 到 Unit ClassCastException。
 */
private suspend fun executeScrollToBottom(
    scrollState: ScrollState?,
    lazyListState: LazyListState?
) {
    if (scrollState != null) {
        scrollState.animateScrollTo(scrollState.maxValue)
    } else if (lazyListState != null) {
        val total = lazyListState.layoutInfo.totalItemsCount
        if (total > 0) {
            lazyListState.animateScrollToItem(total - 1)
        }
    }
}

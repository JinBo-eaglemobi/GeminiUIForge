package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台横向统一滚动条适配器组件（针对普通水平滚动 [ScrollState] 容器）。
 *
 * 遵循项目《滚动条统一接入规范》红线规则：
 * 1. 【唯一入口】：全项目所有横向可滚动区域（Row.horizontalScroll）统一使用本适配器；
 * 2. 【标准布局范式】：
 *    ```kotlin
 *    Box(modifier = Modifier.fillMaxWidth()) {
 *        Row(
 *            modifier = Modifier
 *                .fillMaxWidth()
 *                .horizontalScroll(scrollState)
 *                .padding(bottom = 10.dp) // 预留底部滚动条呼吸位
 *        ) { ... }
 *
 *        HorizontalScrollbarAdapter(
 *            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
 *            scrollState = scrollState
 *        )
 *    }
 *    ```
 *
 * @param modifier 滚动条修饰符，标准使用 `Modifier.align(Alignment.BottomCenter).fillMaxWidth()`
 * @param scrollState 绑定的常规水平滚动状态 [ScrollState]
 */
@Composable
expect fun HorizontalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
)

/**
 * 跨平台横向统一滚动条适配器组件（针对水平懒加载长列表 [LazyListState] 容器）。
 *
 * @param modifier 滚动条修饰符，标准使用 `Modifier.align(Alignment.BottomCenter).fillMaxWidth()`
 * @param scrollState 绑定的懒加载水平列表状态 [LazyListState]
 */
@Composable
expect fun HorizontalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: LazyListState
)

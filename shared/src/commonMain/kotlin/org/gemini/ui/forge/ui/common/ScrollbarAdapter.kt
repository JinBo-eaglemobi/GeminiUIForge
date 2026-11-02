package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台纵向统一滚动条适配器组件（针对普通 [ScrollState] 滚动容器）。
 *
 * 遵循项目《滚动条统一接入规范》红线规则：
 * 1. 【唯一入口】：全项目所有纵向滚动容器（Column.verticalScroll）统一使用本适配器，
 *    严禁在业务端直接裸调 Compose 官方 `VerticalScrollbar`，滑块尺寸与颜色样式在底层收口统一；
 * 2. 【标准布局范式】：
 *    ```kotlin
 *    Box(modifier = Modifier.fillMaxSize()) {
 *        Column(
 *            modifier = Modifier
 *                .fillMaxSize()
 *                .verticalScroll(scrollState)
 *                .padding(end = 12.dp) // 必须为滚动条预留呼吸边距
 *        ) { ... }
 *
 *        VerticalScrollbarAdapter(
 *            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
 *            scrollState = scrollState,
 *            edgeNav = ScrollEdgeNavConfig.DEFAULT // 按需一键开通直达首尾胶囊
 *        )
 *    }
 *    ```
 * 3. 【一键直达首尾】：通过传入 [edgeNav] 参数，即可在右下角一键挂载微型操作胶囊，
 *    自动具备智能边缘感知与平滑滚动动画，无需在业务层手写协程与监听样板代码；
 * 4. 【多端自适应】：在 JVM 桌面端提供统一样式滚动条，在 Android 与 Web 端优雅降级。
 *
 * @param modifier 滚动条自身修饰符，标准使用 `Modifier.align(Alignment.CenterEnd).fillMaxHeight()`
 * @param scrollState 绑定的常规滚动状态 [ScrollState]
 * @param edgeNav 一键直达首尾胶囊配置契约，为 `null` 时不显示直达胶囊（如需开启可传 `ScrollEdgeNavConfig.DEFAULT`）
 */
@Composable
expect fun VerticalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    edgeNav: ScrollEdgeNavConfig? = null
)

/**
 * 跨平台纵向统一滚动条适配器组件（针对长列表 [LazyListState] 滚动容器）。
 *
 * 遵循项目《滚动条统一接入规范》红线规则：
 * 1. 【唯一入口】：全项目所有纵向长列表（LazyColumn）统一使用本适配器，严禁业务端私自实现滑块；
 * 2. 【工业级双轨平滑算法】：底层在 JVM 桌面端通过 [SmoothLazyScrollbarAdapter] 实现微位移连续相对平移（`scrollBy`）
 *    与远距离智能跳转（`snapTo`），彻底根绝非均质 Item 引发的滑块跳变忽大忽小与手势抖动缺陷；
 * 3. 【标准布局范式】：
 *    ```kotlin
 *    Box(modifier = Modifier.fillMaxSize()) {
 *        LazyColumn(
 *            state = listState,
 *            modifier = Modifier
 *                .fillMaxSize()
 *                .padding(end = 12.dp) // 预留呼吸边距
 *        ) { ... }
 *
 *        VerticalScrollbarAdapter(
 *            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
 *            scrollState = listState,
 *            edgeNav = ScrollEdgeNavConfig.DEFAULT // 可选直达首尾
 *        )
 *    }
 *    ```
 * 4. 【一键直达首尾】：支持传入 [edgeNav] 悬浮呈现微型双向药丸胶囊（回到顶部/直达底部），
 *    自动检测首尾阈值并支持平滑滚动到指定 Item 索引。
 *
 * @param modifier 滚动条自身修饰符，标准使用 `Modifier.align(Alignment.CenterEnd).fillMaxHeight()`
 * @param scrollState 绑定的懒加载列表状态 [LazyListState]
 * @param edgeNav 一键直达首尾胶囊配置契约，为 `null` 时不显示直达胶囊（如需开启可传 `ScrollEdgeNavConfig.DEFAULT`）
 */
@Composable
expect fun VerticalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: LazyListState,
    edgeNav: ScrollEdgeNavConfig? = null
)

package org.gemini.ui.forge.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用程序的全局间距系统配置。
 *
 * 该数据类集中管理应用内使用的各种间距大小，以便于在不同屏幕密度或设备形态（如移动端与桌面端）
 * 之间进行一致的间距缩放和适配。
 *
 * @property extraSmall 极小间距，通常用于紧密的元素聚合（如提示图标与文本之间）。
 * @property small 小间距，通常用于相近元素的常规分隔。
 * @property medium 中等间距，通常作为默认的基础间距（如内边距、常规组件间距）。
 * @property large 大间距，通常用于区分不同的内容区块。
 * @property extraLarge 极大间距，通常用于页面级或大型区块间的分隔。
 */
data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

/**
 * 用于在 Compose 树中向下传递 [AppSpacing] 实例的 CompositionLocal。
 * 可以通过 `LocalAppSpacing.current` 访问当前的间距配置。
 */
val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }

/**
 * 默认间距配置。
 * 适用于具有触控交互的设备或移动端应用，提供较大的点击区域和宽松的视觉布局。
 */
val DefaultSpacing = AppSpacing()

/**
 * 紧凑间距配置。
 * 适用于高密度的 PC 端或桌面端应用，空间利用率更高，适合密集的数据展示。
 */
val CompactSpacing = AppSpacing(
    extraSmall = 2.dp,
    small = 4.dp,
    medium = 8.dp,
    large = 16.dp,
    extraLarge = 24.dp
)

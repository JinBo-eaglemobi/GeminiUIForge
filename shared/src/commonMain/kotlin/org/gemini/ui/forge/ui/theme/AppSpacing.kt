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
    val extraLarge: Dp = 32.dp,
    
    /** 是否处于桌面紧凑模式 */
    val isCompact: Boolean = false,

    /** 输入框内部水平内边距 (水平 padding) */
    val textFieldPaddingHorizontal: Dp = if (isCompact) 10.dp else 16.dp,

    /** 输入框内部垂直内边距 (垂直 padding)：紧凑模式保留 6dp 呼吸感对标 IDEA；触控模式 12dp 确保 48dp 大热区不偏上 */
    val textFieldPaddingVertical: Dp = if (isCompact) 6.dp else 12.dp,
    
    /** 表单控件行间距 (垂直间距)：杜绝控件挤压贴死 */
    val formRowSpacing: Dp = if (isCompact) 10.dp else 14.dp,

    /** 表单控件列间距 (同一行内多输入框的水平间距)：保证如 X 与 Y、W 与 H 之间有清晰舒适的分隔 */
    val formColumnSpacing: Dp = if (isCompact) 10.dp else 14.dp,

    /**
     * 弹窗标准宽度 Tokens 阶梯体系 (Design Tokens)
     * [dialogAlertWidth] 用于轻量警告、操作二次确认、简单输入等微型弹窗（默认 440.dp）。
     */
    val dialogAlertWidth: Dp = 440.dp,

    /**
     * [dialogConfirmWidth] 兼容现有调用点，对应 [dialogAlertWidth]（默认 440.dp）。
     */
    val dialogConfirmWidth: Dp = 440.dp,
    
    /**
     * [dialogConfigWidth] 用于添加图层、编译环境、通用配置等复杂表单弹窗（默认 520.dp）。
     */
    val dialogConfigWidth: Dp = 520.dp,

    /**
     * [dialogMediumWidth] 用于中型详情、资产列表、快捷操作等弹窗（默认 720.dp）。
     */
    val dialogMediumWidth: Dp = 720.dp,

    /**
     * [dialogLargeWidth] 用于大型多栏布局、大图对比与编辑器类弹窗（默认 960.dp）。
     */
    val dialogLargeWidth: Dp = 960.dp,

    /**
     * [dialogHugeWidth] 用于全景工作台、会话原始网络通信档案等信息密集型宽屏弹窗（默认 1280.dp）。
     */
    val dialogHugeWidth: Dp = 1280.dp
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
val DefaultSpacing = AppSpacing(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 16.dp,
    large = 24.dp,
    extraLarge = 32.dp,
    isCompact = false,
    textFieldPaddingHorizontal = 16.dp,
    textFieldPaddingVertical = 12.dp,
    formRowSpacing = 14.dp,
    formColumnSpacing = 14.dp
)

/**
 * 紧凑间距配置。
 * 适用于高密度的 PC 端或桌面端应用，空间利用率更高，适合密集的数据展示。
 */
val CompactSpacing = AppSpacing(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 12.dp,
    large = 18.dp,
    extraLarge = 24.dp,
    isCompact = true,
    textFieldPaddingHorizontal = 10.dp,
    textFieldPaddingVertical = 6.dp,
    formRowSpacing = 10.dp,
    formColumnSpacing = 10.dp
)

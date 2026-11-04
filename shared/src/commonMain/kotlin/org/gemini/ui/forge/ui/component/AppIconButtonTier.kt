package org.gemini.ui.forge.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.AppSpacing

/**
 * 图标按钮的标准尺寸阶梯定义。
 *
 * 遵循 8pt/4pt 人机工程学空间网格系统，外层按钮容器与内嵌图标尺寸严格成对守恒：
 * - [MICRO]: 容器 20dp / 图标 12dp / 圆角 4dp，适用于树节点折叠箭头、极紧凑行内标记
 * - [COMPACT]: 容器 28dp / 图标 16dp / 圆角 6dp，适用于工具栏、侧边栏 Header、代码块控制栏
 * - [STANDARD]: 容器 32dp / 图标 18dp / 圆角 6dp，适用于弹窗 Header 关闭键、主要面板顶部工具条
 * - [LARGE]: 容器 36dp / 图标 20dp / 圆角 8dp，适用于全局 TopBar 返回键、大厅全局操作
 */
enum class AppIconButtonTier(
    val defaultButtonSize: Dp,
    val defaultIconSize: Dp,
    val defaultCornerRadius: Dp
) {
    MICRO(
        defaultButtonSize = 20.dp,
        defaultIconSize = 12.dp,
        defaultCornerRadius = 4.dp
    ),
    COMPACT(
        defaultButtonSize = 28.dp,
        defaultIconSize = 16.dp,
        defaultCornerRadius = 6.dp
    ),
    STANDARD(
        defaultButtonSize = 32.dp,
        defaultIconSize = 18.dp,
        defaultCornerRadius = 6.dp
    ),
    LARGE(
        defaultButtonSize = 36.dp,
        defaultIconSize = 20.dp,
        defaultCornerRadius = 8.dp
    );

    /**
     * 根据当前主题的 [AppSpacing] 动态解析容器尺寸。
     */
    fun buttonSize(spacing: AppSpacing): Dp = when (this) {
        MICRO -> spacing.iconButtonMicroSize
        COMPACT -> spacing.iconButtonCompactSize
        STANDARD -> spacing.iconButtonStandardSize
        LARGE -> spacing.iconButtonLargeSize
    }

    /**
     * 根据当前主题的 [AppSpacing] 动态解析图标尺寸。
     */
    fun iconSize(spacing: AppSpacing): Dp = when (this) {
        MICRO -> spacing.iconMicroSize
        COMPACT -> spacing.iconCompactSize
        STANDARD -> spacing.iconStandardSize
        LARGE -> spacing.iconLargeSize
    }
}

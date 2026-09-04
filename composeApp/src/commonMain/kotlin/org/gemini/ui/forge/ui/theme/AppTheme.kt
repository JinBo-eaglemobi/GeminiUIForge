package org.gemini.ui.forge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.app.ThemeMode
import org.gemini.ui.forge.model.app.LayoutMode
import org.gemini.ui.forge.utils.RetryingClipboard

/**
 * 全局平台物理真实密度备份。
 * 用于在嵌入 AWT/Swing 原生组件（如 JCEF 浏览器）时恢复 100% 的物理坐标系，
 * 避免因全局紧凑密度（0.7）导致 SwingPanel 计算 Px 位置发生偏离与越界。
 */
val LocalPlatformDensity = staticCompositionLocalOf { Density(1f) }

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val DefaultTypography = Typography()

// 高密度排版 (用于 PC 端或紧凑视图)
private val CompactTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = DefaultTypography.displayMedium.copy(fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall = DefaultTypography.displaySmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = DefaultTypography.titleLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = DefaultTypography.titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = DefaultTypography.titleSmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = DefaultTypography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = DefaultTypography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = DefaultTypography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = DefaultTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
)

/**
 * 为当前 Typography 的所有字阶统一切换指定字体家族
 */
fun Typography.withFontFamily(fontFamily: FontFamily): Typography {
    return this.copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}

/**
 * 紧凑模式全局缩放系数：所有以 dp 定义尺寸的组件（菜单项、输入框、按钮及各类内边距）的视觉缩放比例。
 * 取值 0.7 由项目此前手动适配的经验值反推而来（菜单项 48dp→32dp、输入框 56dp→36dp）。
 * 如需调整整体紧凑程度，仅需修改此一处常量即可全局生效。
 */
private const val COMPACT_DENSITY_SCALE = 0.7f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTheme(
    themeMode: ThemeMode,
    layoutMode: LayoutMode = LayoutMode.AUTO,
    customTypography: Typography? = null,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colors = if (useDarkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    val actualLayoutMode = if (layoutMode == LayoutMode.AUTO) getSystemDefaultLayoutMode() else layoutMode
    val isCompact = actualLayoutMode == LayoutMode.COMPACT

    // 提取外部注入的自定义字体（如 JS 端加载的 WOFF2 字体）
    val customFontFamily = customTypography?.bodyMedium?.fontFamily

    // 精确融合：确保紧凑排版字号/行高 与 自定义字体 能够同时生效
    val currentTypography = when {
        customFontFamily != null && isCompact -> CompactTypography.withFontFamily(customFontFamily)
        customFontFamily != null -> DefaultTypography.withFontFamily(customFontFamily)
        isCompact -> CompactTypography
        else -> DefaultTypography
    }

    // 在高密度/PC模式下，禁用M3默认的48dp最小触摸目标限制，并使用紧凑间距
    val currentSpacing = if (isCompact) CompactSpacing else DefaultSpacing

    // ===== 全局紧凑样式定型点（唯一配置处，业务层无需再做任何布局模式判断） =====
    // 通过重映射 LocalDensity 实现"启动时一次配置，全局生效"：
    // 1. density × COMPACT_DENSITY_SCALE：所有以 dp 定义尺寸的 M3 组件（菜单项、输入框、
    //    按钮及各类内边距/间距）视觉上整体等比缩小，无需逐组件手动设值；
    // 2. fontScale ÷ COMPACT_DENSITY_SCALE：精确补偿文字的 sp 像素换算，保证文字视觉大小
    //    不变，字号层级仍完全由上方 currentTypography（CompactTypography）独立管理；
    // 3. 位图资源按固有像素显示、不走 dp，不受此配置影响（画布等场景如需精确控制请单独处理）。
    val baseDensity = LocalDensity.current
    val uiDensity = if (isCompact) {
        Density(
            density = baseDensity.density * COMPACT_DENSITY_SCALE,
            fontScale = baseDensity.fontScale / COMPACT_DENSITY_SCALE
        )
    } else {
        baseDensity
    }

    CompositionLocalProvider(
        LocalDensity provides uiDensity,
        LocalPlatformDensity provides baseDensity, // ★ 备份系统的物理真实密度，供 SwingPanel 等原生嵌入组件隔离使用
        LocalMinimumInteractiveComponentSize provides if (isCompact) 0.dp else 48.dp,
        LocalAppSpacing provides currentSpacing,
        // 带重试兜底的剪贴板：统一容忍 Windows 剪贴板瞬锁异常，
        // 覆盖所有粘贴入口（键盘 Ctrl+V / 右键菜单 / 系统剪贴板历史）的读取路径
        LocalClipboard provides RetryingClipboard(LocalClipboard.current)
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = AppShapes,
            typography = currentTypography,
            content = content
        )
    }
}

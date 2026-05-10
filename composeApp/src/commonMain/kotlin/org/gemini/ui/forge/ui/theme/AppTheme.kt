package org.gemini.ui.forge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.app.ThemeMode
import org.gemini.ui.forge.model.app.LayoutMode

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

@Composable
fun AppTheme(
    themeMode: ThemeMode,
    layoutMode: LayoutMode = LayoutMode.AUTO,
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

    val currentTypography = if (actualLayoutMode == LayoutMode.COMPACT) {
        CompactTypography
    } else {
        DefaultTypography
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        typography = currentTypography,
        content = content
    )
}

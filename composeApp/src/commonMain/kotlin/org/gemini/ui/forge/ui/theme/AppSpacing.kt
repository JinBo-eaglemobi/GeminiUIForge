package org.gemini.ui.forge.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }

// 默认间距 (用于触摸屏/移动端)
val DefaultSpacing = AppSpacing()

// 紧凑间距 (用于高密度PC端)
val CompactSpacing = AppSpacing(
    extraSmall = 2.dp,
    small = 4.dp,
    medium = 8.dp,
    large = 16.dp,
    extraLarge = 24.dp
)

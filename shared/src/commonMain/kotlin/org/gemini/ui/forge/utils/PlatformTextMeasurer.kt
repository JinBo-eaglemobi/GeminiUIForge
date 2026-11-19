package org.gemini.ui.forge.utils

/**
 * 跨平台真实字体度量器接口
 */
expect object PlatformTextMeasurer {
    fun measureText(
        text: String,
        fontSize: Int,
        letterSpacing: Int = 0,
        leading: Int = 4
    ): Pair<Float, Float>
}

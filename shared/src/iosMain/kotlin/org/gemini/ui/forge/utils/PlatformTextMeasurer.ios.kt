package org.gemini.ui.forge.utils

import kotlin.math.ceil
import kotlin.math.max

actual object PlatformTextMeasurer {
    actual fun measureText(
        text: String,
        fontSize: Int,
        letterSpacing: Int,
        leading: Int
    ): Pair<Float, Float> {
        val safeText = if (text.isEmpty()) " " else text
        val lines = safeText.split("\n", "\r\n")
        val effectiveFontSize = fontSize.coerceAtLeast(10).toFloat()
        var maxLineWidth = 0f
        for (line in lines) {
            val w = line.length * effectiveFontSize * 0.6f + (line.length - 1).coerceAtLeast(0) * letterSpacing
            if (w > maxLineWidth) maxLineWidth = w
        }
        val safeW = max(10f, ceil(maxLineWidth))
        val safeH = max(effectiveFontSize, ceil(lines.size * effectiveFontSize * 1.25f + (lines.size - 1) * leading))
        return Pair(safeW, safeH)
    }
}

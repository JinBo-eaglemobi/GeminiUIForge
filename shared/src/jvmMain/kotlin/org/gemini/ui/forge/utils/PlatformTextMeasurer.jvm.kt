package org.gemini.ui.forge.utils

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import kotlin.math.ceil
import kotlin.math.max

/**
 * JVM 平台真实字体度量器（对齐 Laya.Text 运行时的 Canvas 2D 真实度量逻辑）：
 * 采用原生 AWT Font 与 FontRenderContext 执行微米级物理字符串测量，
 * 彻底消灭估算偏差，保证组件外包尺寸（bounds）恒等于字体渲染本体大小（0 假留白、0 截断）！
 */
actual object PlatformTextMeasurer {
    private val frc = FontRenderContext(AffineTransform(), true, true)

    actual fun measureText(
        text: String,
        fontSize: Int,
        letterSpacing: Int,
        leading: Int
    ): Pair<Float, Float> {
        val safeText = if (text.isEmpty()) " " else text
        val lines = safeText.split("\n", "\r\n")
        val awtFont = Font(Font.SANS_SERIF, Font.PLAIN, fontSize.coerceAtLeast(10))

        var maxLineWidth = 0f
        for (line in lines) {
            if (line.isEmpty()) continue
            val bounds = awtFont.getStringBounds(line, frc)
            var currentLineWidth = bounds.width.toFloat()
            if (line.length > 1 && letterSpacing > 0) {
                currentLineWidth += (line.length - 1) * letterSpacing.toFloat()
            }
            if (currentLineWidth > maxLineWidth) {
                maxLineWidth = currentLineWidth
            }
        }

        val safeW = max(10f, ceil(maxLineWidth))
        val lineMetrics = awtFont.getLineMetrics(safeText, frc)
        val singleLineHeight = lineMetrics.height
        val lineCount = lines.size.coerceAtLeast(1)
        val totalH = lineCount * singleLineHeight + (lineCount - 1) * leading.toFloat()
        val safeH = max(fontSize.toFloat(), ceil(totalH))

        return Pair(safeW, safeH)
    }
}

package org.gemini.ui.forge.utils

/**
 * 文本排版尺寸动态度量器：
 * 采用原生字体物理度量（完全等价于 Laya.Text 运行时的 Canvas measureText），
 * 彻底消灭估算偏差，保证组件外包尺寸（bounds）恒等于字体渲染本体大小（0 假留白、0 截断）！
 */
object TextDimensionCalculator {

    fun calculateDimension(
        text: String,
        fontSize: Int,
        letterSpacing: Int = 0,
        leading: Int = 4
    ): Pair<Float, Float> {
        return PlatformTextMeasurer.measureText(text, fontSize, letterSpacing, leading)
    }
}

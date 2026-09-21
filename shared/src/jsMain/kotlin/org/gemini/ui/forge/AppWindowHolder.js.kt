package org.gemini.ui.forge

import androidx.compose.ui.unit.IntRect

/**
 * 应用主窗口持有器 (Web 实现)
 * 浏览器环境无法访问窗口离屏渲染与全局事件注入，暂不支持 (待适配)。
 */
actual object AppWindowHolder {
    actual fun holdWindow(ref: Any?) { /* Web 待适配 */ }
    actual fun captureWindowBytes(region: IntRect?): ByteArray? = null
    actual fun tapAt(x: Float, y: Float): Boolean = false
}

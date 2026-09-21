package org.gemini.ui.forge

import androidx.compose.ui.unit.IntRect

/**
 * 应用主窗口持有器 (Android 实现)
 * Android 无桌面 ComposeWindow 概念，离屏截图与坐标注入暂不支持 (待适配)。
 */
actual object AppWindowHolder {
    actual fun holdWindow(ref: Any?) { /* Android 待适配 */ }
    actual fun captureWindowBytes(region: IntRect?): ByteArray? = null
    actual fun tapAt(x: Float, y: Float): Boolean = false
}

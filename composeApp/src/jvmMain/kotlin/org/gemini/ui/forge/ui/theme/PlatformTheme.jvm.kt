package org.gemini.ui.forge.ui.theme

import org.gemini.ui.forge.model.app.LayoutMode

actual fun getSystemDefaultLayoutMode(): LayoutMode {
    return LayoutMode.COMPACT // PC/JVM 端默认紧凑
}
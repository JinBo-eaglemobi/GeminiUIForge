package org.gemini.ui.forge.ui.theme

import org.gemini.ui.forge.model.app.LayoutMode

actual fun getSystemDefaultLayoutMode(): LayoutMode {
    return LayoutMode.TOUCH // Android 默认触控
}
package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

@Serializable
enum class LayoutMode {
    AUTO,
    TOUCH, // 移动端/触控优先 (MD3 默认大尺寸)
    COMPACT // PC端/鼠标优先 (高密度紧凑尺寸)
}

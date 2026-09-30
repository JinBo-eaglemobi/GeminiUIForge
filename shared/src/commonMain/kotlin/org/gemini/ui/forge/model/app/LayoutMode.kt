package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

/**
 * 界面人机工程学布局排版模式
 *
 * 用于控制 UI 组件间距（Padding/Margin）、按钮触控热区与卡片高密度的自适应展示。
 */
@Serializable
enum class LayoutMode {
    /** 自动模式：根据当前宿主视口与屏幕尺寸自适应切换触控或紧凑 */
    AUTO,

    /** 移动端/触控优先：遵循 Material Design 3 默认大尺寸（推荐移动端与大屏平板） */
    TOUCH,

    /** PC端/鼠标优先：高密度紧凑尺寸（紧凑内边距与小号控制组件，提升 PC 空间利用率） */
    COMPACT
}

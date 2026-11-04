package org.gemini.ui.forge.ui.component

/**
 * 图标按钮的视觉变体样式。
 *
 * 规范应用内不同优先级与交互上下文中的图标按钮背景与边框表现：
 * - [GHOST]: 幽灵透明（默认常态透明背景，悬停/按下时显示微底色，适用绝大多数常规工具栏）
 * - [FILLED_TONAL]: 微底板（带有轻量 surfaceVariant / primaryContainer 底色，适用次级强调按钮）
 * - [OUTLINED]: 描边型（带 1dp 细轮廓边框，适用于卡片内或独立成块的操作区域）
 * - [FILLED]: 实体强调（带有 primary 实色背景，适用于高优先级关键操作）
 */
enum class AppIconButtonVariant {
    /** 幽灵透明型（常态无背景） */
    GHOST,

    /** 微底板型（轻量色块底板） */
    FILLED_TONAL,

    /** 描边轮廓型（带细边框） */
    OUTLINED,

    /** 实体强调型（实色填充） */
    FILLED
}

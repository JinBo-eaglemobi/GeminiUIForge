package org.gemini.ui.forge.model.app

/**
 * 软件工作台排版风格模式枚举 (UIStyleMode)。
 * 系统唯一官方标准：工业流线风。
 */
enum class UIStyleMode(
    val displayNameZh: String,
    val displayNameEn: String
) {
    /** 工业紧凑流线风 (系统默认标准：平整无框、高密度双列对齐、紧凑胶囊) */
    STUDIO_COMPACT("工业流线风 (默认推荐)", "Industrial Studio (Default)")
}

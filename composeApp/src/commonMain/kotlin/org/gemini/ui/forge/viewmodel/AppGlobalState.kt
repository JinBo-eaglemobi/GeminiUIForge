package org.gemini.ui.forge.viewmodel

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    /** 跟随系统设置 */
    SYSTEM,
    /** 浅色模式 */
    LIGHT,
    /** 深色模式 */
    DARK
}

/**
 * 应用页面枚举，用于导航管理
 */
enum class AppScreen {
    /** 首页（模板列表） */
    HOME,
    /** 模板编辑器页面 */
    EDITOR,
    /** 通过 AI 生成新模板的页面 */
    TEMPLATE_GENERATOR
}

/**
 * 应用全局状态模型
 * @property currentScreen 当前正在显示的页面
 * @property themeMode 当前应用的主题模式设置
 * @property apiKey 从环境变量或设置中加载的 Gemini API 密钥
 */
data class AppGlobalState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val apiKey: String = ""
)
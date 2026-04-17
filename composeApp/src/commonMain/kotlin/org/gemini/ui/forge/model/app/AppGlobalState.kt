package org.gemini.ui.forge.model.app

/**
 * 应用全局状态模型
 * @property currentScreen 当前正在显示的页面
 * @property themeMode 当前应用的主题模式设置
 * @property languageCode 应用界面的语言设置 (zh, en)
 * @property promptLangPref 提示词显示的语言偏好选项 (AUTO, ZH, EN)
 * @property apiKey 从设置中加载的 Gemini API 密钥
 * @property effectiveApiKey 实际生效的密钥 (apiKey 或环境变量)
 * @property templateStorageDir 模板数据的存储目录
 * @property maxRetries API 请求的最大重试次数
 * @property shortcuts 快捷键映射表 (Action -> 组合键描述)
 */
data class AppGlobalState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageCode: String = "zh",
    val promptLangPref: PromptLanguage = PromptLanguage.AUTO,
    val apiKey: String = "",
    val effectiveApiKey: String = "",
    val templateStorageDir: String = "",
    val maxRetries: Int = 3,
    val shortcuts: Map<ShortcutAction, String> = ShortcutAction.entries.associate { it to it.defaultKey }
)

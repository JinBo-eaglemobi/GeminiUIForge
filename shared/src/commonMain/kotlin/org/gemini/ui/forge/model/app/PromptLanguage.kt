package org.gemini.ui.forge.model.app
/**
 * 提示词显示的语言偏好选项
 */
enum class PromptLanguage(val displayName: String) {
    AUTO("自动"),
    ZH("中文"),
    EN("英文");

    /**
     * 解析自动模式。
     * 如果当前不是 AUTO，则返回自身；
     * 如果是 AUTO，根据传入的语言代码返回 ZH 或 EN。
     */
    fun resolve(languageCode: String?): PromptLanguage {
        if (this != AUTO) return this
        val code = languageCode?.lowercase() ?: "en"
        return if (code.startsWith("zh")) ZH else EN
    }
}

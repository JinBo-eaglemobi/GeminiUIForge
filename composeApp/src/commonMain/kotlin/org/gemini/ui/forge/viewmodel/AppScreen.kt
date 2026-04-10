package org.gemini.ui.forge.viewmodel

/**
 * 应用中支持显示的屏幕枚举
 */
enum class AppScreen {
    /** 首页：展示已有的模板列表 */
    HOME,
    /** 基于模板生成 UI 资源的页面 */
    EDITOR,
    /** 真正的模板结构编辑器页面 */
    TEMPLATE_EDITOR,
    /** 通过 AI 生成新模板的页面 */
    TEMPLATE_GENERATOR
}

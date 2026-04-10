package org.gemini.ui.forge.model.app
/**
 * 应用中支持显示的屏幕枚举
 */
enum class AppScreen {
    /** 首页：展示已有的模板列表 */
    HOME,
    /** 资源生成：基于模板真正生成 UI 资源的页面 */
    TEMPLATE_ASSET_GEN,
    /** 真正的模板结构编辑器页面 (编辑舞台) */
    TEMPLATE_EDITOR,
    /** 通过 AI 生成新模板的页面 (模板分析) */
    TEMPLATE_GENERATOR
}

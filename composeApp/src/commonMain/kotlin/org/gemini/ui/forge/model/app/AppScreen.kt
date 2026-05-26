package org.gemini.ui.forge.model.app
/**
 * 应用中支持显示的屏幕枚举
 */
enum class AppScreen {
    /** 首页：展示已有的模板列表 */
    HOME,
    /** 通过 AI 生成新模板的页面 (模板分析) */
    TEMPLATE_GENERATOR,
    /** 统一工作区页面：包含布局编辑和资产生成 */
    PROJECT_WORKSPACE
}

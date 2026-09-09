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
    PROJECT_WORKSPACE,
    /** 游戏项目管理：创建向导（表单 / 凭据 / 环境检测） */
    GAME_PROJECT_MANAGER,
    /** 游戏项目专属工作台：资源树 / HTML 预览 / 调试面板 */
    GAME_PROJECT_WORKSPACE
}

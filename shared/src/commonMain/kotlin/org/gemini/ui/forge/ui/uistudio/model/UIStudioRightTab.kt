package org.gemini.ui.forge.ui.uistudio.model

/**
 * UI Studio 右侧复合边栏分页模式枚举。
 *
 * 遵循一文件一职责规范：
 * - [PROPERTY]：属性检查器与高保真切片预览面板；
 * - [AI_CHAT]：嵌入式 AI 智能设计助手对话工作台。
 */
enum class UIStudioRightTab {
    /** 属性检查器与实时预览 */
    PROPERTY,

    /** 嵌入式 AI 对话助手 */
    AI_CHAT
}

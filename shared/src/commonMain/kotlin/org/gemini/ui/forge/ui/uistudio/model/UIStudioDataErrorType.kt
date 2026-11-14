package org.gemini.ui.forge.ui.uistudio.model

/**
 * UI Studio 工程数据完整性异常类型枚举
 */
enum class UIStudioDataErrorType {
    /**
     * 图元已引用独立页面 (targetPageId != null)，但自身依然包含了历史子图元 children
     */
    REFERENCE_WITH_CHILDREN,

    /**
     * 图元设置了 targetPageId，但目标页面在工程 studioPages 清单中不存在
     */
    TARGET_PAGE_NOT_FOUND
}

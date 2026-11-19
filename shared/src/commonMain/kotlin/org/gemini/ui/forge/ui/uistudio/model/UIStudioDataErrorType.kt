package org.gemini.ui.forge.ui.uistudio.model

/**
 * UI Studio 工程数据完整性异常类型枚举
 */
enum class UIStudioDataErrorType {
    /**
     * 图元已引用独立页面 (targetPageId != null)，但自身依然包含了历史子图元 children
     * （注：LIST 预置 item 模式已全面豁免）
     */
    REFERENCE_WITH_CHILDREN,

    /**
     * 图元设置了 targetPageId，但目标页面在工程 studioPages 清单中不存在
     */
    TARGET_PAGE_NOT_FOUND,

    /**
     * 无外连引用的内嵌组件携带子树（界面级复合单元被内联，S1 核心违规，支持一键修复拆分）
     */
    INLINE_PAGE_NESTED,

    /**
     * 契约子节点形态违规（非白名单携带 children、子节点非叶子、契约命名非法、数量超标等）
     */
    CONTRACT_CHILD_INVALID,

    /**
     * 控制器或动效挂载在非根节点图元上（违背 S3 Root-Only 铁律）
     */
    CONTROLLER_ON_NON_ROOT,

    /**
     * 页面根级 controllers/transitions 配置非法或动效 target 悬空
     */
    ROOT_CONFIG_INVALID
}

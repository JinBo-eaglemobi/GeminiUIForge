package org.gemini.ui.forge.service.studio

/**
 * 素材文件重名冲突处理策略枚举
 */
enum class NameConflictPolicy {
    /** 发现重名时直接拒绝并报错（适用于界面手动弹窗保存，提示用户修改文件名） */
    REJECT,
    /** 发现重名时自动追加毫秒时间戳后缀保存（适用于 AI 工具自动化流水线，防阻塞） */
    AUTO_RENAME,
    /** 发现重名时直接覆盖原文件 */
    OVERWRITE
}

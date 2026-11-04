package org.gemini.ui.forge.ui.uistudio.verify.model

/**
 * AI 原图去背各阶段 Task 步骤执行状态枚举。
 */
enum class MattingStepStatus {
    /** 尚未执行，排队等待中 */
    PENDING,
    /** 正在全力执行中 */
    RUNNING,
    /** 已成功执行完毕 */
    COMPLETED
}

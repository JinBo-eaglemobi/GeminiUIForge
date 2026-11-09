package org.gemini.ui.forge.service.job

/**
 * 异步生图任务状态枚举
 */
enum class ImageJobStatus {
    /** 任务已创建或已提交 */
    SUBMITTED,

    /** 任务正在后台异步生成中 */
    RUNNING,

    /** 任务已成功完成，图像产物已就绪 */
    SUCCESS,

    /** 任务执行失败 */
    FAILED
}

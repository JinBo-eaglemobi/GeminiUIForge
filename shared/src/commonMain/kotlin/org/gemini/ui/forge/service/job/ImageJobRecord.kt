package org.gemini.ui.forge.service.job

import org.gemini.ui.forge.getCurrentTimeMillis

/**
 * 异步生图任务状态记录实体
 *
 * 持久化与内存跟踪单次文生图作业的完整生命周期、阶段性进度、耗时与产物数据。
 */
data class ImageJobRecord(
    val jobId: String,
    val prompt: String,
    val projectName: String,
    val status: ImageJobStatus = ImageJobStatus.SUBMITTED,
    val progress: Float = 0.0f,
    val statusMessage: String = "任务已提交",
    val base64Data: String? = null,
    val mimeType: String? = null,
    val imageUri: String? = null,
    val savedRelativePath: String? = null,
    val width: Float = 1024f,
    val height: Float = 1024f,
    val errorMessage: String? = null,
    val createdAt: Long = getCurrentTimeMillis(),
    val completedAt: Long? = null
) {
    /** 当前任务已耗时秒数 */
    val elapsedSeconds: Long
        get() {
            val endTime = completedAt ?: getCurrentTimeMillis()
            return ((endTime - createdAt) / 1000L).coerceAtLeast(0L)
        }
}

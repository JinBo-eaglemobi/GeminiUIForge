package org.gemini.ui.forge.model.app

import org.gemini.ui.forge.utils.DownloadProgress

/**
 * 软件更新信息模型
 */
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val publishDate: String? = null
)

/**
 * 更新检查状态
 */
sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val info: UpdateInfo) : UpdateStatus()

    /**
     * 下载中：携带通用下载器的完整进度快照（总量/速度/活跃连接数/分块明细），
     * 兼容属性 [progress]（0~1）供旧展示位直接取用。
     */
    data class Downloading(val snapshot: DownloadProgress?) : UpdateStatus() {
        /** 归一化总进度（0~1）；快照缺失时返回 0 */
        val progress: Float
            get() = if (snapshot != null && snapshot.totalBytes > 0) {
                (snapshot.downloadedBytes.toDouble() / snapshot.totalBytes).coerceIn(0.0, 1.0).toFloat()
            } else 0f
    }

    object ReadyToInstall : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

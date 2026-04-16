package org.gemini.ui.forge.model.app

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
    data class Downloading(val progress: Float) : UpdateStatus()
    object ReadyToInstall : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

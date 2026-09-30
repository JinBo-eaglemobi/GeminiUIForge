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
 * 软件更新检查与下载安装生命周期状态
 */
sealed class UpdateStatus {
    /** 空闲状态：尚未发起更新探测 */
    object Idle : UpdateStatus()

    /** 检查中：正在请求远程 GitHub Releases / Gitee 查询最新版本元数据 */
    object Checking : UpdateStatus()

    /** 发现新版本：包含更新说明与下载地址 */
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

    /** 下载完成：已校验安装包完整性，随时可调起原生安装或重启替换 */
    object ReadyToInstall : UpdateStatus()

    /** 已是最新版本：无需更新 */
    object UpToDate : UpdateStatus()

    /** 更新过程中出现异常（网络中断、超时、签名失效等） */
    data class Error(val message: String) : UpdateStatus()
}

package org.gemini.ui.forge.model.app

/**
 * 远程候选下载源的文件物理元数据
 *
 * @property sourceLabel 镜像源显示名称（如 "[国内] 华为云开源镜像"）
 * @property sourceUrl 下载文件的完整 URL
 * @property contentLength 远程文件的完整字节数（-1 表示未知）
 * @property supportsRange 服务端是否支持 HTTP Range 断点续传
 */
data class RemoteSourceMetadata(
    val sourceLabel: String,
    val sourceUrl: String,
    val contentLength: Long = -1L,
    val supportsRange: Boolean = false
)

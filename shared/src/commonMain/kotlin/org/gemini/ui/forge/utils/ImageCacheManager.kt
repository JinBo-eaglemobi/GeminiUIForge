package org.gemini.ui.forge.utils

import org.gemini.ui.forge.getCurrentTimeMillis

/**
 * 全项目统一的图片磁盘缓存管理中枢（公共工具，不绑定任何 MCP 业务）。
 *
 * 核心职责：
 * 1. 将 [CompactImage] 压缩字节异步落盘到应用数据根目录下的 screenshot_cache/ 子目录；
 * 2. 落盘成功后自动执行非破坏性过期轮转清理（默认 24 小时），防止磁盘无限膨胀；
 * 3. 清理仅依据文件名中编码的毫秒时间戳判定，零额外元数据 IO，跨平台安全。
 *
 * 文件命名规范：{prefix}_{毫秒时间戳}.{extension}，例如 window_1726901234567.webp
 */
object ImageCacheManager {

    /** 缓存根子目录（位于应用数据根目录 ~/.geminiuiforge/ 下） */
    private const val CACHE_DIR = "screenshot_cache"

    /** 默认过期时长：24 小时 */
    private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    /**
     * 将压缩后的图片字节落盘缓存。
     *
     * @param prefix 文件名前缀（业务语义标识，非法字符自动替换为下划线）
     * @param image 已经过 [compressToCompactImage] 压缩的图片载体
     * @return 落盘成功返回文件绝对路径；失败返回 null（绝不抛异常阻断业务）
     */
    suspend fun saveCache(prefix: String, image: CompactImage): String? {
        if (image.bytes.isEmpty()) return null
        return try {
            val storage = LocalFileStorage()
            val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${safePrefix}_${getCurrentTimeMillis()}.${image.extension}"
            val absPath = storage.saveBytesToFile("$CACHE_DIR/$fileName", image.bytes)
            // 落盘成功后顺带执行过期轮转清理（静默容错，绝不影响主流程）
            cleanupExpired()
            absPath
        } catch (e: Exception) {
            AppLogger.w("ImageCacheManager", "图片缓存落盘失败: ${e.message}")
            null
        }
    }

    /**
     * 非破坏性过期轮转清理：仅删除文件名时间戳早于过期线的缓存文件。
     * 不触碰任何模板资产与其他业务数据，无需二次确认。
     */
    suspend fun cleanupExpired(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        try {
            val storage = LocalFileStorage()
            val files = storage.listFiles(CACHE_DIR)
            if (files.isEmpty()) return
            val deadline = getCurrentTimeMillis() - maxAgeMillis
            files.forEach { name ->
                // 解析文件名中段的毫秒时间戳：{prefix}_{millis}.{ext}
                val savedAt = name.substringAfterLast('_', "").substringBefore('.', "").toLongOrNull()
                if (savedAt != null && savedAt < deadline) {
                    storage.deleteFile("$CACHE_DIR/$name")
                }
            }
        } catch (_: Exception) {
            // 清理失败静默容错，绝不影响业务主流程
        }
    }
}

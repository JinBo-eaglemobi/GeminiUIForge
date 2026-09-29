package org.gemini.ui.forge.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gemini.ui.forge.formatUnsignedDateTime
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.parseUnsignedDateTime

/**
 * 全项目统一的图片磁盘缓存管理中枢（公共工具，不绑定任何 MCP 业务）。
 *
 * 核心职责：
 * 1. 将 [CompactImage] 压缩字节异步落盘到应用数据根目录下的 screenshot_cache/ 子目录；
 * 2. 统一命名规范：{projectName}_{prefix}_{yyyyMMddHHmmss}_{sequence}.{ext}
 *    - projectName：可选项目名，非空时置于前缀；
 *    - prefix：业务语义标识；
 *    - yyyyMMddHHmmss：无符号年月日时分秒（14位纯数字）；
 *    - sequence：进程级自增计数值，程序启动/重启时从 0 开始重新计数；
 * 3. 落盘成功后自动执行非破坏性过期轮转清理（默认 24 小时），仅识别并按新规范解析，不兼容旧版毫秒戳。
 */
object ImageCacheManager {

    /** 缓存根子目录（位于应用数据根目录 ~/.geminiuiforge/ 下） */
    private const val CACHE_DIR = "screenshot_cache"

    /** 默认过期时长：24 小时 */
    private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    /** 进程级自增计数器锁 */
    private val counterMutex = Mutex()

    /** 进程级自增计数值，程序启动/重启时从 0 开始重新计数 */
    private var sequenceCounter = 0L

    /**
     * 将压缩后的图片字节落盘缓存。
     *
     * @param prefix 文件名前缀（业务语义标识，非法字符自动替换为下划线）
     * @param image 已经过 [compressToCompactImage] 压缩的图片载体
     * @param projectName 可选所属项目名称，传入时自动作为前缀最前端
     * @return 落盘成功返回文件绝对路径；失败返回 null（绝不抛异常阻断业务）
     */
    suspend fun saveCache(
        prefix: String,
        image: CompactImage,
        projectName: String? = null
    ): String? {
        if (image.bytes.isEmpty()) return null
        return try {
            val storage = LocalFileStorage()
            val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val seq = counterMutex.withLock { sequenceCounter++ }
            val timestamp = formatUnsignedDateTime()
            val fileName = if (!projectName.isNullOrBlank()) {
                val safeProjectName = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                "${safeProjectName}_${safePrefix}_${timestamp}_$seq.${image.extension}"
            } else {
                "${safePrefix}_${timestamp}_$seq.${image.extension}"
            }
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
     * 非破坏性过期轮转清理：
     * 仅按新规范文件名格式（..._{yyyyMMddHHmmss}_{sequence}.{ext}）提取 14 位无符号时间戳进行过期比较；
     * 彻底不兼容旧版 13 位毫秒戳格式，凡无法解析为合法时间戳的旧格式一律视为陈旧文件直接清理，净化缓存目录。
     */
    suspend fun cleanupExpired(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        try {
            val storage = LocalFileStorage()
            val files = storage.listFiles(CACHE_DIR)
            if (files.isEmpty()) return
            val deadline = getCurrentTimeMillis() - maxAgeMillis
            files.forEach { name ->
                val baseName = name.substringBeforeLast('.', "")
                val parts = baseName.split('_')
                // 格式应为：[...parts, yyyyMMddHHmmss, sequence]
                // 至少需要包含时间戳与序号（长度 >= 2）
                if (parts.size >= 2) {
                    val timestampStr = parts[parts.size - 2]
                    val savedAt = parseUnsignedDateTime(timestampStr)
                    if (savedAt != null) {
                        if (savedAt < deadline) {
                            storage.deleteFile("$CACHE_DIR/$name")
                        }
                    } else {
                        // 无法解析出合法的 14 位无符号时间戳，视为历史废弃格式，直接清理
                        storage.deleteFile("$CACHE_DIR/$name")
                    }
                } else {
                    // 非法命名的散落文件直接清理
                    storage.deleteFile("$CACHE_DIR/$name")
                }
            }
        } catch (_: Exception) {
            // 清理失败静默容错，绝不影响业务主流程
        }
    }
}

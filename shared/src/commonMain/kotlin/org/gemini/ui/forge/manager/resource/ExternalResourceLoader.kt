package org.gemini.ui.forge.manager.resource

import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

/**
 * 通用外部资源加载与按需缓存管理器。
 *
 * 核心三级加载管道：
 * 1. 外部本地存储 (~/.geminiuiforge/$subDir/$fileName) 具有最高覆盖优先级；
 * 2. 内置打包资源 ($subDir/$fileName) 作为出厂预设兜底；
 * 3. 内存按需缓存 (Cache)：仅在首次被调用时懒加载并写入内存，后续直接命中；
 * 4. 写入与重置时自动失效对应内存缓存。
 */
class ExternalResourceLoader(private val storage: LocalFileStorage) {
    private val TAG = "ExternalResourceLoader"
    private val memoryCache = mutableMapOf<String, String>()

    /**
     * 按需获取文本内容（外部磁盘优先 -> 内置资源兜底 -> 内存缓存）
     *
     * @param subDir 资源子目录，例如 "prompts" 或 "skills"
     * @param fileName 文件名，包含扩展名，如 "spec_to_view.json" 或 "analyze_template.txt"
     * @param bypassCache 是否强制绕过内存缓存重新从磁盘/资源加载
     */
    @OptIn(InternalResourceApi::class)
    suspend fun loadText(subDir: String, fileName: String, bypassCache: Boolean = false): String {
        val cacheKey = "$subDir/$fileName"
        if (!bypassCache) {
            memoryCache[cacheKey]?.let { return it }
        }

        val relativePath = "$subDir/$fileName"

        // 1. 尝试从外部本地文件读取（用户自定义覆盖）
        try {
            if (storage.exists(relativePath)) {
                val cached = storage.readFromFile(relativePath)
                if (!cached.isNullOrBlank()) {
                    memoryCache[cacheKey] = cached
                    return cached
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取外部本地文件失败: $relativePath", e)
        }

        // 2. 外部无则读取内部内置打包资源出厂预设
        val defaultContent = loadDefaultResourceText(subDir, fileName)
        memoryCache[cacheKey] = defaultContent
        return defaultContent
    }

    /**
     * 仅读取应用内部打包的原始出厂资源
     */
    @OptIn(InternalResourceApi::class)
    suspend fun loadDefaultResourceText(subDir: String, fileName: String): String {
        val resourcePath = "$subDir/$fileName"
        return try {
            val bytes = readResourceBytes(resourcePath)
            val content = bytes.decodeToString()
            if (content.isBlank()) {
                throw IllegalStateException("内置资源 [$resourcePath] 内容为空！")
            }
            content
        } catch (e: Exception) {
            AppLogger.e(TAG, "内置出厂资源 [$resourcePath] 读取失败", e)
            throw IllegalStateException("内置出厂资源 [$resourcePath] 读取失败: ${e.message}", e)
        }
    }

    /**
     * 将定制内容保存到外部本地文件并更新缓存
     */
    suspend fun saveExternalText(subDir: String, fileName: String, content: String): Boolean {
        val relativePath = "$subDir/$fileName"
        return try {
            storage.saveToFile(relativePath, content)
            memoryCache["$subDir/$fileName"] = content
            AppLogger.i(TAG, "已成功保存外部本地文件并更新缓存: $relativePath")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存外部本地文件失败: $relativePath", e)
            false
        }
    }

    /**
     * 删除外部定制文件并淘汰内存缓存
     */
    suspend fun deleteExternalText(subDir: String, fileName: String): Boolean {
        val relativePath = "$subDir/$fileName"
        memoryCache.remove("$subDir/$fileName")
        return try {
            storage.deleteFile(relativePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "删除外部本地文件失败: $relativePath", e)
            false
        }
    }

    /**
     * 判断外部本地文件是否存在
     */
    suspend fun hasExternalFile(subDir: String, fileName: String): Boolean {
        return try {
            storage.exists("$subDir/$fileName")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 淘汰指定缓存
     */
    fun invalidateCache(subDir: String, fileName: String) {
        memoryCache.remove("$subDir/$fileName")
    }

    /**
     * 清空所有内存缓存
     */
    fun clearCache() {
        memoryCache.clear()
    }
}

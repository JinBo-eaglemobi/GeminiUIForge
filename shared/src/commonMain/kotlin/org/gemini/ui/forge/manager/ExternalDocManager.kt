package org.gemini.ui.forge.manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 外部参考文档记录实体
 *
 * @param fileName 文件名称 (如 UX_LAYOUT_SPEC.md)
 * @param absolutePath 本地磁盘物理绝对路径 (如 E:\UX_LAYOUT_SPEC.md)
 * @param fileSizeBytes 文件物理大小 (字节)
 * @param lastReferencedAt 最近一次被引用/拖入的时间戳
 * @param summary 文档开头摘要 (前 200 字)
 */
@Serializable
data class ExternalDocRecord(
    val fileName: String,
    val absolutePath: String,
    val fileSizeBytes: Long = 0L,
    val lastReferencedAt: Long = 0L,
    val summary: String = ""
)

/**
 * 外部参考文档统一索引管理器。
 *
 * 核心职能：
 * 1. 记忆并持久化当前工程引用过的外部磁盘文档 (templates/{projectName}/reference_docs.json)；
 * 2. 支撑 @ 联想菜单快速模糊检索外部文档；
 * 3. 支撑读取外部物理文档内容注入大模型上下文。
 */
object ExternalDocManager {
    private const val TAG = "ExternalDocManager"
    private val memoryCache = mutableMapOf<String, MutableList<ExternalDocRecord>>()

    /**
     * 获取指定工程关联的参考文档相对持久化路径。
     */
    private fun getPersistPath(projectName: String): String {
        val safeName = projectName.trim().replace(" ", "_").ifBlank { "default" }
        return "templates/$safeName/reference_docs.json"
    }

    /**
     * 登记或更新一个外部物理文档记录。
     */
    suspend fun registerDoc(
        projectName: String,
        fileName: String,
        absolutePath: String,
        fileSizeBytes: Long = 0L,
        summary: String = ""
    ): ExternalDocRecord {
        val safeName = projectName.trim().replace(" ", "_").ifBlank { "default" }
        val list = getDocsForProject(safeName).toMutableList()
        val now = getCurrentTimeMillis()

        // 去重更新
        list.removeAll { it.absolutePath.equals(absolutePath, ignoreCase = true) }
        val record = ExternalDocRecord(
            fileName = fileName,
            absolutePath = absolutePath,
            fileSizeBytes = fileSizeBytes,
            lastReferencedAt = now,
            summary = summary
        )
        list.add(0, record)
        memoryCache[safeName] = list

        // 异步物理落盘
        saveDocsForProject(safeName, list)
        return record
    }

    /**
     * 同步获取指定工程已登记的外部文档 (零协程等待，供 UI @ 联想毫秒级响应)
     */
    fun getDocsSync(projectName: String): List<ExternalDocRecord> {
        val safeName = projectName.trim().replace(" ", "_").ifBlank { "default" }
        return memoryCache[safeName] ?: emptyList()
    }

    /**
     * 获取指定工程所有已登记的外部文档。
     */
    suspend fun getDocsForProject(projectName: String): List<ExternalDocRecord> {
        val safeName = projectName.trim().replace(" ", "_").ifBlank { "default" }
        val cached = memoryCache[safeName]
        if (!cached.isNullOrEmpty()) return cached

        val storage = LocalFileStorage()
        val path = getPersistPath(safeName)
        val text = storage.readFromFile(path)
        val loaded = if (!text.isNullOrBlank()) {
            try {
                looseJson.decodeFromString<List<ExternalDocRecord>>(text)
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析工程 [$safeName] 外部文档记录失败", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        val mutable = loaded.toMutableList()
        memoryCache[safeName] = mutable
        return mutable
    }

    /**
     * 模糊检索指定工程登记过的外部文档。
     */
    suspend fun searchDocs(projectName: String, query: String): List<ExternalDocRecord> {
        val all = getDocsForProject(projectName)
        val trimmed = query.trim()
        if (trimmed.isBlank()) return all

        return all.filter {
            it.fileName.contains(trimmed, ignoreCase = true) ||
                    it.absolutePath.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * 持久化文档列表到本地磁盘。
     */
    private suspend fun saveDocsForProject(projectName: String, list: List<ExternalDocRecord>) {
        try {
            val storage = LocalFileStorage()
            val path = getPersistPath(projectName)
            val jsonText = looseJson.encodeToString(list)
            storage.saveToFile(path, jsonText)
        } catch (e: Exception) {
            AppLogger.e(TAG, "持久化工程 [$projectName] 外部文档记录异常", e)
        }
    }
}

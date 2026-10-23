package org.gemini.ui.forge.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 原始通信档案（Raw Traffic Archive）本地持久化管理器。
 *
 * 存储路径规则：
 * 1. 项目模式（UI Studio 绑定的项目会话）：`~/.geminiuiforge/templates/{proj}/sessions/{sessionId}/traffic/{seq}_{direction}.json`
 * 2. 全局模式（非项目绑定的独立场景）：`~/.geminiuiforge/sessions/{safeScope}/{sessionId}/traffic/{seq}_{direction}.json`
 *
 * 严格遵循旁路分文件存储原则与项目高内聚原则，不污染主会话 JSON，按需轻量加载。
 */
class SessionTrafficStore(
    private val storage: LocalFileStorage = LocalFileStorage(),
    private val customRootDir: String? = null,
    private val customRootDirProvider: (() -> String?)? = null
) {
    private val TAG = "SessionTrafficStore"
    private val SESSIONS_ROOT = "sessions"
    private val TRAFFIC_DIR_NAME = "traffic"

    private fun resolveCustomRootDir(): String? {
        return customRootDir ?: customRootDirProvider?.invoke()
    }

    private fun sanitizeScopeId(scopeId: String): String {
        return scopeId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default_scope" }
    }

    fun getTrafficDir(scopeId: String, sessionId: String): String {
        val root = resolveCustomRootDir()
        if (!root.isNullOrBlank()) {
            return "$root/$sessionId/$TRAFFIC_DIR_NAME"
        }
        val safeScope = sanitizeScopeId(scopeId)
        return "$SESSIONS_ROOT/$safeScope/$sessionId/$TRAFFIC_DIR_NAME"
    }

    /**
     * 追加写入一条原始通信档案。
     * 自动分配 4 位补零的自增序号并成对落盘，例如 `0001_REQ.json` / `0001_RESP.json`。
     */
    suspend fun append(record: TrafficRecord, scopeId: String, sessionId: String): Boolean = withContext(Dispatchers.Default) {
        val dir = getTrafficDir(scopeId, sessionId)
        try {
            val existingFiles = storage.listFiles(dir)
            val nextSeq = if (record.seq > 0) {
                record.seq
            } else {
                val maxSeq = existingFiles.mapNotNull { f ->
                    f.substringBefore('_').toIntOrNull()
                }.maxOrNull() ?: 0
                maxSeq + 1
            }

            val seqFormatted = nextSeq.toString().padStart(4, '0')
            val fileName = "${seqFormatted}_${record.direction.name}.json"
            val relativePath = "$dir/$fileName"

            val actualRecord = if (record.seq <= 0) record.copy(seq = nextSeq) else record
            val jsonString = looseJson.encodeToString(TrafficRecord.serializer(), actualRecord)
            storage.saveToFile(relativePath, jsonString)
            AppLogger.d(TAG, "Saved raw traffic archive [${record.direction}] to $relativePath (size: ${jsonString.length} chars)")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to append traffic record to $dir", e)
            false
        }
    }

    /**
     * 按需读取指定会话的全部原始通信档案，按序号升序排序。
     * 若当前处于全局兜底模式且默认路径下无记录，若 scopeId 为 uistudio_ 前缀，
     * 自动回退尝试读取项目私有路径 templates/{projectName}/sessions/{sessionId}/traffic/，保证向后与工具兼容。
     */
    suspend fun listRecords(scopeId: String, sessionId: String): List<TrafficRecord> = withContext(Dispatchers.Default) {
        var dir = getTrafficDir(scopeId, sessionId)
        try {
            var files = storage.listFiles(dir).filter { it.endsWith(".json") }.sorted()

            // 若未配置 customRootDir 且全局目录未找到，尝试推测并读取项目私有目录
            if (files.isEmpty() && resolveCustomRootDir().isNullOrBlank() && scopeId.startsWith("uistudio_")) {
                val projectName = scopeId.removePrefix("uistudio_").trim().replace(" ", "_")
                val fallbackDir = "templates/$projectName/sessions/$sessionId/$TRAFFIC_DIR_NAME"
                val fallbackFiles = storage.listFiles(fallbackDir).filter { it.endsWith(".json") }.sorted()
                if (fallbackFiles.isNotEmpty()) {
                    dir = fallbackDir
                    files = fallbackFiles
                }
            }

            if (files.isEmpty()) return@withContext emptyList()

            files.mapNotNull { fileName ->
                val relativePath = "$dir/$fileName"
                val content = storage.readFromFile(relativePath)
                if (content.isNullOrBlank()) null else {
                    try {
                        looseJson.decodeFromString(TrafficRecord.serializer(), content)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to parse traffic record: $relativePath, ${e.message}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list traffic records from $dir", e)
            emptyList()
        }
    }

    /**
     * 删除指定会话的旁路档案目录。
     */
    suspend fun deleteAll(scopeId: String, sessionId: String): Boolean = withContext(Dispatchers.Default) {
        val root = resolveCustomRootDir()
        val sessionDir = if (!root.isNullOrBlank()) {
            "$root/$sessionId"
        } else {
            val safeScope = sanitizeScopeId(scopeId)
            "$SESSIONS_ROOT/$safeScope/$sessionId"
        }
        try {
            val success = storage.deleteDirectory(sessionDir)
            AppLogger.i(TAG, "Deleted traffic session directory: $sessionDir, success=$success")
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete traffic directory: $sessionDir", e)
            false
        }
    }

    /**
     * 将历史遗留在全局 sessions/{legacyScopeId}/ 下的通信报文无感平滑迁移至指定目标根目录（如 templates/{proj}/sessions/）。
     * 迁移完成后安全清理全局遗留目录。
     */
    suspend fun migrateLegacyTraffic(legacyScopeId: String, targetRootDir: String): Boolean = withContext(Dispatchers.Default) {
        val safeScope = sanitizeScopeId(legacyScopeId)
        val legacyGlobalScopeDir = "$SESSIONS_ROOT/$safeScope"
        try {
            val sessionDirs = storage.listDirectories(legacyGlobalScopeDir)
            if (sessionDirs.isEmpty()) {
                // 即使没有子目录，若遗留空目录存在也尝试清理
                storage.deleteDirectory(legacyGlobalScopeDir)
                return@withContext false
            }

            var anyMigrated = false
            for (sessionId in sessionDirs) {
                val legacyTrafficDir = "$legacyGlobalScopeDir/$sessionId/$TRAFFIC_DIR_NAME"
                val files = storage.listFiles(legacyTrafficDir).filter { it.endsWith(".json") }
                if (files.isNotEmpty()) {
                    val targetTrafficDir = "$targetRootDir/$sessionId/$TRAFFIC_DIR_NAME"
                    for (file in files) {
                        val content = storage.readFromFile("$legacyTrafficDir/$file")
                        if (content != null) {
                            storage.saveToFile("$targetTrafficDir/$file", content)
                        }
                    }
                    anyMigrated = true
                }
                // 安全清理该会话遗留目录
                storage.deleteDirectory("$legacyGlobalScopeDir/$sessionId")
            }
            // 清理全局 scope 外壳目录
            storage.deleteDirectory(legacyGlobalScopeDir)
            AppLogger.i(TAG, "Migrated legacy traffic records from $legacyGlobalScopeDir to $targetRootDir, success=true")
            anyMigrated
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to migrate legacy traffic records from $legacyGlobalScopeDir to $targetRootDir: ${e.message}")
            false
        }
    }
}

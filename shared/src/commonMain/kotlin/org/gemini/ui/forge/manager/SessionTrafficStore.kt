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
 * 存储路径规则：`~/.geminiuiforge/sessions/{safeScope}/{sessionId}/traffic/{seq}_{direction}.json`
 * 严格遵循旁路分文件存储原则，不污染主会话 JSON，按需轻量加载。
 */
class SessionTrafficStore(private val storage: LocalFileStorage = LocalFileStorage()) {
    private val TAG = "SessionTrafficStore"
    private val SESSIONS_ROOT = "sessions"
    private val TRAFFIC_DIR_NAME = "traffic"

    private fun sanitizeScopeId(scopeId: String): String {
        return scopeId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default_scope" }
    }

    private fun getTrafficDir(scopeId: String, sessionId: String): String {
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
     */
    suspend fun listRecords(scopeId: String, sessionId: String): List<TrafficRecord> = withContext(Dispatchers.Default) {
        val dir = getTrafficDir(scopeId, sessionId)
        try {
            val files = storage.listFiles(dir).filter { it.endsWith(".json") }.sorted()
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
        val safeScope = sanitizeScopeId(scopeId)
        val sessionDir = "$SESSIONS_ROOT/$safeScope/$sessionId"
        try {
            val success = storage.deleteDirectory(sessionDir)
            AppLogger.i(TAG, "Deleted traffic session directory: $sessionDir, success=$success")
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete traffic directory: $sessionDir", e)
            false
        }
    }
}

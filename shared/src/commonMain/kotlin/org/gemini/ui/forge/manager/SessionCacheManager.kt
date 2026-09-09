package org.gemini.ui.forge.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 独立功能域会话缓存与持久化管理器。
 *
 * 存储路径规则：`~/.geminiuiforge/sessions/{scopeId}/{sessionId}.json`
 */
class SessionCacheManager(private val storage: LocalFileStorage) {
    private val TAG = "SessionCacheManager"
    private val SESSIONS_ROOT = "sessions"

    /**
     * 安全转义 scopeId 作为目录名
     */
    private fun sanitizeScopeId(scopeId: String): String {
        return scopeId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default_scope" }
    }

    /**
     * 保存或更新会话到本地外部缓存（仅在产生真实对话时才允许落盘）
     */
    suspend fun saveSession(session: VisualChatSession): Boolean = withContext(Dispatchers.Default) {
        // ★ 核心约束：0 轮会话（未发送任何真实用户对话）绝对不保存到磁盘
        if (session.messages.none { it.role == "user" }) {
            return@withContext true
        }

        val safeScope = sanitizeScopeId(session.scopeId)
        val relativePath = "$SESSIONS_ROOT/$safeScope/${session.id}.json"
        try {
            val jsonString = looseJson.encodeToString(VisualChatSession.serializer(), session)
            storage.saveToFile(relativePath, jsonString)
            AppLogger.d(TAG, "Saved session [${session.id}] to $relativePath")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save session: ${session.id}", e)
            false
        }
    }

    /**
     * 读取指定会话
     */
    suspend fun loadSession(scopeId: String, sessionId: String): VisualChatSession? = withContext(Dispatchers.Default) {
        val safeScope = sanitizeScopeId(scopeId)
        val relativePath = "$SESSIONS_ROOT/$safeScope/$sessionId.json"
        try {
            if (!storage.exists(relativePath)) return@withContext null
            val content = storage.readFromFile(relativePath) ?: return@withContext null
            looseJson.decodeFromString(VisualChatSession.serializer(), content)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load session $sessionId", e)
            null
        }
    }

    /**
     * 列出该功能作用域下的所有历史会话（按最后修改时间倒序排列）
     */
    suspend fun listSessions(scopeId: String): List<VisualChatSession> = withContext(Dispatchers.Default) {
        val safeScope = sanitizeScopeId(scopeId)
        val dirPath = "$SESSIONS_ROOT/$safeScope"
        try {
            val root = org.gemini.ui.forge.utils.GlobalAppEnv.currentRootPath.trimEnd('/', '\\')
            val fullDirPath = "$root/$dirPath"
            val files = org.gemini.ui.forge.utils.listFilesInLocalDirectory(fullDirPath)
            files.filter { it.endsWith(".json", ignoreCase = true) }.mapNotNull { absPath ->
                val fileName = absPath.substringAfterLast("/").substringAfterLast("\\")
                val sessionId = fileName.removeSuffix(".json")
                loadSession(scopeId, sessionId)
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to list sessions for $scopeId", e)
            emptyList()
        }
    }

    /**
     * 加载该功能作用域最后一次活跃的会话
     */
    suspend fun loadLastActiveSession(scopeId: String): VisualChatSession? {
        val sessions = listSessions(scopeId)
        return sessions.firstOrNull { it.messages.isNotEmpty() } ?: sessions.firstOrNull()
    }

    /**
     * 删除指定会话
     */
    suspend fun deleteSession(scopeId: String, sessionId: String): Boolean = withContext(Dispatchers.Default) {
        val safeScope = sanitizeScopeId(scopeId)
        val relativePath = "$SESSIONS_ROOT/$safeScope/$sessionId.json"
        try {
            storage.deleteFile(relativePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete session $sessionId", e)
            false
        }
    }

    /**
     * 创建一个新的初始会话对象
     */
    fun createNewSession(scopeId: String, title: String = "新视觉设计会话"): VisualChatSession {
        val now = getCurrentTimeMillis()
        return VisualChatSession(
            id = "sess_${now}_${(1000..9999).random()}",
            scopeId = scopeId,
            title = title,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now
        )
    }
}

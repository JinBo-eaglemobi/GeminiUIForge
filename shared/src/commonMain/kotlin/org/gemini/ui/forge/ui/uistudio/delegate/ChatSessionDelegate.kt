package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.SessionTrafficStore
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.service.ai.ChatCompressionManager
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.GlobalAppEnv
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.listFilesInLocalDirectory

/**
 * UI Studio 会话域委托：专职承载项目级 AI Chat 会话的完整生命周期管理。
 *
 * 职责范围：
 * 1. 会话 CRUD 与持久化（templates/{proj}/sessions/{sessionId}.json）；
 * 2. 会话加载自愈（历史遗留清洗、错误报文回填修复、全局外溢档案迁移）；
 * 3. 会话删除确认弹窗的状态编排；
 * 4. 聊天附件增删与当前会话消息清空确认链；
 * 5. 长对话上下文压缩与设计记忆结晶。
 *
 * 与宿主及其它领域委托解耦：通过构造注入状态读写桥、协程作用域、
 * 序列化器、通信报文存储、配置中心与 AI 引擎，实现零业务耦合平移。
 */
class ChatSessionDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val jsonHelper: Json,
    private val trafficStore: SessionTrafficStore,
    private val trafficScopeIdProvider: () -> String,
    private val configManager: ConfigManager,
    private val chatEngine: GeminiChatEngine,
) {
    /** 状态桥接属性：使方法体内 state 读写与原宿主实现零改动平移 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /** 通信归档作用域标识：动态求值（依赖当前已加载工程），杜绝构造期快照错位 */
    private val trafficScopeId: String
        get() = trafficScopeIdProvider()

    // ====== 会话删除与消息清空确认弹窗 ======

    fun requestDeleteChatSession(session: VisualChatSession) {
        state = state.copy(deletingSession = session)
    }

    fun cancelDeleteChatSession() {
        state = state.copy(deletingSession = null)
    }

    fun confirmDeleteChatSession() {
        val target = state.deletingSession ?: return
        state = state.copy(deletingSession = null)
        deleteChatSession(target.id)
    }

    fun requestClearChatMessages() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearChatMessages() {
        state = state.copy(isClearingCurrentSession = false)
        clearCurrentChatSessionMessages()
    }

    // ====== 聊天附件管理 ======

    fun addChatAttachment(path: String) {
        if (path.isNotBlank() && !state.aiChatAttachments.contains(path)) {
            state = state.copy(aiChatAttachments = state.aiChatAttachments + path)
        }
    }

    fun removeChatAttachment(path: String) {
        state = state.copy(aiChatAttachments = state.aiChatAttachments - path)
    }

    fun clearChatHistory() {
        requestClearChatMessages()
    }

    // ====== 项目专属会话存储与持久化 (templates/{proj}/sessions/{sessionId}.json) ======

    fun loadChatSessions() {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val sessionsDirRel = "templates/$sanitized/sessions"
        scope.launch(Dispatchers.Default) {
            try {
                // 1. 自动自愈迁移历史遗留在全局根目录 sessions/uistudio_xxx 的外溢通信档案至项目私有目录
                migrateLegacyGlobalTraffic(sanitized)

                val root = GlobalAppEnv.currentRootPath.trimEnd('/', '\\')
                val fullDirPath = "$root/$sessionsDirRel"
                val files = listFilesInLocalDirectory(fullDirPath)
                val loadedSessions = files.filter { it.endsWith(".json", ignoreCase = true) }.mapNotNull { absPath ->
                    try {
                        val fileName = absPath.substringAfterLast("/").substringAfterLast("\\")
                        val sessionId = fileName.removeSuffix(".json")
                        val sessionFile = TemplateFile("$sessionsDirRel/$sessionId.json")
                        val text = sessionFile.readBytes()?.decodeToString()
                        if (!text.isNullOrBlank()) {
                            jsonHelper.decodeFromString(VisualChatSession.serializer(), text)
                        } else null
                    } catch (e: Exception) {
                        AppLogger.w("UIStudioViewModel", "加载单个会话文件失败: $absPath", e)
                        null
                    }
                }.sortedByDescending { it.updatedAt }

                if (loadedSessions.isNotEmpty()) {
                    val healedSessions = healSessionErrors(loadedSessions)
                    val activeId = state.currentChatSessionId ?: healedSessions.first().id
                    state = state.copy(
                        chatSessions = healedSessions,
                        currentChatSessionId = activeId
                    )
                    return@launch
                }

                // 首次进入无会话，生成默认初始会话并持久化
                val now = getCurrentTimeMillis()
                val initialSession = VisualChatSession(
                    id = "session_${now}",
                    scopeId = sanitized,
                    title = "主界面架构与排版规划",
                    messages = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )
                val newSessions = listOf(initialSession)
                state = state.copy(
                    chatSessions = newSessions,
                    currentChatSessionId = initialSession.id
                )
                saveSession(initialSession)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "加载项目 Chat 会话失败", e)
            }
        }
    }

    fun saveSession(session: VisualChatSession) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/${session.id}.json"
        scope.launch(Dispatchers.Default) {
            try {
                val text = jsonHelper.encodeToString(VisualChatSession.serializer(), session)
                TemplateFile(path).writeBytes(text.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "保存会话文件失败: ${session.id}", e)
            }
        }
    }

    private suspend fun healSessionErrors(sessions: List<VisualChatSession>): List<VisualChatSession> {
        return sessions.map { session ->
            // 若该会话仅有 1 条消息且为历史生成的欢迎消息，将其过滤清洗为纯净空会话
            val isSingleLegacyWelcome = session.messages.size == 1 &&
                    session.messages.first().let { it.id.contains("welcome") || it.prompt.contains("已开启新的排版讨论会话") || it.prompt.contains("UI Studio 智能设计助理") || it.prompt.contains("UI Studio 智能排版设计助理") }
            if (isSingleLegacyWelcome) {
                val cleaned = session.copy(messages = emptyList())
                saveSession(cleaned)
                return@map cleaned
            }

            val hasUnresolvedError = session.messages.any { msg ->
                msg.role == "model" && (msg.effectiveText.isBlank() || msg.isError) && msg.errorMessage.isNullOrBlank()
            }
            if (!hasUnresolvedError) return@map session

            val records = trafficStore.listRecords(trafficScopeId, session.id)
            val respErrors = records.filter { it.direction == TrafficDirection.RESP }.mapNotNull { r ->
                try {
                    val root = jsonHelper.parseToJsonElement(r.body).jsonObject
                    val errObj = root["error"]?.jsonObject
                    if (errObj != null) {
                        val msg = errObj["message"]?.jsonPrimitive?.contentOrNull ?: "未知服务端错误"
                        val code = errObj["code"]?.jsonPrimitive?.contentOrNull ?: errObj["status"]?.jsonPrimitive?.contentOrNull
                        val formatted = if (!code.isNullOrBlank()) "[$code] $msg" else msg
                        r.timestamp to formatted
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            if (respErrors.isEmpty()) return@map session

            var sessionHealed = false
            val updatedMessages = session.messages.map { msg ->
                if (msg.role == "model" && (msg.effectiveText.isBlank() || msg.isError) && msg.errorMessage.isNullOrBlank()) {
                    val matchedErr = respErrors.minByOrNull { kotlin.math.abs(it.first - msg.timestamp) }?.second
                    if (matchedErr != null) {
                        sessionHealed = true
                        msg.copy(isError = true, errorMessage = matchedErr)
                    } else msg
                } else msg
            }

            val healedSession = if (sessionHealed) session.copy(messages = updatedMessages) else session
            if (sessionHealed) {
                saveSession(healedSession)
            }
            healedSession
        }
    }

    /**
     * 自愈迁移历史遗留在全局根目录 sessions/uistudio_{projectId} 下的外溢通信档案至项目私有目录。
     */
    private suspend fun migrateLegacyGlobalTraffic(sanitizedProjId: String) {
        val legacyScopeId = "uistudio_$sanitizedProjId"
        val targetRootDir = "templates/$sanitizedProjId/sessions"
        try {
            trafficStore.migrateLegacyTraffic(legacyScopeId, targetRootDir)
        } catch (e: Exception) {
            AppLogger.w("UIStudioViewModel", "自愈迁移全局遗留通信报文失败: ${e.message}")
        }
    }

    // ====== 会话切换、新建与删除 ======

    fun createNewChatSession(title: String = "新布局讨论") {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val now = getCurrentTimeMillis()
        val newSession = VisualChatSession(
            id = "session_${now}",
            scopeId = sanitized,
            title = title,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val updatedSessions = listOf(newSession) + state.chatSessions
        state = state.copy(
            chatSessions = updatedSessions,
            currentChatSessionId = newSession.id
        )
        saveSession(newSession)
        Toast.show("已创建新会话: $title", ToastType.SUCCESS)
    }

    fun selectChatSession(sessionId: String) {
        state = state.copy(currentChatSessionId = sessionId)
    }

    fun deleteChatSession(sessionId: String) {
        val proj = state.project ?: return
        val sanitized = proj.projectId.trim().replace(" ", "_")
        val path = "templates/$sanitized/sessions/$sessionId.json"
        scope.launch(Dispatchers.Default) {
            try {
                TemplateFile(path).delete()
                // 同步彻底清理该会话在项目目录内的通信报文归档 (traffic/)
                trafficStore.deleteAll(trafficScopeId, sessionId)
            } catch (e: Exception) {
                AppLogger.w("UIStudioViewModel", "删除会话文件失败: $sessionId", e)
            }
        }

        val remaining = state.chatSessions.filter { it.id != sessionId }
        val newSessions = if (remaining.isEmpty()) {
            val now = getCurrentTimeMillis()
            val initial = VisualChatSession(
                id = "session_${now}",
                scopeId = sanitized,
                title = "主界面排版讨论",
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
            saveSession(initial)
            listOf(initial)
        } else remaining
        val nextActiveId = if (state.currentChatSessionId == sessionId) newSessions.first().id else state.currentChatSessionId
        state = state.copy(
            chatSessions = newSessions,
            currentChatSessionId = nextActiveId
        )
        Toast.show("会话已删除", ToastType.INFO)
    }

    fun clearCurrentChatSessionMessages() {
        val activeSession = state.currentChatSession ?: return
        val updated = activeSession.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        val updatedSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        state = state.copy(chatSessions = updatedSessions)
        saveSession(updated)
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }

    fun updateChatSearchQuery(query: String) {
        state = state.copy(chatSearchQuery = query)
    }

    // ====== 长对话上下文压缩 ======

    /**
     * 手动触发长对话上下文压缩与设计记忆结晶
     */
    fun compressCurrentSession() {
        val currentSession = state.currentChatSession ?: return
        if (currentSession.messages.size <= ChatCompressionManager.PRESERVE_RECENT_ROUNDS) {
            Toast.show("当前会话消息较少，无需压缩", ToastType.INFO)
            return
        }

        scope.launch(Dispatchers.Default) {
            val effectiveApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey() ?: ""
            if (effectiveApiKey.isBlank()) {
                Toast.show("请先在设置中配置有效 API Key", ToastType.ERROR)
                return@launch
            }
            Toast.show("正在提炼设计记忆并压缩上下文...", ToastType.INFO)
            try {
                val updatedSession = ChatCompressionManager.compressSession(
                    session = currentSession,
                    apiFlavor = state.projectAgentConfig.apiFlavor,
                    chatEngine = chatEngine,
                    apiKey = effectiveApiKey,
                    customSystemPrompt = state.projectAgentConfig.systemPrompt
                )
                state = state.copy(
                    chatSessions = state.chatSessions.map {
                        if (it.id == updatedSession.id) updatedSession else it
                    }
                )
                saveSession(updatedSession)
                val stats = ChatCompressionManager.calculateStats(updatedSession)
                Toast.show("长对话压缩成功！已提炼设计记忆，预估节省约 ${stats.estimatedTokensSaved} Tokens", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "长对话压缩失败", e)
                Toast.show("长对话压缩失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    // ====== 当前会话清理（清空会话消息） ======

    fun requestClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = true)
    }

    fun cancelClearCurrentSession() {
        state = state.copy(isClearingCurrentSession = false)
    }

    fun confirmClearCurrentSession() {
        val current = state.currentChatSession ?: return
        val updated = current.copy(messages = emptyList(), updatedAt = getCurrentTimeMillis())
        state = state.copy(
            isClearingCurrentSession = false,
            chatSessions = state.chatSessions.map { if (it.id == updated.id) updated else it }
        )
        saveSession(updated)
        scope.launch(Dispatchers.Default) {
            trafficStore.deleteAll(trafficScopeId, current.id)
        }
        Toast.show("当前会话消息已清空", ToastType.INFO)
    }
}

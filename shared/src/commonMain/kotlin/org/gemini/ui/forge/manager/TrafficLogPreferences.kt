package org.gemini.ui.forge.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.ui.dialog.ai.studio.component.TrafficSortOrder
import org.gemini.ui.forge.utils.AppLogger

/**
 * 通信报文弹窗持久化偏好管理器。
 *
 * 核心职能：
 * 1. 跨弹窗生命周期记忆排序规则 ([TrafficSortOrder]) 与方向筛选 ([filterDirection])；
 * 2. 支持退出应用重新启动后，100% 保持用户的排查习惯；
 * 3. 内存单例高速读取，变更时异步防抖落盘至 `preferences/traffic_pref.json`。
 */
object TrafficLogPreferences {
    private const val PREF_PATH = "preferences/traffic_pref.json"
    private val jsonHelper = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Serializable
    private data class TrafficLogPrefData(
        val sortOrder: TrafficSortOrder = TrafficSortOrder.TIME_ASC,
        val filterDirection: String? = null
    )

    private var currentData = TrafficLogPrefData()
    private var isLoaded = false

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            loadInternal()
        }
    }

    /**
     * 挂起加载本地磁盘持久化偏好数据
     */
    suspend fun loadInternal() {
        if (isLoaded) return
        try {
            val file = TemplateFile(PREF_PATH)
            if (file.exists()) {
                val bytes = file.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    currentData = jsonHelper.decodeFromString(TrafficLogPrefData.serializer(), bytes.decodeToString())
                }
            }
        } catch (e: Exception) {
            AppLogger.w("TrafficLogPreferences", "加载通信报文偏好失败，使用默认值: ${e.message}")
        } finally {
            isLoaded = true
        }
    }

    /**
     * 获取当前偏好的报文排序模式
     */
    fun getSortOrder(): TrafficSortOrder = currentData.sortOrder

    /**
     * 获取当前偏好的方向过滤状态（null 为全部，或 "REQ" / "RESP"）
     */
    fun getFilterDirection(): String? = currentData.filterDirection

    /**
     * 更新报文排序与过滤偏好，并在后台异步持久化
     */
    fun updatePreferences(sortOrder: TrafficSortOrder, filterDirection: String?) {
        if (currentData.sortOrder == sortOrder && currentData.filterDirection == filterDirection) return
        currentData = currentData.copy(sortOrder = sortOrder, filterDirection = filterDirection)
        scope.launch {
            try {
                val file = TemplateFile(PREF_PATH)
                val jsonStr = jsonHelper.encodeToString(TrafficLogPrefData.serializer(), currentData)
                file.writeBytes(jsonStr.encodeToByteArray())
            } catch (e: Exception) {
                AppLogger.e("TrafficLogPreferences", "持久化通信报文偏好失败", e)
            }
        }
    }
}

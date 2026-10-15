package org.gemini.ui.forge.ui.component.chat.scroll

/**
 * 全局会话级滚动位置记忆管理器。
 *
 * 核心职能：
 * 1. 跨弹窗生命周期记录各个会话 ([VisualChatSession.id]) 的最后物理像素滚动偏移；
 * 2. 支持退出聊天弹窗或切换会话后再进入时，精准恢复至上次浏览的位置；
 * 3. 对无历史记录的会话标记为 null，驱动首次进入时默认平滑/直接吸底呈现最新消息。
 */
object ChatScrollMemoryManager {
    private val scrollPositions = mutableMapOf<String, Int>()

    /**
     * 获取指定会话缓存的物理滚动像素偏移。
     *
     * @param sessionId 会话唯一标识
     * @return 缓存的物理滚动偏移量 (px)，若无历史记录返回 null
     */
    fun getScrollOffset(sessionId: String?): Int? {
        if (sessionId.isNullOrBlank()) return null
        return scrollPositions[sessionId]
    }

    /**
     * 保存指定会话当前的物理滚动像素偏移。
     *
     * @param sessionId 会话唯一标识
     * @param offset 当前视口滚动的绝对像素量
     */
    fun saveScrollOffset(sessionId: String?, offset: Int) {
        if (!sessionId.isNullOrBlank()) {
            scrollPositions[sessionId] = offset
        }
    }

    /**
     * 清理指定会话的滚动记录 (如删除会话或清空消息时)。
     */
    fun clear(sessionId: String?) {
        if (!sessionId.isNullOrBlank()) {
            scrollPositions.remove(sessionId)
        }
    }

    /**
     * 清理所有会话的滚动记忆。
     */
    fun clearAll() {
        scrollPositions.clear()
    }
}

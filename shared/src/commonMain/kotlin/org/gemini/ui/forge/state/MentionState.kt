package org.gemini.ui.forge.state

import androidx.compose.runtime.*
import org.gemini.ui.forge.model.chat.MentionItem

/**
 * AI 输入栏「@」上下文引用状态管理中心
 */
@Stable
class MentionState {
    /** 联想菜单浮层是否展开 */
    var isMentionMenuVisible by mutableStateOf(false)

    /** 当前检索关键词（光标跟随 @ 提取） */
    var searchQuery by mutableStateOf("")

    /** 键盘上下键当前聚焦高亮的条目索引 */
    var selectedIndex by mutableStateOf(0)

    /** 当前已挂载并待发送的上下文实体药丸列表 */
    val attachedMentions = mutableStateListOf<MentionItem>()

    /** 打开联想面板 */
    fun openMenu(initialQuery: String = "") {
        searchQuery = initialQuery
        selectedIndex = 0
        isMentionMenuVisible = true
    }

    /** 关闭联想面板 */
    fun closeMenu() {
        isMentionMenuVisible = false
        searchQuery = ""
        selectedIndex = 0
    }

    /** 挂载一个上下文实体 */
    fun attachMention(item: MentionItem) {
        if (attachedMentions.none { it.id == item.id }) {
            attachedMentions.add(item)
        }
        closeMenu()
    }

    /** 移除已挂载实体 */
    fun removeMention(item: MentionItem) {
        attachedMentions.remove(item)
    }

    /** 清空全部已挂载实体（消息发送后调用） */
    fun clearMentions() {
        attachedMentions.clear()
    }
}

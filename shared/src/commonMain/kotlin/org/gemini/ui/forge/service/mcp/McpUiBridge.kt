package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * MCP 触发的 UI 联动事件
 */
sealed interface McpUiEvent {
    /** 切换并加载模板工程至工作区 */
    data class NavigateToProject(val projectName: String, val projectState: ProjectState? = null) : McpUiEvent
    /** 选中并高亮某个图元 */
    data class SelectBlock(val blockId: String) : McpUiEvent
    /** 刷新工作区与工程模板列表 */
    data object RefreshWorkspace : McpUiEvent
}

/**
 * MCP 与 UI 界面的双向响应式联动桥梁 (单例)
 * 当用户在设置中开启 "AI 工具执行界面跟随" 时，AI 通过 MCP 工具对工程做出的修改将实时驱动 UI 跳转与聚焦
 */
object McpUiBridge {
    private val _events = MutableSharedFlow<McpUiEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<McpUiEvent> = _events.asSharedFlow()

    /**
     * 通知主界面：新模板工程已由 AI 生成，请求加载并导航至工作区
     */
    fun notifyTemplateCreated(projectName: String, projectState: ProjectState) {
        _events.tryEmit(McpUiEvent.NavigateToProject(projectName, projectState))
    }

    /**
     * 通知主界面：图元已被 AI 修改/微调/移动，请求聚焦高亮
     */
    fun notifyBlockSelected(blockId: String) {
        _events.tryEmit(McpUiEvent.SelectBlock(blockId))
    }

    /**
     * 通知主界面：刷新工程模板列表
     */
    fun notifyRefresh() {
        _events.tryEmit(McpUiEvent.RefreshWorkspace)
    }
}
